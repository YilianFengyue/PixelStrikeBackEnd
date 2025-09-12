package org.csu.pixelstrikebackend.lobby.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.game.service.GameManager;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.LoginRequest;
import org.csu.pixelstrikebackend.lobby.dto.LoginResponseDTO;
import org.csu.pixelstrikebackend.lobby.dto.RegisterRequest;
import org.csu.pixelstrikebackend.lobby.entity.User;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.FriendMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.util.JwtUtil;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("authService")
public class AuthService {
    @Autowired private UserMapper userMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OnlineUserService onlineUserService;
    @Autowired private WebSocketSessionManager webSocketSessionManager;
    @Autowired private FriendMapper friendMapper;
    @Autowired private PlayerSessionService playerSessionService;
    @Autowired private GameManager gameManager;

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
    public CommonResponse<LoginResponseDTO> login(LoginRequest request) {
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

        Long activeGameId = playerSessionService.getActiveGameId(user.getId());
        if (activeGameId != null) {
            // 通过 GameManager 检查这个游戏ID是否还在活跃游戏列表中
            if (!gameManager.getActiveGames().containsKey(activeGameId)) {
                // 如果游戏已经不存在了（说明已结束），则清理该玩家的会话并重置 activeGameId
                System.out.println("Player " + user.getId() + " is trying to reconnect to a concluded game " + activeGameId + ". Cleaning up session.");
                playerSessionService.removePlayerFromGame(user.getId());
                activeGameId = null; // 重置为 null，让他正常进入大厅
            }
        }
        // 如果玩家不在游戏中，执行“是否已在别处登录”的检查
        if (activeGameId == null && onlineUserService.isUserOnline(user.getId())) {
            return CommonResponse.createForError("登录失败，该账号已在别处登录");
        }
        //将用户添加到在线列表
        onlineUserService.addUser(user.getId());

        // 4. 登录成功，生成 JWT Token
        String token = JwtUtil.generateToken(user.getId());
        if (token == null) {
            return CommonResponse.createForError("登录失败，Token 生成异常");
        }
        UserProfile userProfile = userProfileMapper.selectById(user.getId());

        LoginResponseDTO loginResponse = new LoginResponseDTO();
        loginResponse.setToken(token);
        loginResponse.setUserProfile(userProfile);
        loginResponse.setActiveGameId(activeGameId);


        notifyFriendsAboutStatusChange(user.getId(), "ONLINE");
        return CommonResponse.createForSuccess("登录成功", loginResponse);
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
        // **新增：通知好友该用户已下线**
        notifyFriendsAboutStatusChange(userId, "OFFLINE");

        return CommonResponse.createForSuccessMessage("登出成功");
    }

    /**
     * 辅助方法，用于通知好友状态变更
     * @param userId 状态变更的用户ID
     * @param status 新的状态 (例如 "ONLINE", "OFFLINE")
     */
    private void notifyFriendsAboutStatusChange(Integer userId, String status) {
        // 1. 查找该用户的所有好友
        List<UserProfile> friends = friendMapper.selectFriendsProfiles(userId);
        if (friends.isEmpty()) {
            return;
        }

        // **核心改动1：获取状态变更用户的昵称**
        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) return; // 如果找不到用户信息，则不发送


        // 2. 构建通知消息体
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "status_update"); // 消息类型
        notification.put("userId", userId); // 哪个用户状态变了
        notification.put("nickname", userProfile.getNickname()); // **新增 nickname 字段**
        notification.put("status", status); // 新的状态

        // 3. 遍历好友，逐个发送 WebSocket 消息
        System.out.println("Notifying friends of user " + userId + " about status change to " + status);
        for (UserProfile friend : friends) {
            if (onlineUserService.isUserOnline(friend.getUserId())) {
                webSocketSessionManager.sendMessageToUser(friend.getUserId(), notification);
            }
        }
    }
}
