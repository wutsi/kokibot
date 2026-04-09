package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Context

interface LLM {
    fun init(config: Map<*, *>, context: Context)
    fun destroy() {}
    fun completion(request: LLMRequest): LLMResponse
}
