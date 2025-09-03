package org.csu.pixelstrikebackend.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MatchmakingRoom {
    private final String roomId;
    private final int maxSize = 3;
    // 使用线程安全的 Set 来存储玩家ID
    private final Set<Integer> players = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public MatchmakingRoom() {
        this.roomId = UUID.randomUUID().toString();
    }

    public boolean addPlayer(Integer userId) {
        if (players.size() < maxSize) {
            return players.add(userId);
        }
        return false;
    }

    public void removePlayer(Integer userId) {
        players.remove(userId);
    }

    public boolean isFull() {
        return players.size() >= maxSize;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public int getCurrentSize() {
        return players.size();
    }
}
