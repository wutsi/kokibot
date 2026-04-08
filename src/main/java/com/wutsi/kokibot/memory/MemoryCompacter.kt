package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.util.MapUtil
import java.io.File
import kotlin.math.min

/**
 * Read the chat history and extract facts and information that can be used to answer the question, and save it
 * into workspace/memory/MEMORY.md.
 * The content of this file should be concise and relevant to the question, and should not contain any irrelevant information or noise.
 *
 * This is a strategy used for reducing the LLM context window.
 */
class MemoryCompacter {
    companion object {
        const val DEFAULT_SIZE = 1000
        const val MAX_SIZE = 20000
    }

    private lateinit var context: Context
    private var maxSize: Int = DEFAULT_SIZE

    fun init(config: Map<*, *>, context: Context) {
        maxSize = min(MAX_SIZE, MapUtil.toInt("max-size", config) ?: DEFAULT_SIZE)
        this.context = context
    }

    fun compact() {
    }

    private fun getFile(): File {
        val dir = File(File(context.home, "workspace"), "memory")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "MEMORY.md")
    }
}
