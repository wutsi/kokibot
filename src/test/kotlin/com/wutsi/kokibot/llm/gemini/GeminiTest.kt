package com.wutsi.kokibot.llm.gemini

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.service.credential.CredentialService
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito
import java.io.File
import kotlin.test.assertEquals

class GeminiTest {
    private val llm = Gemini()
    private val credentialService = mock<CredentialService>()
    val config = mapOf(
        "model" to "gemini-2.5-flash-lite",
    )
    private val context = Context(
        home = File("/target"),
        llm = Mockito.mock(),
        config = mapOf("xx" to "yy"),
        credentialService = credentialService,
    )

    @BeforeEach
    fun setUp() {
        whenever(credentialService.get("llm.gemini")).doReturn(System.getenv("KOKIBOT_GEMINI_API_KEY") ?: "")
    }

    @Test
    fun id() {
        assertEquals("llm:gemini", llm.id())
    }

    @Test
    fun balance() {
        llm.init(config, context)

        assertNull(llm.balance())
    }

    @Test
    fun contextLength() {
        assertEquals(1024 * 1024, llm.getMaxContextWindow())
    }

    @Test
    fun completion() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(prompt = "What is the capital of France?"),
            emptyList(),
        )
        // println(response)

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(true, choices[0].content?.contains("Paris"))
        assertEquals(true, choices[0].toolCalls.isEmpty())
    }

    @Test
    fun `completion with tool call`() {
        val meta = ToolMetadata(
            name = "date_tool_now",
            description = "Get the current date and time for a given location",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "The location to get the date and time for (e.g. 'Yaounde', 'Cameroon'). If not provided, ignore this parameter",
                    required = false
                )
            )
        )
        val tool = mock<Tool>()
        doReturn(meta).whenever(tool).metadata()

        val config = mapOf(
            "model" to "gemini-2.5-flash",
            "tools" to listOf("date_tool_now"),
        )
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(prompt = "What time is it at Paris?"),
            listOf(tool)
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.TOOL_CALLS, choices[0].finishReason)
        assertEquals(1, choices[0].toolCalls.size)
        assertEquals(meta.name, choices[0].toolCalls[0].name)
        assertEquals(mapOf("location" to "Paris"), choices[0].toolCalls[0].arguments)
    }

    @Test
    fun `completion with image file`() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you summarize this text?",
                files = listOf(
                    File(this::class.java.getResource("/file/medic.png")!!.file)
                )
            ),
            tools = emptyList()
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(0, choices[0].toolCalls.size)
        assertEquals(false, choices[0].content.isNullOrEmpty())
        println(choices[0].content)
    }

    //    @Test
    fun `completion with PDF file`() {
        llm.init(config, context)

        val response = llm.completion(
            request = LLMRequest(
                prompt = "Can you summarize this text?",
                files = listOf(
                    File(this::class.java.getResource("/file/document-en.pdf")!!.file)
                )
            ),
            tools = emptyList()
        )

        val choices = response.choices
        assertEquals(1, choices.size)
        assertEquals(LLMFinishReason.STOP, choices[0].finishReason)
        assertEquals(0, choices[0].toolCalls.size)
        assertEquals(false, choices[0].content.isNullOrEmpty())
        println(choices[0].content)
    }

    @Test
    fun availableModels() {
        llm.init(config, context)

        assertEquals(
            listOf(
                "gemini-3.5-flash",
                "gemini-3.1-flash-image",
                "gemini-3.1-flash-lite",
                "gemini-3.1-flash-lite-image",
                "gemini-3.1-pro-preview",
                "gemini-3-pro-image",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-2.5-pro",
            ),
            llm.availableModels()
        )
    }
}
