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

    private final Map<Long, Map<String, WebSocketSession>> gameRooms = new ConcurrentHashMap<>();

    public void addSession(Long gameId, WebSocketSession session) {
        // 如果该 gameId 的房间不存在，则创建一个新的
        gameRooms.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    public void removeSession(Long gameId, WebSocketSession session) {
        Map<String, WebSocketSession> room = gameRooms.get(gameId);
        if (room != null) {
            room.remove(session.getId());
            // 如果房间空了，就从内存中移除，释放资源
            if (room.isEmpty()) {
                gameRooms.remove(gameId);
            }
        }
    }

    public void broadcast(Long gameId, String json) {
        Map<String, WebSocketSession> room = gameRooms.get(gameId);
        if (room == null) return; // 如果房间不存在，不执行任何操作

        TextMessage message = new TextMessage(json);
        List<WebSocketSession> currentSessions = new ArrayList<>(room.values());
        System.out.println("[BROADCAST][GameID: " + gameId + "] -> " + currentSessions.size() + " sessions : " + json);
        for (WebSocketSession session : currentSessions) {
            sendTo(session, message);
        }
    }

    public void broadcastToOthers(Long gameId, WebSocketSession sender, String json) {
        Map<String, WebSocketSession> room = gameRooms.get(gameId);
        if (room == null) return; // 如果房间不存在，不执行任何操作

        TextMessage message = new TextMessage(json);
        List<WebSocketSession> currentSessions = new ArrayList<>(room.values());
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
                // ★ 核心修复：对 session 对象本身进行同步，确保消息原子性发送 ★
                synchronized (session) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException | IllegalStateException e) { // 合并异常捕获
            System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            Long gameId = (Long) session.getAttributes().get("gameId");
            if (gameId != null) {
                removeSession(gameId, session);
            }
        }
    }
}