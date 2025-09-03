package org.csu.pixelstrikebackend.lobby.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class UserStatusWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.addSession(userId, session);
        } else {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User ID not found in session"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        sessionManager.removeSession(userId);
    }
}