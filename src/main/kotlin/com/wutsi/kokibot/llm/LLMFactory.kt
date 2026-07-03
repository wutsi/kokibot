package com.wutsi.kokibot.llm

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.gemini.Gemini
import com.wutsi.kokibot.llm.kimi.Kimi
import com.wutsi.kokibot.llm.none.NullLLM

class LLMFactory {
    private val llms = mapOf(
        "deepseek" to Deepseek(),
        "gemini" to Gemini(),
        "kimi" to Kimi(),
    )

    fun names(): List<String> {
        return llms.keys.toList()
    }

    fun create(name: String): LLM {
        return llms[name] ?: NullLLM()
    }
}
