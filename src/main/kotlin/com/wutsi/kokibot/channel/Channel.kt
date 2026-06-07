package com.wutsi.kokibot.channel

import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource

abstract class Channel : Resource {
    abstract fun send(message: Message): Boolean

    /**
     * Send status update (non-blocking, intermediate feedback).
     * Used for showing tool execution progress.
     * Default implementation does nothing (channels can override).
     */
    open fun sendStatus(message: Message) {
        // Default: no-op
    }
}
