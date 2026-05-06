package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This is the daily
 */
class DailyLog : Resource {
    private lateinit var context: Context
    private val lock = ReentrantReadWriteLock()

    override fun id(): String {
        return "service:daily-log"
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
    }

    /**
     * Return today's log content. If the log file does not exist, return null.
     */
    fun get(): String? = lock.read {
        val file = getFile(LocalDate.now())
        if (!file.exists()) {
            return null
        } else {
            return file.readText()
        }
    }

    fun clear() = lock.write {
        val file = getFile(LocalDate.now())
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getFile(date: LocalDate): File {
        val dir = File(context.home.absolutePath + "/memory/history")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filename = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return File(dir, "$filename.md")
    }
}
