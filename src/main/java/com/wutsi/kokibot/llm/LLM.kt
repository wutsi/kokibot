package com.wutsi.kokibot.llm

import com.wutsi.kokibot.tools.ToolRegistry

interface LLM {
    fun init(config: Map<*, *>, toolRegistry: ToolRegistry)
    fun destroy() {}
    fun completion(request: LLMRequest): LLMResponse
}
