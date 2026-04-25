package com.wutsi.kokibot.service

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.time.LocalDate
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

    fun create(filename: String, content: ByteArray): File {
        val dir = getFileDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val basename = FilenameUtils.getBaseName(filename)
        val extension = FilenameUtils.getExtension(filename) ?: ""
        val file = File(dir, "${basename}_${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ""}")
        file.writeBytes(content)
        return file
    }

    fun createTempFile(fileName: String, extension: String): File {
        val dir = getTempDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${fileName}_${UUID.randomUUID()}.${extension.removePrefix(".")}")
    }

    private fun getFileDirectory(): File {
        val now = LocalDate.now()
        val path = "${getFilesDir()}/${now.year}/${now.month.value}/${now.dayOfMonth}"
        return File(path)
    }

    fun getTempDir(): File {
        return File(getWorkspaceDir(), "/tmp")
    }

    fun getFilesDir(): File {
        return File(getWorkspaceDir(), "/files")
    }

    fun getWorkspaceDir(): File {
        return File("${context.home.absolutePath}/workspace")
    }
}
