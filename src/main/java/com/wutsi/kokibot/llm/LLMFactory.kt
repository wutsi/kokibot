package com.wutsi.kokibot.llm

import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.deepseek.Deepseek

class LLMFactory {
    fun create(type: String): LLM {
        return when (type) {
            "deepseek" -> Deepseek()
            else -> throw ConfigurationException("Unsupported llm type: $type")
        }
    }
}
