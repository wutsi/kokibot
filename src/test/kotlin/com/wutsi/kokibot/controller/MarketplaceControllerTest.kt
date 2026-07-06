package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.marketplace.Marketplace
import com.wutsi.kokibot.marketplace.MarketplaceNotFoundException
import com.wutsi.kokibot.marketplace.MarketplaceRegistry
import com.wutsi.kokibot.skill.Skill
import com.wutsi.kokibot.skill.SkillMetadata
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MarketplaceControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Test
    fun marketplaces() {
        val skills = listOf(
            createSkill("crm"),
            createSkill("weather"),
        )
        val marketplaces = listOf(
            createMarketplace("acme", "https://github.com/acme/skills", skills),
        )
        doReturn(listOf(createBootstrap("007", marketplaces = marketplaces))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/marketplaces", List::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(1, body.size)

        val mp = body[0] as Map<*, *>
        assertEquals("acme", mp["name"])
        assertEquals("https://github.com/acme/skills", mp["repoUrl"])

        @Suppress("UNCHECKED_CAST")
        val skillNames = mp["skills"] as List<*>
        assertEquals(2, skillNames.size)
        assertEquals("crm", skillNames[0])
        assertEquals("weather", skillNames[1])
    }

    @Test
    fun `marketplaces returns multiple marketplaces`() {
        val marketplaces = listOf(
            createMarketplace("acme", "https://github.com/acme/skills", listOf(createSkill("crm"))),
            createMarketplace("beta", "https://github.com/beta/skills", emptyList()),
        )
        doReturn(listOf(createBootstrap("007", marketplaces = marketplaces))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/marketplaces", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body!!.size)
    }

    @Test
    fun `marketplaces returns empty list when no marketplaces`() {
        doReturn(listOf(createBootstrap("007", marketplaces = emptyList()))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/marketplaces", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body!!.size)
    }

    @Test
    fun `marketplaces returns 404 when agent not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/marketplaces", List::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun get() {
        val marketplace = createMarketplace("acme", "https://github.com/acme/skills", emptyList())
        doReturn("Acme Skills").whenever(marketplace).getDescription()
        doReturn("https://acme.com/icon.png").whenever(marketplace).getIcon()
        val marketplaceRegistry = mock<MarketplaceRegistry>()
        doReturn(marketplace).whenever(marketplaceRegistry).get("marketplace:acme")
        doReturn(listOf(createBootstrapWithRegistry("007", marketplaceRegistry))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/marketplaces/acme", Map::class.java)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals("acme", body["name"])
        assertEquals("https://github.com/acme/skills", body["repoUrl"])
        assertEquals("Acme Skills", body["description"])
        assertEquals("https://acme.com/icon.png", body["icon"])
    }

    @Test
    fun `get - marketplace not found`() {
        val marketplaceRegistry = mock<MarketplaceRegistry>()
        doThrow(MarketplaceNotFoundException("Marketplace not found: xxx")).whenever(marketplaceRegistry).get("marketplace:xxx")
        doReturn(listOf(createBootstrapWithRegistry("007", marketplaceRegistry))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/007/marketplaces/xxx", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get - assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/assistants/xxx/marketplaces/acme", Map::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `set marketplace setting`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/marketplaces/kokibot/settings",
            mapOf("key" to "enabled", "value" to false),
            Map::class.java,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        verify(bootstrap).set("marketplace.kokibot.enabled", false)
    }

    @Test
    fun `set memory setting - bad request when missing key`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.postForEntity(
            "/assistants/007/marketplaces/kokibot/settings",
            mapOf("value" to false),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `set marketplace setting - bad request on unknown key`() {
        val bootstrap = createBootstrap("007")
        doReturn(listOf(bootstrap)).whenever(multi).bootstraps
        doThrow(ConfigurationException("Unknown marketplace setting: invalid")).whenever(bootstrap).set(any(), any())

        val response = rest.postForEntity(
            "/assistants/007/marketplaces/kokibot/settings",
            mapOf("key" to "invalid", "value" to "x"),
            Map::class.java,
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("Unknown marketplace setting: invalid", (response.body as Map<*, *>)["error"])
    }

    private fun createSkill(name: String): Skill {
        val metadata = SkillMetadata(name = name, home = File("."))
        return Skill(metadata)
    }

    private fun createMarketplace(name: String, repoUrl: String, skills: List<Skill>): Marketplace {
        val marketplace = mock<Marketplace>()
        doReturn(name).whenever(marketplace).getName()
        doReturn(repoUrl).whenever(marketplace).getRepoUrl()
        doReturn(skills).whenever(marketplace).getSkills()
        return marketplace
    }

    private fun createBootstrap(name: String, marketplaces: List<Marketplace> = emptyList()): Bootstrap {
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val marketplaceRegistry = mock<MarketplaceRegistry>()
        doReturn(marketplaces).whenever(marketplaceRegistry).all()

        val context = Context(
            assistant = assistant,
            home = File("target/marketplace-controller/$name"),
            llm = mock<LLM>(),
            marketplaceRegistry = marketplaceRegistry,
        )
        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }

    private fun createBootstrapWithRegistry(name: String, marketplaceRegistry: MarketplaceRegistry): Bootstrap {
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val context = Context(
            assistant = assistant,
            home = File("target/marketplace-controller/$name"),
            llm = mock<LLM>(),
            marketplaceRegistry = marketplaceRegistry,
        )
        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
