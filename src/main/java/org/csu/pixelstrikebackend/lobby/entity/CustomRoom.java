package org.csu.pixelstrikebackend.lobby.entity;

import lombok.Getter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CustomRoom {
    private final String roomId;
    private final int maxSize = 3;
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();
    private Integer hostId;
    private String status; // "WAITING", "IN_GAME"

    public CustomRoom(Integer hostId) {
        this.roomId = UUID.randomUUID().toString().substring(0, 8); // 使用一个更短的ID
        this.hostId = hostId;
        this.players.add(hostId);
        this.status = "WAITING";
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

    public void setHostId(Integer newHostId) {
        if (players.contains(newHostId)) {
            this.hostId = newHostId;
        }
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
