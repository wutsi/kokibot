package com.wutsi.kokibot.mail

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.util.MapUtil
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties

/**
 * This is the IMAP service, which is used to read emails.
 */
class IMAP : Resource {
    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(IMAP::class.java)
    }

    private lateinit var host: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var port: String
    private lateinit var useSSL: String

    override fun id(): String {
        return "service:imap"
    }

    /**
     * Initialize the IMAP client with the given configuration and context.
     * The configuration can contain the following parameters:
     * - host: the IMAP server host (required)
     * - port: the IMAP server port (required)
     * - username: the IMAP server username (required)
     * - password: the IMAP server password (required)
     * - use-ssl: whether to use SSL (default: false)
     */
    override fun init(config: Map<*, *>, context: Context) {
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

    override fun health(): Health {
        try {
            getStore().close()
            return Health(id(), true)
        } catch (ex: Exception) {
            LOGGER.warn("Failed to connect to IMAP", ex)
            return Health(id(), false, ex.message ?: "Unknown error")
        }
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
