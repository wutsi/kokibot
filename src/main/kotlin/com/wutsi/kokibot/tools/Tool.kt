package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context

interface Tool {
    fun init(config: Map<*, *>, context: Context) {}
    fun destroy() {}
    fun metadata(): ToolMetadata
    fun exec(arguments: Map<*, *>): String
}
