package org.csu.pixelstrikebackend.game.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.game.service.GameRoom;
import org.csu.pixelstrikebackend.game.service.GameRoomManager;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class GameWebSocketHandler implements WebSocketHandler {

    @Autowired
    private GameRoomManager roomManager;
    @Autowired
    private OnlineUserService onlineUserService;
    private final Gson gson = new Gson();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String roomId = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("gameId");

        String token = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("token");

        Integer userId = (token != null) ? JwtUtil.verifyTokenAndGetUserId(token) : null;


        if (roomId == null || roomId.trim().isEmpty() || userId == null) {
            return session.close(CloseStatus.BAD_DATA.withReason("RoomId or UserId is missing"));
        }
        session.getAttributes().put("userId", userId);

        roomManager.addPlayerToRoom(roomId, session, userId);
        onlineUserService.updateUserStatus(userId, UserStatus.IN_GAME);

        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    GameRoom room = roomManager.getRoomForPlayer(session.getId());
                    if (room != null) {
                        try {
                            JsonObject jsonObject = gson.fromJson(payload, JsonObject.class);
                            if (jsonObject.has("type") && "ping".equals(jsonObject.get("type").getAsString())) {
                                long timestamp = jsonObject.get("timestamp").getAsLong();
                                Map<String, Object> pongMessage = Map.of("type", "pong", "timestamp", timestamp);
                                return session.send(Mono.just(session.textMessage(gson.toJson(pongMessage))));
                            } else {
                                UserCommand command = gson.fromJson(payload, UserCommand.class);
                                command.setPlayerId(session.getId());
                                room.queueCommand(command);
                            }
                        } catch (Exception e) {
                            System.err.println("解析UserCommand失败: " + e.getMessage());
                        }
                    }
                    return Mono.empty();
                })
                .doOnError(error -> System.err.println("Error on WebSocket input: " + error.getMessage()))
                .doFinally(signalType -> {
                    System.out.println("Connection closed for session: " + session.getId() + ", Status: " + signalType);
                    roomManager.removePlayerFromRoom(session);
                })
                .then();
    }
}