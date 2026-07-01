package com.wutsi.kokibot

import org.springframework.stereotype.Service

@Service
class AssistantRegistry : Registry<Assistant>() {
    override fun id() = "assistant-registry"
    override fun keyOf(assistant: Assistant) = assistant.name
    override fun notFound(name: String) = AssistantNotFoundException("Assistant not found: $name")

    override fun register(assistant: Assistant) {
        if (items.containsKey(assistant.name.lowercase())) {
            throw AssistantAlreadyRegisteredException("Assistant already registered: ${assistant.name}")
        }
        super.register(assistant)
    }
}
