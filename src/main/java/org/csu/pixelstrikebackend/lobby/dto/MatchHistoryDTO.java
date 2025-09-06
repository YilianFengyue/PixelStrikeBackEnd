package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MatchHistoryDTO {
    private Long matchId;
    private String gameMode;
    private LocalDateTime startTime;
    private Integer kills;
    private Integer deaths;
    private Integer ranking;
}