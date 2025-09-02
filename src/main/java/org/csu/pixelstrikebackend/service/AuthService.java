package org.csu.pixelstrikebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.common.CommonResponse;
import org.csu.pixelstrikebackend.dto.LoginRequest;
import org.csu.pixelstrikebackend.dto.RegisterRequest;
import org.csu.pixelstrikebackend.entity.User;
import org.csu.pixelstrikebackend.entity.UserProfile;
import org.csu.pixelstrikebackend.mapper.UserMapper;
import org.csu.pixelstrikebackend.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service("authService")
public class AuthService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OnlineUserService onlineUserService; // 新增注入

    @Transactional // 开启事务，确保两个表的插入操作要么都成功，要么都失败
    public CommonResponse<User> register(RegisterRequest request) {
        // 检查用户名是否存在
        if (userMapper.exists(new QueryWrapper<User>().eq("username", request.getUsername()))) {
            return CommonResponse.createForError("注册失败，用户名重复");
        }

        // 检查邮箱是否存在 [cite: 3]
        if (userMapper.exists(new QueryWrapper<User>().eq("email", request.getEmail()))) {
            return CommonResponse.createForError("注册失败，邮箱重复");
        }

        // 1. 创建并保存 User 对象
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        //user.setStatus("1"); // 初始状态为离线 [cite: 3]
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user); // 插入后，user 对象的 id 会被自动填充

        // 2. 创建并保存 UserProfile 对象
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(user.getId()); // 使用刚插入的 user 的 id
        userProfile.setNickname(request.getNickname());
        userProfile.setTotalMatches(0); // 默认为 0 [cite: 6]
        userProfile.setWins(0); // 默认为 0 [cite: 6]
        userProfileMapper.insert(userProfile);

        return CommonResponse.createForSuccessMessage("注册成功");
    }

    /**
     * 用户登录逻辑
     * @param request 包含用户名和密码的请求体
     * @return 包含 Token 的响应或错误信息
     */
    public CommonResponse<String> login(LoginRequest request) {
        // 1. 根据用户名查询用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", request.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        // 2. 检查用户是否存在
        if (user == null) {
            return CommonResponse.createForError("登录失败，用户名或密码错误");
        }

        // 3. 验证密码
        // user.getPassword() 获取的是数据库中加密后的密码
        // passwordEncoder.matches 会将请求的明文密码加密后与数据库密码进行比对
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return CommonResponse.createForError("登录失败，用户名或密码错误");
        }

        // 检查用户是否已在线，如果需要禁止多端登录，可以在这里处理
        if (onlineUserService.isUserOnline(user.getId())) {
            // 根据游戏策略，可以选择踢掉旧的连接或禁止新的登录
            // 这里我们先简单返回一个提示
            return CommonResponse.createForError("登录失败，该账号已在别处登录");
        }
        //将用户添加到在线列表
        onlineUserService.addUser(user.getId());

        // 4. 登录成功，生成 JWT Token
        String token = JwtUtil.generateToken(user.getId());
        if (token == null) {
            return CommonResponse.createForError("登录失败，Token 生成异常");
        }


        return CommonResponse.createForSuccess("登录成功", token);
    }

    /**
     * 用户登出逻辑
     * @param userId 要登出的用户ID
     * @return 登出结果
     */
    public CommonResponse<?> logout(Integer userId) {
        if (!onlineUserService.isUserOnline(userId)) {
            return CommonResponse.createForError("用户未登录，无需登出");
        }
        onlineUserService.removeUser(userId);
        return CommonResponse.createForSuccessMessage("登出成功");
    }
}
