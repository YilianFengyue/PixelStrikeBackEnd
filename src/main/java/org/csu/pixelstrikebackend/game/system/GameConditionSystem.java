package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GameConditionSystem {

    // 胜利条件：任一玩家击杀数达到 xxx
    private static final int KILLS_TO_WIN = 2;
    // 游戏最长持续时间：5分钟
    private static final long MAX_GAME_DURATION_MS = 5 * 60 * 1000;

    /**
     * 检查游戏是否应该结束。
     * @param playerStates 当前所有玩家的状态
     * @param gameStartTime 游戏开始时的时间戳 (System.currentTimeMillis())
     * @return 如果游戏应该结束，则返回 true
     */
    public boolean shouldGameEnd(Map<String, PlayerState> playerStates, long gameStartTime) {
        // 1. 检查是否达到胜利击杀数
        for (PlayerState player : playerStates.values()) {
            if (player.getKills() >= KILLS_TO_WIN) {
                System.out.println("游戏结束: 玩家 " + player.getPlayerId() + " 达到了 " + KILLS_TO_WIN + " 次击杀。");
                return true;
            }
        }

        // 2. 检查是否达到时间上限
        if (System.currentTimeMillis() - gameStartTime > MAX_GAME_DURATION_MS) {
            System.out.println("游戏结束: 已达到 " + (MAX_GAME_DURATION_MS / 60000) + " 分钟时间上限。");
            return true;
        }

        return false;
    }
}