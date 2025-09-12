package org.csu.pixelstrikebackend.config;

import org.csu.pixelstrikebackend.game.websocket.GameWebSocketHandler;
import org.csu.pixelstrikebackend.lobby.websocket.UserStatusWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private UserStatusWebSocketHandler userStatusWebSocketHandler;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userStatusWebSocketHandler, "/ws")
                // 【核心修改】使用我们自己的拦截器
                .addInterceptors(new HttpAuthHandshakeInterceptor())
                .setAllowedOriginPatterns("*");

        registry.addHandler(gameWebSocketHandler, "/game")
                // 【核心修改】使用我们自己的拦截器
                .addInterceptors(new HttpAuthHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }
}