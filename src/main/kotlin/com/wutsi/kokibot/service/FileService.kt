package com.wutsi.kokibot.service

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.util.UUID

class FileService : Resource {
    companion object {
        const val ID = "service:file"
    }

    private lateinit var context: Context

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun contentType(file: File): String {
        return when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            else -> Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        }
    }

    fun createTempFile(filename: String): File {
        val extension = filename.substringAfterLast('.', "")
        val prefix = filename.substringBeforeLast('.', filename)
        return createTempFile(prefix, extension)
    }

    fun createTempFile(filename: String, extension: String): File {
        val dir = getTempDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$filename.${extension.removePrefix(".")}")
    }

    fun urlPath(path: String): String? {
        if (path.startsWith(context.home.absolutePath)) {
            val suffix = path.removePrefix(context.home.absolutePath)
                .split("/").joinToString("/") { part -> URLEncoder.encode(part, "UTF-8") }
            return "/files/${context.assistant.name}" + suffix
        } else {
            return null
        }
    }

    fun urlPathFile(urlPath: String): File {
        val suffix = urlPath.removePrefix(context.home.absolutePath)
            .split("/").joinToString("/") { part -> URLDecoder.decode(part, "UTF-8") }
        return File(context.home.absolutePath, suffix)
    }

    private fun getTempDir(): File {
        return File(context.home.absolutePath, "workspace/tmp/" + UUID.randomUUID())
    }
}
