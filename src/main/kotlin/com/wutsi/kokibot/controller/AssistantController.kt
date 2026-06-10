package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/assistants")
@RestController
class AssistantController(private val multi: MultiBootstrap) {
    @GetMapping
    fun list(): List<String> {
        return multi.bootstraps.map { bootstrap -> bootstrap.getContext().assistant.name }
    }

    @GetMapping("/{name}")
    fun get(@PathVariable name: String): ResponseEntity<Map<String, Any?>> {
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

    @GetMapping("/{name}/assistant.md")
    fun assistant(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val context = bootstrap.getContext()
        val identity = context.assistant.getInstructions()
        return ResponseEntity.ok(
            mapOf(
                "content" to (identity ?: "")
            )
        )
    }

    @PostMapping("/{name}/assistant.md")
    fun assistant(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val context = bootstrap.getContext()
        context.assistant.saveInstructions(body["content"] as? String ?: "")
        return ResponseEntity.ok(
            mapOf(
                "success" to true
            )
        )
    }

    @GetMapping("/{name}/heartbeat.md")
    fun heartbeat(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val context = bootstrap.getContext()
        val identity = context.heartbeat.getInstructions()
        return ResponseEntity.ok(
            mapOf(
                "content" to (identity ?: "")
            )
        )
    }

    @PostMapping("/{name}/heartbeat.md")
    fun heartbeat(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val context = bootstrap.getContext()
        context.heartbeat.saveInstructions(body["content"] as? String ?: "")
        return ResponseEntity.ok(
            mapOf(
                "success" to true
            )
        )
    }

    @GetMapping("/{name}/llm")
    fun llm(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val llm = bootstrap.getContext().llm
        return ResponseEntity.ok(
            mapOf(
                "name" to llm.name(),
                "model" to llm.model(),
                "maxContextWindow" to llm.maxContextWindow()
            )
        )
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
