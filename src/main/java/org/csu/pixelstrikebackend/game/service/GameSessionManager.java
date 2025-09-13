package org.csu.pixelstrikebackend.game.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        // 创建会话列表的快照以进行迭代，避免在迭代时修改
        List<WebSocketSession> currentSessions = new ArrayList<>(sessions.values());
//        System.out.println("[BROADCAST] -> " + currentSessions.size() + " sessions : " + json);
        for (WebSocketSession session : currentSessions) {
            sendTo(session, message);
        }
    }

    public void broadcastToOthers(WebSocketSession sender, String json) {
        TextMessage message = new TextMessage(json);
        List<WebSocketSession> currentSessions = new ArrayList<>(sessions.values());
//        System.out.println("[BROADCAST TO OTHERS] -> " + (currentSessions.size() -1) + " sessions : " + json);
        for (WebSocketSession session : currentSessions) {
            if (session.isOpen() && !session.getId().equals(sender.getId())) {
                sendTo(session, message);
            }
        }
    }

    public void sendTo(WebSocketSession session, String json) {
        sendTo(session, new TextMessage(json));
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        try {
            if (session != null && session.isOpen()) {
                // 在会话对象上同步，以确保对单个会话的发送是线程安全的
                synchronized (session) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            removeSession(session);
        } catch (IllegalStateException e) {
            System.err.println("Failed to send message (already closed) to session " + session.getId() + ": " + e.getMessage());
            removeSession(session);
        }
    }
}