package org.csu.pixelstrikebackend.lobby.controller;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.MatchHistoryDTO;
import org.csu.pixelstrikebackend.lobby.dto.MatchResultDTO;
import org.csu.pixelstrikebackend.lobby.mapper.MatchParticipantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

@RestController
@RequestMapping("/matches") // 所有与对局历史相关的API都在这里
public class MatchController {

    @Autowired
    private MatchParticipantMapper matchParticipantMapper;

    // 查询上一次比赛的战绩(比赛刚结束时用)
    @GetMapping("/{matchId}/results")
    public CommonResponse<List<MatchResultDTO>> getMatchResults(@PathVariable Long matchId) {
        List<MatchResultDTO> results = matchParticipantMapper.selectMatchResultsWithNickname(matchId);
        if (results == null || results.isEmpty()) {
            return CommonResponse.createForError("未找到该对局的战绩");
        }
        return CommonResponse.createForSuccess("查询成功", results);
    }

    // 查看历史战绩, 即完整对战历史
    @GetMapping("/me")
    public CommonResponse<List<MatchHistoryDTO>> getMyMatchHistory(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        List<MatchHistoryDTO> history = matchParticipantMapper.selectMatchHistoryForUser(userId);
        return CommonResponse.createForSuccess("查询成功", history);
    }
}