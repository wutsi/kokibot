package com.wutsi.kokibot.tools.date

import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals

class ClockToolTest {
    val tool = ClockTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(ClockTool.NAME, meta.name)
        assertEquals(0, meta.parameters.size)
    }

    @Test
    fun exec() {
        val args = emptyMap<String, String>()
        val result = tool.exec(args)
        val fmt = SimpleDateFormat(ClockTool.DATE_PATTERN)
        assertEquals("Today's date is " + fmt.format(Date()), result)
    }
}
