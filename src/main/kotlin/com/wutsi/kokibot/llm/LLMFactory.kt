package com.wutsi.kokibot.llm

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.gemini.Gemini
import com.wutsi.kokibot.llm.kimi.Kimi
import org.springframework.stereotype.Service

@Service
class LLMFactory {
    fun create(type: String): LLM {
        return when (type) {
            "deepseek" -> Deepseek()
            "kimi" -> Kimi()
            "gemini" -> Gemini()
            else -> NullLLM()
        }
    }
}
