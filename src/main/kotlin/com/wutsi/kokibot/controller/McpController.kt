package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/assistants")
@RestController
class McpController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/mcps")
    fun mcps(@PathVariable name: String): ResponseEntity<List<Map<String, Any>>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            context.mcpRegistry.all().map { server ->
                mapOf(
                    "name" to server.config.name,
                    "description" to server.config.description,
                    "icon" to server.config.icon,
                )
            }
        )
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
