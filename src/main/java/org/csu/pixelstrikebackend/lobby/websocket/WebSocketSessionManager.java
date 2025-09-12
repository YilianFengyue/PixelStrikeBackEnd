// 文件路径: src/main/java/org/csu/pixelstrikebackend/lobby/websocket/WebSocketSessionManager.java
package org.csu.pixelstrikebackend.lobby.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    // 字段声明中的 WebSocketSession 现在引用的是 org.springframework.web.socket.WebSocketSession
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

    // 【核心修改】重写消息发送方法
    public void sendMessageToUser(Integer userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String messageString = objectMapper.writeValueAsString(payload);
                // 使用 session.sendMessage()，这是一个同步阻塞方法
                session.sendMessage(new TextMessage(messageString));
                System.out.println("Sent message to user " + userId + ": " + messageString);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing message for user " + userId + ": " + e.getMessage());
            } catch (IOException e) {
                // IOException 通常意味着连接已损坏或关闭
                System.err.println("Error sending message to user " + userId + ". Connection may be closed. Error: " + e.getMessage());
                // 可以在这里做一些清理工作，比如移除这个 session
                removeSession(userId);
            }
        }
    }
}