package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This is the long term memory of the assistant, which is used to store facts and information that can be used to answer questions.
 * It's build by compacting the chat history, and extracting facts and information that can be used to answer questions.
 * The long term memory is stored into workspace/memory/MEMORY.md
 *
 * Thread-safety: `compact()` and `get()` are serialized by a [ReentrantLock] so that
 * concurrent invocations (scheduler tick + `/compact` command) cannot interleave their
 * read-modify-write of `MEMORY.md`. The underlying chat history reads are additionally
 * guarded by [ChatHistory]'s own lock. File writes are atomic (temp file + atomic move).
 */
class Memory : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Memory::class.java)
        const val DEFAULT_WINDOW = 3L
        const val DEFAULT_COMPACTION_FREQUENCY = "6h"
        private const val DEFAULT_MAX_LENGTH = 2000
        private val MAX_FAILURES_BEFORE_ALERT = 3
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val lock = ReentrantLock()
    private var window: Long = DEFAULT_WINDOW
    private var maxLength: Int = DEFAULT_MAX_LENGTH
    private lateinit var context: Context
    private lateinit var job: ScheduledFuture<*>
    private var consecutiveFailures: Int = 0

    override fun id(): String {
        return "service:memory"
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
        this.maxLength = MapUtil.toInt("max-length", config) ?: DEFAULT_MAX_LENGTH
        this.window = MapUtil.toString("window", config)
            ?.let { value ->
                DurationUtil.days(value, DEFAULT_WINDOW)
            } ?: DEFAULT_WINDOW

        val frequency = MapUtil.toString("compaction-frequency", config) ?: DEFAULT_COMPACTION_FREQUENCY
        job = launchJob(frequency)
    }

    override fun destroy() {
        job.cancel(false)

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

    fun get(): String? = lock.withLock {
        val file = getFile()
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun compact() = lock.withLock {
        val to = LocalDate.now()
        val from = to.minusDays(window)
        val merged = context.chatHistory.merge(from, to)
        val compacted = merged?.let { compact(merged) }
        if (compacted != null) {
            atomicWrite(getFile(), compacted)
        }
    }

    private fun launchJob(frequency: String): ScheduledFuture<*> {
        val memory = this
        val task = Runnable {
            LOGGER.info("Running memory compaction job...")
            val now = System.currentTimeMillis()
            try {
                memory.compact()
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

    private fun compact(history: String): String {
        val memory = get()
        val prompt = this::class.java.getResourceAsStream("/prompts/memory.prompt.md")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException(
                "Memory compaction prompt template not found at /prompts/memory.prompt.md. " +
                    "This is a build configuration error."
            )

        val finalPrompt = prompt
            .replace("{{history}}", history)
            .replace("{{memory}}", (memory ?: ""))
            .replace("{{max_length}}", maxLength.toString())

        // Retry LLM call with exponential backoff
        val response = com.wutsi.kokibot.util.RetryConfig.llm().execute(
            onRetry = { attempt, exception ->
                LOGGER.warn(
                    "Memory compaction LLM call failed (attempt $attempt): ${exception.message}. Retrying..."
                )
            }
        ) {
            context.llm.completion(LLMRequest(prompt = finalPrompt), emptyList())
        }

        val content = response.choices.firstOrNull()?.content
            ?: throw IllegalStateException("No result from LLM")

        // Validate compacted memory length
        if (content.length > maxLength * 2) {
            LOGGER.warn(
                "Compacted memory exceeds recommended length: ${content.length} chars > ${maxLength * 2} chars. " +
                    "Truncating to $maxLength chars."
            )
            return content.take(maxLength)
        }

        return content
    }

    private fun atomicWrite(target: File, content: String) {
        val targetPath = target.toPath()
        val tmp = Files.createTempFile(targetPath.parent, target.name, ".tmp")
        try {
            Files.writeString(tmp, content)
            try {
                Files.move(tmp, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun getFile(): File {
        val dir = File(context.home, "memory")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "MEMORY.md")
    }
}
