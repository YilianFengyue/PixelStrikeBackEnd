package org.csu.pixelstrikebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "game")
@Data
public class GameConfig {

    private Matchmaking matchmaking = new Matchmaking();
    private Rules rules = new Rules();
    private Player player = new Player();
    private Physics physics = new Physics();
    private Weapon weapon = new Weapon();
    private Engine engine = new Engine();

    @Data
    public static class Matchmaking {
        private int roomMaxSize; // 匹配房间大小
    }

    @Data
    public static class Rules {
        private int killsToWin; // 胜利击杀数
        private long maxDurationMs; // 游戏最长持续时间
        private int countdownSeconds; // 开局倒计时
    }

    @Data
    public static class Player {
        private int maxHealth; // 最大生命值
        private int initialAmmo; // 初始弹药
        private long respawnTimeMs; // 复活时间
        private double height; // 玩家高度
    }

    @Data
    public static class Physics {
        private double gravity; // 重力
        private double groundY; // 地面Y坐标
        private double deathZoneY; // 死亡区域Y坐标
    }
    
    @Data
    public static class Weapon {
        private int damage; // 武器伤害
    }

    @Data
    public static class Engine {
        private long tickRateMs; // Tick 频率
    }
}