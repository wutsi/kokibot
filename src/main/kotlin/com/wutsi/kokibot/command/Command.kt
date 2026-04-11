package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context

interface Command {
    fun metadata(): CommandMetadata
    fun exec(input: String, context: Context): String
}
