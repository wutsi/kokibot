package com.wutsi.kokibot.service.inbox

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.llm.LLMStreamData
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class InboxPoller(private val inbox: Inbox) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(InboxPoller::class.java)
        const val ID = "service:inbox-poller"
        const val DEFAULT_FREQUENCY = "30s"
        const val DEFAULT_MAX_WIP = 2
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val running = AtomicBoolean(false)
    private lateinit var context: Context
    private var job: ScheduledFuture<*>? = null
    private var maxWip: Int = DEFAULT_MAX_WIP

    override fun id() = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        val frequency = MapUtil.toString("frequency", config) ?: DEFAULT_FREQUENCY
        maxWip = MapUtil.toInt("max-wip", config) ?: DEFAULT_MAX_WIP
        job = launchJob(frequency)
        LOGGER.info("InboxPoller")
        LOGGER.info("  frequency=$frequency")
        LOGGER.info("  max-wip=$maxWip")
    }

    override fun destroy() {
        job?.cancel(false)
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                LOGGER.warn("Scheduler didn't terminate gracefully, forcing shutdown")
                scheduler.shutdownNow()
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.error("Scheduler failed to terminate")
                }
            }
        } catch (_: InterruptedException) {
            LOGGER.warn("Interrupted while waiting for scheduler shutdown")
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    fun tick() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("InboxPoller already running — skipping tick")
            return
        }
        try {
            while (inbox.processingCount() < maxWip) {
                val message = inbox.poll() ?: break
                process(message)
            }
            if (inbox.processingCount() >= maxWip) {
                LOGGER.info("WIP limit reached ($maxWip) — deferring remaining pending messages")
            }
        } finally {
            running.set(false)
        }
    }

    private fun process(inboxMessage: InboxMessage) {
        val channel = resolveChannel(inboxMessage.channelId)
        val query = Message(
            id = inboxMessage.id,
            channelId = inboxMessage.channelId,
            userId = inboxMessage.userId,
            text = inboxMessage.text,
            filePaths = inboxMessage.filePaths,
            subject = inboxMessage.subject,
            conversationId = inboxMessage.conversationId,
            role = Role.USER,
        )
        try {
            val response = context.assistant.process(query, streamCallback(inboxMessage, channel))
            inbox.complete(inboxMessage.id, response.text)
            deliver(inboxMessage, response, channel)
        } catch (e: Exception) {
            LOGGER.error("Failed to process message ${inboxMessage.id}", e)
            inbox.fail(inboxMessage.id, e.message ?: "Unknown error")
        }
    }

    private fun streamCallback(inboxMessage: InboxMessage, channel: Channel?): ((LLMStreamData) -> Unit)? {
        channel ?: return null
        return { data ->
            try {
                channel.sendStatus(
                    Message(
                        channelId = inboxMessage.channelId,
                        userId = inboxMessage.userId,
                        text = data.text,
                        role = Role.ASSISTANT,
                    )
                )
            } catch (e: Exception) {
                LOGGER.warn("Failed to send stream update for ${inboxMessage.id}: ${e.message}")
            }
        }
    }

    private fun deliver(inboxMessage: InboxMessage, response: Message, channel: Channel?) {
        channel ?: return
        try {
            channel.send(
                Message(
                    channelId = inboxMessage.channelId,
                    userId = inboxMessage.userId,
                    subject = inboxMessage.subject?.let { "Re: $it" },
                    text = response.text,
                    role = Role.ASSISTANT,
                )
            )
        } catch (e: Exception) {
            LOGGER.warn("Failed to deliver response for ${inboxMessage.id}: ${e.message}")
        }
    }

    private fun resolveChannel(channelId: String?): Channel? {
        channelId ?: return null
        return try {
            context.channelRegistry.get(channelId)
        } catch (e: Exception) {
            LOGGER.warn("Channel $channelId not found: ${e.message}")
            null
        }
    }

    private fun launchJob(frequency: String): ScheduledFuture<*> {
        val poller = this
        val task = Runnable {
            try {
                poller.tick()
            } catch (e: Exception) {
                LOGGER.error("InboxPoller tick failed", e)
            }
        }
        val delay = DurationUtil.millis(frequency, 5 * DurationUtil.ONE_SECOND)
        return scheduler.scheduleAtFixedRate(task, delay, delay, TimeUnit.MILLISECONDS)
    }
}
