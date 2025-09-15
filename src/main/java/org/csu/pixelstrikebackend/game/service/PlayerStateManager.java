// src/main/java/org/csu/pixelstrikebackend/game/service/PlayerStateManager.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlayerStateManager {

    // --- 权威状态 ---
    private static final int MAX_HP = 100;
    @Getter
    final Map<Integer, Integer> hpByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, String> weaponByPlayer = new ConcurrentHashMap<>();
    private final Set<Integer> deadSet = ConcurrentHashMap.newKeySet();
    @Getter
    private final Map<Integer, Long> deathTimestamps = new ConcurrentHashMap<>();
    @Autowired
    private GameSessionManager gameSessionManager;
    @Getter
    private final Map<Integer, Long> poisonedPlayers = new ConcurrentHashMap<>();

    // --- 【新增】快照与序列号 (来自 fxdemoBakcend) ---
    private static final long SNAPSHOT_KEEP_MS = 2000;
    private final Map<Integer, Deque<GameRoomService.StateSnapshot>> snapshotsByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSeqByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> killsByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> deathsByPlayer = new ConcurrentHashMap<>();

    public void initializePlayer(Integer userId) {
        hpByPlayer.put(userId, MAX_HP);
        deadSet.remove(userId);
        snapshotsByPlayer.remove(userId);
        lastSeqByPlayer.remove(userId);
        deathTimestamps.remove(userId);
        killsByPlayer.put(userId, 0);
        deathsByPlayer.put(userId, 0);
        weaponByPlayer.put(userId, "Pistol"); // 默认武器
    }

    public GameRoomService.DamageResult applyDamage(int byId, int victimId, int amount) {
        // 伤害不能为负，且不能伤害自己
        if (amount <= 0 || byId == victimId) return new GameRoomService.DamageResult(getHp(victimId), isDead(victimId));

        int hp = getHp(victimId);
        if (hp <= 0) return new GameRoomService.DamageResult(0, true);

        hp = Math.max(0, hp - amount);
        hpByPlayer.put(victimId, hp);
        boolean dead = (hp == 0);
        if (dead) {
            deadSet.add(victimId);
            deathTimestamps.put(victimId, System.currentTimeMillis());
            System.out.println("Player " + victimId + " died. Respawn timer started.");
        }
        return new GameRoomService.DamageResult(hp, dead);
    }

    public void respawnPlayer(Integer userId) {
        hpByPlayer.put(userId, MAX_HP);
        deadSet.remove(userId);
        deathTimestamps.remove(userId);
        lastSeqByPlayer.remove(userId); // 重置序列号
    }

    // --- 【新增】记录玩家状态快照 (来自 fxdemoBakcend) ---
    public void recordStateSnapshot(int playerId, long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) {
        Deque<GameRoomService.StateSnapshot> buf = snapshotsByPlayer.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        synchronized (buf) {
            buf.addLast(new GameRoomService.StateSnapshot(srvTS, cliTS, x, y, vx, vy, facing, onGround));
            // 清理过期的快照
            long min = srvTS - SNAPSHOT_KEEP_MS;
            while (!buf.isEmpty() && buf.peekFirst().srvTS < min) buf.removeFirst();
            if (buf.size() > 600) buf.removeFirst(); // 防止内存溢出
        }
    }

    public void recordKill(Integer killerId, Integer victimId) {
        if (killerId != null) {
            killsByPlayer.compute(killerId, (id, kills) -> (kills == null ? 0 : kills) + 1);
        }
        if (victimId != null) {
            deathsByPlayer.compute(victimId, (id, deaths) -> (deaths == null ? 0 : deaths) + 1);
        }
    }

    public Map<String, Integer> getStats(Integer userId) {
        return Map.of(
                "kills", killsByPlayer.getOrDefault(userId, 0),
                "deaths", deathsByPlayer.getOrDefault(userId, 0)
        );
    }

    // --- 【新增】在特定服务器时间点插值计算玩家位置 (来自 fxdemoBakcend) ---
    public Optional<GameRoomService.StateSnapshot> interpolateAt(int playerId, long targetSrvTS) {
        Deque<GameRoomService.StateSnapshot> buf = snapshotsByPlayer.get(playerId);
        if (buf == null || buf.isEmpty()) return Optional.empty();
        synchronized (buf) {
            GameRoomService.StateSnapshot prev = null, next = null;
            for (GameRoomService.StateSnapshot s : buf) {
                if (s.srvTS <= targetSrvTS) prev = s;
                if (s.srvTS >= targetSrvTS) { next = s; break; }
            }
            if (prev == null) prev = buf.peekFirst();
            if (next == null) next = buf.peekLast();
            if (prev == null) return Optional.empty();

            if (next == null || next == prev || next.srvTS == prev.srvTS) {
                return Optional.of(prev);
            }

            double t = (targetSrvTS - prev.srvTS) / (double)(next.srvTS - prev.srvTS);
            t = Math.max(0, Math.min(1, t));

            return Optional.of(GameRoomService.StateSnapshot.lerp(prev, next, t));
        }
    }

    // --- 【新增】验证客户端发来的状态更新序列号，防止乱序 (来自 fxdemoBakcend) ---
    public boolean acceptStateSeq(int playerId, long seq) {
        if (seq <= 0) return true; // 兼容没有seq的老客户端
        Long last = lastSeqByPlayer.get(playerId);
        if (last != null && seq <= last) return false; // 如果新序号不大于上一个，则拒绝
        lastSeqByPlayer.put(playerId, seq);
        return true;
    }

    public int getHp(int userId) {
        return hpByPlayer.getOrDefault(userId, MAX_HP);
    }

    public boolean isDead(int userId) {
        return deadSet.contains(userId);
    }

    public void applyHeal(int userId, int amount) {
        if (isDead(userId) || amount <= 0) return;

        int currentHp = getHp(userId);
        int newHp = Math.min(MAX_HP, currentHp + amount);
        hpByPlayer.put(userId, newHp);

        // 广播回血事件
        ObjectNode healMsg = new ObjectMapper().createObjectNode();
        healMsg.put("type", "player_healed");
        healMsg.put("userId", userId);
        healMsg.put("newHp", newHp);
        gameSessionManager.broadcast(healMsg.toString());
    }

    public void setWeapon(Integer userId, String weaponType) {
        weaponByPlayer.put(userId, weaponType);
    }

    public String getWeapon(Integer userId) {
        return weaponByPlayer.getOrDefault(userId, "Pistol");
    }

    public void applyPoison(int userId, long durationMs) {
        if (isDead(userId)) return;
        long poisonEndTime = System.currentTimeMillis() + durationMs;
        poisonedPlayers.put(userId, poisonEndTime);
    }
}