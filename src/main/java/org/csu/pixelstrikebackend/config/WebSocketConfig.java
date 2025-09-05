package org.csu.pixelstrikebackend.config;

import org.csu.pixelstrikebackend.game.websocket.GameWebSocketHandler;
import org.csu.pixelstrikebackend.lobby.websocket.AuthHandshakeInterceptor;
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
    private AuthHandshakeInterceptor authHandshakeInterceptor;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 1. 为大厅/好友状态的 WebSocket ("/ws") 应用认证
        registry.addHandler(userStatusWebSocketHandler, "/ws")
                .addInterceptors(authHandshakeInterceptor) // 添加认证拦截器
                .setAllowedOrigins("*");

        // 2. 为游戏对战的 WebSocket ("/game") 应用【相同】的认证
        registry.addHandler(gameWebSocketHandler, "/game")
                .addInterceptors(authHandshakeInterceptor) // 确保这一行是存在的
                .setAllowedOrigins("*");
    }
}