// src/main/java/org/csu/pixelstrikebackend/game/system/CombatSystem.java

package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CombatSystem {

    private final GameConfig gameConfig;

    public CombatSystem(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    public void update(Map<String, PlayerState> playerStates, List<GameEvent> events) {
        for (PlayerState attacker : playerStates.values()) {
            if (attacker.getCurrentAction() != PlayerState.PlayerActionState.SHOOT) {
                continue;
            }

            // 【核心修正】：射击后，将状态恢复为一个中性状态，让InputSystem在下一帧决定是IDLE还是FALL
            // 这样就不会错误地覆盖掉RUNNING状态
            attacker.setCurrentAction(PlayerState.PlayerActionState.IDLE);


            for (PlayerState target : playerStates.values()) {
                if (isHit(attacker, target)) {
                    applyDamageAndKnockback(attacker, target, events);
                    // 扣除弹药的操作应该在这里，因为确实发生了一次射击
                    if(attacker.getAmmo() > 0) {
                        attacker.setAmmo(attacker.getAmmo() - 1);
                    }
                    break;
                }
            }
        }
    }

    private void applyDamageAndKnockback(PlayerState attacker, PlayerState target, List<GameEvent> events) {
        target.setHealth(target.getHealth() - gameConfig.getWeapon().getDamage());
        target.setCurrentAction(PlayerState.PlayerActionState.HIT);

        double knockbackStrength = 5.0;
        target.setVelocityX(target.getVelocityX() + (attacker.isFacingRight() ? knockbackStrength : -knockbackStrength));
        target.setVelocityY(target.getVelocityY() - 10.0);

        events.add(createGameEvent(GameEvent.EventType.PLAYER_HIT, target.getPlayerId()));

        if (target.getHealth() <= 0) {
            target.setCurrentAction(PlayerState.PlayerActionState.DEAD);
            attacker.setKills(attacker.getKills() + 1);
            target.setDeaths(target.getDeaths() + 1);
            events.add(createGameEvent(GameEvent.EventType.PLAYER_DIED, target.getPlayerId()));
        }
    }

    private boolean isHit(PlayerState attacker, PlayerState target) {
        if (target.getPlayerId().equals(attacker.getPlayerId()) || target.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
            return false;
        }
        boolean facingTarget = (attacker.isFacingRight() && target.getX() > attacker.getX()) ||
                (!attacker.isFacingRight() && target.getX() < attacker.getX());
        if (!facingTarget) {
            return false;
        }
        return Math.abs(attacker.getY() - target.getY()) < gameConfig.getPlayer().getHeight();
    }

    private GameEvent createGameEvent(GameEvent.EventType type, String relatedPlayerId) {
        GameEvent event = new GameEvent();
        event.setType(type);
        event.setRelatedPlayerId(relatedPlayerId);
        return event;
    }
}