package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.service.memory.ConversationRepository.Companion.BLOCK_SEPARATOR
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
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

    fun append(query: Message, response: Message): String {
        val userId = query.userId ?: return ""
        val channelId = query.channelId ?: return ""

        val conversationId = query.conversationId
            ?: context.conversationRepository.createConversation(userId, channelId, query.text).id

        val files = query.filePaths.joinToString("\n") { file -> "- $file" }
        val content = "<!-- kokibot:conv:$conversationId -->\n" +
            "# ${query.dateTime}: Session ${query.id}\n" +
            "## ${query.role}\n" +
            "### Query:\n" +
            "```markdown\n${query.text}\n```\n" +
            (if (files.isNotEmpty()) "### Files:\n$files\n\n" else "\n") +
            "## ${response.role}\n" +
            "### Response:\n" +
            "```markdown\n${response.text}\n```" +
            BLOCK_SEPARATOR

        val file = getFile(userId, channelId)
        file.parentFile.mkdirs()
        lock.write {
            file.appendText(content)
            return conversationId
        }
    }

    fun get(userId: String?, channelId: String?): String? {
        userId ?: return null
        channelId ?: return null

        lock.read {
            val file = getFile(userId, channelId)
            if (!file.exists()) {
                return null
            }
            return file.readText()
        }
    }

    fun clear(userId: String?, channelId: String?) {
        userId ?: return
        channelId ?: return

        lock.write {
            val file = getFile(userId, channelId)
            if (file.exists()) {
                val bak = File(file.parentFile, file.nameWithoutExtension + "." + System.currentTimeMillis() + ".md")
                file.renameTo(bak)
            }
        }
    }

    private fun getFile(userId: String, channelId: String): File {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val xuserId = sanitizeId(userId)
        val xchannelId = sanitizeId(channelId)
        val dir = File(
            context.home.absolutePath + "/memory/chat/$xuserId/$xchannelId"
        )
        return File(dir, "$today.md")
    }

    private fun sanitizeId(id: String): String {
        return id.removePrefix("channel:")
            .replace(":", "-")
    }
}
