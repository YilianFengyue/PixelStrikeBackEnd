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
    private final Map<String, Integer> sessionToGameId = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> gameIdToUserId = new ConcurrentHashMap<>();
    private final Map<String, String> playerNameBySession = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    // ...以及其他所有字段...

    @Autowired
    public GameRoomService(@Lazy GameManager gameManager) {
        // 构造函数保持原样
    }

    // --- 所有方法都改为 void 或直接返回数据，不再返回 Mono/Flux ---

    public void addSession(WebSocketSession s) {
        sessions.put(s.getId(), s);
        // 其他 rate counter 和 clock 的初始化保持原样
    }

    public void removeSession(WebSocketSession s) {
        sessions.remove(s.getId());
        // 其他清理逻辑保持原样
    }

    public void handleJoin(WebSocketSession session) {
        Long gameId = (Long) session.getAttributes().get("gameId");
        Integer userId = (Integer) session.getAttributes().get("userId");

        if (gameId == null || userId == null) {
            System.err.println("Error on join: gameId or userId is null.");
            return;
        }

        // 内部数据结构更新逻辑保持不变...

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
        // ...
        broadcastToOthers(session, joined.toString());
    }

    public void handleState(WebSocketSession session, JsonNode root) {
        // 内部逻辑完全不变，只是不再返回 Mono
    }

    public void handleShot(WebSocketSession session, JsonNode root) {
        // 内部逻辑完全不变，只是不再返回 Mono
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

    // --- 【核心】广播逻辑改回 for 循环 ---

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            sendTo(session, message);
        }
    }

    public void broadcastToOthers(WebSocketSession sender, String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            if (!session.getId().equals(sender.getId())) {
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
                session.sendMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            // 发生IO错误通常意味着连接已断开，可以做清理
            removeSession(session);
        }
    }

    // --- 【新增】将缺失的 prepareGame 方法加回来 ---
    public void prepareGame(Long gameId, List<Integer> playerIds) {
        System.out.println("Preparing new game " + gameId + " with players: " + playerIds);
        // 在这里可以为即将到来的游戏初始化一些数据
        // 例如：
        // GameState state = new GameState(gameId, playerIds);
        // activeGames.put(gameId, state);
    }
}