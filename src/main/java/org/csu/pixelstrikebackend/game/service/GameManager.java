package org.csu.pixelstrikebackend.game.service;

import lombok.Getter;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameManager implements GameLobbyBridge {

    private final GameRoomService gameRoomService;
    private final MatchService matchService;
    @Autowired private PlayerSessionService playerSessionService;
    @Autowired private OnlineUserService onlineUserService;
    @Autowired private MatchMapper matchMapper;

    @Getter
    private final Map<Long, ActiveGame> activeGames = new ConcurrentHashMap<>();

    @Autowired
    public GameManager(@Lazy GameRoomService gameRoomService, MatchService matchService) {
        this.gameRoomService = gameRoomService;
        this.matchService = matchService;
    }
    public Match getMatchInfo(Long gameId) {
        return matchMapper.selectById(gameId);
    }
    @Override
    public void onMatchSuccess(Long gameId, Map<Integer, Integer> playerCharacterSelections) {
        List<Integer> playerIds = new ArrayList<>(playerCharacterSelections.keySet());

        activeGames.put(gameId, new ActiveGame(gameId, playerIds, playerCharacterSelections));
        playerIds.forEach(playerId -> playerSessionService.registerPlayerInGame(playerId, gameId));
        gameRoomService.prepareGame(gameId, playerIds);
    }

    /**
     * 这是游戏结束时唯一的权威状态清理点。
     */
    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        ActiveGame finishedGame = activeGames.remove(gameId);
        if (finishedGame != null) {
            System.out.println("Game " + gameId + " concluded. Authority cleanup started for players: " + finishedGame.playerIds);
            for (Integer playerId : finishedGame.playerIds) {
                // 1. 清理游戏会话
                playerSessionService.removePlayerFromGame(playerId);
                // 2. ★★★ 立即将玩家状态重置为 ONLINE ★★★
                //这里交给大厅服务器来处理，游戏服务器无需关注
                //onlineUserService.updateUserStatus(playerId, UserStatus.ONLINE);
            }
        }

        // 3. 将战绩处理任务交给 MatchService（它只负责写数据库）
        matchService.processMatchResults(gameId, results);
    }


    public static class ActiveGame {
        public final Long gameId;
        public final List<Integer> playerIds;
        public final Map<Integer, Integer> playerCharacterSelections;
        public final long startTime;

        public ActiveGame(Long gameId, List<Integer> playerIds, Map<Integer, Integer> playerCharacterSelections) { // 4. 修改构造函数
            this.gameId = gameId;
            this.playerIds = playerIds;
            this.playerCharacterSelections = playerCharacterSelections; // 5. 保存映射
            this.startTime = System.currentTimeMillis();
        }
    }
}