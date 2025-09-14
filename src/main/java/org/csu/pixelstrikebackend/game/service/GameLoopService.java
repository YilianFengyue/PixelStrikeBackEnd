// src/main/java/org/csu/pixelstrikebackend/game/service/GameLoopService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.geom.HitMath;
import org.csu.pixelstrikebackend.game.model.ServerProjectile;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.util.*;
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
    @Autowired private ProjectileManager projectileManager;
    
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
            double deltaTime = gameConfig.getEngine().getTickRateMs() / 1000.0; // 转换为秒
            handleRespawns(now);
            // 更新所有子弹的位置并检查碰撞
            updateProjectiles(deltaTime);
            handlePoisonDamage(now);
            checkGameOverConditions(now);
        } catch (Exception e) {
            System.err.println("Error in game tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePoisonDamage(long now) {
        Map<Integer, Long> poisoned = playerStateManager.getPoisonedPlayers();
        if (poisoned.isEmpty()) return;
        // 使用迭代器遍历以安全地移除元素
        for (Iterator<Map.Entry<Integer, Long>> it = poisoned.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> entry = it.next();
            Integer playerId = entry.getKey();
            Long poisonEndTime = entry.getValue();
            if (now >= poisonEndTime) {
                // 中毒时间到，移除效果
                it.remove();
            } else {
                // 每秒造成2点伤害 (可以调整)
                // 通过取模运算，确保大约每秒触发一次
                if (now % 1000 < gameConfig.getEngine().getTickRateMs()) {
                    handleEnvironmentalDamage(playerId, 2, "POISON");                 }
            }
        }
    }

    private void handleEnvironmentalDamage(int victimId, int damage, String damageType) {
        // 环境伤害没有攻击者，可以用一个特殊ID（如-1）或 victimId 本身
        GameRoomService.DamageResult res = playerStateManager.applyDamage(-1, victimId, damage);

        // 同样需要广播 damage 消息，让客户端知道受到了伤害
        ObjectNode dmg = mapper.createObjectNode();
        dmg.put("type", "damage");
        dmg.put("attacker", -1); // -1 代表环境伤害
        dmg.put("victim", victimId);
        dmg.put("damage", damage);
        dmg.put("hp", res.hp);
        dmg.put("dead", res.dead);
        // 环境伤害通常没有击退效果
        dmg.put("kx", 0);
        dmg.put("ky", 0);
        dmg.put("srvTS", System.currentTimeMillis());
        gameSessionManager.broadcast(dmg.toString());

        // 如果玩家因此死亡，记录死亡事件（但没有击杀者）
        if (res.dead) {
            playerStateManager.recordKill(null, victimId);
        }
    }

    // 子弹更新与碰撞检测逻辑
    private void updateProjectiles(double deltaTime) {
        List<ServerProjectile> projectiles = projectileManager.getProjectiles();
        if (projectiles.isEmpty()) return;

        final double HB_OFF_X = 80.0, HB_OFF_Y = 20.0, HB_W = 86.0, HB_H = 160.0;

        // 使用迭代器或复制列表以安全地移除元素
        for (Iterator<ServerProjectile> iterator = projectiles.iterator(); iterator.hasNext(); ) {
            ServerProjectile proj = iterator.next();
            boolean shouldRemove = false;

            // --- 默认子弹/射线枪逻辑 ---
            double oldX = proj.getX();
            double oldY = proj.getY();
            proj.update(deltaTime);
            boolean hit = false;

            for (Integer victimId : playerStateManager.getHpByPlayer().keySet()) {
                if (victimId.equals(proj.getShooterId()) || playerStateManager.isDead(victimId)) {
                    continue;
                }
                Optional<GameRoomService.StateSnapshot> sOpt = playerStateManager.interpolateAt(victimId, System.currentTimeMillis());
                if (sOpt.isEmpty()) continue;

                GameRoomService.StateSnapshot victimState = sOpt.get();
                double minX = victimState.x + HB_OFF_X;
                double minY = victimState.y + HB_OFF_Y;
                double maxX = minX + HB_W;
                double maxY = minY + HB_H;

                double tEnter = HitMath.raySegmentVsAABB(oldX, oldY, proj.getX() - oldX, proj.getY() - oldY, minX, minY, maxX, maxY);

                if (tEnter <= 1.0) {
                    // ★ 伤害修复：使用子弹自身的伤害值，而不是硬编码的 10 ★
                    handleHit(proj, victimId, proj.getDamage());
                    hit = true;
                    break;
                }
            }

            if (hit || proj.isOutOfRange(proj.getX(), proj.getY())) {
                shouldRemove = true;
            }


            if (shouldRemove) {
                // 从 projectileManager 中移除，而不是直接从列表移除
                projectileManager.removeProjectile(proj);
            }
        }
    }

    // 处理命中事件的方法
    private void handleHit(ServerProjectile projectile, int victimId, int damage) {
        int shooterId = projectile.getShooterId();

        GameRoomService.DamageResult res = playerStateManager.applyDamage(shooterId, victimId, damage);
        if (res.dead) {
            playerStateManager.recordKill(shooterId, victimId);
        }

        double sign = projectile.getVelocityX() >= 0 ? 1.0 : -1.0;
        double kx = sign * 220.0; // 击退效果
        double ky = 0.0;

        ObjectNode dmg = mapper.createObjectNode();
        dmg.put("type", "damage");
        dmg.put("attacker", shooterId);
        dmg.put("victim", victimId);
        dmg.put("damage", damage);
        dmg.put("hp", res.hp);
        dmg.put("dead", res.dead);
        dmg.put("kx", kx);
        dmg.put("ky", ky);
        dmg.put("srvTS", System.currentTimeMillis());
        gameSessionManager.broadcast(dmg.toString());
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
        Map<Integer, Integer> characterSelections = game.playerCharacterSelections;
        for (Integer playerId : game.playerIds) {
            Map<String, Integer> stats = playerStateManager.getStats(playerId);
            MatchParticipant p = new MatchParticipant();
            p.setUserId(playerId);
            p.setMatchId(game.gameId);
            p.setKills(stats.get("kills"));
            p.setDeaths(stats.get("deaths"));
            p.setCharacterId(characterSelections.getOrDefault(playerId, 1)); // 如果找不到，默认为角色1
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