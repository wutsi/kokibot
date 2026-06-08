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
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    private val home = File("target/file-controller")

    @BeforeEach
    fun setUp() {
        // Clean up the workspace directory before each test
        File(home, "007/workspace").deleteRecursively()
        File(home, "008/workspace").deleteRecursively()
    }

    @Test
    fun `files - returns file content`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val testFile = createTestFile("007", "workspace/tmp/hello.txt", "Hello World")

        val response = rest.getForEntity("/files/007|workspace|tmp|hello.txt", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Hello World", String(response.body!!))
        assertEquals(testFile.length(), response.headers.contentLength)
    }

    @Test
    fun `files - sets content disposition header with filename`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        createTestFile("007", "workspace/tmp/report.txt", "Report contents")

        val response = rest.getForEntity("/files/007|workspace|tmp|report.txt", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        val contentDisposition = response.headers.contentDisposition
        assertNotNull(contentDisposition)
        assertEquals("attachment", contentDisposition.type)
        assertEquals("report.txt", contentDisposition.filename)
    }

    @Test
    fun `files - detects content type from extension`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        createTestFile("007", "workspace/tmp/note.txt", "Hello")

        val response = rest.getForEntity("/files/007|workspace|tmp|note.txt", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        val contentType = response.headers.contentType
        assertNotNull(contentType)
        assertEquals("text", contentType.type)
        assertEquals("plain", contentType.subtype)
    }

    @Test
    fun `files - falls back to octet-stream for unknown content type`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        // File with unknown extension
        val testFile = createTestFileBytes(
            "007",
            "workspace/tmp/data.kokibotxyz",
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        )

        val response = rest.getForEntity("/files/007|workspace|tmp|data.kokibotxyz", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.headers.contentType)
        assertEquals(testFile.length(), response.headers.contentLength)
    }

    @Test
    fun `files - returns 404 when assistant not found`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/files/xxx|workspace|tmp|hello.txt", ByteArray::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `files - returns 404 when file does not exist`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        val response = rest.getForEntity("/files/007|workspace|tmp|missing.txt", ByteArray::class.java)

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `files - resolves nested path with multiple separators`() {
        doReturn(listOf(createBootstrap("007"))).whenever(multi).bootstraps

        createTestFile("007", "workspace/a/b/c/deep.txt", "Deep file")

        val response = rest.getForEntity("/files/007|workspace|a|b|c|deep.txt", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Deep file", String(response.body!!))
    }

    @Test
    fun `files - finds correct assistant when multiple bootstraps exist`() {
        doReturn(
            listOf(
                createBootstrap("007"),
                createBootstrap("008"),
            )
        ).whenever(multi).bootstraps

        createTestFile("008", "workspace/tmp/from-008.txt", "From 008")

        val response = rest.getForEntity("/files/008|workspace|tmp|from-008.txt", ByteArray::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("From 008", String(response.body!!))
    }

    private fun createTestFile(assistantName: String, relativePath: String, content: String): File {
        return createTestFileBytes(assistantName, relativePath, content.toByteArray())
    }

    private fun createTestFileBytes(assistantName: String, relativePath: String, content: ByteArray): File {
        val file = File(File(home, assistantName), relativePath)
        file.parentFile.mkdirs()
        file.writeBytes(content)
        return file
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

        val bootstrap = mock(Bootstrap::class.java)
        doReturn(context).whenever(bootstrap).getContext()
        return bootstrap
    }
}
