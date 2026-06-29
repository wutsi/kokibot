package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.memory.ConversationDetail
import com.wutsi.kokibot.service.memory.ConversationRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assistants")
class ConversationController(private val multi: MultiBootstrap) {

    @GetMapping("/{name}/conversations")
    fun list(
        @PathVariable name: String,
        @RequestParam(required = false) channelId: String?,
        @RequestParam(defaultValue = "30") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<Any> {
        val userId = "anonymous"
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(repository.getConversations(userId, channelId, limit, offset))
    }

    @GetMapping("/{name}/conversations/{id}")
    fun get(
        @PathVariable name: String,
        @PathVariable id: String,
    ): ResponseEntity<Any> {
        val userId = "anonymous"
        val repository = getRepository(name) ?: return ResponseEntity.notFound().build()
        val conversation = repository.getConversations(userId, limit = Int.MAX_VALUE).find { it.id == id }
            ?: return ResponseEntity.notFound().build()
        val messages = repository.getMessages(id, userId, conversation.channelId)
        return ResponseEntity.ok(
            ConversationDetail(
                id = conversation.id,
                title = conversation.title,
                startDate = conversation.startDate,
                messages = messages,
            )
        )
    }

    private fun getRepository(name: String): ConversationRepository? =
        multi.bootstraps
            .firstOrNull { it.getContext().assistant.name == name }
            ?.getContext()
            ?.conversationRepository
}
