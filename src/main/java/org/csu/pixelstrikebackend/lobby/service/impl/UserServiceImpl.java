package org.csu.pixelstrikebackend.lobby.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.User;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.UserMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.FileStorageService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// 添加这些 import
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;
@Service("userService")
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private FileStorageService fileStorageService; // 注入文件存储服务

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
    public CommonResponse<?> updateNickname(Integer userId, String newNickname) {
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            return CommonResponse.createForError("用户资料不存在");
        }
        // 校验昵称长度
        if (newNickname == null || newNickname.length() < 2 || newNickname.length() > 20) {
            return CommonResponse.createForError("昵称长度必须在2到20位之间");
        }
        userProfile.setNickname(newNickname);
        userProfileMapper.updateById(userProfile);
        return CommonResponse.createForSuccess("昵称更新成功", userProfile);
    }

    @Override
    public Mono<CommonResponse<?>> updateAvatar(Integer userId, FilePart file) { // 注意返回类型
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) {
            // 对于同步错误，可以直接返回一个包含错误的Mono
            return Mono.just(CommonResponse.createForError("用户资料不存在"));
        }

        // 1. 调用非阻塞的 storeFile 方法，它返回一个 Mono<String>
        return fileStorageService.storeFile(file)
                .flatMap(fileName -> {
                    // 2. 构建完整的、可在浏览器访问的URL
                    String fullUrl = "http://localhost:8080/uploads/" + fileName;

                    // 3. 将完整的URL存入数据库
                    userProfile.setAvatarUrl(fullUrl);
                    userProfileMapper.updateById(userProfile);

                    // 4. 将包含完整URL的userProfile对象返回给前端
                    return Mono.just(CommonResponse.createForSuccess("头像上传成功", userProfile));
                });
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
