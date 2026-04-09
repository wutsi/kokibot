package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

interface Command {
    fun name(): String
    fun exec(input: String, context: Context): String
}
