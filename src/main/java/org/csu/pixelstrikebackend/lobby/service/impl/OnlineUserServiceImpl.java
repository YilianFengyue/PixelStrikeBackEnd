package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service("onlineUserService")
public class OnlineUserServiceImpl implements OnlineUserService {

    // 使用线程安全的 ConcurrentHashMap，Key: userId, Value: UserStatus 枚举
    private final ConcurrentHashMap<Integer, UserStatus> onlineUsers = new ConcurrentHashMap<>();

    @Override
    public void addUser(Integer userId) {
        // 用户登录后，默认状态为 ONLINE
        onlineUsers.put(userId, UserStatus.ONLINE);
        System.out.println("User " + userId + " is online with status " + UserStatus.ONLINE +
                ". Current online count: " + onlineUsers.size());
        logOnlineUsers();
    }

    @Override
    public void removeUser(Integer userId) {
        if (onlineUsers.containsKey(userId)) {
            onlineUsers.remove(userId);
            System.out.println("User " + userId + " is offline. Current online count: " + onlineUsers.size());
        }
        logOnlineUsers();
    }

    @Override
    public boolean isUserOnline(Integer userId) {
        logOnlineUsers();
        return onlineUsers.containsKey(userId);
    }

    @Override
    public int getOnlineUserCount() {
        logOnlineUsers();
        return onlineUsers.size();
    }

    @Override
    public void updateUserStatus(Integer userId, UserStatus status) {
        if (onlineUsers.containsKey(userId)) {
            onlineUsers.put(userId, status);
            System.out.println("Updated status for user " + userId + " to " + status);
        } else {
            System.out.println("Failed to update status. User " + userId + " is not online.");
        }
        logOnlineUsers();
    }

    @Override
    public UserStatus getUserStatus(Integer userId) {
        logOnlineUsers();
        return onlineUsers.get(userId);
    }

    /**
     * 私有辅助方法，用于打印当前在线用户列表的状态
     */
    private void logOnlineUsers() {
        System.out.println("--- Online User Log ---");
        if (onlineUsers.isEmpty()) {
            System.out.println("Online users: [empty]");
        } else {
            System.out.println("Online users: " + onlineUsers);
        }
        System.out.println("-----------------------");
    }
}