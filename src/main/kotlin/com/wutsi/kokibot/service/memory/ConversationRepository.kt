package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ConversationRepository : Resource {
    companion object {
        const val ID = "service:conversation-repository"
        const val TITLE_MAX_LENGTH = 60
        private const val CONV_MARKER_PREFIX = "<!-- kokibot:conv:"
        private const val CONV_MARKER_SUFFIX = " -->"
        const val BLOCK_SEPARATOR = "\n\n<!-- kokibot:end -->\n\n"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val LOGGER = LoggerFactory.getLogger(ConversationRepository::class.java)
    }

    private lateinit var context: Context
    private val lock = ReentrantReadWriteLock()

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun createConversation(userId: String, channelId: String, firstMessage: String): Conversation {
        lock.write {
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                channelId = sanitizeId(channelId),
                title = firstMessage.take(TITLE_MAX_LENGTH),
                startDate = LocalDateTime.now(),
            )
            val conversations = readIndex(userId).toMutableList()
            conversations.add(conversation)
            writeIndex(userId, conversations)
            return conversation
        }
    }

    fun getConversations(
        userId: String,
        channelId: String? = null,
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0
    ): List<Conversation> {
        lock.read {
            val sanitized = channelId?.let { sanitizeId(it) }
            return readIndex(userId)
                .filter { sanitized == null || it.channelId == sanitized }
                .sortedByDescending { it.startDate }
                .drop(offset)
                .take(limit)
        }
    }

    fun getMessages(conversationId: String, userId: String): List<ConversationMessage> {
        lock.read {
            val conversation = readIndex(userId).find { it.id == conversationId }
                ?: return emptyList()

            val startDate = conversation.startDate.toLocalDate()
            val channelDir = File(
                "${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}/${conversation.channelId}"
            )
            if (!channelDir.exists()) return emptyList()

            return channelDir.listFiles { f -> f.extension == "md" }
                ?.filter { f ->
                    runCatching {
                        LocalDate.parse(
                            f.nameWithoutExtension,
                            DATE_FORMAT
                        ) >= startDate
                    }.getOrDefault(false)
                }
                ?.sortedBy { f -> f.nameWithoutExtension }
                ?.flatMap { f -> parseMessages(f, conversationId) }
                ?: emptyList()
        }
    }

    private fun parseMessages(file: File, conversationId: String): List<ConversationMessage> {
        val messages = mutableListOf<ConversationMessage>()
        file.readText()
            .split(CONV_MARKER_PREFIX)
            .filter { it.startsWith("$conversationId$CONV_MARKER_SUFFIX") }
            .forEach { block ->
                val trimmed = block.trim()
                val dateTime = extractDateTime(trimmed) ?: return@forEach
                val userText = extractSection(trimmed, "### Query:") ?: return@forEach
                val assistantText = extractSection(trimmed, "### Response:")
                val files = extractFiles(trimmed)
                messages.add(ConversationMessage(role = "user", text = userText, files = files, dateTime = dateTime))
                if (assistantText != null) {
                    messages.add(ConversationMessage(role = "assistant", text = assistantText, dateTime = dateTime))
                }
            }
        return messages
    }

    private fun extractFiles(block: String): List<String> {
        val header = "### Files:\n"
        val start = block.indexOf(header)
        if (start == -1) return emptyList()
        val contentStart = start + header.length
        val end = block.indexOf("\n\n", contentStart)
        val section = if (end == -1) block.substring(contentStart) else block.substring(contentStart, end)
        return section.lines()
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun extractSection(block: String, header: String): String? {
        val marker = "$header\n```markdown\n"
        val start = block.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = closingFence(block, contentStart, header)
        if (end < contentStart) return null
        return block.substring(contentStart, end)
    }

    // Finds the position of the closing \n``` for a section.
    // Response is always the last section in the block, so its fence is the last \n``` in the block.
    // Query ends at a known boundary: \n```\n\n## (no files) or \n```\n### Files: (with files).
    // Using these exact patterns avoids false matches on ## headings or code fences inside LLM content.
    private fun closingFence(block: String, contentStart: Int, header: String): Int {
        if (header == "### Response:") {
            return block.lastIndexOf("\n```")
        }
        val beforeFiles = block.indexOf("\n```\n### Files:", contentStart).takeIf { it >= contentStart }
        val beforeRole = block.indexOf("\n```\n\n## ", contentStart).takeIf { it >= contentStart }
        return when {
            beforeFiles != null && (beforeRole == null || beforeFiles < beforeRole) -> beforeFiles
            beforeRole != null -> beforeRole
            else -> block.lastIndexOf("\n```")
        }
    }

    private fun extractDateTime(block: String): LocalDateTime? {
        val line = block.lines().firstOrNull { it.startsWith("# ") } ?: return null
        return runCatching {
            LocalDateTime.parse(line.removePrefix("# ").substringBefore(": Session "))
        }.getOrNull()
    }

    private fun getIndexFile(userId: String): File {
        return File("${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}", "conversations.json")
    }

    private fun readIndex(userId: String): List<Conversation> {
        val file = getIndexFile(userId)
        if (!file.exists()) return emptyList()
        return try {
            context.jsonMapper.readValue(
                file.readText(),
                context.jsonMapper.typeFactory.constructCollectionType(List::class.java, Conversation::class.java),
            )
        } catch (e: Exception) {
            LOGGER.warn("Failed to read conversation index for user $userId: ${e.message}")
            emptyList()
        }
    }

    private fun writeIndex(userId: String, conversations: List<Conversation>) {
        val file = getIndexFile(userId)
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(context.jsonMapper.writeValueAsString(conversations))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IllegalStateException("Failed to write conversation index for user $userId")
        }
    }

    private fun sanitizeId(id: String): String =
        id.removePrefix("channel:").replace(":", "-")
}
