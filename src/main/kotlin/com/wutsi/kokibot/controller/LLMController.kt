package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.text.NumberFormat
import java.util.Currency

@RequestMapping("/assistants")
@RestController
class LLMController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/llm")
    fun llm(@PathVariable name: String): ResponseEntity<Map<String, Any?>> {
        val bootstrap = getBootstrap(name)
            ?: return ResponseEntity.notFound().build()

        val context = bootstrap.getContext()
        val llm = context.llm
        val balance = llm.balance()
        return ResponseEntity.ok(
            mapOf(
                "name" to llm.name(),
                "model" to llm.model(),
                "maxContextWindow" to llm.maxContextWindow(),
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

    private fun formatMoney(amount: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        val currency = Currency.getInstance(currencyCode)
        format.currency = currency
        return format.format(amount)
    }

    private fun getBootstrap(name: String) = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
