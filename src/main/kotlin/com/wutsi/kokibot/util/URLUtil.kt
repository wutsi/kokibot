package com.wutsi.kokibot.util

import jodd.net.MimeTypes
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Arrays
import java.util.concurrent.TimeUnit

object URLUtil {
    private val LOGGER = LoggerFactory.getLogger(URLUtil::class.java)
    private const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
    const val BUFFER_SIZE = 1024 * 1024 // 1M
    const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB

    fun fetch(url: URL, maxLength: Int = MAX_FILE_SIZE): File {
        val client = OkHttpClient.Builder()
            .protocols(Arrays.asList(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND,
                    "Not found: $url",
                    HttpHeaders(),
                    "".toByteArray(Charset.forName("UTF-8")),
                    Charset.defaultCharset()
                )
            }

            // Download
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            download(url, response, contentType, maxLength)
        }
    }

    private fun download(url: URL, response: okhttp3.Response, contentType: String, maxLength: Int): File {
        val filename = url.file
            .ifEmpty { null }
            ?.removeSuffix("/")
            ?.let { file -> File(file).name }
            ?: url.host.replace(".", "_")
        val extension = getExtension(contentType)
        val prefix = filename.substringBeforeLast('.', filename)

        val file = Files.createTempFile(prefix, ".$extension").toFile()
        response.body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxLength) {
                        throw FileTooLargeException(
                            "File size exceeds maximum allowed size (${maxLength / (1024 * 1024)} MB)"
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
        }

        LOGGER.debug("{} downloaded to {}. Size={}", url, file.absolutePath, file.length())
        return file
    }

    private fun getExtension(contentType: String): String {
        val parts = contentType.split(";")
        return MimeTypes.findExtensionsByMimeTypes(parts[0].trim(), false).firstOrNull() ?: ""
    }
}
