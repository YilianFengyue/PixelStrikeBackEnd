package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.enums.UserStatus;

public interface OnlineUserService {
    void addUser(Integer userId);
    void removeUser(Integer userId);
    boolean isUserOnline(Integer userId);
    int getOnlineUserCount();
    void updateUserStatus(Integer userId, UserStatus status); // 新增方法
    UserStatus getUserStatus(Integer userId); // 新增方法
}