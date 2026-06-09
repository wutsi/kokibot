# Assistant Refactoring - Extract PromptBuilder, AssistantExecutor, and ToolOrchestrator

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose the Assistant class into three focused components: PromptBuilder (prompt construction), AssistantExecutor (reasoning loop orchestration), and ToolOrchestrator (parallel tool execution)

**Architecture:** Extract three single-responsibility classes from Assistant.kt while maintaining all existing behavior. Assistant becomes a thin coordinator that delegates to these components. No feature changes, only structural refactoring.

**Tech Stack:** Kotlin, JUnit 5, Mockito Kotlin

---

## File Structure

**New Files:**
- `src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt` - Constructs prompts from query, memory, history, and system instructions
- `src/main/kotlin/com/wutsi/kokibot/assistant/AssistantExecutor.kt` - Manages the reasoning loop and iteration control
- `src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt` - Handles parallel tool execution and result collection
- `src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt` - Unit tests for PromptBuilder
- `src/test/kotlin/com/wutsi/kokibot/assistant/AssistantExecutorTest.kt` - Unit tests for AssistantExecutor
- `src/test/kotlin/com/wutsi/kokibot/assistant/ToolOrchestratorTest.kt` - Unit tests for ToolOrchestrator

**Modified Files:**
- `src/main/kotlin/com/wutsi/kokibot/Assistant.kt` - Refactored to delegate to new components
- `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt` - Updated to test new component interactions

**Package Structure:**
```
com.wutsi.kokibot/
├── Assistant.kt (coordinator)
└── assistant/
    ├── PromptBuilder.kt
    ├── AssistantExecutor.kt
    └── ToolOrchestrator.kt
```

---

## Task 1: Create ToolOrchestrator

**Rationale:** Start with ToolOrchestrator as it has the clearest boundary and fewest dependencies on other components.

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/assistant/ToolOrchestratorTest.kt`

**Responsibilities:**
- Parallel tool execution using ExecutorService
- Tool result collection and error handling
- Memory updates with tool results
- Session logging for tool use and results
- Tool status broadcasting

### Step 1: Write the failing test

Create test file with test for parallel tool execution:

```kotlin
package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToolOrchestratorTest {
    private val tool1 = mock<Tool>()
    private val tool2 = mock<Tool>()
    private val toolRegistry = mock<ToolRegistry>()
    private val sessionLog = mock<SessionLog>()
    private val context = mock<Context>()
    private lateinit var orchestrator: ToolOrchestrator

    @BeforeEach
    fun setup() {
        doReturn(sessionLog).whenever(context).sessionLog
        doReturn(toolRegistry).whenever(context).toolRegistry

        doReturn(ToolMetadata(name = "tool1", parameters = emptyList())).whenever(tool1).metadata()
        doReturn("result1").whenever(tool1).exec(any())

        doReturn(ToolMetadata(name = "tool2", parameters = emptyList())).whenever(tool2).metadata()
        doReturn("result2").whenever(tool2).exec(any())

        orchestrator = ToolOrchestrator(threadPoolSize = 4)
    }

    @AfterEach
    fun cleanup() {
        orchestrator.destroy()
    }

    @Test
    fun `should execute tools in parallel`() {
        val toolCalls = listOf(
            LLMToolCall(id = "1", name = "tool1", arguments = mapOf("arg1" to "value1")),
            LLMToolCall(id = "2", name = "tool2", arguments = mapOf("arg2" to "value2"))
        )
        val memory = mutableListOf<String>()
        val tools = mapOf("tool1" to tool1, "tool2" to tool2)
        val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

        orchestrator.executeTools(
            id = query.id,
            iteration = 1,
            assistantName = "test-assistant",
            toolCalls = toolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )

        verify(tool1).exec(mapOf("arg1" to "value1"))
        verify(tool2).exec(mapOf("arg2" to "value2"))
        assertTrue(memory.size >= 4) // 2 tools x (usage + result)
    }
}
```

### Step 2: Run test to verify it fails

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: FAIL with "class not found"

### Step 3: Create ToolOrchestrator class

```kotlin
package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.ToolExecutionResult
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.service.ExecutionContext
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.user.AskQuestionException
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ToolOrchestrator(
    private val threadPoolSize: Int = 4
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ToolOrchestrator::class.java)
    }

    private val toolExecutor: ExecutorService = Executors.newFixedThreadPool(threadPoolSize)

    fun destroy() {
        LOGGER.info("Shutting down tool executor")
        toolExecutor.shutdown()
        try {
            if (!toolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("Tool executor did not terminate in 30s, forcing shutdown")
                toolExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            LOGGER.warn("Interrupted while waiting for tool executor shutdown")
            toolExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    fun executeTools(
        id: String,
        iteration: Int,
        assistantName: String,
        toolCalls: List<LLMToolCall>,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ) {
        if (toolCalls.isEmpty()) {
            return
        }

        LOGGER.info("$iteration $assistantName Executing ${toolCalls.size} tool calls in parallel")

        sendToolStatus(query, toolCalls, context)

        val callables = toolCalls.map { call ->
            createToolCallable(id, iteration, assistantName, call, tools, query, context)
        }

        val futures = callables.map { callable ->
            toolExecutor.submit(callable)
        }

        val results = futures.mapIndexed { index, future ->
            try {
                future.get()
            } catch (e: Exception) {
                val call = toolCalls.getOrNull(index) ?: LLMToolCall(name = "unknown", id = "error-$index")
                LOGGER.error("Tool execution failed for ${call.name}: ${e.message}", e)
                val errorMessage = when (e) {
                    is TimeoutException -> "Tool `${call.name}` timed out"
                    is CancellationException -> "Tool `${call.name}` was cancelled"
                    else -> "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
                ToolExecutionResult(call = call, result = errorMessage, error = e)
            }
        }

        results.forEach { result ->
            if (result.error is AskQuestionException) {
                throw result.error
            }

            memory.add(
                "Using tool `${result.call.name}` with arguments: " +
                    result.call.arguments.map { entry ->
                        "${entry.key}=" + entry.value?.let { value ->
                            take(value.toString(), 200)
                        }
                    }.joinToString(",")
            )
            memory.add(result.result)

            context.sessionLog.onToolResult(id, iteration, result.call, result.result)
        }

        LOGGER.info("$iteration $assistantName Completed ${results.size} tool calls")
    }

    private fun createToolCallable(
        id: String,
        iteration: Int,
        assistantName: String,
        call: LLMToolCall,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): Callable<ToolExecutionResult> {
        return Callable {
            ExecutionContext.set(id, assistantName, query.userId, query.channelId)
            val startTime = System.currentTimeMillis()
            LOGGER.info(
                "$iteration $assistantName TOOL ${call.name} " +
                    call.arguments.map { entry ->
                        "${entry.key}=" + entry.value?.let { value -> take(value.toString(), 200) }
                    }.joinToString(",")
            )
            context.sessionLog.onToolUse(id, iteration, call)

            var exception: Exception? = null
            val result = tools[call.name]?.let { tool ->
                try {
                    tool.exec(call.arguments)
                } catch (e: Exception) {
                    exception = e
                    val duration = System.currentTimeMillis() - startTime
                    LOGGER.warn("Unexpected error while executing tool `${call.name}` after ${duration}ms. Error=${e.message}")
                    "Unexpected error while executing tool `${call.name}`. Error=${e.message}"
                }
            }
            ToolExecutionResult(
                call = call,
                result = result ?: "Tool `${call.name}` not found",
                error = exception,
            )
        }
    }

    private fun sendToolStatus(query: Message, toolCalls: List<LLMToolCall>, context: Context) {
        toolCalls.groupBy { toolCall -> toolCall.name }
            .forEach { entry ->
                val tool = context.toolRegistry.get(entry.key)
                val statusText = "⚙️ " + tool.statusText(entry.value)
                sendToolStatus(query, statusText, context)
            }
    }

    private fun sendToolStatus(query: Message, statusText: String, context: Context) {
        try {
            val userId = query.userId
            val channelId = query.channelId
            if (userId != null && channelId != null) {
                val channel = context.channelRegistry.get(channelId)
                channel.sendStatus(
                    Message(
                        text = statusText,
                        role = Role.SYSTEM,
                        userId = userId,
                        channelId = channelId,
                    )
                )
            }
        } catch (e: Exception) {
            LOGGER.debug("Failed to send tool status: ${e.message}")
        }
    }

    private fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim()
        return if (text.length > n) {
            "$xtext..."
        } else {
            xtext
        }
    }
}
```

### Step 4: Run test to verify it passes

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: PASS

### Step 5: Add test for error handling

```kotlin
@Test
fun `should handle tool errors gracefully`() {
    val errorTool = mock<Tool>()
    doReturn(ToolMetadata(name = "error-tool", parameters = emptyList())).whenever(errorTool).metadata()
    doThrow(RuntimeException("Tool failed")).whenever(errorTool).exec(any())

    val toolCalls = listOf(
        LLMToolCall(id = "1", name = "error-tool", arguments = mapOf("arg1" to "value1"))
    )
    val memory = mutableListOf<String>()
    val tools = mapOf("error-tool" to errorTool)
    val query = Message(id = "test-id", userId = "user1", channelId = "channel1")

    orchestrator.executeTools(
        id = query.id,
        iteration = 1,
        assistantName = "test-assistant",
        toolCalls = toolCalls,
        memory = memory,
        tools = tools,
        query = query,
        context = context
    )

    assertTrue(memory.any { it.contains("Unexpected error") })
}
```

### Step 6: Run test to verify it passes

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: PASS (error handling already implemented)

### Step 7: Commit

```bash
git add src/main/kotlin/com/wutsi/kokibot/assistant/ToolOrchestrator.kt \
        src/test/kotlin/com/wutsi/kokibot/assistant/ToolOrchestratorTest.kt
git commit -m "feat: extract ToolOrchestrator for parallel tool execution

- New ToolOrchestrator class handles parallel tool execution
- Manages ExecutorService lifecycle
- Handles tool errors and result collection
- Updates memory and session log
- Sends tool status to channels"
```

---

## Task 2: Create PromptBuilder

**Rationale:** PromptBuilder has clear inputs/outputs and no reasoning logic, making it straightforward to extract.

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt`

**Responsibilities:**
- Construct user prompts from query, memory, and history
- Build system instructions from multiple sources (ASSISTANT.md, COORDINATOR.md, SECURITY.md, skills, chat history)
- Load and template instruction files

### Step 1: Write the failing test

```kotlin
package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class PromptBuilderTest {
    private val home = File(javaClass.getResource("/home/007")!!.file)
    private val memory = mock<Memory>()
    private val dailyLog = mock<DailyLog>()
    private val chatHistory = mock<ChatHistory>()
    private val skillRegistry = mock<SkillRegistry>()
    private val context = mock<Context>()
    private lateinit var builder: PromptBuilder

    @BeforeEach
    fun setup() {
        doReturn(home).whenever(context).home
        doReturn(memory).whenever(context).memory
        doReturn(dailyLog).whenever(context).dailyLog
        doReturn(chatHistory).whenever(context).chatHistory
        doReturn(skillRegistry).whenever(context).skillRegistry

        doReturn(null).whenever(memory).get()
        doReturn(null).whenever(dailyLog).get()
        doReturn(emptyList<Skill>()).whenever(skillRegistry).all()

        builder = PromptBuilder(assistantName = "test-assistant")
    }

    @Test
    fun `should build prompt with query text`() {
        val query = Message(text = "What is the weather?")
        val iterationMemory = emptyList<String>()

        val prompt = builder.buildPrompt(query, iterationMemory, context)

        assertTrue(prompt.contains("Query: What is the weather?"))
    }

    @Test
    fun `should include long-term memory in prompt`() {
        doReturn("User prefers concise answers").whenever(memory).get()
        val query = Message(text = "Test query")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("# Long-Term Memory"))
        assertTrue(prompt.contains("User prefers concise answers"))
    }

    @Test
    fun `should include iteration memory in prompt`() {
        val query = Message(text = "Test query")
        val iterationMemory = listOf("Step 1: Called tool X", "Step 2: Got result Y")

        val prompt = builder.buildPrompt(query, iterationMemory, context)

        assertTrue(prompt.contains("# Previous reasoning steps"))
        assertTrue(prompt.contains("Step 1: Called tool X"))
        assertTrue(prompt.contains("Step 2: Got result Y"))
    }

    @Test
    fun `should build system instructions with assistant identity`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            coordinator = false,
            context = context
        )

        assertTrue(instructions.contains("You are 007"))
    }

    @Test
    fun `should include coordinator instructions when enabled`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            coordinator = true,
            context = context
        )

        assertTrue(instructions.contains("COORDINATOR"))
    }

    @Test
    fun `should include skills in system instructions`() {
        val skill1 = mock<Skill>()
        doReturn(Health(up = true, id = "skill1")).whenever(skill1).health()
        doReturn(
            SkillMetadata(
                name = "weather",
                description = "Get weather info",
                home = File("/tmp/skills/weather")
            )
        ).whenever(skill1).metadata

        doReturn(listOf(skill1)).whenever(skillRegistry).all()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("# Available skills"))
        assertTrue(instructions.contains("weather"))
        assertTrue(instructions.contains("Get weather info"))
    }
}
```

### Step 2: Run test to verify it fails

Run: `mvn test -Dtest=PromptBuilderTest`
Expected: FAIL with "class not found"

### Step 3: Create PromptBuilder class

```kotlin
package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import org.apache.commons.io.IOUtils
import java.io.File

class PromptBuilder(
    private val assistantName: String
) {
    fun buildPrompt(
        query: Message,
        iterationMemory: List<String>,
        context: Context
    ): String {
        val sb = StringBuilder()
        sb.append("Query: ${query.text}\n")

        val longTermMemory = context.memory.get()
        if (longTermMemory != null) {
            sb.append("\n---\n")
            sb.append("# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$longTermMemory\n```\n")
        }

        val shortTermMemory = context.dailyLog.get()
        if (shortTermMemory != null) {
            sb.append("\n---\n\n")
            sb.append("# Short-Term Memory\n")
            sb.append("Here are information that you have stored in your short-term memory in Markdown format:\n")
            sb.append("```markdown\n$shortTermMemory\n```\n")
        }

        if (iterationMemory.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
            iterationMemory.forEach { line -> sb.append("$line\n\n") }
        }

        return sb.toString()
    }

    fun buildSystemInstructions(
        query: Message,
        coordinator: Boolean,
        context: Context
    ): String {
        val entries = listOfNotNull(
            loadIdentity(context.home),
            if (coordinator) coordinatorInstructions(context.home) else null,
            dailyLogInstructions(context.home),
            chatHistoryInstructions(query, context.home),
            skillsInstructions(context),
            securityInstructions(context.home),
        )
        return entries.joinToString("\n\n---\n\n")
    }

    private fun loadIdentity(home: File): String? {
        val file = File(home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText().replace("{{ASSISTANT_NAME}}", assistantName)
        } else {
            null
        }
    }

    private fun skillsInstructions(context: Context): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "## Skill: ${skill.metadata.name}\n\n" +
                        "**Home Directory:** ${skill.metadata.home}\n\n" +
                        "**Description:** ${skill.metadata.description}"
                ).joinToString("\n\n")
            }
            .ifEmpty { null }

        return skills?.let { "# Available skills\n\nHere are the skills available:\n\n$skills" }
    }

    private fun securityInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/SECURITY.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun coordinatorInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/COORDINATOR.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun dailyLogInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/DAILY_LOG.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun chatHistoryInstructions(query: Message, home: File): String? {
        val userId = query.userId
        val channelId = query.channelId
        if (userId == null || channelId == null) {
            return null
        }
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/CHAT_HISTORY.md"),
            "utf-8"
        )
            .replace("{{HOME}}", home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}
```

### Step 4: Run test to verify it passes

Run: `mvn test -Dtest=PromptBuilderTest`
Expected: PASS

### Step 5: Commit

```bash
git add src/main/kotlin/com/wutsi/kokibot/assistant/PromptBuilder.kt \
        src/test/kotlin/com/wutsi/kokibot/assistant/PromptBuilderTest.kt
git commit -m "feat: extract PromptBuilder for prompt construction

- New PromptBuilder class handles all prompt construction
- Builds user prompts with query, memory, and history
- Builds system instructions from multiple sources
- Loads and templates instruction files
- Supports coordinator mode"
```

---

## Task 3: Create AssistantExecutor

**Rationale:** Now that tool execution and prompt building are extracted, we can extract the reasoning loop orchestration.

**Files:**
- Create: `src/main/kotlin/com/wutsi/kokibot/assistant/AssistantExecutor.kt`
- Create: `src/test/kotlin/com/wutsi/kokibot/assistant/AssistantExecutorTest.kt`

**Responsibilities:**
- Manage the reasoning loop (iteration control, max iterations)
- Coordinate LLM calls via PromptBuilder
- Decide when to stop (tool calls vs text response)
- Coordinate tool execution via ToolOrchestrator
- Session and chat history management

### Step 1: Write the failing test

```kotlin
package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.FinishReason
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandRegistry
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssistantExecutorTest {
    private val llm = mock<LLM>()
    private val toolRegistry = mock<ToolRegistry>()
    private val commandRegistry = mock<CommandRegistry>()
    private val sessionLog = mock<SessionLog>()
    private val chatHistory = mock<ChatHistory>()
    private val context = mock<Context>()
    private val promptBuilder = mock<PromptBuilder>()
    private val toolOrchestrator = mock<ToolOrchestrator>()
    private lateinit var executor: AssistantExecutor

    @BeforeEach
    fun setup() {
        doReturn(llm).whenever(context).llm
        doReturn(toolRegistry).whenever(context).toolRegistry
        doReturn(commandRegistry).whenever(context).commandRegistry
        doReturn(sessionLog).whenever(context).sessionLog
        doReturn(chatHistory).whenever(context).chatHistory

        doReturn(emptyList<Tool>()).whenever(toolRegistry).all()
        doReturn(false).whenever(llm).supportsStreaming()

        executor = AssistantExecutor(
            assistantName = "test-assistant",
            maxIterations = 10,
            coordinator = false,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )
    }

    @AfterEach
    fun cleanup() {
        executor.destroy()
    }

    @Test
    fun `should execute simple query with text response`() {
        val query = Message(text = "Hello", userId = "user1", channelId = "channel1")
        doReturn("Query: Hello").whenever(promptBuilder).buildPrompt(any(), any(), any())
        doReturn("Instructions").whenever(promptBuilder).buildSystemInstructions(any(), any(), any())

        val llmResponse = LLMResponse(
            choices = listOf(
                LLMResponseChoice(
                    content = "Hello there!",
                    finishReason = LLMFinishReason.DONE
                )
            )
        )
        doReturn(llmResponse).whenever(llm).completion(any(), any())

        val result = executor.execute(query, null, 0, mutableListOf(), context)

        assertEquals("Hello there!", result.text)
        assertEquals(Role.ASSISTANT, result.role)
        assertEquals(FinishReason.DONE, result.finishReason)
    }

    @Test
    fun `should execute command`() {
        val command = mock<Command>()
        val metadata = CommandMetadata(name = "/help")
        doReturn(metadata).whenever(command).metadata()
        doReturn("Help text").whenever(command).exec(any(), any())
        doReturn(command).whenever(commandRegistry).get("/help")

        val query = Message(text = "/help", userId = "user1", channelId = "channel1")
        val result = executor.execute(query, null, 0, mutableListOf(), context)

        assertEquals("Help text", result.text)
        assertEquals(Role.COMMAND, result.role)
    }
}
```

### Step 2: Run test to verify it fails

Run: `mvn test -Dtest=AssistantExecutorTest`
Expected: FAIL with "class not found"

### Step 3: Create AssistantExecutor class

```kotlin
package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.FinishReason
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.TooManyIterationException
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata
import com.wutsi.kokibot.command.CommandNotFoundException
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.user.AskQuestionException
import org.slf4j.LoggerFactory
import java.io.File

class AssistantExecutor(
    private val assistantName: String,
    private val maxIterations: Int,
    private val coordinator: Boolean,
    private val promptBuilder: PromptBuilder,
    private val toolOrchestrator: ToolOrchestrator
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssistantExecutor::class.java)
    }

    fun destroy() {
        toolOrchestrator.destroy()
    }

    fun execute(
        query: Message,
        streamCallback: ((String) -> Unit)?,
        startIteration: Int,
        memory: MutableList<String>,
        context: Context
    ): Message {
        var iteration = startIteration
        val tools = mutableMapOf<String, Tool>()
        context.toolRegistry.all().forEach { tool -> tools[tool.metadata().name] = tool }

        while (true) {
            if (iteration++ > maxIterations) {
                throw TooManyIterationException("Sorry, I cannot find the answer to your question.")
            }

            val command = getCommand(query, context)
            if (command != null) {
                val result = execCommand(iteration, query, command, context)
                return Message(
                    text = result,
                    role = Role.COMMAND,
                    finishReason = FinishReason.DONE,
                )
            } else {
                try {
                    val response = callLLM(iteration, query, memory, streamCallback, context)
                    if (shouldStop(query.id, iteration, response, memory, tools, query, context)) {
                        return Message(
                            text = response.choices.mapNotNull { choice -> choice.content }.joinToString("\n\n"),
                            role = Role.ASSISTANT,
                            finishReason = FinishReason.DONE,
                        )
                    } else {
                        if (streamCallback != null) {
                            response.choices.forEach { choice ->
                                if (!choice.content.isNullOrEmpty()) {
                                    streamCallback(choice.content)
                                }
                            }
                        }
                    }
                } catch (ex: AskQuestionException) {
                    context.sessionLog.pause(query.userId, query.channelId, query.id)
                    return Message(
                        text = ex.question,
                        role = Role.ASSISTANT,
                        finishReason = FinishReason.DONE,
                    )
                }
            }
        }
    }

    private fun callLLM(
        iteration: Int,
        query: Message,
        memory: MutableList<String>,
        streamCallback: ((String) -> Unit)?,
        context: Context
    ): LLMResponse {
        LOGGER.info("$iteration $assistantName LLM " + take(query.text, 200))

        val request = LLMRequest(
            prompt = promptBuilder.buildPrompt(query, memory, context),
            systemInstructions = promptBuilder.buildSystemInstructions(query, coordinator, context),
            files = query.filePaths.map { path -> File(path) }
        )

        val tools = context.toolRegistry.all()
        val streamingEnabled = context.llm.supportsStreaming()
        val response = if (streamingEnabled && streamCallback != null) {
            context.llm.completionStream(
                request = request,
                tools = tools,
                onChunk = { chunk ->
                    chunk.reasoningDelta?.let { delta ->
                        streamCallback(delta)
                    }
                }
            )
        } else {
            context.llm.completion(request = request, tools)
        }

        LOGGER.info("$iteration $assistantName LLM - tokens=" + response.usage?.totalTokens + ", cached=" + response.usage?.promptCacheHitTokens)
        context.sessionLog.onLLMResponse(query.id, iteration, response, memory)

        response.choices.forEach { choice ->
            if (!choice.content.isNullOrEmpty()) {
                LOGGER.info(take(choice.content, 200))
                memory.add(choice.content)
            }
        }
        return response
    }

    private fun shouldStop(
        id: String,
        iteration: Int,
        response: LLMResponse,
        memory: MutableList<String>,
        tools: Map<String, Tool>,
        query: Message,
        context: Context
    ): Boolean {
        val allToolCalls = response.choices.flatMap { choice -> choice.toolCalls }
        if (allToolCalls.isEmpty()) {
            return true
        }

        toolOrchestrator.executeTools(
            id = id,
            iteration = iteration,
            assistantName = assistantName,
            toolCalls = allToolCalls,
            memory = memory,
            tools = tools,
            query = query,
            context = context
        )
        return false
    }

    private fun getCommand(query: Message, context: Context): Command? {
        val text = query.text.trim()
        if (!text.startsWith("/")) {
            return null
        }

        val name = text.split(" ").firstOrNull() ?: return null
        try {
            return context.commandRegistry.get(name)
        } catch (ex: CommandNotFoundException) {
            LOGGER.warn("Command not found: $name", ex)
            return object : Command {
                override fun metadata(): CommandMetadata {
                    return CommandMetadata(name = "")
                }

                override fun exec(input: Message, context: Context): String {
                    return "Invalid command: ${input.text.split(" ").first()}.\nUse /help to get the list of available commands."
                }
            }
        }
    }

    private fun execCommand(iteration: Int, query: Message, command: Command, context: Context): String {
        val text = query.text.trim()
        val name = command.metadata().name
        val commandText = if (text.equals(name, ignoreCase = true)) {
            ""
        } else {
            text.substring(name.length).trim()
        }

        LOGGER.info("$iteration - COMMAND: {} {}", name, commandText)
        return command.exec(query.copy(text = commandText), context)
    }

    private fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim()
        return if (text.length > n) {
            "$xtext..."
        } else {
            xtext
        }
    }
}
```

### Step 4: Run test to verify it passes

Run: `mvn test -Dtest=AssistantExecutorTest`
Expected: PASS

### Step 5: Commit

```bash
git add src/main/kotlin/com/wutsi/kokibot/assistant/AssistantExecutor.kt \
        src/test/kotlin/com/wutsi/kokibot/assistant/AssistantExecutorTest.kt
git commit -m "feat: extract AssistantExecutor for reasoning loop

- New AssistantExecutor class manages reasoning loop
- Coordinates LLM calls and tool execution
- Handles commands and iteration control
- Delegates to PromptBuilder and ToolOrchestrator
- Manages session pause/resume for user questions"
```

---

## Task 4: Refactor Assistant to Use Extracted Components

**Rationale:** Now that all components are extracted and tested, refactor Assistant to delegate to them.

**Files:**
- Modify: `src/main/kotlin/com/wutsi/kokibot/Assistant.kt`

**Changes:**
- Remove all prompt building logic (delegate to PromptBuilder)
- Remove all tool execution logic (delegate to ToolOrchestrator via AssistantExecutor)
- Remove reasoning loop logic (delegate to AssistantExecutor)
- Keep: configuration, timeout management, session restoration, delegation stack, logging

### Step 1: Run existing tests to establish baseline

Run: `mvn test -Dtest=AssistantTest`
Expected: PASS (baseline before refactoring)

### Step 2: Refactor Assistant class

Replace the implementation methods with delegation to new components:

```kotlin
package com.wutsi.kokibot

import com.wutsi.kokibot.assistant.AssistantExecutor
import com.wutsi.kokibot.assistant.PromptBuilder
import com.wutsi.kokibot.assistant.ToolOrchestrator
import com.wutsi.kokibot.util.DurationUtil
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Assistant(val name: String = "") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Assistant::class.java)
        private const val DEFAULT_ITERATIONS = 10
        const val DEFAULT_MAX_DURATION_MINUTES = 5L
        const val ERROR_TOO_MANY_ITERATIONS = "Oups, the request has been cancelled."
        const val ERROR_TIMEOUT = "Oups, the request has been cancelled because it took too much time to process."
        const val ERROR_FAILURE = "Oups, an unexpected error occurred while processing the query."
    }

    private var maxIterations: Int = DEFAULT_ITERATIONS
    private var maxDurationMinutes: Long = DEFAULT_MAX_DURATION_MINUTES
    lateinit var description: String
    private lateinit var context: Context
    private var coordinator: Boolean = false
    private var threadPoolSize: Int = 4
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var toolOrchestrator: ToolOrchestrator
    private lateinit var executor: AssistantExecutor

    fun init(config: Map<*, *>, context: Context) {
        maxIterations = MapUtil.toInt("max-iterations", config) ?: DEFAULT_ITERATIONS
        description = MapUtil.toString("description", config) ?: ""
        coordinator = MapUtil.toBoolean("coordinator", config) ?: false
        maxDurationMinutes = MapUtil.toString("max-duration", config)
            ?.let { value -> DurationUtil.minutes(value, DEFAULT_MAX_DURATION_MINUTES) }
            ?: DEFAULT_MAX_DURATION_MINUTES

        threadPoolSize = MapUtil.toInt("thread-pool-size", config) ?: 4
        if (threadPoolSize < 2) {
            LOGGER.warn("thread-pool-size must be at least 2, using 2")
            threadPoolSize = 2
        }

        promptBuilder = PromptBuilder(assistantName = name)
        toolOrchestrator = ToolOrchestrator(threadPoolSize = threadPoolSize)
        executor = AssistantExecutor(
            assistantName = name,
            maxIterations = maxIterations,
            coordinator = coordinator,
            promptBuilder = promptBuilder,
            toolOrchestrator = toolOrchestrator
        )

        this.context = context
        context.assistantRegistry.register(this)

        LOGGER.info("Assistant: $name")
        LOGGER.info("  coordinator: $coordinator")
        LOGGER.info("  max-duration: ${maxDurationMinutes}m")
        LOGGER.info("  max-iterations: $maxIterations")
        LOGGER.info("  thread-pool-size: $threadPoolSize")
    }

    fun destroy() {
        if (::executor.isInitialized) {
            executor.destroy()
        }
    }

    fun contextLength(userId: String?, channelId: String?): Int {
        val query = Message(userId = userId, channelId = channelId)
        return promptBuilder.buildPrompt(query, emptyList(), context).length +
            promptBuilder.buildSystemInstructions(query, coordinator, context).length
    }

    fun process(
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
    ): Message {
        val now = System.currentTimeMillis()
        val sessionId = context.sessionLog.resume(query.userId, query.channelId)
        val sessions = sessionId?.let { context.sessionLog.get(sessionId) }

        var xquery = query
        var iteration = 0
        var memory = mutableListOf<String>()
        if (sessions != null && sessions.isNotEmpty()) {
            xquery = query.copy(
                id = sessionId,
                text = sessions.first().content.firstOrNull { content -> content.type == "text" }?.text ?: query.text,
                filePaths = sessions.first().content.filter { content -> content.type == "file" }
                    .mapNotNull { content -> content.text }
            )
            memory = sessions.lastOrNull { session -> session.memory != null && session.memory.isNotEmpty() }
                ?.memory
                ?.toMutableList()
                ?: mutableListOf()
            memory.add(query.text)

            iteration = sessions.last { session -> session.iteration != null }.iteration ?: 0
        }

        LOGGER.info(
            "${xquery.id} $name ${xquery.userId ?: "-"}@${xquery.channelId ?: "-"} files=${xquery.filePaths} " +
                take(xquery.text, 200)
        )
        context.sessionLog.onQuery(xquery.id, iteration, xquery)

        context.delegationStack.push(xquery.id, name, streamCallback)
        val response = try {
            val timer = Executors.newSingleThreadExecutor()
            val future = timer.submit<Message> {
                doProcessAsync(xquery, streamCallback, iteration, memory)
            }

            try {
                future.get(maxDurationMinutes, TimeUnit.MINUTES)
            } catch (_: TimeoutException) {
                future.cancel(true)
                Message(ERROR_TIMEOUT, Role.ASSISTANT, FinishReason.TIMEOUT)
            } catch (e: Exception) {
                Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
            } finally {
                try {
                    timer.shutdown()
                } catch (e: Exception) {
                    LOGGER.warn("Error while shutting down scheduler. ${e.message}")
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Delegation stack push failed for $name", e)
            Message("Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        } finally {
            context.delegationStack.pop(query.id)
        }

        val duration = DurationUtil.hms(System.currentTimeMillis() - now)
        LOGGER.info("${query.id} $name FINAL ANSWER ($duration): " + take(response.text, 200))
        context.chatHistory.append(query, response)
        context.sessionLog.onResponse(query.id, response)
        return response
    }

    private fun doProcessAsync(
        query: Message,
        streamCallback: ((String) -> Unit)? = null,
        iteration: Int,
        memory: MutableList<String>,
    ): Message {
        return try {
            executor.execute(query, streamCallback, iteration, memory, context)
        } catch (e: TooManyIterationException) {
            LOGGER.error("Too many iterations!", e)
            Message(ERROR_TOO_MANY_ITERATIONS, Role.ASSISTANT, FinishReason.TOO_MANY_ITERATIONS)
        } catch (e: Exception) {
            LOGGER.error("Unexpected error!", e)
            Message(ERROR_FAILURE + ". Error: ${e.message}", Role.ASSISTANT, FinishReason.FAILURE)
        }
    }

    private fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim()
        return if (text.length > n) {
            "$xtext..."
        } else {
            xtext
        }
    }
}
```

### Step 3: Run tests to verify refactoring

Run: `mvn test -Dtest=AssistantTest`
Expected: PASS (all tests still pass after refactoring)

### Step 4: Run full test suite

Run: `mvn test`
Expected: PASS (no regressions)

### Step 5: Commit

```bash
git add src/main/kotlin/com/wutsi/kokibot/Assistant.kt
git commit -m "refactor: delegate Assistant logic to extracted components

- Assistant now delegates to PromptBuilder, ToolOrchestrator, AssistantExecutor
- Removed all prompt building logic (moved to PromptBuilder)
- Removed all tool execution logic (moved to ToolOrchestrator)
- Removed reasoning loop logic (moved to AssistantExecutor)
- Kept configuration, timeout, session restoration, delegation stack
- All existing tests pass without modification"
```

---

## Task 5: Update Integration Tests

**Rationale:** Ensure AssistantTest properly covers the new component interactions.

**Files:**
- Modify: `src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt`

### Step 1: Review current test coverage

Run: `mvn test -Dtest=AssistantTest`
Expected: PASS

Note: Tests should already pass from Task 4. This task ensures tests cover component integration.

### Step 2: Add test for component initialization

Add test to verify components are properly initialized:

```kotlin
@Test
fun `should initialize all components`() {
    val config = mapOf(
        "max-iterations" to 5,
        "coordinator" to true,
        "thread-pool-size" to 8
    )
    val newAssistant = Assistant("test")
    newAssistant.init(config, context)

    // Verify initialization by calling process (components must be initialized)
    val query = Message(text = "Test")
    doReturn(
        LLMResponse(
            choices = listOf(
                LLMResponseChoice(content = "Response", finishReason = LLMFinishReason.DONE)
            )
        )
    ).whenever(llm).completion(any(), any())

    val result = newAssistant.process(query)
    assertEquals("Response", result.text)

    newAssistant.destroy()
}
```

### Step 3: Run test to verify it passes

Run: `mvn test -Dtest=AssistantTest#should_initialize_all_components`
Expected: PASS

### Step 4: Run full test suite

Run: `mvn test`
Expected: PASS

### Step 5: Verify code coverage

Run: `mvn clean test jacoco:report`
Then: `open target/site/jacoco/index.html`
Expected: Coverage >= 90% for all classes

### Step 6: Commit

```bash
git add src/test/kotlin/com/wutsi/kokibot/AssistantTest.kt
git commit -m "test: verify component integration in AssistantTest

- Added test for component initialization
- Verified coverage remains >= 90%
- All integration tests pass"
```

---

## Task 6: Run Linter and Final Verification

**Rationale:** Ensure code meets project style guidelines.

### Step 1: Run ktlint format

Run: `mvn antrun:run@ktlint-format`
Expected: SUCCESS with formatting applied

### Step 2: Run full build with tests

Run: `mvn clean install`
Expected: SUCCESS with all tests passing and coverage >= 90%

### Step 3: Verify no compilation warnings

Check build output for warnings
Expected: No warnings related to new classes

### Step 4: Manual smoke test check

Review the following manually:
- [ ] All new classes have proper package declarations
- [ ] All imports are used
- [ ] No unused parameters
- [ ] Logging uses proper levels (INFO for operations, WARN for issues, ERROR for failures)
- [ ] No hardcoded paths or magic numbers
- [ ] Thread pool cleanup in destroy methods

### Step 5: Commit lint fixes if any

```bash
git add -A
git commit -m "style: apply ktlint formatting to extracted components"
```

---

## Self-Review

### Spec Coverage

✅ **PromptBuilder extraction** - Task 2 creates PromptBuilder with all prompt construction logic
✅ **AssistantExecutor extraction** - Task 3 creates AssistantExecutor with reasoning loop
✅ **ToolOrchestrator extraction** - Task 1 creates ToolOrchestrator with parallel execution
✅ **Assistant refactoring** - Task 4 refactors Assistant to delegate to components
✅ **Test coverage** - Each component has dedicated unit tests, integration tests updated
✅ **No behavior changes** - Refactoring only, all existing tests pass

### Placeholder Scan

✅ No "TBD", "TODO", "implement later", "fill in details"
✅ No "add appropriate error handling" without implementation
✅ No "similar to Task N" without actual code
✅ All test code is complete and executable
✅ All implementation code is complete

### Type Consistency

✅ `PromptBuilder.buildPrompt()` returns `String` consistently
✅ `PromptBuilder.buildSystemInstructions()` returns `String` consistently
✅ `AssistantExecutor.execute()` returns `Message` consistently
✅ `ToolOrchestrator.executeTools()` returns `Unit` consistently
✅ Component construction signatures match between tasks
✅ `Context` parameter passed consistently across all components

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-09-refactor-assistant-extraction.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** - Fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
