package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context

abstract class Channel(val assistant: Assistant) {
    abstract fun init(config: Map<*, *>, context: Context)
    abstract fun destroy()
}
