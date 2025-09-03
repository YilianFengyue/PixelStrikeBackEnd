package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;

@Data
public class FriendListDTO {
    private Integer userId;
    private String nickname;
    private String avatarUrl; // 列表也通常会显示头像
    private UserStatus onlineStatus; // 在线、游戏中或 null (离线)
}