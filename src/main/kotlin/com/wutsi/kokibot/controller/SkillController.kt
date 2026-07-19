package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.skill.SkillNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/assistants")
@RestController
class SkillController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/skills")
    fun skills(
        @PathVariable name: String,
        @RequestParam(required = false) active: Boolean? = null,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        return ResponseEntity.ok(
            context.skillRegistry.all()
                .filter { skill -> active == null || context.skillRegistry.isActive(skill) == active }
                .map { skill ->
                    mapOf(
                        "name" to skill.metadata.name,
                        "description" to skill.metadata.description,
                        "marketplace" to skill.marketplace,
                        "enabled" to skill.isEnabled(),
                        "active" to skill.isActive(),
                        "displayName" to skill.getDisplayName()
                    )
                }
                .sortedBy { skill -> skill["name"] as String }
        )
    }

    @GetMapping("/{name}/skills/{skill}")
    fun get(
        @PathVariable name: String,
        @PathVariable skill: String,
    ): ResponseEntity<Map<String, Any?>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        return try {
            val sk = bootstrap.getContext().skillRegistry.get(skill)
            ResponseEntity.ok(
                mapOf(
                    "name" to sk.metadata.name,
                    "description" to sk.metadata.description,
                    "categories" to sk.metadata.categories,
                    "keywords" to sk.metadata.keywords,
                    "instructions" to sk.instructions,
                    "requiredBinaries" to sk.metadata.requiredBinaries,
                    "requiredEnv" to sk.metadata.requiredEnv,
                    "requiredOS" to sk.metadata.requiredOS,
                    "marketplace" to sk.marketplace,
                    "enabled" to sk.isEnabled(),
                    "active" to sk.isActive(),
                    "displayName" to sk.getDisplayName()
                )
            )
        } catch (_: SkillNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{name}/skills/{skill}/settings")
    fun settings(
        @PathVariable name: String,
        @PathVariable skill: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            bootstrap.set("skill.$skill.$key", value)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        } catch (_: SkillNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
