package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PhysicsSystem {

    private static final double GRAVITY = 0.8;
    private static final double GROUND_Y = 500.0;
    private static final double DEATH_ZONE_Y = 1200.0;

    public void update(Map<String, PlayerState> playerStates, List<GameEvent> events) {
        for (PlayerState player : playerStates.values()) {
            // 1. 应用重力
            player.setVelocityY(player.getVelocityY() + GRAVITY);

            // 2. 应用速度更新位置
            player.setX(player.getX() + player.getVelocityX());
            player.setY(player.getY() + player.getVelocityY());

            // 3. 模拟空气阻力/摩擦力
            player.setVelocityX(player.getVelocityX() * 0.95);

            // 4. 地面检测
            if (player.getY() >= GROUND_Y) {
                player.setY(GROUND_Y);
                player.setVelocityY(0);
            }

            // 5. 掉落死亡检测
            if (player.getY() > DEATH_ZONE_Y && player.getCurrentAction() != PlayerState.PlayerActionState.DEAD) {
                player.setHealth(0);
                player.setDeaths(player.getDeaths() + 1);
                player.setCurrentAction(PlayerState.PlayerActionState.DEAD);
                
                GameEvent dieEvent = new GameEvent();
                dieEvent.setType(GameEvent.EventType.PLAYER_DIED);
                dieEvent.setRelatedPlayerId(player.getPlayerId());
                events.add(dieEvent);
            }
        }
    }
}