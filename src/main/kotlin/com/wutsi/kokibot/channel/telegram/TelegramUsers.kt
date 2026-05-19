package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Thread-safe mapping between Telegram username and chat id.
 *
 * Storage: <HOME>/workspace/telegram/users.properties
 *
 * Concurrency notes:
 *  - All public methods are synchronized on the instance, so the
 *    contains-then-put race in [put] is eliminated.
 *  - Writes are performed atomically: data is written to a sibling
 *    temp file then moved into place with [StandardCopyOption.ATOMIC_MOVE].
 *    This guarantees that a concurrent reader (in this process or
 *    another) never sees a half-written users.properties file.
 */
class TelegramUsers {
    private val properties = Properties()
    private lateinit var context: Context

    @Synchronized
    fun init(context: Context) {
        this.context = context
        properties.clear()
        try {
            val file = getFile()
            if (file.exists()) {
                file.inputStream().use { properties.load(it) }
            }
        } catch (_: Exception) {
            properties.clear()
        }
    }

    @Synchronized
    fun put(username: String, chatId: String) {
        if (properties.containsKey(username)) {
            return
        }

        // Cache
        properties.setProperty(username, chatId)

        // Persist atomically: write to temp file, then move into place
        val file = getFile()
        val tmp = File(file.parentFile, "${file.name}.${ProcessHandle.current().pid()}.tmp")
        try {
            tmp.outputStream().use { out -> properties.store(out, null) }
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (tmp.exists()) {
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun get(username: String): String? {
        return properties.getProperty(username)?.trim()
    }

    private fun getFile(): File {
        val dir = File(context.home, "workspace/telegram")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "users.properties")
    }
}
