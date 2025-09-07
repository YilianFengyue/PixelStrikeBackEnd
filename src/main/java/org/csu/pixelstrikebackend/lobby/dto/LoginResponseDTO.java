package org.csu.pixelstrikebackend.lobby.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private UserProfile userProfile;
    private Long activeGameId; // 玩家当前所在的游戏ID，如果不在游戏中则为null
}
