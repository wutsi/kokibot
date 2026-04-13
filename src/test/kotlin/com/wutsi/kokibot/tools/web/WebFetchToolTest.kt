package com.wutsi.kokibot.tools.web

import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WebFetchToolTest {
    val tool = WebFetchTool()

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(WebFetchTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)
        assertEquals("url", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
        assertTrue(meta.parameters[0].required)
    }

    @Test
    fun exec() {
        val args = mapOf("url" to "https://evendo.com/locations/cameroon/yaounde/odza")
        val result = tool.exec(args)
//        println(result)
    }

    @Test
    fun `exec PDF`() {
        val args = mapOf("url" to "https://www.amicaall.org/publications/profiles/Profil_municipal%20Soa_finalise.pdf")
        val result = tool.exec(args)
//        println(result)
    }

    @Test
    fun `exec - empty command`() {
        assertThrows<IllegalArgumentException> { tool.exec(mapOf("url" to "")) }
    }

    @Test
    fun `exec - no command`() {
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, String>()) }
    }
}
