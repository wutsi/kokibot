package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ChatHistory : Resource {
    companion object {
        const val ID = "service:chat-history"
    }

    private lateinit var context: Context
    private val lock = ReentrantReadWriteLock()

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun append(query: Message, response: Message) {
        lock.write {
            val userId = query.userId ?: return
            val channelId = query.channelId ?: return
            val files = query.filePaths.joinToString("\n") { file -> "- $file" }

            val content = "# ${query.dateTime}: Session ${query.id}\n" +
                "## ${query.role}\n" +
                "### Query:\n" +
                "```markdown\n${query.text}\n```" +
                (if (files.isNotEmpty()) "\n### Files:\n$files" else "") +
                "\n\n## ${response.role}\n" +
                "### Response:\n" +
                "```markdown\n${response.text}\n```\n\n---\n\n"

            getFile(userId, channelId).appendText(content)
        }
    }

    private fun getFile(userId: String, channelId: String): File {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val xuserId = sanitizeId(userId)
        val xchannelId = sanitizeId(channelId)
        val dir = File(
            context.home.absolutePath + "/memory/chat/$xuserId/$xchannelId"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$today.md")
    }

    private fun sanitizeId(id: String): String {
        return id.removePrefix("channel:")
            .replace(":", "-")
    }
}
