package org.csu.pixelstrikebackend.game;

import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface GameLobbyBridge {

    // 大厅调用此方法，通知游戏模块准备一个新对局
    void onMatchSuccess(Long gameId, List<Integer> playerIds);

    // 游戏模块调用此方法，通知大厅模块游戏已结束并上报战绩
    void onGameConcluded(Long gameId, List<MatchParticipant> results);
}