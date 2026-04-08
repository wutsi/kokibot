package com.wutsi.kokibot

import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.tools.ToolRegistry
import java.io.File

data class Context(
    val home: File,
    val llm: LLM,
    val toolRegistry: ToolRegistry,
    val chatHistory: ChatHistory,
    val config: Map<*, *>
)
