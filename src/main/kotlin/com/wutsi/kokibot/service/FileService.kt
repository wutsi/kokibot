package com.wutsi.kokibot.service

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import java.io.File
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

    fun createTempFile(filename: String): File {
        val extension = filename.substringAfterLast('.', "")
        return createTempFile(filename, extension)
    }

    fun createTempFile(filename: String, extension: String): File {
        val dir = getTempDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${filename}_${UUID.randomUUID()}.${extension.removePrefix(".")}")
    }

    fun url(path: String): String? {
        if (path.startsWith(context.home.absolutePath)) {
            return "/files" + path.substring(context.home.absolutePath.length)
        } else {
            return null
        }
    }

    private fun getTempDir(): File {
        return File(context.home.absolutePath, "workspace/tmp")
    }
}
