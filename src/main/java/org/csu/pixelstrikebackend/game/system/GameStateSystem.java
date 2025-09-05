package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class GameStateSystem {

    private static final int PLAYER_MAX_HEALTH = 100;
    private static final int INITIAL_AMMO = 30;
    private static final long RESPAWN_TIME_MS = 3000;

    public void update(Map<String, PlayerState> playerStates, Map<String, Long> deadPlayerTimers) {
        // 将死亡玩家加入计时器
        for (PlayerState player : playerStates.values()) {
            if (player.getCurrentAction() == PlayerState.PlayerActionState.DEAD && !deadPlayerTimers.containsKey(player.getPlayerId())) {
                deadPlayerTimers.put(player.getPlayerId(), System.currentTimeMillis() + RESPAWN_TIME_MS);
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
        player.setHealth(PLAYER_MAX_HEALTH);
        player.setAmmo(INITIAL_AMMO);
        player.setCurrentAction(PlayerState.PlayerActionState.IDLE);
        player.setX(100); // 重置到出生点
        player.setY(100);
        player.setVelocityX(0);
        player.setVelocityY(0);
    }
}