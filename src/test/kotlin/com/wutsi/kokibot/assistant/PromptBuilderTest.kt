package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import com.wutsi.kokibot.skill.SkillRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class PromptBuilderTest {
    private fun getResourceFile(path: String): File {
        return File(javaClass.getResource(path)!!.file)
    }

    private val home = getResourceFile("/home/007")
    private val memory = mock<Memory>()
    private val dailyLog = mock<DailyLog>()
    private val skillRegistry = mock<SkillRegistry>()
    private val context = mock<Context>()
    private lateinit var builder: PromptBuilder

    @BeforeEach
    fun setup() {
        doReturn(home).whenever(context).home
        doReturn(memory).whenever(context).memory
        doReturn(dailyLog).whenever(context).dailyLog
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
        assertTrue(prompt.contains("```markdown"))
    }

    @Test
    fun `should include short-term memory in prompt`() {
        doReturn("Today's task: implement feature X").whenever(dailyLog).get()
        val query = Message(text = "Test query")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("# Short-Term Memory"))
        assertTrue(prompt.contains("Today's task: implement feature X"))
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
    fun `should build prompt with all memories combined`() {
        doReturn("Long-term fact").whenever(memory).get()
        doReturn("Short-term info").whenever(dailyLog).get()
        val query = Message(text = "Test query")
        val iterationMemory = listOf("Iteration step")

        val prompt = builder.buildPrompt(query, iterationMemory, context)

        assertTrue(prompt.contains("Query: Test query"))
        assertTrue(prompt.contains("Long-term fact"))
        assertTrue(prompt.contains("Short-term info"))
        assertTrue(prompt.contains("Iteration step"))
    }

    @Test
    fun `should build system instructions with assistant identity`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            coordinator = false,
            context = context
        )

        assertTrue(instructions.contains("You are a system agent"))
    }

    @Test
    fun `should include coordinator instructions when enabled`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            coordinator = true,
            context = context
        )

        assertTrue(instructions.contains("# Coordinator"))
    }

    @Test
    fun `should not include coordinator instructions when disabled`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            coordinator = false,
            context = context
        )

        assertFalse(instructions.contains("# Coordinator"))
    }

    @Test
    fun `should include daily log instructions`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("# Daily Log Protocol"))
    }

    @Test
    fun `should include chat history instructions when userId and channelId present`() {
        val query = Message(userId = "user1", channelId = "channel:telegram")

        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("# Conversation History"))
        assertTrue(instructions.contains("user1"))
        assertTrue(instructions.contains("telegram")) // channel prefix removed
    }

    @Test
    fun `should not include chat history instructions when userId missing`() {
        val query = Message(channelId = "channel1")

        val instructions = builder.buildSystemInstructions(query, false, context)

        assertFalse(instructions.contains("# Conversation History"))
    }

    @Test
    fun `should not include chat history instructions when channelId missing`() {
        val query = Message(userId = "user1")

        val instructions = builder.buildSystemInstructions(query, false, context)

        assertFalse(instructions.contains("# Conversation History"))
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

        val skill2 = mock<Skill>()
        doReturn(Health(up = true, id = "skill2")).whenever(skill2).health()
        doReturn(
            SkillMetadata(
                name = "news",
                description = "Get news articles",
                home = File("/tmp/skills/news")
            )
        ).whenever(skill2).metadata

        doReturn(listOf(skill1, skill2)).whenever(skillRegistry).all()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("# Available skills"))
        assertTrue(instructions.contains("## Skill: weather"))
        assertTrue(instructions.contains("Get weather info"))
        assertTrue(instructions.contains("## Skill: news"))
        assertTrue(instructions.contains("Get news articles"))
    }

    @Test
    fun `should exclude skills that are down`() {
        val skill1 = mock<Skill>()
        doReturn(Health(up = true, id = "skill1")).whenever(skill1).health()
        doReturn(
            SkillMetadata(
                name = "weather",
                description = "Get weather info",
                home = File("/tmp/skills/weather")
            )
        ).whenever(skill1).metadata

        val skill2 = mock<Skill>()
        doReturn(Health(up = false, id = "skill2")).whenever(skill2).health()
        doReturn(
            SkillMetadata(
                name = "broken",
                description = "Broken skill",
                home = File("/tmp/skills/broken")
            )
        ).whenever(skill2).metadata

        doReturn(listOf(skill1, skill2)).whenever(skillRegistry).all()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("weather"))
        assertFalse(instructions.contains("broken"))
    }

    @Test
    fun `should include security instructions`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("# Security"))
        assertTrue(instructions.contains(home.absolutePath))
    }

    @Test
    fun `should replace assistant name placeholder in identity`() {
        // Create a temporary file with placeholder
        val tempHome = File.createTempFile("test", "home")
        tempHome.delete()
        tempHome.mkdir()
        val assistantFile = File(tempHome, "ASSISTANT.md")
        assistantFile.writeText("You are {{ASSISTANT_NAME}}")

        doReturn(tempHome).whenever(context).home

        val query = Message(userId = "user1", channelId = "channel1")
        val customBuilder = PromptBuilder(assistantName = "MyCustomBot")

        val instructions = customBuilder.buildSystemInstructions(query, false, context)

        assertTrue(instructions.contains("MyCustomBot"))
        assertFalse(instructions.contains("{{ASSISTANT_NAME}}"))

        // Cleanup
        assistantFile.delete()
        tempHome.delete()
    }

    @Test
    fun `should handle missing ASSISTANT md file gracefully`() {
        val emptyHome = File("/tmp/nonexistent")
        doReturn(emptyHome).whenever(context).home

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, false, context)

        // Should still have other instructions even without ASSISTANT.md
        assertTrue(instructions.contains("# Security"))
    }
}
