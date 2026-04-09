package com.wutsi.kokibot

import com.wutsi.kokibot.channel.Channel
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.memory.ChatHistory
import com.wutsi.kokibot.memory.Memory
import com.wutsi.kokibot.tools.ToolRegistry
import tools.jackson.databind.json.JsonMapper
import java.io.File

data class Context(
    val home: File,
    val llm: LLM,
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val chatHistory: ChatHistory = ChatHistory(),
    val memory: Memory = Memory(),
    val config: Map<*, *> = emptyMap<String, String>(),
    val channels: MutableList<Channel> = mutableListOf(),
    val jsonMapper: JsonMapper = JsonMapper(),
)
