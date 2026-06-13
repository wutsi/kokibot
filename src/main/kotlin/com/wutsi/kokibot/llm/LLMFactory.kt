package com.wutsi.kokibot.llm

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.gemini.Gemini
import com.wutsi.kokibot.llm.kimi.Kimi
import com.wutsi.kokibot.llm.none.NullLLM

class LLMFactory {
    fun create(type: String): LLM {
        return when (type) {
            "deepseek" -> Deepseek()
            "gemini" -> Gemini()
            "kimi" -> Kimi()
            else -> NullLLM()
        }
    }
}
