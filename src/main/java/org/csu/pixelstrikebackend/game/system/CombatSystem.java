package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class CombatSystem {

    private static final int WEAPON_DAMAGE = 25;
    private static final double PLAYER_HEIGHT = 40.0;

    public void update(Map<String, PlayerState> playerStates, List<GameEvent> events) {
        for (PlayerState attacker : playerStates.values()) {
            // 只处理处于射击状态的玩家
            if (attacker.getCurrentAction() != PlayerState.PlayerActionState.SHOOT) {
                continue;
            }

            for (PlayerState target : playerStates.values()) {
                if (isHit(attacker, target)) {
                    applyDamageAndKnockback(attacker, target, events);
                    // 一次只能击中一个目标
                    break;
                }
            }
        }
    }

    private void applyDamageAndKnockback(PlayerState attacker, PlayerState target, List<GameEvent> events) {
        target.setHealth(target.getHealth() - WEAPON_DAMAGE);
        target.setCurrentAction(PlayerState.PlayerActionState.HIT);

        double knockbackStrength = 25.0;
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
        // 不能打自己或已死亡的玩家
        if (target.getPlayerId().equals(attacker.getPlayerId()) || target.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
            return false;
        }

        boolean facingTarget = (attacker.isFacingRight() && target.getX() > attacker.getX()) ||
                             (!attacker.isFacingRight() && target.getX() < attacker.getX());

        if (!facingTarget) {
            return false;
        }

        return Math.abs(attacker.getY() - target.getY()) < PLAYER_HEIGHT;
    }

    private GameEvent createGameEvent(GameEvent.EventType type, String relatedPlayerId) {
        GameEvent event = new GameEvent();
        event.setType(type);
        event.setRelatedPlayerId(relatedPlayerId);
        return event;
    }
}