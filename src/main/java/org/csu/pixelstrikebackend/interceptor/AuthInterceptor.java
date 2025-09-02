package org.csu.pixelstrikebackend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.csu.pixelstrikebackend.common.CommonResponse;
import org.csu.pixelstrikebackend.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头中获取 token
        String token = request.getHeader("Authorization");

        // 检查 token 是否存在且格式正确 (通常以 "Bearer " 开头)
        if (token == null || !token.startsWith("Bearer ")) {
            sendErrorResponse(response, "请求未认证，请提供 Token");
            return false; // 拦截请求
        }

        // 移除 "Bearer " 前缀，获取真正的 token
        String jwtToken = token.substring(7);

        // 验证 token
        Integer userId = JwtUtil.verifyTokenAndGetUserId(jwtToken);
        if (userId == null) {
            sendErrorResponse(response, "Token 无效或已过期");
            return false; // 拦截请求
        }

        // 将 userId 存入 request，方便后续 Controller 使用
        request.setAttribute("userId", userId);

        return true; // 放行请求
    }

    /**
     * 辅助方法，用于发送 JSON 格式的错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json;charset=UTF-8");
        CommonResponse<?> errorResponse = CommonResponse.createForError(message);
        // 使用 Jackson ObjectMapper 将对象转换为 JSON 字符串
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(errorResponse));
    }
}
