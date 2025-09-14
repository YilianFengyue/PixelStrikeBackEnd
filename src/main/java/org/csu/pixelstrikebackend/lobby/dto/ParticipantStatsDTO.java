package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

@Data
public class ParticipantStatsDTO {
    private Integer userId;
    private String nickname;
    private Integer kills;
    private String characterName;
    private Integer deaths;
    private Integer ranking;
}
