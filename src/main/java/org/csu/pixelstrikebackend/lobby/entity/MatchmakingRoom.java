package org.csu.pixelstrikebackend.lobby.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;
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
    // **新增：用 Map 存储玩家及其选择的角色 (Key: userId, Value: characterId)**
    private final Map<Integer, Integer> playerCharacterSelections = new ConcurrentHashMap<>();

    public MatchmakingRoom(int maxSize) {
        this.roomId = UUID.randomUUID().toString();
        this.maxSize = maxSize;
    }

    // **修改：addPlayer 方法现在需要传入 characterId**
    public boolean addPlayer(Integer userId, Integer characterId) {
        if (players.size() < maxSize) {
            if (players.add(userId)) {
                playerCharacterSelections.put(userId, characterId);
                if (players.isEmpty()) {
                    this.hostId = userId;
                }
                return true;
            }
        }
        return false;
    }

    // **修改：removePlayer 方法，同步移除角色选择**
    public void removePlayer(Integer userId) {
        players.remove(userId);
        playerCharacterSelections.remove(userId); // 同步移除
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
