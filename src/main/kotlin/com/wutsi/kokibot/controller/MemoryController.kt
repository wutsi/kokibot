package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ConfigurationException
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
class MemoryController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/memory")
    fun get(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val memory = bootstrap.getContext().memory
        return ResponseEntity.ok(
            mapOf(
                "enabled" to memory.isEnabled(),
                "maxLength" to memory.getMaxLength(),
                "window" to memory.getWindow(),
                "frequency" to memory.getCompactionFrequency(),
            )
        )
    }

    @PostMapping("/{name}/memory/settings")
    fun set(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            bootstrap.set("memory.$key", value)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        }
    }

    private fun getBootstrap(name: String) =
        multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
