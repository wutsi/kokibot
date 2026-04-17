package com.wutsi.kokibot.skill

import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillTest {
    val context = Context(
        home = getResourceFile("/home/007"),
        llm = mock()
    )
    private val skill = Skill(
        metadata = SkillMetadata(
            name = "land-title-verifier",
            categories = listOf("real estate"),
            keywords = listOf("land title", "land ownership", "property title"),
            requiredBinaries = listOf("java"),
            requiredEnv = listOf("PATH"),
        ),
        body = "",
    )

    @Test
    fun id() {
        assertEquals("skill:land-title-verifier", skill.id())
    }

    @Test
    fun health() {
        skill.init(emptyMap<String, Any>(), context)
        val health = skill.health()

        assertEquals(skill.id(), health.id)
        assertEquals(0, health.children.size)
        assertEquals(true, health.up)
        assertNull(health.details)
    }

    @Test
    fun `health - missing env`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(requiredEnv = listOf("__MISSING_ENV__")),
            body = "",
        )
        xskill.init(emptyMap<String, Any>(), context)
        val health = xskill.health()

        assertEquals(false, health.up)
        assertNotNull(health.details)
    }

    @Test
    fun activate() {
        skill.init(emptyMap<String, Any>(), context)

        val result = skill.activate()

        assertTrue(result)
    }

    @Test
    fun `activate - no dependencies`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(requiredEnv = emptyList()),
            body = "",
        )
        xskill.init(emptyMap<String, Any>(), context)
        val result = xskill.activate()

        assertTrue(result)
    }

    @Test
    fun `activate - missing env`() {
        val xskill = Skill(
            metadata = skill.metadata.copy(requiredEnv = listOf("__MISSING_ENV__")),
            body = "",
        )
        xskill.init(emptyMap<String, Any>(), context)
        val result = xskill.activate()

        assertFalse(result)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
