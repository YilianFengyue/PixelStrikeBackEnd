package org.csu.pixelstrikebackend.config;

import org.csu.pixelstrikebackend.game.websocket.GameWebSocketHandler;
import org.csu.pixelstrikebackend.lobby.websocket.UserStatusWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Autowired
    private UserStatusWebSocketHandler userStatusWebSocketHandler;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        // 注意：这里的WebSocketHandler需要是Spring WebFlux的版本
        map.put("/ws", (WebSocketHandler) userStatusWebSocketHandler);
        map.put("/game", (WebSocketHandler) gameWebSocketHandler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        // 优先级设高一点，确保WebSocket请求被优先处理
        handlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}