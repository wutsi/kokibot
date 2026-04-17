package com.wutsi.kokibot.skill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
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
    private val meta1 = SkillMetadata(
        name = "land-title-verifier",
        description = "Verify the land title and ownership information for a given property, providing accurate and up-to-date details to assist users in making informed decisions about real estate transactions.",
    )
    private val meta2 = SkillMetadata(
        name = "crm",
        description = "A CRM (Customer Relationship Management) skill that helps manage customer interactions, track sales leads, and organize customer data to improve business relationships and drive sales growth.",
    )

    @Test
    fun init() {
        // GIVEN
        doReturn(Pair(meta1, ""))
            .doReturn(Pair(meta2, ""))
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
    fun `init - failure`() {
        // GIVEN
        doReturn(Pair(meta1, ""))
            .doThrow(IllegalArgumentException::class.java)
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

        assertEquals(1, skills.size)

        assertEquals(meta1, skills[0].metadata)
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

    @Test
    fun get() {
        // GIVEN
        doReturn(Pair(meta1, ""))
            .doReturn(Pair(meta2, ""))
            .whenever(parser).parse(any())

        val home = getResourceFile("/home/007")
        val context = Context(
            home = home,
            llm = mock(),
        )

        // WHEN
        registry.init(context)

        // THEN
        val skill = registry.get(meta1.name)
        assertEquals(meta1, skill.metadata)
    }

    @Test
    fun destroy() {
        // GIVEN
        val home = getResourceFile("/home/007")
        val context = Context(
            home = home,
            llm = mock(),
        )

        val skill1 = mock<Skill>()
        doReturn(meta1).whenever(skill1).metadata
        registry.register(skill1)

        val skill2 = mock<Skill>()
        doReturn(meta2).whenever(skill2).metadata
        registry.register(skill2)

        // WHEN
        registry.init(context)
        registry.destroy()

        // THEN
        verify(skill1).destroy()
        verify(skill2).destroy()
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
