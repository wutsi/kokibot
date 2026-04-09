package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * This is the long term memory of the assistant, which is used to store facts and information that can be used to answer questions.
 * It's build by compacting the chat history, and extracting facts and information that can be used to answer questions.
 */
class Memory {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Memory::class.java)
        const val DEFAULT_WINDOW = 3
        const val DEFAULT_COMPACTION_FREQUENCY = 6L
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var window: Int = DEFAULT_WINDOW
    private lateinit var context: Context
    private lateinit var compactJob: ScheduledFuture<*>

    fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.window = MapUtil.toInt("window", config) ?: DEFAULT_WINDOW

        val frequency = MapUtil.toLong("compaction-frequency", config) ?: DEFAULT_COMPACTION_FREQUENCY
        compactJob = launchCompactJob(frequency)
    }

    fun destroy() {
        compactJob.cancel(true)
    }

    fun get(): String? {
        val file = getFile()
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun compact() {
        /* Compacting the memory */
        val to = LocalDate.now()
        val from = to.minusDays(window.toLong())
        val merged = context.chatHistory.merge(from, to)
        val compacted = merged?.let { compact(merged) }
        if (compacted != null) {
            val file = getFile()
            file.writeText(compacted)

            /* Clear the short term memory */
            context.chatHistory.clear()
        }
    }

    private fun launchCompactJob(frequency: Long): ScheduledFuture<*> {
        val memory = this
        val task = Runnable {
            LOGGER.info("Running memory compaction job...")
            memory.compact()
        }

        LOGGER.info("Scheduling memory compaction job to run every $frequency hours...")
        return scheduler.scheduleAtFixedRate(task, frequency, frequency, TimeUnit.HOURS)
    }

    private fun compact(history: String): String {
        val memory = get()
        val prompt = this::class.java.getResourceAsStream("/prompts/memory.prompt.md")!!
            .bufferedReader()
            .readText()
            .replace("{{history}}", history)
            .replace("{{memory}}", (memory ?: ""))

        val response = context.llm.completion(LLMRequest(prompt = prompt))
        return response.choices.firstOrNull()?.content ?: ""
    }

    private fun getFile(): File {
        val dir = File(File(context.home, "workspace"), "memory")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "MEMORY.md")
    }
}
