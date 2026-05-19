package com.wutsi.kokibot.config

import com.wutsi.kokibot.channel.websocket.WebSocketChannelRegistry
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
open class WebSocketConfiguration(
    private val webSocketChannelRegistry: WebSocketChannelRegistry,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Delegate to the channel registry to register all WebSocket handlers
        webSocketChannelRegistry.registerHandlers(registry)
    }
}
