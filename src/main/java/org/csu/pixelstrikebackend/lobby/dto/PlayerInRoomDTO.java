package org.csu.pixelstrikebackend.lobby.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInRoomDTO {
    private Integer userId;
    private String nickname;
    private boolean isHost;
    private Integer characterId;     // **新增**
    private String characterName;    // **新增**
}
