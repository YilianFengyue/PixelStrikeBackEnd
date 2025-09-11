// src/main/java/org/csu/pixelstrikebackend/game/service/GameLoopService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
            long respawnDelay = gameConfig.getPlayer().getRespawnTimeMs();

            Map<Integer, Long> deathTimestamps = playerStateManager.getDeathTimestamps();
            
            // 使用entrySet的快照进行迭代
            for (Map.Entry<Integer, Long> entry : new ConcurrentHashMap<>(deathTimestamps).entrySet()) {
                Integer deadPlayerId = entry.getKey();
                Long deathTime = entry.getValue();

                if (now - deathTime >= respawnDelay) {
                    respawnPlayer(deadPlayerId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in game tick: " + e.getMessage());
            e.printStackTrace();
        }
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