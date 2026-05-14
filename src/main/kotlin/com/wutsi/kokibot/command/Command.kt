package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message

interface Command {
    fun metadata(): CommandMetadata
    fun exec(input: Message, context: Context): String
}
