package com.wutsi.kokibot.tools.file

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool

abstract class AbstractFileTool : Tool {
    protected lateinit var context: Context

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    protected fun accessingMemory(toolCalls: List<LLMToolCall>): Boolean {
        val dir = "${context.home.absolutePath}/memory/"
        return toolCalls.find { call -> call.arguments["path"]?.toString()?.startsWith(dir) == true } != null
    }
}
