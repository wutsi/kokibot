package com.wutsi.kokibot

import org.springframework.stereotype.Service

@Service
class AssistantRegistry {
    private val assistants = mutableMapOf<String, Assistant>()

    fun register(assistant: Assistant) {
        assistants[assistant.name.lowercase()] = assistant
    }

    fun get(name: String): Assistant {
        return assistants[name.lowercase()]
            ?: throw AssistantNotFoundException("Assistant not found: $name")
    }
}
