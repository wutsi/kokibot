package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.io.File
import java.util.UUID

class AssistantTest {
    private val home = getResourceFile("/home/007")
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val memory = mock<Memory>()
    private val commandRegistry = mock<CommandRegistry>()
    private val skillRegistry = mock<SkillRegistry>()
    private val dailyLog = mock<DailyLog>()
    private val sessionLog = mock<SessionLog>()
    private val assistantRegistry = mock<AssistantRegistry>()
    private val chatHistory = mock<ChatHistory>()
    private val context = Context(
        home = home,
        llm = llm,
        toolRegistry = toolRegistry,
        memory = memory,
        commandRegistry = commandRegistry,
        skillRegistry = skillRegistry,
        dailyLog = dailyLog,
        sessionLog = sessionLog,
        chatHistory = chatHistory,
        assistantRegistry = assistantRegistry,
        config = emptyMap<String, String>(),
    )
    private val assistant: Assistant = Assistant()

    private val cmd = mock<Command>()
    private val meta = CommandMetadata(name = "/tool")

    @BeforeEach
    fun setup() {
        assistant.init(emptyMap<Any, Any>(), context)

        doReturn(
            ToolMetadata(
                name = "test-tool",
                parameters = emptyList()
            )
        ).whenever(tool1).metadata()
        doReturn("Yaounde").whenever(tool1).exec(any())

        doReturn(
            ToolMetadata(
                name = "test-tool-2",
                parameters = emptyList()
            )
        ).whenever(tool2).metadata()
        doReturn("Paris").whenever(tool2).exec(any())

        doReturn(tool1).whenever(toolRegistry).get(any())
        doReturn(listOf(tool1, tool2)).whenever(toolRegistry).all()

        val skill1 = mock<Skill>()
        doReturn(Health(up = true, id = "xxx")).whenever(skill1).health()
        doReturn(
            SkillMetadata(
                name = "skill1",
                description = "Test skill",
                home = File("/target"),
            )
        ).whenever(skill1).metadata
        doReturn(listOf(skill1)).whenever(skillRegistry).all()
    }

    @Test
    fun init() {
        // WHEN
        // init() called in setup()

        // THEN
        verify(assistantRegistry).register(assistant)
    }

    @Test
    fun process() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("Yo", Role.USER, userId = "user-1", channelId = "channel:telegram")
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()

        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))

        val systemInstructions = req.firstValue.systemInstructions
        assertEquals(
            true,
            systemInstructions?.contains("You are a system agent designed to assist users with various tasks.\n")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Security Guidelines")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Daily Log Protocol")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Conversation History")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Available skills")
        )
    }

    @Test
    fun `process without ASSISTANT_md`() {
        // GIVEN
        assistant.init(
            emptyMap<Any, Any>(),
            context = Context(
                home = getResourceFile("/home/no-assistant-md"),
                llm = llm,
                toolRegistry = toolRegistry,
                memory = memory,
                skillRegistry = skillRegistry,
                dailyLog = dailyLog,
                sessionLog = sessionLog,
                chatHistory = chatHistory,
            )
        )

        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("Yo", Role.USER, userId = "user-1", channelId = "channel:telegram")
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))

        // ASSISANT.md is missing

        val systemInstructions = req.firstValue.systemInstructions
//        println(systemInstructions)
        assertEquals(
            false,
            systemInstructions?.contains("You are a system agent designed to assist users with various tasks.\n")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Security Guidelines")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Daily Log Protocol")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Conversation History")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Available skills")
        )
        assertEquals(
            false,
            systemInstructions?.contains("# Coordinator Agent Identity")
        )
    }

    @Test
    fun `process with security instructions`() {
        // GIVEN
        assistant.init(
            emptyMap<Any, Any>(),
            context = Context(
                home = getResourceFile("/home/007"),
                llm = llm,
                toolRegistry = toolRegistry,
                memory = memory,
                dailyLog = dailyLog,
                sessionLog = sessionLog,
            )
        )

        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))
    }

    @Test
    fun `process with memory`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        val memory = "- Fact1, Fact2"
        doReturn(memory).whenever(this.memory).get()

        val history = "This si the memory."
        doReturn(history).whenever(context.dailyLog).get()

        // WHEN
        val prompt = Message("Yo", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))

        assertEquals(
            """
Query: Yo

---
# Long-Term Memory
Here are information that you have stored in your long-term memory in Markdown format:
```markdown
$memory
```

---

# Short-Term Memory
Here are information that you have stored in your short-term memory in Markdown format:
```markdown
$history
```

               """.trimIndent(),
            req.firstValue.prompt
        )
    }

    @Test
    fun `process swarm`() {
        // GIVEN
        val planner = mock<Assistant>()
        doReturn(Message("from planner")).whenever(planner).process(any(), anyOrNull())

        assistant.init(
            mapOf(
                "coordinator" to true
            ),
            context = Context(
                home = getResourceFile("/home/swarm"),
                llm = llm,
                toolRegistry = toolRegistry,
                memory = memory,
                skillRegistry = skillRegistry,
                dailyLog = dailyLog,
                sessionLog = sessionLog,
                chatHistory = chatHistory,
                assistantRegistry = assistantRegistry,
            )
        )

        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Man",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("Yo", Role.USER, userId = "user-1", channelId = "channel:telegram")
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Man", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        val req = argumentCaptor<LLMRequest>()
        verify(llm).completion(req.capture(), eq(listOf(tool1, tool2)))
        assertEquals(true, req.firstValue.prompt.contains("Query: ${prompt.text}"))

        val systemInstructions = req.firstValue.systemInstructions
        println(systemInstructions)
        assertEquals(
            true,
            systemInstructions?.contains("You are a system agent designed to assist users with various tasks.")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Security Guidelines")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Daily Log Protocol")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Conversation History")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Available skills")
        )
        assertEquals(
            true,
            systemInstructions?.contains("# Coordinator Agent Identity")
        )
    }

    @Test
    fun `process with tool call`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "The capital of Cameroon is Yaounde",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The capital of Cameroon is Yaounde", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(llm, times(2)).completion(any(), eq(listOf(tool1, tool2)))
    }

    @Test
    fun `process with LLM error`() {
        // GIVEN
        doThrow(RuntimeException("Failed")).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.ERROR_FAILURE + ". Error: Failed", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.FAILURE, result.finishReason)
    }

    @Test
    fun `process with Tool error - ignore error`() {
        // GIVEN
        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "The capital of Cameroon is Yaounde",
                        finishReason = LLMFinishReason.STOP,
                        reasoningContent = null,
                        toolCalls = emptyList(),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        doThrow(RuntimeException("Failed")).whenever(tool1).exec(any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("The capital of Cameroon is Yaounde", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `process execute command`() {
        // GIVEN
        doReturn(meta).whenever(cmd).metadata()
        doReturn(cmd).whenever(commandRegistry).get(any())
        doReturn("Command executed").whenever(cmd).exec(any(), any())

        // WHEN
        val prompt = Message("/tool Hello world", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Command executed", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(cmd).exec(any(), any())
    }

    @Test
    fun `process execute command without arguments`() {
        // GIVEN
        doReturn(meta).whenever(cmd).metadata()
        doReturn(cmd).whenever(commandRegistry).get(any())
        doReturn("Command executed").whenever(cmd).exec(any(), any())

        // WHEN
        val prompt = Message("/tool", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Command executed", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)

        verify(cmd).exec(any(), any())
    }

    @Test
    fun `process execute invalid command`() {
        // GIVEN
        doThrow(CommandNotFoundException::class).whenever(commandRegistry).get(any())

        // WHEN
        val prompt = Message("/tool Hello world", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals("Invalid command: /tool.\nUse /help to get the list of available commands.", result.text)
        assertEquals(Role.COMMAND, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `too many iterations`() {
        // GIVEN
        assistant.init(mapOf("max-iterations" to 3), context)

        doReturn(
            LLMResponse(
                id = UUID.randomUUID().toString(),
                choices = listOf(
                    LLMResponseChoice(
                        content = "Calling the tool",
                        finishReason = LLMFinishReason.TOOL_CALLS,
                        reasoningContent = null,
                        toolCalls = listOf(
                            LLMToolCall(
                                name = "test-tool",
                                arguments = emptyMap<String, Any>()
                            )
                        ),
                    )
                )
            )
        ).whenever(llm).completion(any(), any())

        // WHEN
        val prompt = Message("What is the capital of Cameroon", Role.USER)
        val result = assistant.process(prompt)

        // THEN
        assertEquals(Assistant.ERROR_TOO_MANY_ITERATIONS, result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.TOO_MANY_ITERATIONS, result.finishReason)

        verify(llm, times(4)).completion(any(), eq(listOf(tool1, tool2)))
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
