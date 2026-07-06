package com.wutsi.kokibot.assistant

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.mcp.McpRegistry
import com.wutsi.kokibot.mcp.McpServer
import com.wutsi.kokibot.mcp.McpServerConfig
import com.wutsi.kokibot.service.kb.KBEntry
import com.wutsi.kokibot.service.kb.KBEntryStatus
import com.wutsi.kokibot.service.kb.KnowledgeBase
import com.wutsi.kokibot.service.memory.ConversationMessage
import com.wutsi.kokibot.service.memory.ConversationRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class PromptBuilderTest {
    private fun getResourceFile(path: String): File {
        return File(javaClass.getResource(path)!!.file)
    }

    private val home = getResourceFile("/home/007")
    private val memory = mock<Memory>()
    private val dailyLog = mock<DailyLog>()
    private val skillRegistry = mock<SkillRegistry>()
    private val mcpRegistry = mock<McpRegistry>()
    private val conversationRepository = mock<ConversationRepository>()
    private val assistant = Assistant("test-assistant")
    private val kb = mock<KnowledgeBase>()
    private val activatedMcps: MutableList<McpServer> = CopyOnWriteArrayList()
    private val context = createContext()

    // Fixed clock for deterministic date/time assertions: 2026-06-09T14:30:15Z
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-06-09T14:30:15Z"),
        ZoneId.of("UTC")
    )
    private lateinit var builder: PromptBuilder

    @BeforeEach
    fun setup() {
        assistant.init(
            mapOf(
                "full-name" to "Ray Sponsible",
                "email" to "Ray.Sponsible@gmail.com",
                "language" to "fr"
            ), context
        )

        doReturn(false).whenever(kb).isEnabled()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(emptyList<KBEntry>()).whenever(kb).entries()

        doReturn(true).whenever(memory).isEnabled()
        doReturn(null).whenever(memory).get()

        doReturn(null).whenever(dailyLog).get()

        doReturn(emptyList<Skill>()).whenever(skillRegistry).all()

        doReturn(emptyList<ConversationMessage>()).whenever(conversationRepository).getMessages(
            com.nhaarman.mockitokotlin2.any(),
            com.nhaarman.mockitokotlin2.any(),
            com.nhaarman.mockitokotlin2.any()
        )

        doReturn(emptyList<McpServer>()).whenever(mcpRegistry).all()

        builder = PromptBuilder(clock = fixedClock)
    }

    @Test
    fun `should build prompt with query text`() {
        val query = Message(text = "What is the weather?")
        val iterationMemory = emptyList<String>()

        val prompt = builder.buildPrompt(query, iterationMemory, context)

        assertTrue(prompt.contains("What is the weather?"))
    }

    @Test
    fun `should include tracking information`() {
        val query = Message(text = "Test query", conversationId = UUID.randomUUID().toString())

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("# Tracking Information"))
        assertTrue(prompt.contains("Current Date and Time"))
        // Fixed clock is 2026-06-09T14:30:15Z (UTC)
        assertTrue(prompt.contains("Tuesday, June 9, 2026"))
        assertTrue(prompt.contains("14:30:15"))
        assertTrue(prompt.contains("2026-06-09T14:30:15Z"))

        assertTrue(prompt.contains(query.id))
        assertTrue(prompt.contains(query.conversationId!!))
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
    fun `should include short-term memory in prompt`() {
        doReturn("Today's task: implement feature X").whenever(dailyLog).get()
        val query = Message(text = "Test query")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("# Short-Term Memory"))
        assertTrue(prompt.contains("Today's task: implement feature X"))
    }

    @Test
    fun `should exclude long-term memory when memory disabled`() {
        doReturn(false).whenever(memory).isEnabled()
        doReturn("User prefers concise answers").whenever(memory).get()
        val query = Message(text = "Test query")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertFalse(prompt.contains("# Long-Term Memory"))
        assertFalse(prompt.contains("User prefers concise answers"))
    }

    @Test
    fun `should exclude short-term memory when memory disabled`() {
        doReturn(false).whenever(memory).isEnabled()
        doReturn("Today's task: implement feature X").whenever(dailyLog).get()
        val query = Message(text = "Test query")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertFalse(prompt.contains("# Short-Term Memory"))
        assertFalse(prompt.contains("Today's task: implement feature X"))
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

        assertTrue(prompt.contains("Test query"))
        assertTrue(prompt.contains("Long-term fact"))
        assertTrue(prompt.contains("Short-term info"))
        assertTrue(prompt.contains("Iteration step"))
    }

    @Test
    fun `should include coordinator instructions`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(
            query = query,
            context = context
        )

        assertTrue(instructions.contains("# Coordinator"))
    }

    @Test
    fun `should include daily log instructions`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("# Daily Log Protocol"))
    }

    @Test
    fun `should include conversation history in prompt when conversationId is set`() {
        val messages = listOf(
            ConversationMessage(role = "user", text = "What is the weather?"),
            ConversationMessage(role = "assistant", text = "It is sunny today."),
        )
        doReturn(messages).whenever(conversationRepository).getMessages("conv-1", "user1", "channel:telegram")

        val query = Message(
            text = "Follow-up question",
            userId = "user1",
            channelId = "channel:telegram",
            conversationId = "conv-1"
        )
        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("# Conversation History"))
        assertTrue(prompt.contains("What is the weather?"))
        assertTrue(prompt.contains("It is sunny today."))
    }

    @Test
    fun `should not include conversation history in prompt when conversationId is null`() {
        val query = Message(text = "First message", userId = "user1", channelId = "channel:telegram")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertFalse(prompt.contains("# Conversation History"))
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
        val instructions = builder.buildSystemInstructions(query, context)

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
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("weather"))
        assertFalse(instructions.contains("broken"))
    }

    @Test
    fun `should include security instructions`() {
        val query = Message(userId = "user1", channelId = "channel1")

        val instructions = builder.buildSystemInstructions(query, context)

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

        val context = createContext(tempHome)
        val query = Message(userId = "user1", channelId = "channel1")
        val customBuilder = PromptBuilder()

        val instructions = customBuilder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("test-assistant"))
        assertFalse(instructions.contains("{{ASSISTANT_NAME}}"))

        // Cleanup
        assistantFile.delete()
        tempHome.delete()
    }

    @Test
    fun `should handle missing ASSISTANT md file gracefully`() {
        val emptyHome = File("/tmp/nonexistent")
        val context = createContext(emptyHome)

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        // Should still have other instructions even without ASSISTANT.md
        assertTrue(instructions.contains("# Security"))
    }

    @Test
    fun `should append telegram channel instructions to prompt text`() {
        val query = Message(text = "Hello", channelId = "telegram")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.contains("# Telegram  Formatting Instructions"))
    }

    @Test
    fun `should append websocket channel instructions to prompt text`() {
        val query = Message(text = "Hello", channelId = "websocket")

        val prompt = builder.buildPrompt(query, emptyList(), context)

        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.contains("# Web Formatting Instructions"))
    }

    @Test
    fun `should include available MCP servers in system instructions`() {
        val server1 = mock<McpServer>()
        val server2 = mock<McpServer>()
        doReturn(
            McpServerConfig(
                name = "weather-mcp",
                description = "Weather data and forecasts",
                url = "https://w.example.com"
            )
        ).whenever(server1).config
        doReturn(
            McpServerConfig(
                name = "news-mcp",
                description = "Latest news",
                url = "https://n.example.com"
            )
        ).whenever(server2).config
        doReturn(listOf(server1, server2)).whenever(mcpRegistry).all()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query = query, context = context)

        assertTrue(instructions.contains("# Available MCP Servers"))
        assertTrue(instructions.contains("weather-mcp"))
        assertTrue(instructions.contains("Weather data and forecasts"))
        assertTrue(instructions.contains("news-mcp"))
        assertTrue(instructions.contains("Latest news"))
        assertTrue(instructions.contains("mcp_activate"))
    }

    @Test
    fun `should omit MCP section when no servers configured`() {
        doReturn(emptyList<McpServer>()).whenever(mcpRegistry).all()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query = query, context = context)

        assertFalse(instructions.contains("# Available MCP Servers"))
    }

    @Test
    fun `should include activated MCP server in system instructions`() {
        val server = mock<McpServer>()
        doReturn(
            McpServerConfig(
                name = "weather-mcp",
                description = "Weather data",
                url = "https://w.example.com"
            )
        ).whenever(server).config
        doReturn(listOf(server)).whenever(mcpRegistry).all()
        activatedMcps.add(server)

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query = query, context = context)

        assertTrue(instructions.contains("# Available MCP Servers"))
        assertTrue(instructions.contains("weather-mcp"))
        assertTrue(instructions.contains("Weather data"))
    }

    // ── Knowledge Base Instruction Tests ──────────────────────────────────

    private fun sampleEntry(
        name: String = "Guide",
        scope: String = "general",
        keywords: List<String> = listOf("kotlin", "spring"),
        summary: String? = "kb/summary/guide.summary.md",
        raw: String? = "kb/raw/guide.md",
        source: String = "docs/guide.pdf",
        status: KBEntryStatus = KBEntryStatus.READY,
    ) = KBEntry(
        name = name,
        scope = scope,
        keywords = keywords,
        summary = summary,
        raw = raw,
        source = source,
        status = status
    )

    @Test
    fun `should not include knowledge base instructions when KB is disabled`() {
        // kb.isEnabled() returns false by default (set in @BeforeEach)
        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)
        assertFalse(instructions.contains("# Knowledge Base Instructions"))
    }

    @Test
    fun `should not include knowledge base instructions when KB has no entries`() {
        doReturn(true).whenever(kb).isEnabled()
        // entries() already returns emptyList in @BeforeEach
        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)
        assertFalse(instructions.contains("# Knowledge Base Instructions"))
    }

    @Test
    fun `should include knowledge base section when KB is enabled with entries`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry())).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("# Knowledge Base Instructions"))
        assertTrue(instructions.contains("## Knowledge Base Content"))
        assertTrue(instructions.contains("Guide"))
        assertTrue(instructions.contains("general"))
        assertTrue(instructions.contains("kotlin, spring"))
    }

    @Test
    fun `should include exclusive usage instructions when KB is exclusive`() {
        val entry = sampleEntry()
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(entry)).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("Use only your knowledge base"))
        assertTrue(instructions.contains("Do not use your own training knowledge"))
        assertTrue(instructions.contains("Do not make up information"))
        assertTrue(instructions.contains("${home.absolutePath}/${entry.raw}"))
        assertTrue(instructions.contains("${home.absolutePath}/${entry.summary}"))
    }

    @Test
    fun `should include non-exclusive usage instructions when KB is not exclusive`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(false).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry())).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("Prefer your knowledge base content over your own training knowledge"))
    }

    @Test
    fun `should not include web search restriction when KB web search is enabled`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(true).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry())).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertFalse(instructions.contains("Do not use web search"))
    }

    @Test
    fun `should ignore when entry status is PROCESSING`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry(status = KBEntryStatus.PROCESSING))).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertFalse(instructions.contains("# Knowledge Base Instructions"))
    }

    @Test
    fun `should ignore when entry status is ERROR`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry(status = KBEntryStatus.ERROR))).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertFalse(instructions.contains("# Knowledge Base Instructions"))
    }

    @Test
    fun `should resolve raw path against home when entry raw is set`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(listOf(sampleEntry(raw = "raw/guide.txt"))).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("${home.absolutePath}/raw/guide.txt"))
    }

    @Test
    fun `should include all entries when KB has multiple entries`() {
        doReturn(true).whenever(kb).isEnabled()
        doReturn(true).whenever(kb).isExclusive()
        doReturn(false).whenever(kb).isWebSearch()
        doReturn(
            listOf(
                sampleEntry(name = "Entry A", source = "docs/a.pdf"),
                sampleEntry(name = "Entry B", source = "docs/b.pdf"),
            )
        ).whenever(kb).entries()

        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("Entry A"))
        assertTrue(instructions.contains("Entry B"))
    }

    @Test
    fun `should include identity section`() {
        val query = Message(userId = "user1", channelId = "channel1")
        val instructions = builder.buildSystemInstructions(query, context)

        assertTrue(instructions.contains("# Assistant Identity"))
        assertTrue(instructions.contains("test-assistant"))
        assertTrue(instructions.contains("Ray Sponsible"))
        assertTrue(instructions.contains("Ray.Sponsible@gmail.com"))
    }

    private fun createContext(home: File = this.home): Context {
        return Context(
            assistant = assistant,
            home = home,
            llm = mock(),
            knowledgeBase = kb,
            memory = memory,
            dailyLog = dailyLog,
            mcpRegistry = mcpRegistry,
            skillRegistry = skillRegistry,
            conversationRepository = conversationRepository,
            activatedMcps = activatedMcps,
        )
    }
}
