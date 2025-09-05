package org.csu.pixelstrikebackend.dto;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlayerState {

    private String playerId;

    // 服务器权威的位置和速度
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;

    private int health;
    private int currentWeaponId;

    /**
     * 当前武器的剩余弹药。
     */
    private int ammo;

    /**
     * 本局游戏中的击杀数。
     */
    private int kills = 0;

    /**
     * 本局游戏中的死亡数。
     */
    private int deaths = 0;

    /**
     * 角色是否朝向右边。
     * 客户端用这个布尔值来决定是否需要水平翻转角色的精灵图。
     */
    private boolean isFacingRight;

    /**
     * 玩家当前的动画状态。
     * 这是一个关键字段，用于告诉客户端C(Dev5)应该播放哪个动画。
     * 例如：IDLE, RUN, JUMP, FALL, SHOOT, HIT, DEAD
     */
    private PlayerActionState currentAction;

    // 二段跳属性
    private boolean canDoubleJump = false;

    public PlayerState(PlayerState original) {
        this.playerId = original.playerId;
        this.x = original.x;
        this.y = original.y;
        this.velocityX = original.velocityX;
        this.velocityY = original.velocityY;
        this.health = original.health;
        this.currentWeaponId = original.currentWeaponId;
        this.ammo = original.ammo;
        this.kills = original.kills;
        this.deaths = original.deaths;
        this.isFacingRight = original.isFacingRight;
        this.currentAction = original.currentAction;
        this.canDoubleJump = original.canDoubleJump;
    }

    public enum PlayerActionState {
        IDLE, RUN, JUMP, FALL, SHOOT, HIT, DEAD
    }
}