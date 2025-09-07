package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;

@Data
public class MatchResultDTO {
    private String nickname;
    private Integer kills;
    private Integer deaths;
    private Integer ranking;
}