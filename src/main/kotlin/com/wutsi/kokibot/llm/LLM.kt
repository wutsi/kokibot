package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.tools.Tool

interface LLM : Resource {
    override fun destroy() {
    }

    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse
}
