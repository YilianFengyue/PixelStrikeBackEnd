// src/main/java/org/csu/pixelstrikebackend/game/system/InputSystem.java

package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;

@Component
public class InputSystem {

    private static final byte JUMP_ACTION = 1;
    private static final byte SHOOT_ACTION = 2;
    private static final double MOVE_SPEED = 7.0;

    private final GameConfig gameConfig;

    public InputSystem(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    public void processCommands(Queue<UserCommand> commandQueue, Map<String, PlayerState> playerStates) {
        // 重置所有玩家的瞬时动作状态
        for (PlayerState player : playerStates.values()) {
            player.setJustJumped(false); // 假设有一个这样的字段来处理一次性事件
        }

        while (!commandQueue.isEmpty()) {
            UserCommand command = commandQueue.poll();
            if (command == null) continue;

            PlayerState player = playerStates.get(command.getPlayerId());
            if (player == null || player.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
                continue;
            }

            // 默认状态
            player.setCurrentAction(player.getY() < gameConfig.getPhysics().getGroundY() ? PlayerState.PlayerActionState.FALL : PlayerState.PlayerActionState.IDLE);

            // 处理移动
            if (command.getMoveInput() != 0) {
                player.setVelocityX(command.getMoveInput() * MOVE_SPEED);
                player.setFacingRight(command.getMoveInput() > 0);
                if (player.getY() >= gameConfig.getPhysics().getGroundY()) {
                    player.setCurrentAction(PlayerState.PlayerActionState.RUN);
                }
            }

            // 【核心修正】：处理持续的射击指令
            if ((command.getActions() & SHOOT_ACTION) != 0) {
                if (player.getAmmo() > 0) {
                    player.setCurrentAction(PlayerState.PlayerActionState.SHOOT);
                }
            }

            // 处理跳跃 (瞬时动作)
            if ((command.getActions() & JUMP_ACTION) != 0) {
                handleJump(player);
                player.setCurrentAction(PlayerState.PlayerActionState.JUMP);
            }
        }
    }

    private void handleJump(PlayerState player) {
        final double JUMP_STRENGTH = -20.0;
        if (player.getY() >= gameConfig.getPhysics().getGroundY()) {
            player.setVelocityY(JUMP_STRENGTH);
            player.setCanDoubleJump(true);
        } else if (player.isCanDoubleJump()) {
            player.setVelocityY(JUMP_STRENGTH);
            player.setCanDoubleJump(false);
        }
    }
}