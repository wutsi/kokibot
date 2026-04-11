package com.wutsi.kokibot.skill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.BootstrapTest
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
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
        tools = listOf(
            ToolMetadata(
                name = "land-title-verifier",
                description = "Verify the land title and ownership information for a given property, providing accurate and up-to-date details to assist users in making informed decisions about real estate transactions.",
                parameters = listOf(
                    ToolParameter(
                        name = "address",
                        description = "The address of the property to verify.",
                        type = ToolParameterType.STRING,
                        required = true,
                    )
                )
            )
        )
    )

    private val meta2 = SkillMetadata(
        name = "crm",
        description = "A CRM (Customer Relationship Management) skill that helps manage customer interactions, track sales leads, and organize customer data to improve business relationships and drive sales growth.",
        tools = listOf(
            ToolMetadata(
                name = "crm_ls",
                description = "List all the contact",
                parameters = emptyList()
            ),
            ToolMetadata(
                name = "crm_contact",
                description = "Search a given contact in the CRM system and return the details of the contact.",
                parameters = listOf(
                    ToolParameter(
                        name = "customer_id",
                        description = "The unique identifier of the customer.",
                        type = ToolParameterType.STRING,
                        required = true,
                    )
                )
            )
        )
    )

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
        assertEquals(1, skills[0].getTools().size)
        assertEquals(meta1.tools[0], skills[0].getTools()[0].metadata())

        assertEquals(meta2, skills[1].metadata)
        assertEquals(2, skills[1].getTools().size)
        assertEquals(meta2.tools[0], skills[1].getTools()[0].metadata())
        assertEquals(meta2.tools[1], skills[1].getTools()[1].metadata())
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
