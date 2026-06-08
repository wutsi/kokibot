package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/assistants")
@RestController
class AssistantController(private val multi: MultiBootstrap) {
    @GetMapping
    fun list(): List<String> {
        return multi.bootstraps.map { bootstrap -> bootstrap.getContext().assistant.name }
    }

    @GetMapping("/{name}")
    fun get(@PathVariable("name") name: String): ResponseEntity<Map<String, Any?>> {
        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            mapOf(
                "name" to context.assistant.name,
                "description" to context.assistant.description,
            )
        )
    }

    @GetMapping("/{name}/context-length")
    fun contextLength(
        @PathVariable("name") name: String,
        @RequestParam("user-id", required = false) userId: String? = null,
        @RequestParam("channel-id", required = false) channelId: String? = null,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            mapOf(
                "value" to context.assistant.contextLength(userId, channelId),
                "max" to context.llm.maxContextLength(),
            )
        )
    }
}
