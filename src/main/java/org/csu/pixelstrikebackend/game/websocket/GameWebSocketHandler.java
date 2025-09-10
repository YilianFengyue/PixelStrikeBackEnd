package org.csu.pixelstrikebackend.game.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.csu.pixelstrikebackend.game.service.GameRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameRoomService roomService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Game WebSocket OPEN: " + session.getId());

        // 从拦截器填充的 attributes 中获取 userId
        Integer userId = (Integer) session.getAttributes().get("userId");

        // 从URI中获取 gameId
        String gameIdStr = UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst("gameId");

        if (userId == null || gameIdStr == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing userId or gameId"));
            return;
        }

        Long gameId = Long.parseLong(gameIdStr);
        session.getAttributes().put("gameId", gameId); // 将 gameId 也存入 session

        // 连接建立后，立即处理玩家加入
        roomService.addSession(session);
        roomService.handleJoin(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            JsonNode root = mapper.readTree(payload);
            String type = root.path("type").asText("");

            switch (type) {
                case "state":
                    roomService.handleState(session, root);
                    break;
                case "shot":
                    roomService.handleShot(session, root);
                    break;
                default:
                    System.out.println("IGNORED unknown msg type=" + type + " raw=" + payload);
            }
        } catch (Exception e) {
            System.err.println("Error processing game message: " + e.getMessage());
            // 你可以选择在这里关闭连接或忽略错误
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("Game WebSocket CLOSE: " + session.getId() + " with status: " + status);
        roomService.handleLeave(session);
        roomService.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Game WebSocket Transport ERROR for session " + session.getId() + ": " + exception.getMessage());
        // 错误发生时也确保清理资源
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }
}