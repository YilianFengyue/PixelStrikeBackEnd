package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomRoomDTO {
    private String roomId;
    private Integer hostId;
    private List<PlayerInRoomDTO> players;
    // ★ 修改点：将原来的 private int maxSize = 2; 修改为下面的样子 ★
    private int maxSize; // 只声明字段，不再硬编码赋值
    private String roomStatus; // e.g., "WAITING", "IN_GAME"
    private Integer mapId; // **新增**
    private String mapName; // **新增**
}
