package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.env.Environment
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals

class MultiBootstrapTest {
    private val jsonMapper = JsonMapper()
    private val env = mock<Environment>()
    private val assistantRegistry = mock<AssistantRegistry>()
    private val home = File(this::class.java.getResource("/home/multi-agent")!!.file)
    private val bootstrap = MultiBootstrap(env, jsonMapper, assistantRegistry)

    @BeforeEach
    fun setup() {
        doReturn(arrayOf("local")).whenever(env).activeProfiles
    }

    @Test
    fun init() {
        bootstrap.init(home)

        assertEquals(2, bootstrap.bootstraps.size)
    }

    @Test
    fun destroy() {
        bootstrap.init(home)
        bootstrap.destroy()

        assertEquals(0, bootstrap.bootstraps.size)
    }

    @Test
    fun `init - non-existent agents directory`() {
        val nonExistentHome = File("target/test-data/non-existent-" + System.currentTimeMillis())
        bootstrap.init(nonExistentHome)

        assertEquals(0, bootstrap.bootstraps.size)
    }
}
