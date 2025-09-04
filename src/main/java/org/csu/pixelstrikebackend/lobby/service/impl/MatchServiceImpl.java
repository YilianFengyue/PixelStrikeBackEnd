package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchParticipantMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private MatchParticipantMapper matchParticipantMapper;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private OnlineUserService onlineUserService;
    @Autowired
    private WebSocketSessionManager webSocketSessionManager;

    @Override
    @Transactional // 保证所有数据库操作要么全部成功，要么全部失败
    public void processMatchResults(Long gameId, List<MatchParticipant> results) {
        // 1. 更新 matches 表，记录结束时间
        Match match = new Match();
        match.setId(gameId);
        match.setEndTime(LocalDateTime.now());
        matchMapper.updateById(match);

        // 2. 批量插入 match_participants 表，记录每个人的战绩
        for (MatchParticipant participant : results) {
            participant.setMatchId(gameId);
            matchParticipantMapper.insert(participant);

            // 3. 更新 user_profiles 表，增加总场次和胜利场次
            UserProfile userProfile = userProfileMapper.selectById(participant.getUserId());
            if (userProfile != null) {
                userProfile.setTotalMatches(userProfile.getTotalMatches() + 1);
                if (participant.getRanking() == 1) { // 假设排名第一为胜利
                    userProfile.setWins(userProfile.getWins() + 1);
                }
                userProfileMapper.updateById(userProfile);
            }

            // 4. 更新玩家在线状态为 ONLINE
            onlineUserService.updateUserStatus(participant.getUserId(), UserStatus.ONLINE);
        }

        // 5. 通过大厅WebSocket(/ws)向所有参与者广播最终战绩
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
