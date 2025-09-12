package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchParticipantMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired private MatchMapper matchMapper;
    @Autowired private MatchParticipantMapper matchParticipantMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private WebSocketSessionManager webSocketSessionManager;

    /**
     * 此方法现在只负责持久化战绩和更新用户资料，不再管理在线状态。
     */
    @Override
    @Transactional
    public void processMatchResults(Long gameId, List<MatchParticipant> results) {
        System.out.println("Processing results for game " + gameId + " into database.");

        Match match = new Match();
        match.setId(gameId);
        match.setEndTime(LocalDateTime.now());
        matchMapper.updateById(match);

        for (MatchParticipant participant : results) {
            participant.setMatchId(gameId);
            matchParticipantMapper.insert(participant);

            UserProfile userProfile = userProfileMapper.selectById(participant.getUserId());
            if (userProfile != null) {
                userProfile.setTotalMatches(userProfile.getTotalMatches() + 1);
                if (participant.getRanking() != null && participant.getRanking() == 1) {
                    userProfile.setWins(userProfile.getWins() + 1);
                }
                userProfileMapper.updateById(userProfile);
            }
        }

        Map<String, Object> resultMessage = Map.of(
                "type", "game_results",
                "gameId", gameId,
                "results", results
        );
        for (MatchParticipant participant : results) {
            webSocketSessionManager.sendMessageToUser(participant.getUserId(), resultMessage);
        }
    }
}