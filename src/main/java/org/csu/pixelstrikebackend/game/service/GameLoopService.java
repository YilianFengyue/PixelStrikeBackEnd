// src/main/java/org/csu/pixelstrikebackend/game/service/GameLoopService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.geom.HitMath;
import org.csu.pixelstrikebackend.game.model.ServerProjectile;
import org.csu.pixelstrikebackend.lobby.entity.GameMap;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.CharacterMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MapMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GameLoopService {

    @Autowired private GameConfig gameConfig;
    @Autowired private PlayerStateManager playerStateManager;
    @Autowired private GameSessionManager gameSessionManager;
    @Autowired private GameManager gameManager;
    @Autowired private ProjectileManager projectileManager;
    @Autowired private MapMapper mapMapper;
    @Autowired private CharacterMapper characterMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private PlayerSessionService playerSessionService;
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
            broadcastScoreboard();
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
        Long gameId = playerSessionService.getActiveGameId(victimId);
        if (gameId != null) {
            gameSessionManager.broadcast(gameId, dmg.toString());
        }

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
        List<ServerProjectile> projectilesToRemove = new ArrayList<>();

        // 使用迭代器或复制列表以安全地移除元素
        for (ServerProjectile proj : projectiles) {

            boolean shouldRemove = false;

            // --- 默认子弹/射线枪逻辑 ---
            double oldX = proj.getX();
            double oldY = proj.getY();
            proj.update(deltaTime);
            boolean hit = false;

            GameManager.ActiveGame currentGame = gameManager.getActiveGames().get(proj.getGameId());
            if (currentGame == null) {
                projectilesToRemove.add(proj); // 游戏已结束，标记待移除
                continue;
            }
            for (Integer victimId : currentGame.playerIds) {
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
                projectilesToRemove.add(proj); // 旧代码是 iterator.remove()
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
        Long gameId = playerSessionService.getActiveGameId(projectile.getShooterId());
        if (gameId != null) {
            gameSessionManager.broadcast(gameId, dmg.toString());
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

        // 4. 查询本局对局的元数据（地图名、模式等）
        Match matchInfo = gameManager.getMatchInfo(game.gameId);
        GameMap mapInfo = null;
        if (matchInfo != null) {
            mapInfo = mapMapper.selectById(matchInfo.getMapId());
        }
// 5. 准备一个与 MatchDetailDTO 结构类似的 Map 用于发送
        Map<String, Object> detailedResults = new HashMap<>();
        detailedResults.put("matchId", game.gameId);
        detailedResults.put("gameMode", matchInfo != null ? matchInfo.getGameMode() : "未知模式");
        detailedResults.put("mapName", mapInfo != null ? mapInfo.getName() : "未知地图");
        detailedResults.put("startTime", matchInfo != null ? matchInfo.getStartTime().toString() : "");
        detailedResults.put("endTime", java.time.LocalDateTime.now().toString()); // 结束时间用当前时间

        // 6. 转换玩家战绩列表，加入昵称和角色名
        List<Integer> userIds = results.stream().map(MatchParticipant::getUserId).collect(java.util.stream.Collectors.toList());
        Map<Integer, org.csu.pixelstrikebackend.lobby.entity.UserProfile> userProfileMap = userProfileMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(org.csu.pixelstrikebackend.lobby.entity.UserProfile::getUserId, up -> up));

        List<Integer> characterIds = results.stream().map(MatchParticipant::getCharacterId).distinct().collect(java.util.stream.Collectors.toList());
        Map<Integer, String> characterMap = characterMapper.selectBatchIds(characterIds).stream()
                .collect(java.util.stream.Collectors.toMap(org.csu.pixelstrikebackend.lobby.entity.GameCharacter::getId, org.csu.pixelstrikebackend.lobby.entity.GameCharacter::getName));

        List<Map<String, Object>> participantsForJson = new ArrayList<>();
        for (MatchParticipant p : results) {
            Map<String, Object> participantMap = new HashMap<>();
            participantMap.put("userId", p.getUserId());
            participantMap.put("nickname", userProfileMap.getOrDefault(p.getUserId(), new org.csu.pixelstrikebackend.lobby.entity.UserProfile()).getNickname());
            participantMap.put("kills", p.getKills());
            participantMap.put("deaths", p.getDeaths());
            participantMap.put("ranking", p.getRanking());
            participantMap.put("characterName", characterMap.getOrDefault(p.getCharacterId(), "未知角色"));
            participantsForJson.add(participantMap);
        }
        detailedResults.put("participants", participantsForJson);

        // 7. 向所有客户端广播游戏结束和详细战绩的消息
        ObjectNode gameOverMsg = mapper.createObjectNode();
        gameOverMsg.put("type", "game_over"); // 保持 game_over 类型，让客户端先弹窗
        // 将详细战绩作为一个内嵌的 JSON 对象发送
        gameOverMsg.set("results", mapper.valueToTree(detailedResults));

        gameSessionManager.broadcast(game.gameId, gameOverMsg.toString());
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

        Long gameId = playerSessionService.getActiveGameId(userId);
        if (gameId != null) {
            gameSessionManager.broadcast(gameId, respawnMsg.toString());
        }
    }

    private void broadcastScoreboard() {
        // 遍历所有正在进行的游戏
        for (GameManager.ActiveGame game : gameManager.getActiveGames().values()) {
            List<Map<String, Object>> scoreboard = new ArrayList<>();
            List<Integer> playerIds = game.getPlayerIds();

            // 批量获取玩家的昵称，提高效率
            if (playerIds.isEmpty()) continue;
            List<UserProfile> profiles = userProfileMapper.selectBatchIds(playerIds);
            Map<Integer, String> idToNicknameMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfile::getUserId, UserProfile::getNickname));

            // 为每个玩家构建战绩信息
            for (Integer playerId : playerIds) {
                Map<String, Integer> stats = playerStateManager.getStats(playerId);
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("id", playerId);
                playerData.put("nickname", idToNicknameMap.getOrDefault(playerId, "玩家 " + playerId));
                playerData.put("kills", stats.get("kills"));
                playerData.put("deaths", stats.get("deaths"));
                scoreboard.add(playerData);
            }

            // 按击杀数降序排序
            scoreboard.sort((p1, p2) -> (Integer)p2.get("kills") - (Integer)p1.get("kills"));

            long elapsedTime = System.currentTimeMillis() - game.startTime;
            long maxDuration = gameConfig.getRules().getMaxDurationMs();
            int remainingSeconds = (int) Math.max(0, (maxDuration - elapsedTime) / 1000);

            // 构建并发送包含排行榜和剩余时间的消息
            ObjectNode msg = mapper.createObjectNode();
            msg.put("type", "scoreboard_update");
            msg.put("gameTimeRemainingSeconds", remainingSeconds); // <-- 将剩余时间加入消息体
            msg.set("scores", mapper.valueToTree(scoreboard));
            gameSessionManager.broadcast(msg.toString());
        }
    }
}