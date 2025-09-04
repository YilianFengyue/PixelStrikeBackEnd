package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MatchHistoryDTO {
    private Long matchId;
    private String gameMode;
    private String mapName;
    private LocalDateTime startTime;
    private Integer ranking; // 玩家在该局的排名
}
