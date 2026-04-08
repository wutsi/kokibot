package com.wutsi.kokibot.tools.date

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Returns today's date/time in a human-readable format, e.g. 'Monday, January 01, 2024  21:30 GMT'.
 */
class ClockTool : Tool {
    companion object {
        const val NAME = "clock"
        const val DATE_PATTERN = "EEEE, MMMM dd, yyyy HH:mm z"
    }

    private val formatter = DateTimeFormatter.ofPattern(DATE_PATTERN)

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Returns today's date and time in a human-readable format, e.g. 'Monday, January 01, 2024 21:30 GMT'.",
    )

    override fun exec(arguments: Map<*, *>): String {
        val zoneId = ZoneId.systemDefault()
        val dateTime = ZonedDateTime.now(zoneId)
        return "Today's date is ${dateTime.format(formatter)}"
    }
}
