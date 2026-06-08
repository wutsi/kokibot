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
    fun url() {
        service.init(emptyMap<String, Any>(), context)

        val url = service.url(context.home.absolutePath + "/workspace/a/file.pdf")
        assertEquals("/files/workspace/a/file.pdf", url)
    }

    @Test
    fun `url - not in homw`() {
        service.init(emptyMap<String, Any>(), context)

        val url = service.url("/workspace/a/file.pdf")
        assertEquals(null, url)
    }
}
