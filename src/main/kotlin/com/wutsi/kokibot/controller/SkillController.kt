package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.skill.SkillNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/assistants")
@RestController
class SkillController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/skills")
    fun skills(@PathVariable name: String): ResponseEntity<List<Map<String, Any>>> {
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
                .sortedBy { skill -> skill["name"] as String }
        )
    }

    @GetMapping("/{name}/skills/skill.md")
    fun content(
        @PathVariable name: String,
        @RequestParam skill: String,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        return try {
            val skill = bootstrap.getContext().skillRegistry.get(skill)
            ResponseEntity.ok(mapOf("content" to skill.body))
        } catch (e: SkillNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
