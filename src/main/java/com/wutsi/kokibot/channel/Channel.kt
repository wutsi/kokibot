package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Assistant

abstract class Channel(val agent: Assistant) {
    abstract fun init(config: Map<*, *>)
    abstract fun destroy()
}
