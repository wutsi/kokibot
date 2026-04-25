package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import tools.jackson.core.type.TypeReference
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This is the short term memory of the assistant, which is used to store the chat history of the current day.
 * The conversation history is stored into workspace/memory/history/YYYY-MM-DD.json.
 *
 * Thread-safety: all read/write operations are guarded by a [ReentrantReadWriteLock]. Concurrent
 * readers are allowed, but writers (`append`, `clear`) are serialized. File writes are atomic
 * (write to a temp file, then move with `ATOMIC_MOVE`) to prevent partial/corrupted reads.
 */
class ChatHistory : Resource {
    private lateinit var context: Context
    private val lock = ReentrantReadWriteLock()

    fun append(prompt: Message, response: Message) {
        return append(prompt, response, LocalDate.now())
    }

    override fun id(): String {
        return "service:chat-history"
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    internal fun append(prompt: Message, response: Message, date: LocalDate) = lock.write {
        val history = load(date).toMutableList()
        history.add(prompt)
        history.add(response)

        val json = toString(history)
        atomicWrite(getFile(date), json)
    }

    /**
     * Return the content of the history file, or null if the file does not exist.
     * The history is returned as JSON string, and can be parsed by the caller if needed.
     */
    fun get(): String? = lock.read {
        val file = getFile(LocalDate.now())
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun clear() = lock.write {
        val file = getFile(LocalDate.now())
        if (file.exists()) {
            file.delete()
        }
    }

    fun merge(from: LocalDate, to: LocalDate): String? = lock.read {
        val history = mutableListOf<Message>()
        var date = from
        while (!date.isAfter(to)) {
            history.addAll(load(date))
            date = date.plusDays(1)
        }
        if (history.isEmpty()) {
            return null
        } else {
            return toString(history)
        }
    }

    private fun toString(messages: List<Message>): String {
        return context.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages)
    }

    private fun load(date: LocalDate): List<Message> {
        val file = getFile(date)
        if (!file.exists()) {
            return emptyList()
        } else {
            val json = file.readText()
            return context.jsonMapper.readValue(json, object : TypeReference<List<Message>>() {})
        }
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

    private fun getFile(date: LocalDate): File {
        val dir = File(context.home.absolutePath + "/workspace/memory/history")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filename = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return File(dir, "$filename.json")
    }
}
