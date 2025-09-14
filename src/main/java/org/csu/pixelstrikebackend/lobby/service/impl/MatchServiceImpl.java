package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.FriendMapper;
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

    @Autowired private MatchMapper matchMapper;
    @Autowired private MatchParticipantMapper matchParticipantMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private WebSocketSessionManager webSocketSessionManager;
    @Autowired private FriendMapper friendMapper;
    @Autowired private OnlineUserService onlineUserService;
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
            onlineUserService.updateUserStatus(participant.getUserId(), UserStatus.ONLINE);
            notifyFriendsAboutStatusChange(participant.getUserId(), "ONLINE");
            webSocketSessionManager.sendMessageToUser(participant.getUserId(), resultMessage);
        }
    }

    /**
     * 辅助方法，用于通知好友状态变更
     * @param userId 状态变更的用户ID
     * @param status 新的状态 (例如 "IN_GAME")
     */
    private void notifyFriendsAboutStatusChange(Integer userId, String status) {
        List<UserProfile> friends = friendMapper.selectFriendsProfiles(userId);
        if (friends.isEmpty()) {
            return;
        }

        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) return;

        Map<String, Object> notification = Map.of(
                "type", "status_update",
                "userId", userId,
                "nickname", userProfile.getNickname(), // **新增 nickname 字段**
                "status", status
        );

        System.out.println("Notifying online friends of user " + userId + " about status change to " + status);
        for (UserProfile friend : friends) {
            if (onlineUserService.isUserOnline(friend.getUserId())) {
                webSocketSessionManager.sendMessageToUser(friend.getUserId(), notification);
            }
        }
    }

}