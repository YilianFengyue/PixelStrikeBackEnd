package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.validation.Valid;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/users") // 为所有接口添加 /users 前缀
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileMapper userProfileMapper;
    /**
     * 重置密码接口 (无需认证)
     */
    @PostMapping("/reset-password")
    public CommonResponse<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return userService.resetPassword(request);
    }

    /**
     * 更新当前登录用户的个人信息 (需要认证)
     */
    @PutMapping("/me")
    public CommonResponse<UserProfile> updateUserProfile(@Valid @RequestBody UpdateProfileRequest request, ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return userService.updateUserProfile(userId, request);
    }

    /**
     * 注销当前登录用户的账户 (需要认证)
     */
    @DeleteMapping("/me")
    public CommonResponse<?> deleteAccount(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return userService.deleteAccount(userId);
    }

    @GetMapping("/me")
    public CommonResponse<UserProfile> getMyProfile(ServerWebExchange exchange) {
        // 从 request 中获取拦截器存入的 userId
        Integer userId = exchange.getAttribute("userId");

        // 查询并返回用户信息
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            return CommonResponse.createForError("用户不存在");
        }

        return CommonResponse.createForSuccess("获取成功", userProfile);
    }
}