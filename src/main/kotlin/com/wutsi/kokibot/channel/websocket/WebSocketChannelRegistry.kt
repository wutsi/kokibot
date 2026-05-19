package com.wutsi.kokibot.channel.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketChannelRegistry {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketChannelRegistry::class.java)

        // Static registry - accessible without Spring injection
        private val channels = ConcurrentHashMap<String, WebSocketChannel>()

        // Static methods for registration (called by WebSocketChannel instances)
        fun registerChannel(channel: WebSocketChannel) {
            val path = channel.getPath()
            channels[path] = channel
            LOGGER.info("Registered WebSocket channel at path: $path")
        }

        fun unregisterChannel(channel: WebSocketChannel) {
            val path = channel.getPath()
            channels.remove(path)
            LOGGER.info("Unregistered WebSocket channel at path: $path")
        }

        fun getAllChannels(): List<WebSocketChannel> {
            return channels.values.toList()
        }
    }

    // Instance method called by WebSocketConfiguration
    fun registerHandlers(registry: WebSocketHandlerRegistry) {
        channels.values.forEach { channel ->
            LOGGER.info("Registering WebSocket handler at ${channel.getPath()}")
            registry.addHandler(channel.getHandler(), channel.getPath())
                .setAllowedOrigins("*") // TODO: Configure for production
        }
    }
}
