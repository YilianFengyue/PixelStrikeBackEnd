package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.springframework.stereotype.Component;

@Component
public class GameTimerSystem {

    // 从 GameConditionSystem 中获取游戏最长持续时间
    private static final long MAX_GAME_DURATION_MS = 5 * 60 * 1000;

    /**
     * 更新游戏快照中的剩余时间。
     * @param snapshot 要更新的游戏状态快照
     * @param gameStartTime 游戏开始时的时间戳 (System.currentTimeMillis())
     */
    public void update(GameStateSnapshot snapshot, long gameStartTime) {
        long elapsedTime = System.currentTimeMillis() - gameStartTime;
        long remainingTimeMs = MAX_GAME_DURATION_MS - elapsedTime;

        if (remainingTimeMs < 0) {
            remainingTimeMs = 0;
        }

        // 将剩余时间（毫秒）转换为秒，并向上取整，以获得更友好的显示效果
        int remainingSeconds = (int) Math.ceil(remainingTimeMs / 1000.0);

        snapshot.setGameTimeRemainingSeconds(remainingSeconds);
    }
}