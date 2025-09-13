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
        private int maxHealth;
        private long respawnTimeMs;
    }

    @Data
    public static class Physics {
        private double mapW;
        private double mapH;
        private double groundY;
        private double deathZoneY;
    }

    @Data
    public static class Engine {
        private long tickRateMs; // Tick 频率
    }
}