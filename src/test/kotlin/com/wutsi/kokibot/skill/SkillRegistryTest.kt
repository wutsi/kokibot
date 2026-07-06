package com.wutsi.kokibot.skill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.marketplace.Marketplace
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File

class SkillRegistryTest {
    private val parser = mock<SkillParser>()
    private val registry = SkillRegistry(parser)
    private val meta1 = SkillMetadata(
        name = "land-title-verifier",
        description = "Verify the land title and ownership information for a given property, providing accurate and up-to-date details to assist users in making informed decisions about real estate transactions.",
        home = File("target"),
    )
    private val meta2 = SkillMetadata(
        name = "crm",
        description = "A CRM (Customer Relationship Management) skill that helps manage customer interactions, track sales leads, and organize customer data to improve business relationships and drive sales growth.",
        home = File("target"),
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

        assertEquals(meta2, skills[0].metadata)

        assertEquals(meta1, skills[1].metadata)
    }

    @Test
    fun `init - marketplace enabled`() {
        // GIVEN
        doReturn(Pair(meta1, ""))
            .doReturn(Pair(meta2, ""))
            .whenever(parser).parse(any())

        val meta11 = SkillMetadata(
            name = "obsidian:obsidian-cli",
            home = File("target/obsidian-cli"),
        )
        val skill11 = mock<Skill>()
        doReturn(meta11).whenever(skill11).metadata

        val marketplace = mock(Marketplace::class.java)
        doReturn(true).whenever(marketplace).isEnabled()
        doReturn("obsidian").whenever(marketplace).id()
        doReturn(listOf(skill11)).whenever(marketplace).getSkills()

        val marketplaceRegistry = mock<MarketplaceRegistry>()
        doReturn(listOf(marketplace)).whenever(marketplaceRegistry).all()

        val context = Context(
            home = getResourceFile("/home/007"),
            llm = mock(),
            marketplaceRegistry = marketplaceRegistry,
        )

        // WHEN
        registry.init(context)

        // THEN
        val skills = registry.all()

        assertEquals(3, skills.size)

        assertEquals(meta2, skills[0].metadata)
        assertEquals(meta1, skills[1].metadata)
        assertEquals(meta11, skills[2].metadata)
    }

    @Test
    fun `init - marketplace disabled`() {
        // GIVEN
        doReturn(Pair(meta1, ""))
            .doReturn(Pair(meta2, ""))
            .whenever(parser).parse(any())

        val meta11 = SkillMetadata(
            name = "obsidian:obsidian-cli",
            home = File("target/obsidian-cli"),
        )
        val skill11 = mock<Skill>()
        doReturn(meta11).whenever(skill11).metadata

        val marketplace = mock(Marketplace::class.java)
        doReturn(false).whenever(marketplace).isEnabled()
        doReturn("obsidian").whenever(marketplace).id()
        doReturn(listOf(skill11)).whenever(marketplace).getSkills()

        val marketplaceRegistry = mock<MarketplaceRegistry>()
        doReturn(listOf(marketplace)).whenever(marketplaceRegistry).all()

        val context = Context(
            home = getResourceFile("/home/007"),
            llm = mock(),
            marketplaceRegistry = marketplaceRegistry,
        )

        // WHEN
        registry.init(context)

        // THEN
        val skills = registry.all()

        assertEquals(2, skills.size)
        assertEquals(meta2, skills[0].metadata)
        assertEquals(meta1, skills[1].metadata)
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
    fun `init - bad structure`() {
        // GIVEN
        val home = getResourceFile("/home/bad-structure")
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
    fun `get invalid skill`() {
        doReturn(Pair(meta1, ""))
            .doReturn(Pair(meta2, ""))
            .whenever(parser).parse(any())

        val home = getResourceFile("/home/007")
        val context = Context(
            home = home,
            llm = mock(),
        )
        registry.init(context)

        assertThrows<SkillNotFoundException> { registry.get("xxx") }
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
        assertEquals(0, registry.all().size)
    }

    @Test
    fun register() {
        // GIVEN
        val skill1 = mock<Skill>()
        doReturn(meta1).whenever(skill1).metadata

        // WHEN
        registry.register(skill1)

        // THEN
        assertEquals(listOf(skill1), registry.all())
    }

    @Test
    fun unregister() {
        // GIVEN
        val skill1 = mock<Skill>()
        doReturn(meta1).whenever(skill1).metadata
        registry.register(skill1)

        // WHEN
        registry.unregister(skill1)

        // THEN
        assertEquals(0, registry.all().size)
    }

    @Test
    fun apply() {
        // GIVEN
        val skill1 = mock<Skill>()
        doReturn(SkillMetadata(name = "my-skill", home = File("target"))).whenever(skill1).metadata
        registry.register(skill1)

        // WHEN
        registry.apply("my-skill.instructions", "new content")

        // THEN
        verify(skill1).apply("instructions", "new content")
    }

    @Test
    fun `apply - missing dot throws`() {
        assertThrows<IllegalArgumentException> {
            registry.apply("no-dot", "value")
        }
    }

    @Test
    fun `apply - skill not found throws`() {
        assertThrows<SkillNotFoundException> {
            registry.apply("unknown-skill.instructions", "value")
        }
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}
