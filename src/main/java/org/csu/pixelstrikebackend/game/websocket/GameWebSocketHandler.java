package org.csu.pixelstrikebackend.game.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.game.service.GameRoom;
import org.csu.pixelstrikebackend.game.service.GameRoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameRoomManager roomManager;
    private final Gson gson = new Gson();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // --- 从WebSocket连接的URI中解析出roomId ---
        URI uri = session.getUri();
        if (uri == null) {
            try {
                session.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        String roomId = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("gameId");

        if (roomId == null || roomId.trim().isEmpty()) {
            System.err.println("Player connected without a roomId. Closing connection.");
            try {
                session.close(); // 如果客户端没有提供roomId，则拒绝连接
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 现在，我们不再负责创建房间，只负责将玩家加入GameRoomManager已知的房间
        roomManager.addPlayerToRoom(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        JsonObject jsonObject = gson.fromJson(payload, JsonObject.class);

        // 判断消息类型
        if (jsonObject.has("type") && "ping".equals(jsonObject.get("type").getAsString())) {

            // --- 【核心BUG修复】 START ---
            // 1. 先将时间戳明确地解析为 long 类型
            long timestamp = jsonObject.get("timestamp").getAsLong();

            // 2. 将这个 long 类型的值放入响应体中
            // 这样 gson 序列化时就会保证它是标准的数字格式
            Map<String, Object> pongMessage = Map.of("type", "pong", "timestamp", timestamp);

            // 3. 发送 pong 消息
            session.sendMessage(new TextMessage(gson.toJson(pongMessage)));
            // --- 【核心BUG修复】 END ---

        } else {
            // 否则，认为是玩家指令
            GameRoom room = roomManager.getRoomForPlayer(session.getId());
            if (room != null) {
                try {
                    UserCommand command = gson.fromJson(payload, UserCommand.class);
                    command.setPlayerId(session.getId());
                    room.queueCommand(command);
                } catch (Exception e) {
                    System.err.println("解析UserCommand失败: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roomManager.removePlayerFromRoom(session);
    }
}