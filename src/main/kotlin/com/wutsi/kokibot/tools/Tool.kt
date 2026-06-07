package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.llm.LLMToolCall

interface Tool : Resource {
    override fun id(): String {
        return "tool:" + metadata().name
    }

    override fun init(config: Map<*, *>, context: Context) {
    }

    fun metadata(): ToolMetadata
    fun exec(arguments: Map<*, *>): String
    fun statusText(toolCalls: List<LLMToolCall>): String
}
