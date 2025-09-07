package org.csu.pixelstrikebackend.lobby.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final Map<Integer, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void addSession(Integer userId, WebSocketSession session) {
        sessions.put(userId, session);
        System.out.println("WebSocket session added for user: " + userId + ". Total sessions: " + sessions.size());
    }

    public void removeSession(Integer userId) {
        if (userId != null) {
            sessions.remove(userId);
            System.out.println("WebSocket session removed for user: " + userId + ". Total sessions: " + sessions.size());
        }
    }

    public void sendMessageToUser(Integer userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String message = objectMapper.writeValueAsString(payload);
                // 2. 【核心修改】使用响应式的方式发送消息
                // session.send() 返回一个 Mono<Void>，代表一个异步操作。
                // 我们需要 .subscribe() 来触发这个操作。
                session.send(Mono.just(session.textMessage(message)))
                        .subscribe(
                                null, // 成功时不做任何事
                                error -> System.err.println("Error sending message to user " + userId + ": " + error.getMessage())
                        );
                System.out.println("Sent message to user " + userId + ": " + message);
            } catch (IOException e) {
                // 这个异常现在主要由序列化失败引起
                System.err.println("Error serializing message for user " + userId + ": " + e.getMessage());
            }
        }
    }
}