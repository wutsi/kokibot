package com.wutsi.kokibot.llm

import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.llm.deepseek.Deepseek
import com.wutsi.kokibot.llm.kimi.Kimi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LLMFactoryTest {
    val factory = LLMFactory()

    @Test
    fun deepseek() {
        val llm = factory.create("deepseek")
        assertTrue(llm is Deepseek)
    }

    @Test
    fun kimi() {
        val llm = factory.create("kimi")
        assertTrue(llm is Kimi)
    }

    @Test
    fun unsupported() {
        assertThrows<ConfigurationException> {
            factory.create("unknown")
        }
    }
}
