package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomRoomDTO {
    private String roomId;
    private Integer hostId;
    private List<PlayerInRoomDTO> players;
    private int maxSize = 2; // 和匹配房间保持一致
    private String roomStatus; // e.g., "WAITING", "IN_GAME"
    private Integer mapId; // **新增**
    private String mapName; // **新增**
}
