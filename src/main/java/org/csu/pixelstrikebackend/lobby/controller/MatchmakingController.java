package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matchmaking")
public class MatchmakingController {

    @Autowired
    private MatchmakingService matchmakingService;

    /**
     * 开始匹配
     */
    @PostMapping("/start")
    public CommonResponse<?> startMatchmaking(HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return matchmakingService.startMatchmaking(userId);
    }

    /**
     * 取消匹配
     */
    @PostMapping("/cancel")
    public CommonResponse<?> cancelMatchmaking(HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return matchmakingService.cancelMatchmaking(userId);
    }
}
