package org.csu.pixelstrikebackend.game.service;

import lombok.Getter;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameManager implements GameLobbyBridge {

    private final GameRoomService gameRoomService;
    private final MatchService matchService;
    @Autowired private PlayerSessionService playerSessionService;
    @Getter
    private final Map<Long, ActiveGame> activeGames = new ConcurrentHashMap<>();

    @Autowired
    public GameManager(@Lazy GameRoomService gameRoomService, MatchService matchService) {
        this.gameRoomService = gameRoomService;
        this.matchService = matchService;
    }

    @Override
    public void onMatchSuccess(Long gameId, List<Integer> playerIds) {
        activeGames.put(gameId, new ActiveGame(gameId, playerIds));
        playerIds.forEach(playerId -> playerSessionService.registerPlayerInGame(playerId, gameId));
        gameRoomService.prepareGame(gameId, playerIds);
    }

    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        ActiveGame finishedGame = activeGames.get(gameId);
        if (finishedGame != null) {
            for (Integer playerId : finishedGame.playerIds) {
                playerSessionService.removePlayerFromGame(playerId);
                System.out.println("Cleaned up game session for player " + playerId + " from game " + gameId);
            }
        }
        activeGames.remove(gameId);
        matchService.processMatchResults(gameId, results);
    }


    public static class ActiveGame {
        public final Long gameId;
        public final List<Integer> playerIds;
        public final long startTime;

        public ActiveGame(Long gameId, List<Integer> playerIds) {
            this.gameId = gameId;
            this.playerIds = playerIds;
            this.startTime = System.currentTimeMillis();
        }
    }
}