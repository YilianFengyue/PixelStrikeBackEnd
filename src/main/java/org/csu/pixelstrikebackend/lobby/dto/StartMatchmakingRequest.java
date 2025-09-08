package org.csu.pixelstrikebackend.lobby.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartMatchmakingRequest {

    //@NotNull(message = "必须选择一张地图")
    private Integer mapId;

    //@NotNull(message = "必须选择一个角色")
    private Integer characterId;
}
