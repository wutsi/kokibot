package com.wutsi.kokibot.mail

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties

class IMAP {
    private lateinit var host: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var port: String
    private lateinit var useSSL: String

    fun init(config: Map<*, *>, context: Context) {
        host = config["host"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/imap/host")

        username = config["username"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/imap/username")

        password = config["password"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/imap/password")

        port = config["port"]?.toString()?.ifEmpty { null }
            ?: throw ConfigurationException("Missing required configuration: mail/imap/port")

        useSSL = MapUtil.toString("use-ssl", config) ?: "false"
    }

    fun destroy() {
    }

    fun getStore(): Store {
        val props = getProperties()
        val session = Session.getDefaultInstance(props)
        val store = session.getStore(if (useSSL == "false") "imap" else "imaps")
        store.connect(host, port.toInt(), username, password)
        return store
    }

    internal fun getProperties(): Properties {
        return Properties().apply {
            if (useSSL == "false") {
                put("mail.store.protocol", "imap")
                put("mail.imap.host", host)
                put("mail.imap.port", port)
            } else {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.host", host)
                put("mail.imaps.port", port)
            }
        }
    }
}
