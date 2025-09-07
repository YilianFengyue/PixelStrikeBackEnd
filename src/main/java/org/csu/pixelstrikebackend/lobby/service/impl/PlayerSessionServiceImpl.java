package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlayerSessionServiceImpl implements PlayerSessionService {

    // 使用一个线程安全的Map来存储 userId -> gameId 的映射
    private final Map<Integer, Long> playerGameMap = new ConcurrentHashMap<>();

    @Override
    public void registerPlayerInGame(Integer userId, Long gameId) {
        playerGameMap.put(userId, gameId);
        System.out.println("[PlayerSessionService] Registered player " + userId + " in game " + gameId);
    }

    @Override
    public void removePlayerFromGame(Integer userId) {
        if (playerGameMap.remove(userId) != null) {
            System.out.println("[PlayerSessionService] Removed player " + userId + " from game session.");
        }
    }

    @Override
    public boolean isPlayerInGame(Integer userId) {
        return playerGameMap.containsKey(userId);
    }

    @Override
    public Long getActiveGameId(Integer userId) {
        return playerGameMap.get(userId);
    }
}