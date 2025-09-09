// 文件路径: src/main/java/org/csu/pixelstrikebackend/game/websocket/GameWebSocketHandler.java
package org.csu.pixelstrikebackend.game.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.csu.pixelstrikebackend.game.service.GameRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class GameWebSocketHandler implements WebSocketHandler {

    @Autowired
    private GameRoomService roomService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) {
            return session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid Token or Missing userId"));
        }

        // 1. 将连接建立的操作链接到主处理流的开始
        Mono<Void> setupFlow = Mono.fromRunnable(() -> roomService.addSession(session))
                .then(roomService.handleHello(session));

        // 2. 创建一个处理所有入站消息的流
        Mono<Void> inputHandlingFlow = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> handlePayload(session, payload)) // 将每个 payload 交给 handlePayload 处理
                .then();

        // 3. 将设置流和消息处理流合并，并在最终完成后执行清理
        return Mono.when(setupFlow, inputHandlingFlow)
                .doFinally(signalType -> {
                    // 使用 thenReturn().subscribe() 确保清理操作在流终止时执行
                    roomService.handleLeave(session).then(Mono.fromRunnable(() -> {
                        roomService.removeSession(session);
                        System.out.println("Game WebSocket connection closed for session: " + session.getId() + " with signal: " + signalType);
                    })).subscribe();
                });
    }

    // handlePayload 现在返回 Mono<Void> 以便链接到主处理流中
    private Mono<Void> handlePayload(WebSocketSession session, String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            final String type = root.path("type").asText("");

            // 根据消息类型，返回对应的响应式操作
            switch (type) {
                case "join":
                    return roomService.handleJoin(session, root);
                case "state":
                    return roomService.handleState(session, root);
                case "shot":
                    return roomService.handleShot(session, root);
                default:
                    System.out.println("IGNORED msg type=" + type + " raw=" + payload);
                    return Mono.empty(); // 对于未知类型，返回一个完成的 Mono
            }
        } catch (Exception e) {
            System.err.println("Error parsing WebSocket message payload: " + e.getMessage());
            return Mono.error(e); // 如果解析失败，将异常传递给流
        }
    }
}