package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;

@Component
public class InputSystem {

    private static final byte JUMP_ACTION = 1;
    private static final byte SHOOT_ACTION = 2;

    public void processCommands(Queue<UserCommand> commandQueue, Map<String, PlayerState> playerStates) {
        while (!commandQueue.isEmpty()) {
            UserCommand command = commandQueue.poll();
            if (command == null) continue;

            PlayerState player = playerStates.get(command.getPlayerId());
            if (player == null || player.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
                continue;
            }

            // 1. 重置瞬时状态
            player.setCurrentAction(PlayerState.PlayerActionState.IDLE);

            // 2. 处理移动
            if (command.getMoveInput() != 0) {
                player.setX(player.getX() + command.getMoveInput() * 5.0);
                player.setFacingRight(command.getMoveInput() > 0);
                player.setCurrentAction(PlayerState.PlayerActionState.RUN);
            }

            // 3. 处理跳跃
            if ((command.getActions() & JUMP_ACTION) != 0) {
                handleJump(player);
            }

            // 4. 处理开火
            if ((command.getActions() & SHOOT_ACTION) != 0) {
                if (player.getAmmo() > 0) {
                    player.setAmmo(player.getAmmo() - 1);
                    player.setCurrentAction(PlayerState.PlayerActionState.SHOOT);
                }
            }
        }
    }

    private void handleJump(PlayerState player) {
        final double GROUND_Y = 500.0;
        final double JUMP_STRENGTH = -15.0;

        if (player.getY() >= GROUND_Y) { // 在地面，可以起跳
            player.setVelocityY(JUMP_STRENGTH);
            player.setCanDoubleJump(true);
        } else if (player.isCanDoubleJump()) { // 在空中，且有二段跳能力
            player.setVelocityY(JUMP_STRENGTH);
            player.setCanDoubleJump(false);
        }
    }
}