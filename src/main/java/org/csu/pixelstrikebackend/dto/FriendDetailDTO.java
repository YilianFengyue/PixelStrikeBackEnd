package org.csu.pixelstrikebackend.dto;

import lombok.Data;
import org.csu.pixelstrikebackend.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;

@Data
public class FriendDetailDTO {
    // 包含好友的完整个人资料
    private UserProfile profile;
    // 好友的在线状态
    private UserStatus onlineStatus;
}