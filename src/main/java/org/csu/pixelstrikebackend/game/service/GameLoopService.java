// src/main/java/org/csu/pixelstrikebackend/game/service/GameLoopService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.lobby.entity.GameMap;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.CharacterMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MapMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    @Autowired private MapMapper mapMapper;
    @Autowired private CharacterMapper characterMapper;
    @Autowired private UserProfileMapper userProfileMapper;

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
            handleRespawns(now);
            handlePoisonDamage(now);
            checkGameOverConditions(now);
        } catch (Exception e) {
            System.err.println("Error in game tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ... (此处省略 handlePoisonDamage, handleEnvironmentalDamage, handleRespawns, checkGameOverConditions, endGame, respawnPlayer 等方法，它们与您之前的版本一致，无需修改)
    private void handlePoisonDamage(long now) {
        Map<Integer, Long> poisoned = playerStateManager.getPoisonedPlayers();
        if (poisoned.isEmpty()) return;
        for (Iterator<Map.Entry<Integer, Long>> it = poisoned.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> entry = it.next();
            Integer playerId = entry.getKey();
            Long poisonEndTime = entry.getValue();
            if (now >= poisonEndTime) {
                it.remove();
            } else {
                if (now % 1000 < gameConfig.getEngine().getTickRateMs()) {
                    handleEnvironmentalDamage(playerId, 2, "POISON");
                }
            }
        }
    }

    private void handleEnvironmentalDamage(int victimId, int damage, String damageType) {
        GameRoomService.DamageResult res = playerStateManager.applyDamage(-1, victimId, damage);
        ObjectNode dmg = mapper.createObjectNode();
        dmg.put("type", "damage");
        dmg.put("attacker", -1);
        dmg.put("victim", victimId);
        dmg.put("damage", damage);
        dmg.put("hp", res.hp);
        dmg.put("dead", res.dead);
        dmg.put("kx", 0);
        dmg.put("ky", 0);
        dmg.put("srvTS", System.currentTimeMillis());
        gameSessionManager.broadcast(dmg.toString());
        if (res.dead) {
            playerStateManager.recordKill(null, victimId);
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
        Map<Long, GameManager.ActiveGame> activeGames = gameManager.getActiveGames();
        if (activeGames.isEmpty()) return;

        int killsToWin = gameConfig.getRules().getKillsToWin();
        long maxDuration = gameConfig.getRules().getMaxDurationMs();

        for (GameManager.ActiveGame game : activeGames.values()) {
            boolean timeIsUp = (now - game.startTime) >= maxDuration;
            boolean scoreReached = false;

            for (Integer playerId : game.playerIds) {
                if (playerStateManager.getStats(playerId).get("kills") >= killsToWin) {
                    scoreReached = true;
                    break;
                }
            }
            if (timeIsUp || scoreReached) {
                System.out.println("Game " + game.gameId + " is over. Reason: " + (timeIsUp ? "Time is up" : "Score reached"));
                endGame(game);
            }
        }
    }

    private void endGame(GameManager.ActiveGame game) {
        List<MatchParticipant> results = new ArrayList<>();
        Map<Integer, Integer> characterSelections = game.playerCharacterSelections;
        for (Integer playerId : game.playerIds) {
            Map<String, Integer> stats = playerStateManager.getStats(playerId);
            MatchParticipant p = new MatchParticipant();
            p.setUserId(playerId);
            p.setMatchId(game.gameId);
            p.setKills(stats.get("kills"));
            p.setDeaths(stats.get("deaths"));
            p.setCharacterId(characterSelections.getOrDefault(playerId, 1));
            results.add(p);
        }

        results.sort(Comparator.comparing(MatchParticipant::getKills).reversed());
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRanking(i + 1);
        }

        gameManager.onGameConcluded(game.gameId, results);

        Match matchInfo = gameManager.getMatchInfo(game.gameId);
        GameMap mapInfo = null;
        if (matchInfo != null) {
            mapInfo = mapMapper.selectById(matchInfo.getMapId());
        }
        Map<String, Object> detailedResults = new HashMap<>();
        detailedResults.put("matchId", game.gameId);
        detailedResults.put("gameMode", matchInfo != null ? matchInfo.getGameMode() : "未知模式");
        detailedResults.put("mapName", mapInfo != null ? mapInfo.getName() : "未知地图");
        detailedResults.put("startTime", matchInfo != null ? matchInfo.getStartTime().toString() : "");
        detailedResults.put("endTime", java.time.LocalDateTime.now().toString());

        List<Integer> userIds = results.stream().map(MatchParticipant::getUserId).collect(Collectors.toList());
        Map<Integer, UserProfile> userProfileMap = userProfileMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, up -> up));
        List<Integer> characterIds = results.stream().map(MatchParticipant::getCharacterId).distinct().collect(Collectors.toList());
        Map<Integer, String> characterMap = characterMapper.selectBatchIds(characterIds).stream()
                .collect(Collectors.toMap(org.csu.pixelstrikebackend.lobby.entity.GameCharacter::getId, org.csu.pixelstrikebackend.lobby.entity.GameCharacter::getName));

        List<Map<String, Object>> participantsForJson = new ArrayList<>();
        for (MatchParticipant p : results) {
            Map<String, Object> participantMap = new HashMap<>();
            participantMap.put("userId", p.getUserId());
            participantMap.put("nickname", userProfileMap.getOrDefault(p.getUserId(), new UserProfile()).getNickname());
            participantMap.put("kills", p.getKills());
            participantMap.put("deaths", p.getDeaths());
            participantMap.put("ranking", p.getRanking());
            participantMap.put("characterName", characterMap.getOrDefault(p.getCharacterId(), "未知角色"));
            participantsForJson.add(participantMap);
        }
        detailedResults.put("participants", participantsForJson);
        ObjectNode gameOverMsg = mapper.createObjectNode();
        gameOverMsg.put("type", "game_over");
        gameOverMsg.set("results", mapper.valueToTree(detailedResults));
        gameSessionManager.broadcast(gameOverMsg.toString());
    }

    private void respawnPlayer(Integer userId) {
        System.out.println("Respawning player " + userId);
        playerStateManager.respawnPlayer(userId);

        double spawnX = 500;
        double spawnY = gameConfig.getPhysics().getGroundY() - 128;

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