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
            val sanitizedChannel = sanitizeId(channelId)
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                channelId = sanitizedChannel,
                title = firstMessage.take(TITLE_MAX_LENGTH),
                startDate = LocalDateTime.now(),
            )
            val conversations = readIndex(userId, sanitizedChannel).toMutableList()
            conversations.add(conversation)
            writeIndex(userId, sanitizedChannel, conversations)
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
            val conversations = if (channelId != null) {
                readIndex(userId, channelId)
            } else {
                readAllChannels(userId)
            }
            return conversations
                .sortedByDescending { it.startDate }
                .drop(offset)
                .take(limit)
        }
    }

    fun getMessages(conversationId: String, userId: String, channelId: String): List<ConversationMessage> {
        lock.read {
            val sanitizedChannel = sanitizeId(channelId)
            val conversation = readIndex(userId, sanitizedChannel).find { it.id == conversationId }
                ?: return emptyList()

            val startDate = conversation.startDate.toLocalDate()
            val channelDir = File(
                "${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}/$sanitizedChannel"
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
                val userText = extractTag(trimmed, "query") ?: return@forEach
                val assistantText = extractTag(trimmed, "response")
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

    // Extracts the content wrapped between <tag> and </tag> markers written by ChatHistory.
    // The writer pads the content with newlines (<tag>\n...\n</tag>); those padding newlines are stripped.
    private fun extractTag(block: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = block.indexOf(open)
        if (start == -1) return null
        val contentStart = start + open.length
        val end = block.indexOf(close, contentStart)
        if (end < contentStart) return null
        return block.substring(contentStart, end).trim('\n')
    }

    private fun extractDateTime(block: String): LocalDateTime? {
        val line = block.lines().firstOrNull { it.startsWith("# ") } ?: return null
        return runCatching {
            LocalDateTime.parse(line.removePrefix("# ").substringBefore(": Session "))
        }.getOrNull()
    }

    private fun getIndexFile(userId: String, channelId: String): File {
        return File(
            "${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}/${sanitizeId(channelId)}",
            "conversations.json"
        )
    }

    private fun readIndex(userId: String, channelId: String): List<Conversation> {
        val file = getIndexFile(userId, channelId)
        if (!file.exists()) return emptyList()
        return try {
            context.jsonMapper.readValue(
                file.readText(),
                context.jsonMapper.typeFactory.constructCollectionType(List::class.java, Conversation::class.java),
            )
        } catch (e: Exception) {
            LOGGER.warn("Failed to read conversation index for user $userId channel $channelId: ${e.message}")
            emptyList()
        }
    }

    private fun writeIndex(userId: String, channelId: String, conversations: List<Conversation>) {
        val file = getIndexFile(userId, channelId)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        val json = context.jsonMapper.writeValueAsString(conversations)
        file.writeText(json)
    }

    private fun readAllChannels(userId: String): List<Conversation> {
        val userDir = File("${context.home.absolutePath}/memory/chat/${sanitizeId(userId)}")
        if (!userDir.exists()) return emptyList()
        return userDir.listFiles { f -> f.isDirectory }
            ?.flatMap { dir -> readIndex(userId, dir.name) }
            ?: emptyList()
    }

    private fun sanitizeId(id: String): String =
        id.removePrefix("channel:").replace(":", "-")
}
