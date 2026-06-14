package com.wutsi.kokibot.service.heartbeat

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Heartbeat() : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Heartbeat::class.java)

        const val ID = "service:heartbeat"
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var context: Context
    private var job: ScheduledFuture<*>? = null

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        val frequency = MapUtil.toString("frequency", config)?.ifEmpty { null }

        frequency?.let {
            job = launchJob(frequency)
        }
    }

    override fun destroy() {
        job?.cancel(false)
    }

    fun tick() {
        LOGGER.info("Tick")

        val query = getInstructions()
        if (!query.isNullOrEmpty()) {
            context.assistant.process(
                Message(
                    userId = System.getProperty("user.name"),
                    channelId = id(),
                    text = query,
                    role = Role.SYSTEM,
                )
            )
        }
    }

    fun getInstructions(): String? {
        val file = File(context.home, "HEARTBEAT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    fun saveInstructions(content: String) {
        val file = File(context.home, "HEARTBEAT.md")
        file.writeText(content)
    }

    private fun launchJob(frequency: String): ScheduledFuture<*> {
        val heartbeat = this
        val task = Runnable {
            LOGGER.info("Heartbeat tick")
            heartbeat.tick()
        }

        val delay = DurationUtil.millis(frequency, DurationUtil.ONE_HOUR)
        LOGGER.info("Scheduling Heartbeat every $frequency ($delay ms)")
        return scheduler.scheduleAtFixedRate(task, delay, delay, TimeUnit.MILLISECONDS)
    }
}
