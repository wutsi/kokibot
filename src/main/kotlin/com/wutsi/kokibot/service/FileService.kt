package com.wutsi.kokibot.service

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
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

    override fun health(): Health {
        return Health(id = id(), up = true)
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    fun create(filename: String, content: ByteArray): File {
        val file = File(getFileDirectory(), filename)
        file.parentFile.mkdirs()
        file.writeBytes(content)
        return file
    }

    fun createTempFile(fileName: String, extension: String): File {
        val dir = getTempDir()
        dir.mkdirs()
        return File(dir, "$fileName.$extension")
    }

    private fun getFileDirectory(): File {
        val now = LocalDate.now()
        val path =
            "${context.home.absolutePath}/workspace/files/${now.year}/${now.month.value}/${now.dayOfMonth}/${UUID.randomUUID()}"
        return File(path)
    }

    private fun getTempDir(): File {
        val path = "${context.home.absolutePath}/workspace/tmp/${UUID.randomUUID()}"
        return File(path)
    }
}
