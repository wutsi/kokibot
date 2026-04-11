package com.wutsi.kokibot.tools.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.tools.ToolParameterType
import okio.IOException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

class MailUnsubscribeToolTest {
    private val http = mock<HttpClient>()
    private val tool = MailUnsubscribeTool(http)

    private val url = "http://example.com/unsubscribe"
    private val arguments = mapOf("url" to url)

    @Test
    fun metadata() {
        val meta = tool.metadata()
        assertEquals(MailUnsubscribeTool.NAME, meta.name)
        assertEquals(1, meta.parameters.size)
        assertEquals("url", meta.parameters[0].name)
        assertEquals(ToolParameterType.STRING, meta.parameters[0].type)
    }

    @Test
    fun exec() {
        // WHEN
        val result = tool.exec(arguments)
        assertEquals("Unsubscribed from $url", result)

        val request = argumentCaptor<HttpRequest>()
        verify(http).send(request.capture(), any<HttpResponse.BodyHandler<*>>())
        assertEquals(url, request.firstValue.uri().toString())
        assertEquals("POST", request.firstValue.method())
        assertEquals("application/x-www-form-urlencoded", request.firstValue.headers().map()["Content-Type"]?.first())
        assertEquals(MailUnsubscribeTool.USER_AGENT, request.firstValue.headers().map()["User-Agent"]?.first())
    }

    @Test
    fun failure() {
        // GIVEN
        doThrow(IOException("Failure")).whenever(http).send(any(), any<HttpResponse.BodyHandler<*>>())

        // WHEN
        val result = tool.exec(arguments)
        assertEquals("Failed to unsubscribe from $url. Error: Failure", result)
    }

    @Test
    fun `exec - missing url`() {
        // WHEN
        assertThrows<IllegalArgumentException> { tool.exec(emptyMap<String, Any>()) }
    }
}
