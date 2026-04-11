package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.Tool

interface LLM {
    fun init(config: Map<*, *>, context: Context)
    fun destroy() {}
    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse
}
