package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.MultiBootstrap
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.text.NumberFormat
import java.util.Currency

@RequestMapping()
@RestController
class LLMController(private val multi: MultiBootstrap) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LLMController::class.java)
    }

    @GetMapping("/llms")
    fun llms(@RequestParam(required = false) assistant: String? = null): ResponseEntity<Map<String, Any?>> {
        val credentialService = assistant?.let {
            getBootstrap(assistant)?.getContext()?.credentialService
        }
        return ResponseEntity.ok(
            mapOf(
                "models" to multi.llmFactory.names().map { llm ->
                    credentialService?.getOrNull("llm.$llm")?.let { apiKey ->
                        mapOf(
                            "name" to llm,
                            "models" to multi.llmFactory.create(llm).availableModels(),
                            "apiKey" to apiKey.let { "******************" }
                        )
                    }
                }
            )
        )
    }

    @GetMapping("/assistants/{name}/llm/settings")
    fun set(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            bootstrap.set("llm.$key", value)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        }
    }

    @GetMapping("/assistants/{name}/llm")
    fun llm(@PathVariable name: String): ResponseEntity<Map<String, Any?>> {
        val bootstrap = getBootstrap(name)
            ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        val llm = context.llm
        val balance = try {
            llm.balance()
        } catch (ex: Exception) {
            LOGGER.warn("Unable to get balance for LLM ${llm.getName()}", ex)
            null
        }
        return ResponseEntity.ok(
            mapOf(
                "name" to llm.getName(),
                "model" to llm.getModel(),
                "reasoningEffort" to llm.getReasoningEffort(),
                "maxContextWindow" to llm.getMaxContextWindow(),
                "availableBalance" to balance?.let {
                    mapOf(
                        "amount" to balance.total,
                        "currency" to balance.currency,
                        "text" to formatMoney(balance.total, balance.currency)
                    )
                }
            )
        )
    }

    @PostMapping("/assistants/{name}/llm")
    fun llm(@PathVariable name: String, @RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any?>> {
        val bootstrap = getBootstrap(name)
            ?: return ResponseEntity.notFound().build()

        val llm = request["llm"] as? String ?: return ResponseEntity.badRequest().build()
        val model = request["model"] as? String ?: return ResponseEntity.badRequest().build()
        bootstrap.changeLLM(llm, model)

        return ResponseEntity.ok(mapOf("success" to true))
    }

    private fun formatMoney(amount: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        val currency = Currency.getInstance(currencyCode)
        format.currency = currency
        return format.format(amount)
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
