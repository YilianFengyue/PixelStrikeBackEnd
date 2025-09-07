package org.csu.pixelstrikebackend.lobby.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class UserStatusWebSocketHandler implements WebSocketHandler {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 从握手请求的URI中获取token
        String token = org.springframework.web.util.UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("token");

        // 验证token并获取userId
        Integer userId = (token != null) ? org.csu.pixelstrikebackend.lobby.util.JwtUtil.verifyTokenAndGetUserId(token) : null;

        // 如果在AuthWebFilter中没有成功放入userId，则拒绝连接
        if (userId == null) {
            System.err.println("WebSocket handshake rejected for session " + session.getId() + ": userId is null or token is invalid.");
            return session.close(org.springframework.web.reactive.socket.CloseStatus.POLICY_VIOLATION.withReason("User ID not found or token invalid"));
        }

        // 将验证后的userId存入session的attributes中，方便后续使用
        session.getAttributes().put("userId", userId);

        // 使用 doOnSubscribe 在连接实际建立时执行操作
        return Mono.fromRunnable(() -> {
                    sessionManager.addSession(userId, session);
                })
                .then(session.receive() // 持续监听客户端消息（即使我们不处理），以保持连接
                        .then())
                .doFinally(signalType -> { // 当连接关闭或出错时，执行清理
                    System.out.println("WebSocket connection closed for user: " + userId + " with signal: " + signalType);
                    sessionManager.removeSession(userId);
                });
    }
}