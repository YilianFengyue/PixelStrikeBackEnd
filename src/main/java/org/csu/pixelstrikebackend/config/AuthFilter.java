package org.csu.pixelstrikebackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.csu.pixelstrikebackend.lobby.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/uploads/",
            "/game-data/",
            "/users/reset-password"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token;
        if (path.startsWith("/ws") || path.startsWith("/game")) {
            token = request.getParameter("token");
        } else {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = null;
            }
        }

        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":1, \"message\":\"Missing Token\"}");
            return;
        }

        Integer userId = JwtUtil.verifyTokenAndGetUserId(token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":1, \"message\":\"Invalid Token\"}");
            return;
        }

        // 【核心修改】START: 将用户信息存入 Spring Security 上下文
        // 创建一个 Authentication 对象，代表当前已认证的用户
        // 我们将 userId 作为 principal (当事人)，并且由于我们不使用角色权限，所以权限列表为空
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId, null, Collections.emptyList()
        );

        // 将这个 Authentication 对象设置到 SecurityContextHolder 中
        // 这样 Spring Security 就知道当前请求是已认证的了
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 【核心修改】END

        // 为了方便，我们仍然将 userId 存入 request attribute，WebSocket 握手时需要用到
        request.setAttribute("userId", userId);

        filterChain.doFilter(request, response);
    }
}