package com.wutsi.kokibot.channel.telegram

import com.wutsi.kokibot.Context
import java.io.File
import java.util.Properties

/**
 * A simple class to store the mapping between Telegram username and chat id.
 * The mapping is stored into <HOME>/workspace/telegram/users.properties
 */
class TelegramUsers {
    private val properties = Properties()
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context

        try {
            getFile().inputStream().use { properties.load(it) }
        } catch (_: Exception) {
            properties.clear()
        }
    }

    fun put(username: String, chatId: String) {
        if (properties.containsKey(username)) {
            return
        }

        // Cache
        properties.setProperty(username, chatId)

        // Persist
        val file = getFile()
        file.outputStream().use { out ->
            properties.store(out, null)
        }
    }

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
