// src/main/java/org/csu/pixelstrikebackend/game/service/GameLoopService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameLoopService {

    @Autowired private GameConfig gameConfig;
    @Autowired private PlayerStateManager playerStateManager;
    @Autowired private GameSessionManager gameSessionManager;
    @Autowired private GameManager gameManager;
    
    private ScheduledExecutorService gameLoopExecutor;
    private final ObjectMapper mapper = new ObjectMapper();


    @PostConstruct
    public void init() {
        gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
        gameLoopExecutor.scheduleAtFixedRate(this::gameTick, 0, gameConfig.getEngine().getTickRateMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (gameLoopExecutor != null) {
            gameLoopExecutor.shutdown();
        }
    }

    private void gameTick() {
        try {
            long now = System.currentTimeMillis();

            // --- 处理复活逻辑 (保持不变) ---
            handleRespawns(now);

            // --- 新增：处理游戏结束逻辑 ---
            checkGameOverConditions(now);

        } catch (Exception e) {
            System.err.println("Error in game tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleRespawns(long now) {
        long respawnDelay = gameConfig.getPlayer().getRespawnTimeMs();
        Map<Integer, Long> deathTimestamps = playerStateManager.getDeathTimestamps();
        for (Map.Entry<Integer, Long> entry : new ConcurrentHashMap<>(deathTimestamps).entrySet()) {
            if (now - entry.getValue() >= respawnDelay) {
                respawnPlayer(entry.getKey());
            }
        }
    }

    // --- ★ 新增：检查游戏结束的核心方法 ---
    private void checkGameOverConditions(long now) {
        // 获取所有正在进行的游戏
        Map<Long, GameManager.ActiveGame> activeGames = gameManager.getActiveGames();
        if (activeGames.isEmpty()) return;

        int killsToWin = gameConfig.getRules().getKillsToWin();
        long maxDuration = gameConfig.getRules().getMaxDurationMs();

        // 遍历每一个游戏实例
        for (GameManager.ActiveGame game : activeGames.values()) {
            boolean timeIsUp = (now - game.startTime) >= maxDuration;
            boolean scoreReached = false;

            // 检查是否有玩家达到胜利分数
            for (Integer playerId : game.playerIds) {
                if (playerStateManager.getStats(playerId).get("kills") >= killsToWin) {
                    scoreReached = true;
                    break;
                }
            }

            // 如果满足任一结束条件，则结束游戏
            if (timeIsUp || scoreReached) {
                System.out.println("Game " + game.gameId + " is over. Reason: " + (timeIsUp ? "Time is up" : "Score reached"));
                endGame(game);
            }
        }
    }

    // --- ★ 新增：结束游戏并处理结果的方法 ---
    private void endGame(GameManager.ActiveGame game) {
        // 1. 收集所有玩家的最终战绩
        List<MatchParticipant> results = new ArrayList<>();
        for (Integer playerId : game.playerIds) {
            Map<String, Integer> stats = playerStateManager.getStats(playerId);
            MatchParticipant p = new MatchParticipant();
            p.setUserId(playerId);
            p.setMatchId(game.gameId);
            p.setKills(stats.get("kills"));
            p.setDeaths(stats.get("deaths"));
            results.add(p);
        }

        // 2. 根据击杀数进行排名
        results.sort(Comparator.comparing(MatchParticipant::getKills).reversed());
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRanking(i + 1);
        }

        // 3. 通知 GameManager 将结果上报给大厅模块
        gameManager.onGameConcluded(game.gameId, results);

        // 4. 向所有客户端广播游戏结束的消息
        ObjectNode gameOverMsg = mapper.createObjectNode();
        gameOverMsg.put("type", "game_over");
        gameOverMsg.put("gameId", game.gameId);
        // 你可以在这里附带上最终的排名结果
        // gameOverMsg.set("results", mapper.valueToTree(results));

        gameSessionManager.broadcast(gameOverMsg.toString());
    }
    
    private void respawnPlayer(Integer userId) {
        System.out.println("Respawning player " + userId);
        playerStateManager.respawnPlayer(userId);

        double spawnX = 500;
        double spawnY = gameConfig.getPhysics().getGroundY() - 128; // 使用配置

        ObjectNode respawnMsg = mapper.createObjectNode();
        respawnMsg.put("type", "respawn");
        respawnMsg.put("id", userId);
        respawnMsg.put("x", spawnX);
        respawnMsg.put("y", spawnY);
        respawnMsg.put("hp", gameConfig.getPlayer().getMaxHealth());
        respawnMsg.put("serverTime", System.currentTimeMillis());

        gameSessionManager.broadcast(respawnMsg.toString());
    }
}