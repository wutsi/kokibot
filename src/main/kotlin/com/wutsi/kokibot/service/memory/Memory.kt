package com.wutsi.kokibot.service.memory

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
import java.util.concurrent.locks.ReentrantLock

/**
 * This is the long term memory of the assistant, which is used to store facts and information that can be used to answer questions.
 * It's build by compacting the chat history, and extracting facts and information that can be used to answer questions.
 * The long term memory is stored into workspace/memory/MEMORY.md
 *
 * Thread-safety: `compact()` and `get()` are serialized by a [ReentrantLock] so that
 * concurrent invocations (scheduler tick + `/compact` command) cannot interleave their
 * read-modify-write of `MEMORY.md`. The underlying chat history reads are additionally
 * guarded by [DailyLog]'s own lock. File writes are atomic (temp file + atomic move).
 */
class Memory : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Memory::class.java)
        const val ID = "service:memory"
        const val DEFAULT_WINDOW = 7L
        const val DEFAULT_COMPACTION_FREQUENCY = "6h"
        private const val DEFAULT_MAX_LENGTH = 10240
        private val MAX_FAILURES_BEFORE_ALERT = 3
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var enabled: Boolean = true
    private var window: Long = DEFAULT_WINDOW
    private var maxLength: Int = DEFAULT_MAX_LENGTH
    private var compactionFrequency: String = DEFAULT_COMPACTION_FREQUENCY
    private lateinit var context: Context
    private var job: ScheduledFuture<*>? = null
    private var consecutiveFailures: Int = 0

    override fun id(): String {
        return ID
    }

    /**
     * Initialize the memory with the given configuration and context.
     *
     * The configuration can contain the following parameters:
     * - window: the number of days to look back when compacting the memory (default: 3d)
     * - compaction-frequency: the frequency to run the compaction job (ex: 1h, 30m, 1d - default: 6h)
     */
    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.enabled = MapUtil.toBoolean("enabled", config) ?: true
        this.maxLength = MapUtil.toInt("max-length", config) ?: DEFAULT_MAX_LENGTH
        this.window = MapUtil.toString("window", config)
            ?.let { value ->
                DurationUtil.days(value, DEFAULT_WINDOW)
            } ?: DEFAULT_WINDOW

        compactionFrequency = MapUtil.toString("compaction-frequency", config) ?: DEFAULT_COMPACTION_FREQUENCY
        if (enabled) {
            job = launchJob(compactionFrequency)
        } else {
            LOGGER.info("Memory compaction is disabled")
        }
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

    fun isEnabled(): Boolean = enabled

    fun getWindow(): Long = window

    fun getMaxLength(): Int = maxLength

    fun getCompactionFrequency(): String = compactionFrequency

    fun get(): String? {
        val file = getFile()
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun compact() {
        if (!enabled) {
            LOGGER.info("Memory compaction is disabled, skipping")
            return
        }

        val prompt = this::class.java.getResourceAsStream("/instructions/MEMORY.md")!!
            .bufferedReader()
            .readText()
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{DAYS}}", window.toString())
            .replace("{{MAX_LENGTH}}", maxLength.toString())

        context.assistant.process(
            query = Message(
                role = Role.SYSTEM,
                text = prompt,
            ),
        )
    }

    fun apply(key: String, value: Any) {
        when (key) {
            "enabled" -> {
                enabled = value.toString().toBoolean()
                if (enabled) {
                    job = launchJob(compactionFrequency)
                } else {
                    job?.cancel(false)
                    job = null
                    LOGGER.info("Memory compaction is disabled")
                }
            }

            "window" -> window = DurationUtil.days(value.toString(), DEFAULT_WINDOW)

            "max-length" -> maxLength = value.toString().toIntOrNull()
                ?: throw ConfigurationException("Invalid value for max-length: $value")

            "compaction-frequency" -> {
                compactionFrequency = value.toString()
                job?.cancel(false)
                job = if (enabled) launchJob(compactionFrequency) else null
            }

            else -> throw ConfigurationException("Unknown memory setting: $key")
        }
    }

    private fun launchJob(frequency: String): ScheduledFuture<*> {
        val task = Runnable {
            LOGGER.info("Running memory compaction job...")
            val now = System.currentTimeMillis()
            try {
                compact()
                consecutiveFailures = 0
                LOGGER.info("Memory compaction completed successfully in ${(System.currentTimeMillis() - now) / 1000} s")
            } catch (ex: Throwable) {
                consecutiveFailures++
                LOGGER.error("Memory compaction failed", ex)

                if (consecutiveFailures > MAX_FAILURES_BEFORE_ALERT) {
                    LOGGER.error("Memory compaction has failed $consecutiveFailures times in a row. Memory is stalled")
                }
            }
        }

        val delay = DurationUtil.millis(frequency, DurationUtil.ONE_HOUR)
        LOGGER.info("Scheduling memory compaction every $frequency ($delay ms)")
        return scheduler.scheduleAtFixedRate(task, delay, delay, TimeUnit.MILLISECONDS)
    }

    private fun getFile(): File {
        val dir = File(context.home, "memory")
        return File(dir, "MEMORY.md")
    }
}
