package org.csu.pixelstrikebackend.lobby.service;

public interface PlayerSessionService {
    void registerPlayerInGame(Integer userId, Long gameId);
    void removePlayerFromGame(Integer userId);
    boolean isPlayerInGame(Integer userId);
    Long getActiveGameId(Integer userId);
}