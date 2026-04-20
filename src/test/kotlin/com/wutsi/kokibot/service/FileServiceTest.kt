package com.wutsi.kokibot.service

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.kokibot.Context
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileServiceTest {
    private val service = FileService()
    private val context = Context(
        home = File("target/test-data/file-service"),
        llm = mock(),
    )

    @Test
    fun id() {
        assertEquals(FileService.ID, service.id())
    }

    @Test
    fun health() {
        val health = service.health()
        assertEquals(FileService.ID, health.id)
        assertTrue(health.up)
    }

    @Test
    fun create() {
        val content = "Hello, world!".toByteArray()

        service.init(emptyMap<String, String>(), context)
        val file = service.create("hello.txt", content)

        assertTrue(file.exists())
        assertEquals(content.size.toLong(), file.length())
        assertEquals("Hello, world!", file.readText())
    }
}
