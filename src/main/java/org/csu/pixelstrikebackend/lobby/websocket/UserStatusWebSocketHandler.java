package org.csu.pixelstrikebackend.lobby.websocket;

import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.service.CustomRoomService;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService; // 1. 引入 PlayerSessionService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class UserStatusWebSocketHandler extends TextWebSocketHandler {

    @Autowired private WebSocketSessionManager sessionManager;
    @Autowired private OnlineUserService onlineUserService;
    @Autowired private MatchmakingService matchmakingService;
    @Autowired private CustomRoomService customRoomService;
    @Autowired private PlayerSessionService playerSessionService; // 2. 注入 PlayerSessionService

    /**
     * 每次连接建立（无论是首次还是重连），都应视为一次用户上线事件。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User ID not found, authentication failed."));
            return;
        }

        // 确保每次连接都将用户加入在线列表
        onlineUserService.addUser(userId);

        System.out.println("Lobby WebSocket connection established for user: " + userId);
        sessionManager.addSession(userId, session);
    }

    /**
     * 连接关闭时，执行必要的清理，并将用户标记为离线。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }

        sessionManager.removeSession(userId);

        // ★★★ 核心修复：增加游戏状态检查 ★★★
        // 在移除用户前，检查他们是否还在一个有效的游戏会话中。
        // 如果是，说明游戏尚未结束或正在由GameManager清理，此时不应将他们标记为离线。
        if (playerSessionService.isPlayerInGame(userId)) {
            System.out.println("Lobby WS closed for user " + userId + ", but they are in a game. Deferring status change to GameManager.");
            return; // 提前返回，将状态清理的责任完全交给 GameManager
        }

        System.out.println("Lobby WebSocket connection closed for user: " + userId + " with status: " + status);

        // 如果玩家不在游戏中，则执行正常的掉线清理
        UserStatus currentUserStatus = onlineUserService.getUserStatus(userId);
        if (currentUserStatus == UserStatus.MATCHING) {
            matchmakingService.cancelMatchmaking(userId);
        } else if (currentUserStatus == UserStatus.IN_ROOM) {
            customRoomService.leaveRoom(userId);
        }

        // 最后，只有当玩家确实不在任何活动中时，才将他们从在线列表中移除
        onlineUserService.removeUser(userId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Lobby WebSocket transport error for user " + session.getAttributes().get("userId") + ": " + exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }
}