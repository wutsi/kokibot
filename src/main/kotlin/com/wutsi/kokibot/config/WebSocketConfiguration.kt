package com.wutsi.kokibot.config

import com.wutsi.kokibot.channel.websocket.WebSocketRouter
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
open class WebSocketConfiguration(
    private val webSocketRouter: WebSocketRouter,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(webSocketRouter, "/ws")
            .setAllowedOrigins("*")
    }
}
