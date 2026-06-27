package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.service.memory.ChatHistory
import com.wutsi.kokibot.service.memory.DailyLog
import com.wutsi.kokibot.service.memory.Memory
import com.wutsi.kokibot.service.memory.SessionLog
import com.wutsi.kokibot.skill.SkillRegistry
import com.wutsi.kokibot.tools.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class AssistantApplyTest {
    private lateinit var assistant: Assistant

    @BeforeEach
    fun setup() {
        val context = Context(
            home = File("target/test-data/assistant-apply"),
            llm = mock<LLM>(),
            toolRegistry = mock<ToolRegistry>(),
            memory = mock<Memory>(),
            skillRegistry = mock<SkillRegistry>(),
            dailyLog = mock<DailyLog>(),
            sessionLog = mock<SessionLog>(),
            chatHistory = mock<ChatHistory>(),
            assistantRegistry = AssistantRegistry(),
        )
        assistant = Assistant("test")
        assistant.init(
            mapOf(
                "max-iterations" to 5,
                "description" to "original",
                "coordinator" to false,
                "thread-pool-size" to 4,
            ),
            context,
        )
    }

    @Test
    fun `apply max-iterations updates field and rebuilds loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("max-iterations", 20)
        assertEquals(20, assistant.getMaxIterations())
        assertNotSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply max-duration updates field`() {
        assistant.apply("max-duration", "10m")
        assertEquals(10L, assistant.getMaxDurationMinutes())
    }

    @Test
    fun `apply description updates field without rebuilding loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("description", "new description")
        assertEquals("new description", assistant.getDescription())
        assertSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply coordinator updates field and rebuilds loop`() {
        val originalLoop = assistant.reasoningLoop
        assistant.apply("coordinator", true)
        assertEquals(true, assistant.isCoordinator())
        assertNotSame(originalLoop, assistant.reasoningLoop)
    }

    @Test
    fun `apply instructions persists to ASSISTANT md`() {
        val home = File("target/test-data/assistant-apply")
        val instructionsFile = File(home, "ASSISTANT.md")
        instructionsFile.parentFile.mkdirs()

        assistant.apply("instructions", "You are a helpful assistant")

        assertEquals("You are a helpful assistant", instructionsFile.readText())
    }

    @Test
    fun `apply unknown key throws ConfigurationException`() {
        assertThrows<ConfigurationException> {
            assistant.apply("unknown-key", "value")
        }
    }
}
