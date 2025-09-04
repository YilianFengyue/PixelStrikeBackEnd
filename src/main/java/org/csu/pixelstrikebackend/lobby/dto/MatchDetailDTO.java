package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MatchDetailDTO {
    private Long matchId;
    private String gameMode;
    private String mapName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<ParticipantStatsDTO> participants;
}
