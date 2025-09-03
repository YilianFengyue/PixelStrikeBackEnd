package org.csu.pixelstrikebackend.config;

import org.csu.pixelstrikebackend.websocket.AuthHandshakeInterceptor;
import org.csu.pixelstrikebackend.websocket.GameWebSocketHandler;
import org.csu.pixelstrikebackend.websocket.UserStatusWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket // 开启 WebSocket 功能
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private UserStatusWebSocketHandler userStatusWebSocketHandler;

    @Autowired
    private AuthHandshakeInterceptor authHandshakeInterceptor;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userStatusWebSocketHandler, "/ws") // 将处理器映射到 /ws 路径
                .addInterceptors(authHandshakeInterceptor) // 添加握手拦截器
                .setAllowedOrigins("*"); // 允许所有来源的连接

        registry.addHandler(gameWebSocketHandler, "/game") // 游戏对战
                .setAllowedOrigins("*"); // 暂时不加拦截器，因为游戏房间ID在URL中，而不在Token里
    }
}