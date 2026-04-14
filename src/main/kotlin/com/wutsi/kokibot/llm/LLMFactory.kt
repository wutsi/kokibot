package com.wutsi.kokibot.llm

import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.kimi.Kimi
import org.springframework.stereotype.Service

@Service
class LLMFactory {
    /**
     * Return the instance of the LLM implementation based on the given type.
     * Currently, the supported types are:
     *  - `deepseek`: the Deepseek LLM implementation
     *  - `kimi`: the Kimi LLM implementation
     */
    fun create(type: String): LLM {
        return when (type) {
            "deepseek" -> Deepseek()
            "kimi" -> Kimi()
            else -> throw ConfigurationException("Unsupported llm type: $type")
        }
    }
}
