package com.wutsi.kokibot.skill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File

class SkillRegistryTest {
    private val parser = mock<SkillParser>()
    private val registry = SkillRegistry(parser)
    private val meta1 = SkillMetadata(name = "land-title-verifier")
    private val meta2 = SkillMetadata(name = "crm")

    @Test
    fun init() {
        // GIVEN
        doReturn(meta1)
            .doReturn(meta2)
            .whenever(parser).parse(any())

        val home = getResourceFile("/home/007")
        val context = Context(
            home = home,
            llm = mock(),
        )

        // WHEN
        registry.init(context)

        // THEN
        val skills = registry.all()

        assertEquals(2, skills.size)
        assertEquals(meta1, skills[0].metadata)
        assertEquals(meta2, skills[1].metadata)
    }

    @Test
    fun `init - no skills`() {
        // GIVEN
        val home = getResourceFile("/home/no-skills")
        val context = Context(
            home = home,
            llm = mock(),
        )

        // WHEN
        registry.init(context)

        // THEN
        val skills = registry.all()

        assertEquals(0, skills.size)
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
