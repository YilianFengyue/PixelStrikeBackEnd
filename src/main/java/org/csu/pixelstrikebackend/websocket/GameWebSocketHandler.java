package org.csu.pixelstrikebackend.websocket;

import com.google.gson.Gson;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.service.GameRoom;
import org.csu.pixelstrikebackend.service.GameRoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameRoomManager roomManager;
    private final Gson gson = new Gson();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 实际项目中, 玩家应该先通过HTTP请求加入房间, 成功后再建立WebSocket连接
        // 这里我们为了测试，可以先硬编码加入一个房间
        // 假设Dev2的逻辑已经告诉我们这个玩家应该去哪个房间
//        String roomId = "room1";
        String roomId = (session.getId().hashCode() % 2 == 0) ? "room_even" : "room_odd";
        roomManager.createAndStartRoom(roomId); // 如果房间不存在则创建
        roomManager.addPlayerToRoom(roomId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        GameRoom room = roomManager.getRoomForPlayer(session.getId());
        if (room != null) {
            try {
                UserCommand command = gson.fromJson(message.getPayload(), UserCommand.class);
                command.setPlayerId(session.getId());
                room.queueCommand(command);
            } catch (Exception e) { /* ... */ }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roomManager.removePlayerFromRoom(session);
    }
}