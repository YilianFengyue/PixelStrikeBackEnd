package org.csu.pixelstrikebackend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class HttpAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            
            // 从 HttpServletRequest 中获取我们 AuthFilter 设置的 userId
            Integer userId = (Integer) httpServletRequest.getAttribute("userId");
            
            if (userId != null) {
                // 将 userId 放入 attributes map 中，这个 map 会被传递给 WebSocketSession
                attributes.put("userId", userId);
                return true; // 返回 true，允许握手继续
            }
        }
        // 如果获取不到 userId，则握手失败
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 不需要实现
    }
}