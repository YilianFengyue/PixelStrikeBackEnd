package org.csu.pixelstrikebackend.lobby.controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange; // 导入这个类
import jakarta.validation.Valid;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
// 添加这个 import
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

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

    // 拆分后的接口 1: 更新昵称
    @PutMapping("/me/nickname")
    public CommonResponse<?> updateNickname(@RequestParam String newNickname, ServerWebExchange exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return userService.updateNickname(userId, newNickname);
    }

    @PostMapping("/me/avatar")
    public Mono<CommonResponse<?>> updateAvatar(@RequestPart("avatar") FilePart filePart, ServerWebExchange exchange) { // 返回类型改为 Mono<CommonResponse<?>>
        Integer userId = (Integer) exchange.getAttribute("userId");
        return userService.updateAvatar(userId, filePart); // userService 现在会返回一个 Mono
    }

    /**
     * 注销当前登录用户的账户 (需要认证)
     */
    @DeleteMapping("/me")
    public CommonResponse<?> deleteAccount(ServerWebExchange servletRequest) {
        Integer userId = (Integer) servletRequest.getAttribute("userId");
        return userService.deleteAccount(userId);
    }

    @GetMapping("/me")
    public CommonResponse<UserProfile> getMyProfile(ServerWebExchange request) {
        // 从 request 中获取拦截器存入的 userId
        Integer userId = (Integer) request.getAttribute("userId");

        // 查询并返回用户信息
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            return CommonResponse.createForError("用户不存在");
        }

        return CommonResponse.createForSuccess("获取成功", userProfile);
    }
}