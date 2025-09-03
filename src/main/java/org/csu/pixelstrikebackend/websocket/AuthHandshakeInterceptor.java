package org.csu.pixelstrikebackend.websocket;

import org.csu.pixelstrikebackend.util.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 从 URL 查询参数中获取 token, 例如: ws://localhost:8080/ws?token=xxxx
        String query = request.getURI().getQuery();
        if (query != null && query.startsWith("token=")) {
            String token = query.substring(6);
            Integer userId = JwtUtil.verifyTokenAndGetUserId(token);
            if (userId != null) {
                // 验证成功, 将 userId 放入 WebSocket session 的 attributes 中
                attributes.put("userId", userId);
                System.out.println("Handshake successful for user: " + userId);
                return true;
            }
        }
        // 验证失败，拒绝连接
        System.out.println("Handshake failed: Invalid or missing token.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // do nothing
    }
}
