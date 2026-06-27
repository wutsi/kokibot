package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
                        "name" to marketplace.getName(),
                        "repoUrl" to marketplace.getRepoUrl(),
                        "description" to marketplace.getDescription(),
                        "icon" to marketplace.getIcon(),
                        "skills" to marketplace.getSkills().map { skill -> skill.metadata.name }
                    )
                }
        )
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
