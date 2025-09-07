package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GameConditionSystem {

    private final GameConfig gameConfig; // 声明为 final

    // 使用构造器注入
    public GameConditionSystem(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }
    /**
     * 检查游戏是否应该结束。
     * @param playerStates 当前所有玩家的状态
     * @param gameStartTime 游戏开始时的时间戳 (System.currentTimeMillis())
     * @return 如果游戏应该结束，则返回 true
     */
    public boolean shouldGameEnd(Map<String, PlayerState> playerStates, long gameStartTime) {
        // 1. 检查是否达到胜利击杀数
        for (PlayerState player : playerStates.values()) {
            if (player.getKills() >= gameConfig.getRules().getKillsToWin()) {
                System.out.println("游戏结束: 玩家 " + player.getPlayerId() + " 达到了 " + gameConfig.getRules().getKillsToWin() + " 次击杀。");
                return true;
            }
        }

        // 2. 检查是否达到时间上限
        if (System.currentTimeMillis() - gameStartTime > gameConfig.getRules().getMaxDurationMs()) {
            System.out.println("游戏结束: 已达到 " + (gameConfig.getRules().getMaxDurationMs() / 60000) + " 分钟时间上限。");
            return true;
        }

        return false;
    }
}