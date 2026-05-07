package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource

abstract class Channel : Resource {
    abstract fun send(message: Message): Boolean
}
