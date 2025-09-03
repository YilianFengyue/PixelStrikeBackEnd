package org.csu.pixelstrikebackend.lobby.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    // 存储 userId 和对应的 WebSocketSession
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

    /**
     * 向指定用户发送消息
     * @param userId 目标用户ID
     * @param payload 要发送的数据对象
     */
    public void sendMessageToUser(Integer userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String message = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(message));
                System.out.println("Sent message to user " + userId + ": " + message);
            } catch (IOException e) {
                System.err.println("Error sending message to user " + userId + ": " + e.getMessage());
            }
        }
    }
}