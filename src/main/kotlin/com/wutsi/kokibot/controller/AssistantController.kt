package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File

@RequestMapping("/assistants")
@RestController
class AssistantController(private val multi: MultiBootstrap) {
    @GetMapping
    fun list(
        @RequestParam(required = false) exclude: String? = null,
        @RequestParam(required = false) limit: Int = 10,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val items = multi.bootstraps
            .map { bootstrap ->
                mapOf(
                    "name" to bootstrap.getContext().assistant.name,
                    "description" to bootstrap.getContext().assistant.getDescription(),
                )
            }
        val result = items
            .filter { item -> item["name"] != exclude }
            .take(limit)

        return ResponseEntity
            .status(200)
            .header("X-Total-Count", items.size.toString())
            .body(result)
    }

    @GetMapping("/{name}")
    fun get(@PathVariable name: String): ResponseEntity<Map<String, Any?>> {
        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        val assistant = context.assistant
        return ResponseEntity.ok(
            mapOf(
                "name" to assistant.name,
                "description" to assistant.getDescription(),
                "coordinator" to assistant.isCoordinator(),
                "firstName" to assistant.getFullName(),
                "email" to assistant.getEmail(),
                "language" to assistant.getLanguage(),
                "workspaceDirectory" to "${context.home.absolutePath}/workspace",
                "instructions" to assistant.getInstructions()
            )
        )
    }

    @GetMapping("/{name}/context-window")
    fun contextWindow(
        @PathVariable name: String,
        @RequestParam userId: String,
        @RequestParam channelId: String,
        @RequestParam(required = false) conversationId: String?,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val cw = bootstrap.getContext().assistant.contextWindow(userId, channelId, conversationId)
        return ResponseEntity.ok(
            mapOf(
                "baseline" to cw.baseline,
                "max" to cw.max,
            )
        )
    }

    @GetMapping("/{name}/icon.png")
    fun icon(@PathVariable name: String): ResponseEntity<ByteArray> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val icon = File(bootstrap.getContext().home, "config/icon.png")
        if (!icon.exists()) return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(icon.readBytes())
    }

    @PostMapping("/{name}/icon.png", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadIcon(
        @PathVariable name: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val iconFile = File(bootstrap.getContext().home, "config/icon.png")
        iconFile.parentFile.mkdirs()
        iconFile.writeBytes(file.bytes)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/{name}/settings")
    fun set(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            if (key == "assistant.name") {
                multi.rename(name, (value as? String) ?: "")
            } else {
                bootstrap.set(key, value)
            }
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        }
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
