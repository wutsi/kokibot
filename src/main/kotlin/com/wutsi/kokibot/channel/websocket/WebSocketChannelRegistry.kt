package com.wutsi.kokibot.channel.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketChannelRegistry {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketChannelRegistry::class.java)
        private val channels = ConcurrentHashMap<String, WebSocketChannel>()

        fun registerChannel(agentName: String, channel: WebSocketChannel) {
            LOGGER.info("Registering WebSocket channel for agent: $agentName")
            channels[agentName.lowercase()] = channel
        }

        fun unregisterChannel(agentName: String) {
            LOGGER.info("Unregistering WebSocket channel for agent: $agentName")
            channels.remove(agentName.lowercase())
        }

        fun findChannel(agentName: String): WebSocketChannel? = channels[agentName.lowercase()]

        fun getAllChannels(): List<WebSocketChannel> = channels.values.toList()
    }
}
