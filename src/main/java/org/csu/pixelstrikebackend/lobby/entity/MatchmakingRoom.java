package org.csu.pixelstrikebackend.lobby.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MatchmakingRoom {
    private final String roomId;
    //匹配所需的玩家数量
    private final int maxSize;
    // 使用线程安全的 Set 来存储玩家ID
    private final Set<Integer> players = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Integer hostId = null;

    public MatchmakingRoom(int maxSize) {
        this.roomId = UUID.randomUUID().toString();
        this.maxSize = maxSize;
    }

    public boolean addPlayer(Integer userId) {
        if (players.size() < maxSize) {
            if (players.isEmpty()) {
                this.hostId = userId; // 第一个加入的玩家是房主
            }
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
