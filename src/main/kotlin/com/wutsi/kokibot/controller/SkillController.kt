package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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
        )
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
