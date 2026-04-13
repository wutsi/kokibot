package com.wutsi.kokibot.tools

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource

interface Tool : Resource {
    override fun id(): String {
        return "tool:" + metadata().name
    }

    override fun health(): Health {
        return Health(
            id = id(),
            up = true,
        )
    }

    override fun init(config: Map<*, *>, context: Context) {
    }

    fun metadata(): ToolMetadata
    fun exec(arguments: Map<*, *>): String
}
