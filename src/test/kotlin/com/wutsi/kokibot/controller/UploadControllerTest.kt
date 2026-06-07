package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.LinkedMultiValueMap
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UploadControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    private val home = File("target/upload-controller")

    @BeforeEach
    fun setUp() {
        // Clean up the workspace temp directory before each test
        File(home, "workspace/tmp").deleteRecursively()
    }

    @Test
    fun upload() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response =
            rest.postForEntity(
                "/upload?name=007",
                createMultipartRequest("hello.txt", "Hello World"),
                Map::class.java
            )

        assertEquals(200, response.statusCode.value())

        @Suppress("UNCHECKED_CAST")
        val item = response.body!! as Map<String, String>
        assertEquals("hello.txt", item["name"])
        val path = item["path"]
        assertNotNull(path)
        val storedFile = File(path)
        assertTrue(storedFile.exists())
        assertEquals("Hello World", storedFile.readText())
        assertTrue(storedFile.absolutePath.contains("workspace/tmp"))
    }

    @Test
    fun `upload with invalid bootstraps returns 404`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response =
            rest.postForEntity("/upload?name=xxx", createMultipartRequest("hello.txt", "Hello World"), Any::class.java)

        assertEquals(404, response.statusCode.value())
    }

    private fun createMultipartRequest(
        filename: String,
        content: String
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val parts = LinkedMultiValueMap<String, Any>()
        parts.add("file", buildResource(filename, content))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(parts, headers)
    }

    private fun buildResource(filename: String, content: String): ByteArrayResource {
        return object : ByteArrayResource(content.toByteArray()) {
            override fun getFilename(): String = filename
        }
    }

    private fun createBootstrap(name: String): Bootstrap {
        val llm = mock<LLM>()
        val assistant = mock<Assistant>()
        doReturn(name).whenever(assistant).name

        val context = Context(
            assistant = assistant,
            home = File(home, name),
            llm = llm,
        )
        // Initialize FileService with the context
        context.fileService.init(emptyMap<String, Any>(), context)

        // Override fileService init since we are not running full context.init
        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
