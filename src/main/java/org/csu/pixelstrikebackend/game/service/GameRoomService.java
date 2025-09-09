// 文件路径: src/main/java/org/csu/pixelstrikebackend/game/service/GameRoomService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.csu.pixelstrikebackend.game.geom.HitMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GameRoomService {

    // --- 字段保持不变 ---
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger gameIdGen = new AtomicInteger(1000);
    private final Map<String, Integer> sessionToGameId = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> gameIdToUserId = new ConcurrentHashMap<>();
    private final Map<String, String>  playerNameBySession = new ConcurrentHashMap<>();
    private final Map<String, RateCounter> anyCounterBySession = new ConcurrentHashMap<>();
    private final Map<String, RateCounter> stateCounterBySession = new ConcurrentHashMap<>();
    private static final long SNAPSHOT_KEEP_MS = 2000;
    private final Map<Integer, Deque<StateSnapshot>> snapshotsByPlayer = new ConcurrentHashMap<>();
    private static final int MAX_HP = 100;
    private final Map<Integer, Integer> hpByPlayer = new ConcurrentHashMap<>();
    private final Set<Integer> deadSet = ConcurrentHashMap.newKeySet();
    private final Map<String, ClockAlign> clockBySession = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSeqByPlayer = new ConcurrentHashMap<>();
    private static final double HB_OFF_X = 80.0, HB_OFF_Y = 20.0, HB_W = 86.0, HB_H = 160.0;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final double KB_X = 220.0, KB_Y = 0.0;
    private final GameManager gameManager;

    @Autowired
    public GameRoomService(@Lazy GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // --- 【关键修改】所有 handleXXX 和消息发送方法返回 Mono<Void> ---

    public Mono<Void> handleHello(WebSocketSession session) {
        ObjectNode hello = mapper.createObjectNode();
        hello.put("type", "hello");
        hello.put("serverTime", System.currentTimeMillis());
        return sendTo(session, hello.toString());
    }

    public Mono<Void> handleJoin(WebSocketSession session, JsonNode root) {
        int gameId = ensurePlayerGameId(session);
        String name = root.path("name").asText("");
        setPlayerName(session, name);

        ObjectNode welcome = mapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("id", gameId);
        welcome.put("serverTime", System.currentTimeMillis());

        ObjectNode joined = mapper.createObjectNode();
        joined.put("type", "join_broadcast");
        joined.put("id", gameId);
        joined.put("name", name);
        joined.put("serverTime", System.currentTimeMillis());

        System.out.println("JOIN: gameId=" + gameId + " name=" + name);

        // 将发送 welcome 和广播 join 两个异步操作链接起来
        return sendTo(session, welcome.toString())
                .then(broadcast(joined.toString()));
    }

    public Mono<Void> handleState(WebSocketSession session, JsonNode root) {
        Integer gameId = getPlayerGameId(session);
        if (gameId == null) gameId = ensurePlayerGameId(session);

        long now = System.currentTimeMillis();
        if (!allowAnyMessage(session, now) || !allowStateMessage(session, now)) {
            return Mono.empty(); // 如果节流，返回一个完成的Mono
        }

        long cliTS = root.path("ts").asLong(0);
        updateClock(session, cliTS, now);

        double x = root.path("x").asDouble(), y = root.path("y").asDouble();
        double vx = root.path("vx").asDouble(), vy = root.path("vy").asDouble();
        boolean facing = root.path("facing").asBoolean(), onGround = root.path("onGround").asBoolean();
        long seq = root.path("seq").asLong(0);

        if (!acceptStateSeq(gameId, seq)) {
            return Mono.empty();
        }

        recordStateSnapshot(gameId, now, cliTS, x, y, vx, vy, facing, onGround);

        ObjectNode state = (ObjectNode) root.deepCopy();
        state.put("type", "state");
        state.put("id", gameId);
        state.put("serverTime", now);
        return broadcast(state.toString());
    }

    public Mono<Void> handleShot(WebSocketSession session, JsonNode root) {
        Integer shooterId = getPlayerGameId(session);
        if (shooterId == null) shooterId = ensurePlayerGameId(session);

        long now = System.currentTimeMillis();
        long cliTS = root.path("ts").asLong(0);
        long shotSrvTS = toServerTime(session, cliTS);
        double ox = root.path("ox").asDouble(), oy = root.path("oy").asDouble();
        double dx = root.path("dx").asDouble(), dy = root.path("dy").asDouble();
        double range = root.path("range").asDouble(0);
        int damage = root.path("damage").asInt(0);

        ObjectNode shot = mapper.createObjectNode();
        shot.put("type", "shot");
        shot.put("attacker", shooterId);
        shot.put("by", shooterId);
        shot.put("ox", ox); shot.put("oy", oy);
        shot.put("dx", dx); shot.put("dy", dy);
        shot.put("range", range);
        shot.put("srvTS", now);

        Mono<Void> shotBroadcast = broadcast(shot.toString());

        var hitOpt = validateShot(shooterId, shotSrvTS, ox, oy, dx, dy, range);
        if (hitOpt.isEmpty()) {
            return shotBroadcast;
        }

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

        return shotBroadcast.then(broadcast(dmg.toString()));
    }

    public Mono<Void> handleLeave(WebSocketSession session) {
        Integer gameId = getPlayerGameId(session);
        if (gameId != null) {
            ObjectNode leave = mapper.createObjectNode();
            leave.put("type", "leave");
            leave.put("id", gameId);
            leave.put("serverTime", System.currentTimeMillis());
            System.out.println("LEAVE: gameId=" + gameId + " session=" + session.getId());
            return broadcast(leave.toString());
        } else {
            System.out.println("WS CLOSE (no id): " + session.getId());
            return Mono.empty();
        }
    }

    public Mono<Void> broadcast(String json) {
        System.out.println("[BROADCAST] -> " + sessions.values().size() + " sessions : " + json);
        return Flux.fromIterable(sessions.values())
                .filter(WebSocketSession::isOpen)
                .flatMap(s -> sendTo(s, json))
                .then();
    }

    public Mono<Void> sendTo(WebSocketSession s, String json) {
        if (s != null && s.isOpen()) {
            WebSocketMessage message = s.textMessage(json);
            return s.send(Mono.just(message))
                    .doOnError(e -> System.err.println("发送消息失败 to session " + s.getId() + ": " + e.getMessage()));
        }
        return Mono.empty();
    }

    // --- 以下是其他所有未改变的方法，保持原样 ---
    public void addSession(WebSocketSession s) { sessions.put(s.getId(), s); anyCounterBySession.put(s.getId(), new RateCounter(200)); stateCounterBySession.put(s.getId(), new RateCounter(120)); clockBySession.put(s.getId(), new ClockAlign()); }
    public void removeSession(WebSocketSession s) { sessions.remove(s.getId()); Integer gameId = sessionToGameId.remove(s.getId()); if (gameId != null) gameIdToUserId.remove(gameId); playerNameBySession.remove(s.getId()); anyCounterBySession.remove(s.getId()); stateCounterBySession.remove(s.getId()); clockBySession.remove(s.getId()); }
    public int ensurePlayerGameId(WebSocketSession s) { return sessionToGameId.computeIfAbsent(s.getId(), k -> { Integer userId = (Integer) s.getAttributes().get("userId"); int gameId = gameIdGen.getAndIncrement(); if (userId != null) gameIdToUserId.put(gameId, userId); hpByPlayer.put(gameId, MAX_HP); deadSet.remove(gameId); return gameId; }); }
    public Integer getPlayerGameId(WebSocketSession s) { return sessionToGameId.get(s.getId()); }
    public void setPlayerName(WebSocketSession s, String name) { if (s != null) playerNameBySession.put(s.getId(), name); }
    public void updateClock(WebSocketSession s, long clientTs, long srvTs) { if (clientTs <= 0) return; ClockAlign ca = clockBySession.get(s.getId()); if (ca != null) ca.update(clientTs, srvTs); }
    public long toServerTime(WebSocketSession s, long clientTs) { ClockAlign ca = clockBySession.get(s.getId()); return (ca != null) ? ca.toServerTime(clientTs) : clientTs; }
    public void recordStateSnapshot(int playerId, long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) { Deque<StateSnapshot> buf = snapshotsByPlayer.computeIfAbsent(playerId, k -> new ArrayDeque<>()); synchronized (buf) { buf.addLast(new StateSnapshot(srvTS, cliTS, x, y, vx, vy, facing, onGround)); long min = srvTS - SNAPSHOT_KEEP_MS; while (!buf.isEmpty() && buf.peekFirst().srvTS < min) buf.removeFirst(); if (buf.size() > 600) buf.removeFirst(); } }
    public Optional<StateSnapshot> interpolateAt(int playerId, long targetSrvTS) { Deque<StateSnapshot> buf = snapshotsByPlayer.get(playerId); if (buf == null || buf.isEmpty()) return Optional.empty(); synchronized (buf) { StateSnapshot prev = null, next = null; for (StateSnapshot s : buf) { if (s.srvTS <= targetSrvTS) prev = s; if (s.srvTS >= targetSrvTS) { next = s; break; } } if (prev == null) prev = buf.peekFirst(); if (next == null) next = buf.peekLast(); if (prev == null) return Optional.empty(); if (next == null || next == prev || next.srvTS == prev.srvTS) return Optional.of(prev); double t = (targetSrvTS - prev.srvTS) / (double)(next.srvTS - prev.srvTS); t = Math.max(0, Math.min(1, t)); return Optional.of(StateSnapshot.lerp(prev, next, t)); } }
    public DamageResult applyDamage(int byId, int victimId, int amount) { if (amount <= 0) return new DamageResult(0, false); if (byId == victimId) return new DamageResult(0, false); hpByPlayer.putIfAbsent(victimId, MAX_HP); int hp = hpByPlayer.get(victimId); if (hp <= 0) return new DamageResult(0, true); hp = Math.max(0, hp - amount); hpByPlayer.put(victimId, hp); boolean dead = (hp == 0); if (dead) deadSet.add(victimId); return new DamageResult(hp, dead); }
    public Optional<HitInfo> validateShot(int shooterId, long shotSrvTS, double ox, double oy, double dx, double dy, double range) { dx = clampDir(dx); dy = clampDir(dy); double len = Math.hypot(dx, dy); if (len < 1e-6 || range <= 0) return Optional.empty(); dx /= len; dy /= len; double rx = dx * range, ry = dy * range; double bestT = Double.POSITIVE_INFINITY; int bestVictim = -1; for (Integer victimId : hpByPlayer.keySet()) { if (victimId.equals(shooterId)) continue; if (deadSet.contains(victimId)) continue; Optional<StateSnapshot> sOpt = interpolateAt(victimId, shotSrvTS); if (sOpt.isEmpty()) continue; StateSnapshot s = sOpt.get(); double minX = s.x + HB_OFF_X, minY = s.y + HB_OFF_Y, maxX = minX + HB_W, maxY = minY + HB_H; double tEnter = HitMath.raySegmentVsAABB(ox, oy, rx, ry, minX, minY, maxX, maxY); if (tEnter < bestT) { bestT = tEnter; bestVictim = victimId; } } if (bestVictim >= 0 && bestT <= 1.0) return Optional.of(new HitInfo(bestVictim, bestT)); return Optional.empty(); }
    public boolean acceptStateSeq(int playerId, long seq) { if (seq <= 0) return true; Long last = lastSeqByPlayer.get(playerId); if (last != null && seq <= last) return false; lastSeqByPlayer.put(playerId, seq); return true; }
    public boolean allowAnyMessage(WebSocketSession s, long nowMs) { RateCounter rc = anyCounterBySession.get(s.getId()); return rc == null || rc.allow(nowMs); }
    public boolean allowStateMessage(WebSocketSession s, long nowMs) { RateCounter rc = stateCounterBySession.get(s.getId()); return rc == null || rc.allow(nowMs); }
    static final class RateCounter { private final int maxPerSecond; private long windowStartMs = 0; private int count = 0; RateCounter(int maxPerSecond) { this.maxPerSecond = maxPerSecond; } synchronized boolean allow(long nowMs) { if (nowMs - windowStartMs >= 1000) { windowStartMs = nowMs; count = 0; } return count++ < maxPerSecond; } }
    public static final class StateSnapshot { public final long srvTS, cliTS; public final double x, y, vx, vy; public final boolean facing, onGround; public StateSnapshot(long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) { this.srvTS = srvTS; this.cliTS = cliTS; this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.facing = facing; this.onGround = onGround; } public static StateSnapshot lerp(StateSnapshot a, StateSnapshot b, double t) { return new StateSnapshot( (long)(a.srvTS + (b.srvTS - a.srvTS) * t), (long)(a.cliTS + (b.cliTS - a.cliTS) * t), a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.vx + (b.vx - a.vx) * t, a.vy + (b.vy - a.vy) * t, (t < 0.5 ? a.facing : b.facing), (t < 0.5 ? a.onGround : b.onGround) ); } }
    static final class ClockAlign { private boolean inited = false; private double offsetMs = 0.0; private static final double ALPHA = 0.1; synchronized void update(long cliTs, long srvTs) { double off = srvTs - (double) cliTs; if (!inited) { offsetMs = off; inited = true; } else { offsetMs = offsetMs * (1 - ALPHA) + off * ALPHA; } } synchronized long toServerTime(long cliTs) { return (long) (cliTs + offsetMs); } }
    public static final class DamageResult { public final int hp; public final boolean dead; public DamageResult(int hp, boolean dead) { this.hp = hp; this.dead = dead; } }
    public static final class HitInfo { public final int victimId; public final double t; public HitInfo(int victimId, double t) { this.victimId = victimId; this.t = t; } }
    public void prepareGame(Long gameId, List<Integer> playerIds) { System.out.println("Preparing new game " + gameId + " with players: " + playerIds); }
    private static double clampDir(double v) { if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0; if (Math.abs(v) > 1e4) return Math.signum(v); return v; }
}