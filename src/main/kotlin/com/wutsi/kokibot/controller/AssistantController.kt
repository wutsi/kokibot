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
        val llm = context.llm
        return ResponseEntity.ok(
            mapOf(
                "name" to context.assistant.name,
                "description" to context.assistant.description,
                "workspaceDirectory" to "${context.home.absolutePath}/workspace",
                "llm" to mapOf(
                    "name" to llm.name(),
                    "model" to llm.model(),
                    "maxContextWindow" to llm.maxContextWindow()
                )
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

    @GetMapping("/{name}/skills")
    fun llm(@PathVariable name: String): ResponseEntity<List<Map<String, Any>>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            context.skillRegistry.all()
                .filter { skill -> skill.activate() }
                .map { skill ->
                    mapOf(
                        "name" to skill.metadata.name,
                        "description" to skill.metadata.description,
                    )
                }
                +
                context.marketplaceRegistry.all().flatMap { marketplace -> marketplace.getSkills() }
                    .filter { skill -> skill.activate() }
                    .map { skill ->
                        mapOf(
                            "name" to skill.metadata.name,
                            "description" to skill.metadata.description,
                        )
                    }
        )
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
