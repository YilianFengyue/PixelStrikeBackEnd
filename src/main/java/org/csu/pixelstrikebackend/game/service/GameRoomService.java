package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.geom.HitMath; // 确保你项目中存在这个类
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameRoomService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GameManager gameManager;
    @Autowired
    private GameConfig gameConfig;

    // --- 从 DemoGameRoomService 完整移植的核心游戏逻辑字段 ---

    // 会话管理
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 节流控制
    private final Map<String, RateCounter> anyCounterBySession = new ConcurrentHashMap<>();
    private final Map<String, RateCounter> stateCounterBySession = new ConcurrentHashMap<>();

    private final Map<Integer, Long> deathTimestamps = new ConcurrentHashMap<>(); // 存储玩家死亡时间戳
    private ScheduledExecutorService gameLoopExecutor;

    // 快照缓存 (用于服务器回滚验证)
    private static final long SNAPSHOT_KEEP_MS = 2000;
    private final Map<Integer, Deque<StateSnapshot>> snapshotsByPlayer = new ConcurrentHashMap<>();

    // 权威状态 (服务器说了算)
    private static final int MAX_HP = 100;
    private final Map<Integer, Integer> hpByPlayer = new ConcurrentHashMap<>();
    private final Set<Integer> deadSet = ConcurrentHashMap.newKeySet();

    // 客户端时钟同步
    private final Map<String, ClockAlign> clockBySession = new ConcurrentHashMap<>();

    // 客户端状态包序列号 (防止乱序)
    private final Map<Integer, Long> lastSeqByPlayer = new ConcurrentHashMap<>();

    // 命中判定常量 (与客户端一致)
    private static final double HB_OFF_X = 80.0;
    private static final double HB_OFF_Y = 20.0;
    private static final double HB_W     = 86.0;
    private static final double HB_H     = 160.0;
    private static final double KB_X = 220.0;
    private static final double KB_Y = 0.0;

    @Autowired
    public GameRoomService(@Lazy GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // --- 新增方法：初始化游戏循环 ---
    @PostConstruct
    public void init() {
        gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
        gameLoopExecutor.scheduleAtFixedRate(this::gameTick, 0, gameConfig.getEngine().getTickRateMs(), TimeUnit.MILLISECONDS);
    }

    // --- 新增方法：销毁游戏循环 ---
    @PreDestroy
    public void shutdown() {
        if (gameLoopExecutor != null) {
            gameLoopExecutor.shutdown();
        }
    }

    private void respawnPlayer(Integer userId) {
        System.out.println("Respawning player " + userId);
        // 1. 重置服务器上的权威状态
        hpByPlayer.put(userId, MAX_HP);
        deadSet.remove(userId);
        lastSeqByPlayer.remove(userId); // 清除旧的序列号以接受新状态

        // 2. (可选) 计算一个新的、安全的出生点
        // 这里为了简化，我们暂时使用一个固定的出生点
        double spawnX = 500;
        double spawnY = 3300 - 211 - 128;

        // 3. 构造 respawn 消息
        ObjectNode respawnMsg = mapper.createObjectNode();
        respawnMsg.put("type", "respawn");
        respawnMsg.put("id", userId);
        respawnMsg.put("x", spawnX);
        respawnMsg.put("y", spawnY);
        respawnMsg.put("hp", MAX_HP);
        respawnMsg.put("serverTime", System.currentTimeMillis());

        // 4. 向所有客户端广播这条消息
        broadcast(respawnMsg.toString());
    }

    public void prepareGame(Long gameId, List<Integer> playerIds) {
        System.out.println("Preparing new game " + gameId + " with players: " + playerIds);
        // 初始化本局所有玩家的生命值等状态
        for(Integer userId : playerIds) {
            hpByPlayer.put(userId, MAX_HP);
            deadSet.remove(userId);
            snapshotsByPlayer.remove(userId); // 清理上一局可能残留的快照
            lastSeqByPlayer.remove(userId); // 清理上一局的序列号
        }
    }

    public void addSession(WebSocketSession s) {
        sessions.put(s.getId(), s);
        anyCounterBySession.put(s.getId(), new RateCounter(200));
        stateCounterBySession.put(s.getId(), new RateCounter(120));
        clockBySession.put(s.getId(), new ClockAlign());
    }

    public void removeSession(WebSocketSession s) {
        sessions.remove(s.getId());
        anyCounterBySession.remove(s.getId());
        stateCounterBySession.remove(s.getId());
        clockBySession.remove(s.getId());
    }

    public void handleJoin(WebSocketSession session) {
        Long gameId = (Long) session.getAttributes().get("gameId");
        Integer userId = (Integer) session.getAttributes().get("userId");

        if (gameId == null || userId == null) {
            System.err.println("Error on join: gameId or userId is null for session " + session.getId());
            return;
        }

        System.out.println("Player " + userId + " is joining game " + gameId);

        // 初始化玩家游戏内状态
        hpByPlayer.putIfAbsent(userId, MAX_HP);
        deadSet.remove(userId);

        // 构造 welcome 消息
        ObjectNode welcome = mapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("id", userId);
        welcome.put("serverTime", System.currentTimeMillis());
        sendTo(session, welcome.toString());

        // 构造并广播 join_broadcast 消息
        ObjectNode joined = mapper.createObjectNode();
        joined.put("type", "join_broadcast");
        joined.put("id", userId);
        joined.put("name", "Player " + userId);
        broadcastToOthers(session, joined.toString());
    }

    public void handleState(WebSocketSession session, JsonNode root) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if(userId == null) return;

        long now = System.currentTimeMillis();
        if (!allowAnyMessage(session, now) || !allowStateMessage(session, now)) {
            return;
        }

        long cliTS = root.path("ts").asLong(0);
        updateClock(session, cliTS, now);

        long seq = root.path("seq").asLong(0);
        if (!acceptStateSeq(userId, seq)) {
            return; // 丢弃乱序或过时的包
        }

        double x = root.path("x").asDouble(), y = root.path("y").asDouble();
        double vx = root.path("vx").asDouble(), vy = root.path("vy").asDouble();
        boolean facing = root.path("facing").asBoolean(), onGround = root.path("onGround").asBoolean();

        recordStateSnapshot(userId, now, cliTS, x, y, vx, vy, facing, onGround);

        ObjectNode stateToBroadcast = (ObjectNode) root.deepCopy();
        stateToBroadcast.put("id", userId);
        stateToBroadcast.put("serverTime", now);
        broadcastToOthers(session, stateToBroadcast.toString());
    }

    public void handleShot(WebSocketSession session, JsonNode root) {
        Integer shooterId = (Integer) session.getAttributes().get("userId");
        if (shooterId == null) return;

        long now = System.currentTimeMillis();
        long cliTS = root.path("ts").asLong(0);
        long shotSrvTS = toServerTime(session, cliTS);

        double ox = root.path("ox").asDouble(), oy = root.path("oy").asDouble();
        double dx = root.path("dx").asDouble(), dy = root.path("dy").asDouble();
        double range = root.path("range").asDouble(0);
        int damage = root.path("damage").asInt(0);

        // 1. 广播射击特效（给所有客户端看）
        ObjectNode shot = mapper.createObjectNode();
        shot.put("type", "shot");
        shot.put("attacker", shooterId);
        shot.put("by", shooterId); // 兼容字段
        shot.put("ox", ox); shot.put("oy", oy);
        shot.put("dx", dx); shot.put("dy", dy);
        shot.put("range", range);
        shot.put("srvTS", now);
        broadcast(shot.toString());

        // 2. 在服务器上进行权威命中判定
        var hitOpt = validateShot(shooterId, shotSrvTS, ox, oy, dx, dy, range);
        if (hitOpt.isEmpty()) return; // 未命中

        // 3. 如果命中，计算伤害并广播权威的伤害结果
        var hit = hitOpt.get();
        var res = applyDamage(shooterId, hit.victimId, damage);

        double sign = (dx >= 0) ? 1.0 : -1.0;
        double kx = sign * KB_X;
        double ky = KB_Y;

        ObjectNode dmg = mapper.createObjectNode();
        dmg.put("type", "damage");
        dmg.put("attacker", shooterId);
        dmg.put("by", shooterId);
        dmg.put("victim", hit.victimId);
        dmg.put("damage", damage);
        dmg.put("hp", res.hp);
        dmg.put("dead", res.dead);
        dmg.put("kx", kx);
        dmg.put("ky", ky);
        dmg.put("t", hit.t);
        dmg.put("srvTS", System.currentTimeMillis());
        broadcast(dmg.toString());
    }

    public void handleLeave(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            ObjectNode leave = mapper.createObjectNode();
            leave.put("type", "leave");
            leave.put("id", userId);
            broadcastToOthers(session, leave.toString());
        }
    }

    // --- 广播逻辑 (保持线程安全版本) ---

    public synchronized void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        List<WebSocketSession> activeSessions = new ArrayList<>();
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                activeSessions.add(session);
            }
        }
        System.out.println("[BROADCAST] -> " + activeSessions.size() + " sessions : " + json);
        for (WebSocketSession session : activeSessions) {
            sendTo(session, message);
        }
    }

    public synchronized void broadcastToOthers(WebSocketSession sender, String json) {
        TextMessage message = new TextMessage(json);
        List<WebSocketSession> activeSessions = new ArrayList<>();
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen() && !session.getId().equals(sender.getId())) {
                activeSessions.add(session);
            }
        }
        System.out.println("[BROADCAST TO OTHERS] -> " + activeSessions.size() + " sessions : " + json);
        for (WebSocketSession session : activeSessions) {
            sendTo(session, message);
        }
    }

    public void sendTo(WebSocketSession session, String json) {
        sendTo(session, new TextMessage(json));
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        try {
            if (session != null && session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            removeSession(session);
        } catch (IllegalStateException e) {
            System.err.println("Failed to send message (already closed) to session " + session.getId() + ": " + e.getMessage());
            removeSession(session);
        }
    }

    // --- 以下是从 Demo 完整移植的所有辅助方法和内部类 ---

    public boolean allowAnyMessage(WebSocketSession s, long nowMs) {
        RateCounter rc = anyCounterBySession.get(s.getId());
        return rc == null || rc.allow(nowMs);
    }

    public boolean allowStateMessage(WebSocketSession s, long nowMs) {
        RateCounter rc = stateCounterBySession.get(s.getId());
        return rc == null || rc.allow(nowMs);
    }

    public void updateClock(WebSocketSession s, long clientTs, long srvTs) {
        if (clientTs <= 0) return;
        ClockAlign ca = clockBySession.get(s.getId());
        if (ca != null) ca.update(clientTs, srvTs);
    }

    public long toServerTime(WebSocketSession s, long clientTs) {
        ClockAlign ca = clockBySession.get(s.getId());
        return (ca != null) ? ca.toServerTime(clientTs) : clientTs;
    }

    public void recordStateSnapshot(int playerId, long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) {
        Deque<StateSnapshot> buf = snapshotsByPlayer.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        synchronized (buf) {
            buf.addLast(new StateSnapshot(srvTS, cliTS, x, y, vx, vy, facing, onGround));
            long min = srvTS - SNAPSHOT_KEEP_MS;
            while (!buf.isEmpty() && buf.peekFirst().srvTS < min) buf.removeFirst();
            if (buf.size() > 600) buf.removeFirst();
        }
    }

    public Optional<StateSnapshot> interpolateAt(int playerId, long targetSrvTS) {
        Deque<StateSnapshot> buf = snapshotsByPlayer.get(playerId);
        if (buf == null || buf.isEmpty()) return Optional.empty();
        synchronized (buf) {
            StateSnapshot prev = null, next = null;
            for (StateSnapshot s : buf) {
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

            return Optional.of(StateSnapshot.lerp(prev, next, t));
        }
    }

    public DamageResult applyDamage(int byId, int victimId, int amount) {
        if (amount <= 0) return new DamageResult(0, false);
        if (byId == victimId) return new DamageResult(0, false);
        hpByPlayer.putIfAbsent(victimId, MAX_HP);
        int hp = hpByPlayer.get(victimId);
        if (hp <= 0) return new DamageResult(0, true);

        hp = Math.max(0, hp - amount);
        hpByPlayer.put(victimId, hp);
        boolean dead = (hp == 0);
        if (dead) {
            deadSet.add(victimId);
            deathTimestamps.put(victimId, System.currentTimeMillis()); // ★ 记录死亡时间
            System.out.println("Player " + victimId + " died. Respawn timer started.");
        }
        return new DamageResult(hp, dead);
    }

    private void gameTick() {
        try {
            long now = System.currentTimeMillis();
            long respawnDelay = gameConfig.getPlayer().getRespawnTimeMs();

            // 遍历所有死亡时间戳
            for (Map.Entry<Integer, Long> entry : deathTimestamps.entrySet()) {
                Integer deadPlayerId = entry.getKey();
                Long deathTime = entry.getValue();

                if (now - deathTime >= respawnDelay) {
                    // 时间到了，执行复活
                    respawnPlayer(deadPlayerId);
                    // 从计时器中移除，防止重复复活
                    deathTimestamps.remove(deadPlayerId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in game tick: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public Optional<HitInfo> validateShot(int shooterId, long shotSrvTS, double ox, double oy, double dx, double dy, double range) {
        dx = clampDir(dx);
        dy = clampDir(dy);
        double len = Math.hypot(dx, dy);
        if (len < 1e-6 || range <= 0) return Optional.empty();
        dx /= len; dy /= len;

        double rx = dx * range, ry = dy * range;

        double bestT = Double.POSITIVE_INFINITY;
        int bestVictim = -1;

        for (Integer victimId : hpByPlayer.keySet()) {
            if (victimId.equals(shooterId)) continue;
            if (deadSet.contains(victimId)) continue;

            Optional<StateSnapshot> sOpt = interpolateAt(victimId, shotSrvTS);
            if (sOpt.isEmpty()) continue;
            StateSnapshot s = sOpt.get();

            double minX = s.x + HB_OFF_X;
            double minY = s.y + HB_OFF_Y;
            double maxX = minX + HB_W;
            double maxY = minY + HB_H;

            double tEnter = HitMath.raySegmentVsAABB(ox, oy, rx, ry, minX, minY, maxX, maxY);
            if (tEnter < bestT) {
                bestT = tEnter;
                bestVictim = victimId;
            }
        }

        if (bestVictim >= 0 && bestT <= 1.0) {
            return Optional.of(new HitInfo(bestVictim, bestT));
        }
        return Optional.empty();
    }

    private static double clampDir(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (Math.abs(v) > 1e4) return Math.signum(v);
        return v;
    }

    public boolean acceptStateSeq(int playerId, long seq) {
        if (seq <= 0) return true;
        Long last = lastSeqByPlayer.get(playerId);
        if (last != null && seq <= last) return false;
        lastSeqByPlayer.put(playerId, seq);
        return true;
    }

    // --- 内部类 ---

    static final class RateCounter {
        private final int maxPerSecond;
        private long windowStartMs = 0;
        private int count = 0;
        RateCounter(int maxPerSecond) { this.maxPerSecond = maxPerSecond; }
        synchronized boolean allow(long nowMs) {
            if (nowMs - windowStartMs >= 1000) { windowStartMs = nowMs; count = 0; }
            return count++ < maxPerSecond;
        }
    }

    public static final class StateSnapshot {
        public final long srvTS, cliTS;
        public final double x, y, vx, vy;
        public final boolean facing, onGround;

        public StateSnapshot(long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) {
            this.srvTS = srvTS; this.cliTS = cliTS;
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.facing = facing; this.onGround = onGround;
        }

        public static StateSnapshot lerp(StateSnapshot a, StateSnapshot b, double t) {
            return new StateSnapshot(
                    (long) (a.srvTS + (b.srvTS - a.srvTS) * t),
                    (long) (a.cliTS + (b.cliTS - a.cliTS) * t),
                    a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t,
                    a.vx + (b.vx - a.vx) * t,
                    a.vy + (b.vy - a.vy) * t,
                    (t < 0.5 ? a.facing : b.facing),
                    (t < 0.5 ? a.onGround : b.onGround)
            );
        }
    }

    static final class ClockAlign {
        private boolean inited = false;
        private double offsetMs = 0.0;
        private static final double ALPHA = 0.1;

        synchronized void update(long cliTs, long srvTs) {
            double off = srvTs - (double) cliTs;
            if (!inited) { offsetMs = off; inited = true; }
            else { offsetMs = offsetMs * (1 - ALPHA) + off * ALPHA; }
        }

        synchronized long toServerTime(long cliTs) {
            return (long) (cliTs + offsetMs);
        }
    }

    public static final class DamageResult {
        public final int hp; public final boolean dead;
        public DamageResult(int hp, boolean dead) { this.hp = hp; this.dead = dead; }
    }

    public static final class HitInfo {
        public final int victimId; public final double t;
        public HitInfo(int victimId, double t) { this.victimId = victimId; this.t = t; }
    }
}