// 文件路径: src/main/java/org/csu/pixelstrikebackend/lobby/websocket/UserStatusWebSocketHandler.java
package org.csu.pixelstrikebackend.lobby.websocket;

import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class UserStatusWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private OnlineUserService onlineUserService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // AuthFilter 已经将 userId 放入了 request attributes，
        // 而 HttpSessionHandshakeInterceptor 会将它们复制到 WebSocket session 的 attributes 中。
        Integer userId = (Integer) session.getAttributes().get("userId");

        if (userId == null) {
            System.err.println("Lobby WebSocket handshake rejected: userId not found in session attributes.");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User ID not found, authentication failed."));
            return;
        }

        // 注意：我们不再需要在这里手动存入 "userId"，拦截器已经帮我们做了。
        System.out.println("Lobby WebSocket connection established for user: " + userId);
        sessionManager.addSession(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            System.out.println("Lobby WebSocket connection closed for user: " + userId + " with status: " + status);
            sessionManager.removeSession(userId);

            // 用户的在线状态由登出(logout)或游戏结束逻辑管理，这里不需要处理
            // onlineUserService.removeUser(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Lobby WebSocket transport error for user " + session.getAttributes().get("userId") + ": " + exception.getMessage());
        // 发生传输错误时，也调用关闭连接的清理逻辑
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }
}