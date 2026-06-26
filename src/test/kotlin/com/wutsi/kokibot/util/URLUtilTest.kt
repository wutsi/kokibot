package com.wutsi.kokibot.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import java.net.URL

class URLUtilTest {
    @Test
    fun `fetch file`() {
        val file = URLUtil.fetch(URL("https://calibre-ebook.com/downloads/demos/demo.docx"))

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertTrue(file.name.contains("demo"))
        assertTrue(file.name.endsWith("docx"))
    }

    @Test
    fun `fetch downloads the content into a file`() {
        val file = URLUtil.fetch(URL("https://example.com"))

        assertTrue(file.name.contains("example_com"))
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun `fetch throws when the URL returns an error`() {
        val url = URL(
            "https://raw.githubusercontent.com/wutsi/kokibot/refs/heads/master/this-file-does-not-exist-123456789.txt"
        )

        assertThrows<HttpClientErrorException.NotFound> {
            URLUtil.fetch(url)
        }
    }

    @Test
    fun `fetch file too large`() {
        val url = URL("https://www.gutenberg.org/files/2600/2600-0.txt")

        assertThrows<FileTooLargeException> {
            URLUtil.fetch(url, 100)
        }
    }
}
