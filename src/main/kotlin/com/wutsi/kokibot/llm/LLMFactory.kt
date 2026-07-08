package com.wutsi.kokibot.llm

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.gemini.Gemini
import com.wutsi.kokibot.llm.kimi.Kimi
import com.wutsi.kokibot.llm.none.NullLLM

class LLMFactory {
    fun names(): List<String> {
        return listOf(
            "deepseek",
            "gemini",
            "kimi"
        )
    }

    fun create(name: String): LLM {
        when (name) {
            "deepseek" -> return Deepseek()
            "gemini" -> return Gemini()
            "kimi" -> return Kimi()
            else -> return NullLLM()
        }
    }
}
