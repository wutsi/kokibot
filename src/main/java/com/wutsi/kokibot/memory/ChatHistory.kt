package com.wutsi.kokibot.memory

import com.wutsi.kokibot.Message
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatHistory(private val home: File, private val jsonMapper: JsonMapper) {
    fun save(prompt: Message, response: Message) {
        val history = load().toMutableList()
        history.add(prompt)
        history.add(response)

        val json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(history)
        getFile().writeText(json)
    }

    fun load(): List<Message> {
        val file = getFile()
        if (!file.exists()) {
            return emptyList()
        } else {
            val json = file.readText()
            return jsonMapper.readValue(json, object : TypeReference<List<Message>>() {})
        }
    }

    fun loadJson(): String? {
        val file = getFile()
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun clear() {
        val file = getFile()
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getFile(): File {
        val dir = File(File(home, "workspace"), "memory")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return File(dir, "$filename.json")
    }
}
