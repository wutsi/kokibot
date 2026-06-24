package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.Context
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
import java.io.File
import java.text.NumberFormat
import java.util.Currency

@RequestMapping("/assistants")
@RestController
class AssistantController(private val multi: MultiBootstrap) {
    @GetMapping
    fun list(
        @RequestParam(required = false, name = "channel-id") channelId: String? = null,
    ): List<String> {
        return multi.bootstraps
            .filter { bootstrap -> channelId == null || hasChannel(bootstrap.getContext(), channelId) }
            .map { bootstrap -> bootstrap.getContext().assistant.name }
    }

    private fun hasChannel(context: Context, channelId: String): Boolean {
        try {
            val name = if (channelId.startsWith("channel:")) channelId else "channel:$channelId"
            context.channelRegistry.get(name)
            return true
        } catch (_: ChannelNotFoundException) {
            return false
        }
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
                "description" to assistant.description,
                "coordinator" to assistant.coordinator,
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

    @GetMapping("/{name}/icon.png")
    fun icon(@PathVariable name: String): ResponseEntity<ByteArray> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val icon = File(bootstrap.getContext().home, "config/icon.png")
        if (!icon.exists()) return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(icon.readBytes())
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

    private fun formatMoney(amount: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        val currency = Currency.getInstance(currencyCode)
        format.currency = currency
        return format.format(amount)
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
