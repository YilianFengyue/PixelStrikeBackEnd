// 文件: src/main/java/org/csu/pixelstrikebackend/demogame/service/GameManager.java
package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // 导入 @Lazy
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameManager implements GameLobbyBridge {

    private final GameRoomService gameRoomService;
    private final MatchService matchService;

    // 使用构造器注入
    @Autowired
    public GameManager(@Lazy GameRoomService gameRoomService, MatchService matchService) {
        this.gameRoomService = gameRoomService;
        this.matchService = matchService;
    }

    @Override
    public void onMatchSuccess(Long gameId, List<Integer> playerIds) {
        gameRoomService.prepareGame(gameId, playerIds);
    }

    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        matchService.processMatchResults(gameId, results);
    }
}