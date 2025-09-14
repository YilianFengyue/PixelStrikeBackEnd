package org.csu.pixelstrikebackend.lobby.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CustomRoom {
    private final String roomId;
    @Setter
    private Integer mapId; // **新增：房间地图ID**
    private final int maxSize = 2;
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();
    // **核心改动：用 Map 存储玩家及其选择的角色 (Key: userId, Value: characterId)**
    private final Map<Integer, Integer> playerCharacterSelections = new ConcurrentHashMap<>();
    private Integer hostId;
    private String status; // "WAITING", "IN_GAME"

    public CustomRoom(Integer hostId, Integer mapId) {
        this.roomId = UUID.randomUUID().toString().substring(0, 8);
        this.hostId = hostId;
        this.mapId = mapId;
        this.status = "WAITING";
        addPlayer(hostId); // 创建者自动加入并选择默认角色
    }

    public boolean addPlayer(Integer userId) {
        if (players.size() >= maxSize) return false;
        if (players.add(userId)) {
            // **新增：玩家加入时，默认选择角色1**
            playerCharacterSelections.put(userId, 1);
            return true;
        }
        return false;
    }

    public void removePlayer(Integer userId) {
        playerCharacterSelections.remove(userId);
        players.remove(userId);
    }

    // **新增：更换角色方法**
    public void changeCharacter(Integer userId, Integer newCharacterId) {
        if (players.contains(userId)) {
            playerCharacterSelections.put(userId, newCharacterId);
        }
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
