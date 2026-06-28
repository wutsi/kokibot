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
class MarketplaceController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/marketplaces")
    fun marketplaces(@PathVariable name: String): ResponseEntity<List<Map<String, Any?>>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            context.marketplaceRegistry.all()
                .map { marketplace ->
                    mapOf(
                        "enabled" to marketplace.isEnabled(),
                        "name" to marketplace.getName(),
                        "repoUrl" to marketplace.getRepoUrl(),
                        "description" to marketplace.getDescription(),
                        "icon" to marketplace.getIcon(),
                        "skills" to marketplace.getSkills().map { skill -> skill.metadata.name }
                    )
                }
                .sortedBy { it["name"].toString() }
        )
    }

    @PostMapping("/{name}/marketplaces/{marketplace}/settings")
    fun set(
        @PathVariable name: String,
        @PathVariable marketplace: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            bootstrap.set("marketplace.$marketplace.$key", value)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        }
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
