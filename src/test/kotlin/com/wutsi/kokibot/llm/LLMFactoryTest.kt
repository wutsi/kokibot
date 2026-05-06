package com.wutsi.kokibot.llm

import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.gemini.Gemini
import com.wutsi.kokibot.llm.none.NullLLM
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LLMFactoryTest {
    val factory = LLMFactory()

    @Test
    fun deepseek() {
        val llm = factory.create("deepseek")
        assertTrue(llm is Deepseek)
    }

    @Test
    fun gemini() {
        val llm = factory.create("gemini")
        assertTrue(llm is Gemini)
    }

    @Test
    fun unsupported() {
        val llm = factory.create("unknown")
        assertTrue(llm is NullLLM)
    }
}
