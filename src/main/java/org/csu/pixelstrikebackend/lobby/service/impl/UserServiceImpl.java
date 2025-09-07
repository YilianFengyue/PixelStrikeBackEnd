package org.csu.pixelstrikebackend.lobby.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.User;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.UserMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("userService")
public class UserServiceImpl implements UserService {

    @Autowired private UserMapper userMapper;

    @Autowired private UserProfileMapper userProfileMapper;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private OnlineUserService onlineUserService;

    @Override
    public CommonResponse<?> resetPassword(ResetPasswordRequest request) {
        // 1. 根据用户名查找用户
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", request.getUsername()));
        if (user == null) {
            return CommonResponse.createForError("用户不存在");
        }

        // 2. 校验邮箱是否匹配
        if (!user.getEmail().equals(request.getEmail())) {
            return CommonResponse.createForError("用户名与邮箱不匹配");
        }

        // 3. 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);

        return CommonResponse.createForSuccessMessage("密码重置成功");
    }

    @Override
    public CommonResponse<UserProfile> updateUserProfile(Integer userId, UpdateProfileRequest request) {
        // 1. 查找用户的个人资料
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            return CommonResponse.createForError("用户资料不存在");
        }

        // 2. 按需更新字段
        if (request.getNickname() != null) {
            userProfile.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null) {
            userProfile.setAvatarUrl(request.getAvatarUrl());
        }

        // 3. 保存更新
        userProfileMapper.updateById(userProfile);

        return CommonResponse.createForSuccess("用户信息更新成功", userProfile);
    }

    @Override
    @Transactional
    public CommonResponse<?> deleteAccount(Integer userId) {
        // 1. 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            return CommonResponse.createForError("用户不存在");
        }

        // 2. 如果用户在线，先将其从在线列表移除
        if (onlineUserService.isUserOnline(userId)) {
            onlineUserService.removeUser(userId);
        }

        // 3. 删除用户记录 (由于外键约束设置了 ON DELETE CASCADE, user_profiles 会被自动删除)
        int result = userMapper.deleteById(userId);

        if (result > 0) {
            return CommonResponse.createForSuccessMessage("账户注销成功");
        } else {
            return CommonResponse.createForError("账户注销失败");
        }
    }



}
