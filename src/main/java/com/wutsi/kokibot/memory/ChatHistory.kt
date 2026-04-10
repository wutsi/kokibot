package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import tools.jackson.core.type.TypeReference
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * This is the short term memory of the assistant, which is used to store the chat history of the current day.
 * The conversation history is stored into workspace/memory/YYYY-MM-DD.json.
 */
class ChatHistory {
    private lateinit var context: Context

    fun append(prompt: Message, response: Message) {
        return append(prompt, response, LocalDate.now())
    }

    fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun destroy() {
    }

    internal fun append(prompt: Message, response: Message, date: LocalDate) {
        val history = load(date).toMutableList()
        history.add(prompt)
        history.add(response)

        val json = toString(history)
        getFile(date).writeText(json)
    }

    /**
     * Return the content of the history file, or null if the file does not exist.
     * The history is returned as JSON string, and can be parsed by the caller if needed.
     */
    fun get(): String? {
        val file = getFile(LocalDate.now())
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun clear() {
        val file = getFile(LocalDate.now())
        if (file.exists()) {
            file.delete()
        }
    }

    fun merge(from: LocalDate, to: LocalDate): String? {
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

    private fun getFile(date: LocalDate): File {
        val dir = File(context.home.absolutePath + "/workspace/memory/history")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filename = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return File(dir, "$filename.json")
    }
}
