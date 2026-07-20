package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.llm.LLMToolCallDelta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ToolCallAccumulatorTest {
    private val jsonMapper = JsonMapper()
    private val accumulator = ToolCallAccumulator(jsonMapper)

    @Test
    fun `build returns null when no deltas merged`() {
        assertNull(accumulator.build(jsonMapper))
    }

    @Test
    fun `build returns null when name is missing`() {
        accumulator.merge(LLMToolCallDelta(id = "call_1", argumentsFragment = "{\"a\":1}"))

        assertNull(accumulator.build(jsonMapper))
    }

    @Test
    fun `build merges fragments and parses arguments`() {
        accumulator.merge(LLMToolCallDelta(index = 0, id = "call_1", name = "search"))
        accumulator.merge(LLMToolCallDelta(index = 0, argumentsFragment = "{\"q\":"))
        accumulator.merge(LLMToolCallDelta(index = 0, argumentsFragment = "\"hello\"}"))

        val call = accumulator.build(jsonMapper)

        assertEquals("search", call?.name)
        assertEquals(mapOf("q" to "hello"), call?.arguments)
    }

    @Test
    fun `merge keeps previous id and name when delta has nulls or empties`() {
        accumulator.merge(LLMToolCallDelta(id = "call_1", name = "search"))
        accumulator.merge(LLMToolCallDelta(id = null, name = null, argumentsFragment = "{}"))
        accumulator.merge(LLMToolCallDelta(id = "", name = "", argumentsFragment = null))

        val call = accumulator.build(jsonMapper)

        assertEquals("search", call?.name)
        assertEquals(emptyMap<String, Any>(), call?.arguments)
    }

    @Test
    fun `build returns empty arguments when no fragments accumulated`() {
        accumulator.merge(LLMToolCallDelta(name = "ping"))

        val call = accumulator.build(jsonMapper)

        assertEquals("ping", call?.name)
        assertTrue(call?.arguments?.isEmpty() == true)
    }

    @Test
    fun `setComplete short-circuits build and ignores accumulated state`() {
        accumulator.merge(LLMToolCallDelta(name = "search", argumentsFragment = "{not-json"))
        val complete = LLMToolCall(name = "tool", arguments = mapOf("x" to 1))
        accumulator.setComplete(complete)

        assertSame(complete, accumulator.build(jsonMapper))
    }
}
