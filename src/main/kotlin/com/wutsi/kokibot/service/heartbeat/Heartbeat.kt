package com.wutsi.kokibot.service.heartbeat

import com.wutsi.kokibot.ConfigurationException
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
import java.util.concurrent.atomic.AtomicBoolean

class Heartbeat() : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Heartbeat::class.java)

        const val ID = "service:heartbeat"
        const val DEFAULT_FREQUENCY = "1h"
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var context: Context
    private var job: ScheduledFuture<*>? = null
    private var frequency: String = DEFAULT_FREQUENCY
    private val running: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var enabled: Boolean = true

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.enabled = MapUtil.toBoolean("enabled", config) ?: true
        this.frequency = MapUtil.toString("frequency", config) ?: DEFAULT_FREQUENCY

        if (enabled) {
            job = launchJob(frequency)
        } else {
            LOGGER.info("Heartbeat is disabled")
        }
    }

    override fun destroy() {
        job?.cancel(false)
        scheduler.shutdown()
    }

    fun tick() {
        if (!enabled) {
            LOGGER.info("Heartbeat is disabled. Skipping tick.")
            return
        }
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Heartbeat is already running. Skipping tick.")
            return
        }
        try {
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
        } finally {
            running.set(false)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun getFrequency(): String = frequency

    fun getInstructions(): String? {
        val file = File(context.home, "HEARTBEAT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    private fun setInstructions(content: String) {
        val file = File(context.home, "HEARTBEAT.md")
        file.writeText(content)
    }

    @Synchronized
    fun apply(key: String, value: Any) {
        when (key) {
            "enabled" -> {
                enabled = value.toString().toBoolean()
                if (enabled) {
                    if (job == null) {
                        job = launchJob(frequency)
                    }
                } else {
                    job?.cancel(false)
                    job = null
                }
            }

            "frequency" -> {
                frequency = value.toString()
                job?.cancel(false)
                job = if (enabled) launchJob(frequency) else null
            }

            "instructions" -> setInstructions(value.toString())

            else -> throw ConfigurationException("Unknown heartbeat setting: $key")
        }
    }

    private fun launchJob(frequencyMinutes: String): ScheduledFuture<*> {
        val heartbeat = this
        val task = Runnable {
            LOGGER.info("Heartbeat tick")
            try {
                heartbeat.tick()
            } catch (e: Exception) {
                LOGGER.error("Failed to launch heartbeat", e)
            }
        }

        val delay = DurationUtil.millis(frequencyMinutes, DurationUtil.ONE_HOUR / 2)
        LOGGER.info("Scheduling Heartbeat every ${frequencyMinutes}m ($delay ms)")
        return scheduler.scheduleAtFixedRate(task, delay, delay, TimeUnit.MILLISECONDS)
    }
}
