package org.csu.pixelstrikebackend.config;

import org.csu.pixelstrikebackend.lobby.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpRequest; // 导入响应式Http请求

import java.util.List;

@Component
public class AuthWebFilter implements WebFilter {

    // 定义需要排除的路径
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/users/reset-password"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 如果当前路径是需要排除的路径，则直接放行
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 【核心改动】对于WebSocket连接请求，token在查询参数里
        String token;
        if (path.startsWith("/ws") || path.startsWith("/game")) {
            token = request.getQueryParams().getFirst("token");
        } else {
            // 对于普通HTTP请求，token在Header里
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = null;
            }
        }

        // 检查 token 是否存在
        if (token == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete(); // 拦截请求
        }

        // 验证 token
        Integer userId = JwtUtil.verifyTokenAndGetUserId(token);
        if (userId == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete(); // 拦截请求
        }

        // 将 userId 存入 exchange 的 attributes 中，方便后续 Controller 和 WebSocket Handler 使用
        exchange.getAttributes().put("userId", userId);

        return chain.filter(exchange); // 放行请求
    }
}