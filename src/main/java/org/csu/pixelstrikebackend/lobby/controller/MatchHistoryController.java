package org.csu.pixelstrikebackend.lobby.controller;
import org.springframework.web.server.ServerWebExchange; // 导入这个类
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.service.MatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
public class MatchHistoryController {

    @Autowired
    private MatchHistoryService matchHistoryService;

    @GetMapping
    public CommonResponse<?> getMatchHistory(ServerWebExchange request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return matchHistoryService.getMatchHistory(userId);
    }

    @GetMapping("/{matchId}")
    public CommonResponse<?> getMatchDetails(@PathVariable Long matchId) {
        return matchHistoryService.getMatchDetails(matchId);
    }
}
