package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.validation.Valid;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.StartMatchmakingRequest;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/matchmaking")
public class MatchmakingController {

    @Autowired
    private MatchmakingService matchmakingService;

    /**
     * 开始匹配
     */
    @PostMapping("/start")
    public CommonResponse<?> startMatchmaking(@RequestBody(required = false) StartMatchmakingRequest matchmakingRequest, ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");

        // **新增：设置默认值**
        int mapIdToUse = 4; // 默认地图ID为1
        int characterIdToUse = 1; // 默认角色ID为1

        // **新增：如果前端提供了参数，则使用前端的参数**
        if (matchmakingRequest != null) {
            if (matchmakingRequest.getMapId() != null) {
                mapIdToUse = matchmakingRequest.getMapId();
            }
            if (matchmakingRequest.getCharacterId() != null) {
                characterIdToUse = matchmakingRequest.getCharacterId();
            }
        }

        return matchmakingService.startMatchmaking(
                userId,
                mapIdToUse,
                characterIdToUse
        );
    }

    /**
     * 取消匹配
     */
    @PostMapping("/cancel")
    public CommonResponse<?> cancelMatchmaking(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return matchmakingService.cancelMatchmaking(userId);
    }
}
