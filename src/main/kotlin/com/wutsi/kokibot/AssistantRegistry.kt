package com.wutsi.kokibot

import org.springframework.stereotype.Service

@Service
class AssistantRegistry {
    private val assistants = mutableMapOf<String, Assistant>()

    fun all(): List<Assistant> {
        return assistants.values.toList()
    }

    fun register(assistant: Assistant) {
        val key = assistant.name.lowercase()
        if (assistants.containsKey(key)) {
            throw AssistantAlreadyRegisteredException("Assistant already registered: ${assistant.name}")
        }
        assistants[key] = assistant
    }

    fun get(name: String): Assistant {
        return assistants[name.lowercase()]
            ?: throw AssistantNotFoundException("Assistant not found: $name")
    }
}
