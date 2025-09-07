package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.validation.Valid;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.LoginRequest;
import org.csu.pixelstrikebackend.lobby.dto.LoginResponseDTO;
import org.csu.pixelstrikebackend.lobby.dto.RegisterRequest;
import org.csu.pixelstrikebackend.lobby.entity.User;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.AuthService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @GetMapping("/demo")
    public String demo() {
        return "Hello World!";
    }

    @PostMapping("/register")
    public CommonResponse<User> register(@Valid @RequestBody RegisterRequest user) {
        return authService.register(user);
    }

    @PostMapping("/login")
    public CommonResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }

    /**
     * 用户登出接口（需要认证）
     */
    @PostMapping("/logout")
    public CommonResponse<?> logout(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return authService.logout(userId);
    }

    @GetMapping("/me")
    public CommonResponse<UserProfile> getMyProfile(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            return CommonResponse.createForError("用户不存在");
        }
        return CommonResponse.createForSuccess("获取成功", userProfile);
    }

    /**
     * 获取当前在线人数接口（可选，用于测试）
     */
    @GetMapping("/online-count")
    public CommonResponse<Integer> getOnlineCount() {
        int count = onlineUserService.getOnlineUserCount();
        return CommonResponse.createForSuccess("获取成功", count);
    }
}
