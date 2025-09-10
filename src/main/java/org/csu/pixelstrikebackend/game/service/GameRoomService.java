package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomService {

    // 所有的字段声明都保持原样...
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    // 假设您还有其他字段，如 gameManager, hpByPlayer 等，请保持它们不变
    private final GameManager gameManager;


    @Autowired
    public GameRoomService(@Lazy GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void prepareGame(Long gameId, List<Integer> playerIds) {
        System.out.println("Preparing new game " + gameId + " with players: " + playerIds);
    }

    public void addSession(WebSocketSession s) {
        sessions.put(s.getId(), s);
    }

    public void removeSession(WebSocketSession s) {
        sessions.remove(s.getId());
    }

    public void handleJoin(WebSocketSession session) {
        Long gameId = (Long) session.getAttributes().get("gameId");
        Integer userId = (Integer) session.getAttributes().get("userId");

        if (gameId == null || userId == null) {
            System.err.println("Error on join: gameId or userId is null for session " + session.getId());
            return;
        }

        System.out.println("Player " + userId + " is joining game " + gameId);

        // 构造 welcome 消息
        ObjectNode welcome = mapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("id", userId);
        welcome.put("serverTime", System.currentTimeMillis());
        sendTo(session, welcome.toString());

        // 构造并广播 join_broadcast 消息
        ObjectNode joined = mapper.createObjectNode();
        joined.put("type", "join_broadcast");
        joined.put("id", userId);
        joined.put("name", "Player " + userId); // 暂时使用占位符名字
        broadcastToOthers(session, joined.toString());
    }

    public void handleState(WebSocketSession session, JsonNode root) {
        // 你的游戏状态处理逻辑...
        Integer userId = (Integer) session.getAttributes().get("userId");
        if(userId == null) return;

        ObjectNode stateToBroadcast = (ObjectNode) root.deepCopy();
        stateToBroadcast.put("id", userId);
        broadcastToOthers(session, stateToBroadcast.toString());
    }

    public void handleShot(WebSocketSession session, JsonNode root) {
        // 你的射击处理逻辑...
    }

    public void handleLeave(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            ObjectNode leave = mapper.createObjectNode();
            leave.put("type", "leave");
            leave.put("id", userId);
            broadcastToOthers(session, leave.toString());
        }
    }

    // --- 【核心修复】广播逻辑与 fxdemoBackend 完全同步 ---

    public synchronized void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        // 创建会话列表的副本进行迭代，防止并发修改
        List<WebSocketSession> activeSessions = new ArrayList<>();
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                activeSessions.add(session);
            }
        }
        System.out.println("[BROADCAST] -> " + activeSessions.size() + " sessions : " + json);
        for (WebSocketSession session : activeSessions) {
            sendTo(session, message);
        }
    }

    public synchronized void broadcastToOthers(WebSocketSession sender, String json) {
        TextMessage message = new TextMessage(json);
        List<WebSocketSession> activeSessions = new ArrayList<>();
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen() && !session.getId().equals(sender.getId())) {
                activeSessions.add(session);
            }
        }
        System.out.println("[BROADCAST TO OTHERS] -> " + activeSessions.size() + " sessions : " + json);
        for (WebSocketSession session : activeSessions) {
            sendTo(session, message);
        }
    }

    public void sendTo(WebSocketSession session, String json) {
        sendTo(session, new TextMessage(json));
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        try {
            if (session != null && session.isOpen()) {
                // 【重要】在发送前也加上同步锁，确保发送操作的原子性
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

    // ...这里保留你所有其他的辅助方法 (interpolateAt, validateShot, applyDamage, 等)...
}