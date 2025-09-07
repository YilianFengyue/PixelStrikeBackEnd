package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

@Component
public class PhysicsSystem {

    private final GameConfig gameConfig;
    private final MapData mapData;

    public PhysicsSystem(GameConfig gameConfig, MapData mapData) {
        this.gameConfig = gameConfig;
        this.mapData = mapData;
    }

    public void update(Map<String, PlayerState> playerStates, List<GameEvent> events) {
        final double PLAYER_BBOX_WIDTH = 86.0;
        final double PLAYER_BBOX_HEIGHT = 160.0;

        for (PlayerState player : playerStates.values()) {
            if (player.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
                continue;
            }

            // 在每一帧开始时，先假设玩家在空中
            player.setOnGround(false);

            // 记录更新前的位置
            double previousY = player.getY();

            // 1. 应用重力
            player.setVelocityY(player.getVelocityY() + gameConfig.getPhysics().getGravity());

            // 2. 应用速度更新位置
            player.setX(player.getX() + player.getVelocityX());
            player.setY(player.getY() + player.getVelocityY());

            // 3. 模拟空气阻力/摩擦力
            player.setVelocityX(player.getVelocityX() * 0.95);

            // 4. 平台碰撞检测 (【核心新增逻辑】)
            boolean onPlatform = false;
            if (player.getVelocityY() >= 0) { // 只在玩家下落或静止时检测
                for (Rectangle2D.Double platform : mapData.getPlatforms()) {
                    // 【核心修正2】: 使用更健壮的AABB(轴对齐包围盒)碰撞检测
                    // 玩家的包围盒
                    Rectangle2D.Double playerBox = new Rectangle2D.Double(player.getX(), player.getY(), PLAYER_BBOX_WIDTH, PLAYER_BBOX_HEIGHT);

                    // 上一帧玩家的脚底
                    double previousFeetY = previousY + PLAYER_BBOX_HEIGHT;

                    // 检查X轴重叠 和 Y轴上玩家是否“穿过”平台顶面
                    if (playerBox.intersects(platform) && previousFeetY <= platform.y) {
                        player.setY(platform.y - PLAYER_BBOX_HEIGHT); // 将玩家的“头顶”放在平台表面减去自身高度的位置
                        player.setVelocityY(0);
                        player.setCanDoubleJump(true); // 落在平台上可以二段跳
                        onPlatform = true;
                        player.setOnGround(true);
                        break;
                    }
                }
            }

            // 5. 地面检测 (只有当玩家不在平台上时才进行)
            if (!onPlatform && player.getY() >= gameConfig.getPhysics().getGroundY()) {
                player.setY(gameConfig.getPhysics().getGroundY());
                player.setVelocityY(0);
                player.setCanDoubleJump(true);
                player.setOnGround(true);
            }

            // 6. 掉落死亡检测
            if (player.getY() > gameConfig.getPhysics().getDeathZoneY()) {
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