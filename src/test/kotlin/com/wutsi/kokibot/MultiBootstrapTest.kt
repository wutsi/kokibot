package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.core.env.Environment
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiBootstrapTest {
    private val jsonMapper = JsonMapper()
    private val env = mock<Environment>()
    private val assistantRegistry = AssistantRegistry()
    private val home = File(this::class.java.getResource("/home/multi-agent")!!.file)
    private val bootstrap = MultiBootstrap(env, jsonMapper, assistantRegistry)
    private var tempHome: File? = null

    @BeforeEach
    fun setup() {
        doReturn(arrayOf("local")).whenever(env).activeProfiles
    }

    @AfterEach
    fun tearDown() {
        tempHome?.deleteRecursively()
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

    @Test
    fun rename() {
        tempHome = Files.createTempDirectory("kokibot-rename-test").toFile()
        home.copyRecursively(tempHome!!)

        bootstrap.init(tempHome!!)
        assertEquals(2, bootstrap.bootstraps.size)

        bootstrap.rename("007", "james-bond")

        assertEquals(2, bootstrap.bootstraps.size)
        assertNotNull(bootstrap.get("james-bond"))
        assertFalse(File(tempHome, "agents/007").exists())
        assertTrue(File(tempHome, "agents/james-bond").exists())
        assertThrows<AssistantNotFoundException> { assistantRegistry.get("007") }
    }

    @Test
    fun `rename - same name is no-op`() {
        bootstrap.init(home)

        val sizeBefore = bootstrap.bootstraps.size
        bootstrap.rename("007", "007")

        assertEquals(sizeBefore, bootstrap.bootstraps.size)
    }

    @Test
    fun `rename - target already exists throws`() {
        bootstrap.init(home)

        assertThrows<AssistantAlreadyRegisteredException> {
            bootstrap.rename("007", "008")
        }
    }

    @Test
    fun `rename - unknown name throws`() {
        bootstrap.init(home)

        assertThrows<AssistantNotFoundException> {
            bootstrap.rename("unknown", "new-name")
        }
    }

    @Test
    fun `rename - new name is trimmed`() {
        tempHome = Files.createTempDirectory("kokibot-rename-trim-test").toFile()
        home.copyRecursively(tempHome!!)

        bootstrap.init(tempHome!!)

        bootstrap.rename("007", "  james-bond  ")

        assertNotNull(bootstrap.get("james-bond"))
        assertTrue(File(tempHome, "agents/james-bond").exists())
        assertFalse(File(tempHome, "agents/007").exists())
    }

    @Test
    fun `rename - trimmed name equal to old name is no-op`() {
        bootstrap.init(home)

        val sizeBefore = bootstrap.bootstraps.size
        bootstrap.rename("007", "  007  ")

        assertEquals(sizeBefore, bootstrap.bootstraps.size)
        assertNotNull(bootstrap.get("007"))
    }

    @Test
    fun `rename - name with forward slash throws`() {
        bootstrap.init(home)

        assertThrows<IllegalArgumentException> {
            bootstrap.rename("007", "foo/bar")
        }
        assertNotNull(bootstrap.get("007"))
    }

    @Test
    fun `rename - name with backslash throws`() {
        bootstrap.init(home)

        assertThrows<IllegalArgumentException> {
            bootstrap.rename("007", "foo\\bar")
        }
        assertNotNull(bootstrap.get("007"))
    }

    @Test
    fun `rename - name with internal whitespace throws`() {
        bootstrap.init(home)

        assertThrows<IllegalArgumentException> {
            bootstrap.rename("007", "foo bar")
        }
        assertNotNull(bootstrap.get("007"))
    }

    @Test
    fun `rename - blank name throws`() {
        bootstrap.init(home)

        assertThrows<IllegalArgumentException> {
            bootstrap.rename("007", "   ")
        }
        assertNotNull(bootstrap.get("007"))
    }

    @Test
    fun delete() {
        tempHome = Files.createTempDirectory("kokibot-delete-test").toFile()
        home.copyRecursively(tempHome!!)

        bootstrap.init(tempHome!!)
        assertEquals(2, bootstrap.bootstraps.size)

        bootstrap.delete("007")

        assertEquals(1, bootstrap.bootstraps.size)
        assertThrows<AssistantNotFoundException> { assistantRegistry.get("007") }
        assertFalse(File(tempHome, "agents/007").exists())

        val trashed = File(tempHome, "agents/.trash").listFiles()
        assertNotNull(trashed)
        assertEquals(1, trashed!!.size)
        assertTrue(trashed[0].name.startsWith("007-"))
        assertTrue(File(trashed[0], "config/settings.json").exists())
    }

    @Test
    fun `delete - unknown name throws`() {
        bootstrap.init(home)

        assertThrows<AssistantNotFoundException> {
            bootstrap.delete("unknown")
        }
        assertEquals(2, bootstrap.bootstraps.size)
    }
}
