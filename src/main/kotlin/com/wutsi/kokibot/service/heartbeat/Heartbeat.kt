package com.wutsi.kokibot.service.heartbeat

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
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

class Heartbeat(private val assistant: Assistant) : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Heartbeat::class.java)

        const val ID = "service:heartbeat"
        const val DEFAULT_FREQUENCY = "1h"
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var context: Context
    private lateinit var job: ScheduledFuture<*>

    override fun id(): String {
        return ID
    }

    override fun health(): Health {
        return Health(id = id(), up = true)
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        val frequency = MapUtil.toString("frequency", config) ?: DEFAULT_FREQUENCY

        job = launchJob(frequency)
    }

    override fun destroy() {
        job.cancel(false)
    }

    fun tick() {
        LOGGER.info("Tick")

        val file = File(context.home, "HEARTBEAT.md")
        if (!file.exists()) {
            LOGGER.debug("No heartbeat file found, skipping")
            return
        }

        val query = file.readText()
        if (query.isNotEmpty()) {
            assistant.process(
                Message(
                    userId = id(),
                    text = query,
                    role = Role.SYSTEM,
                ),
                interactive = false,
            )
        }
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
