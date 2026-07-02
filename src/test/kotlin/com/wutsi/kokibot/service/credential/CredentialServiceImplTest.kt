package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialServiceImplTest {
    @TempDir
    lateinit var tempDir: File

    private fun globalFile() = File(tempDir, "global.json")
    private fun localFile() = File(tempDir, "local.json")

    @Test
    fun `getOrNull returns null when both files missing`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertNull(svc.getOrNull("llm.deepseek"))
    }

    @Test
    fun `get throws ConfigurationException when key missing`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertThrows<ConfigurationException> { svc.get("llm.deepseek") }
    }

    @Test
    fun `get returns global value`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("global-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `get returns local value`() {
        localFile().writeText("""{"llm.deepseek":"local-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("local-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `local overrides global`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        localFile().writeText("""{"llm.deepseek":"local-key"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("local-key", svc.get("llm.deepseek"))
    }

    @Test
    fun `get falls back to global when key only in global`() {
        globalFile().writeText("""{"llm.deepseek":"global-key"}""")
        localFile().writeText("""{"channel.telegram":"local-token"}""")
        val svc = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("global-key", svc.get("llm.deepseek"))
        assertEquals("local-token", svc.get("channel.telegram"))
    }

    @Test
    fun `malformed JSON throws ConfigurationException`() {
        globalFile().writeText("not-json")
        assertThrows<ConfigurationException> { CredentialServiceImpl(globalFile(), localFile()) }
    }

    @Test
    fun `set LOCAL writes to local map and persists file`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("channel.telegram", "new-token", CredentialScope.LOCAL)
        assertEquals("new-token", svc.getOrNull("channel.telegram"))
        assertTrue(localFile().exists())
    }

    @Test
    fun `set GLOBAL writes to global map and persists file`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.deepseek", "new-key", CredentialScope.GLOBAL)
        assertEquals("new-key", svc.getOrNull("llm.deepseek"))
        assertTrue(globalFile().exists())
    }

    @Test
    fun `set default scope is LOCAL`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.kimi", "kimi-key")
        assertEquals("kimi-key", svc.getOrNull("llm.kimi"))
        assertTrue(localFile().exists())
    }

    @Test
    fun `set persisted value survives reload`() {
        val svc = CredentialServiceImpl(globalFile(), localFile())
        svc.set("llm.deepseek", "persisted-key", CredentialScope.LOCAL)

        val svc2 = CredentialServiceImpl(globalFile(), localFile())
        assertEquals("persisted-key", svc2.get("llm.deepseek"))
    }
}
