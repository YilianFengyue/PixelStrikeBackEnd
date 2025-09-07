package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class GameStateSystem {

    private final GameConfig gameConfig;

    public GameStateSystem(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    public void update(Map<String, PlayerState> playerStates, Map<String, Long> deadPlayerTimers) {
        // 将死亡玩家加入计时器
        for (PlayerState player : playerStates.values()) {
            if (player.getCurrentAction() == PlayerState.PlayerActionState.DEAD && !deadPlayerTimers.containsKey(player.getPlayerId())) {
                deadPlayerTimers.put(player.getPlayerId(), System.currentTimeMillis() + gameConfig.getPlayer().getRespawnTimeMs());
            }
        }

        // 处理复活
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = deadPlayerTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime >= entry.getValue()) {
                PlayerState playerToRespawn = playerStates.get(entry.getKey());
                if (playerToRespawn != null) {
                    respawnPlayer(playerToRespawn);
                }
                iterator.remove();
            }
        }
    }

    private void respawnPlayer(PlayerState player) {
        player.setHealth(gameConfig.getPlayer().getMaxHealth());
        player.setAmmo(gameConfig.getPlayer().getInitialAmmo());
        player.setCurrentAction(PlayerState.PlayerActionState.IDLE);
        player.setX(100); // 重置到出生点
        player.setY(100);
        player.setVelocityX(0);
        player.setVelocityY(0);
    }
}