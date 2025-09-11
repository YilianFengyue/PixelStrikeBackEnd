package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.csu.pixelstrikebackend.game.model.ServerProjectile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;

@Service
public class GameRoomService {

    private final ObjectMapper mapper = new ObjectMapper();

    // --- 注入新的、解耦的Service ---
    @Autowired private GameManager gameManager;
    @Autowired private GameSessionManager sessionManager;
    @Autowired private PlayerStateManager playerStateManager;
    @Autowired private HitValidationService hitValidationService;
    @Autowired private ClientStateService clientStateService;
    @Autowired private ProjectileManager projectileManager;

    // 命中判定常量 (可以考虑移到GameConfig)
    private static final double KB_X = 220.0;
    private static final double KB_Y = 0.0;
    private static final double BULLET_SPEED = 1500.0;

    public void prepareGame(Long gameId, List<Integer> playerIds) {
        System.out.println("Preparing new game " + gameId + " with players: " + playerIds);
        for (Integer userId : playerIds) {
            playerStateManager.initializePlayer(userId);
        }
    }

    public void addSession(WebSocketSession s) {
        sessionManager.addSession(s);
        clientStateService.registerSession(s);
    }

    public void removeSession(WebSocketSession s) {
        sessionManager.removeSession(s);
        clientStateService.unregisterSession(s);
    }

    public void handleJoin(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;

        playerStateManager.initializePlayer(userId);

        ObjectNode welcome = mapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("id", userId);
        welcome.put("serverTime", System.currentTimeMillis());
        sessionManager.sendTo(session, welcome.toString());

        ObjectNode joined = mapper.createObjectNode();
        joined.put("type", "join_broadcast");
        joined.put("id", userId);
        joined.put("name", "Player " + userId);
        sessionManager.broadcastToOthers(session, joined.toString());
    }

    public void handleState(WebSocketSession session, JsonNode root) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;

        long now = System.currentTimeMillis();
        if (!clientStateService.allowAnyMessage(session, now) || !clientStateService.allowStateMessage(session, now)) {
            return;
        }

        long cliTS = root.path("ts").asLong(0);
        clientStateService.updateClock(session, cliTS, now);

        long seq = root.path("seq").asLong(0);
        if (!playerStateManager.acceptStateSeq(userId, seq)) {
            return;
        }

        double x = root.path("x").asDouble(), y = root.path("y").asDouble();
        double vx = root.path("vx").asDouble(), vy = root.path("vy").asDouble();
        boolean facing = root.path("facing").asBoolean(), onGround = root.path("onGround").asBoolean();

        playerStateManager.recordStateSnapshot(userId, now, cliTS, x, y, vx, vy, facing, onGround);

        ObjectNode stateToBroadcast = (ObjectNode) root.deepCopy();
        stateToBroadcast.put("id", userId);
        stateToBroadcast.put("serverTime", now);
        sessionManager.broadcastToOthers(session, stateToBroadcast.toString());
    }

    public void handleShot(WebSocketSession session, JsonNode root) {
        Integer shooterId = (Integer) session.getAttributes().get("userId");
        if (shooterId == null) return;

        double ox = root.path("ox").asDouble(), oy = root.path("oy").asDouble();
        double dx = root.path("dx").asDouble(), dy = root.path("dy").asDouble();
        double range = root.path("range").asDouble(0);

        // 不再进行射线检测，而是创建并注册一个权威子弹
        ServerProjectile serverProjectile = new ServerProjectile(shooterId, ox, oy, dx, dy, BULLET_SPEED, range);
        projectileManager.addProjectile(serverProjectile);

        // 广播 shot 消息，让所有客户端生成纯视觉的子弹特效
        // 如果想完全依赖服务器，也可以注释掉下面这段广播
        long now = System.currentTimeMillis();
        ObjectNode shot = mapper.createObjectNode();
        shot.put("type", "shot");
        shot.put("attacker", shooterId);
        shot.put("ox", ox); shot.put("oy", oy);
        shot.put("dx", dx); shot.put("dy", dy);
        shot.put("range", range);
        shot.put("srvTS", now);
        sessionManager.broadcast(shot.toString());

    }

    public void handleLeave(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            ObjectNode leave = mapper.createObjectNode();
            leave.put("type", "leave");
            leave.put("id", userId);
            sessionManager.broadcastToOthers(session, leave.toString());
        }
    }

    // --- 内部类需要保持public，因为其他Service会用到它们 ---
    public static final class StateSnapshot {
        public final long srvTS, cliTS;
        public final double x, y, vx, vy;
        public final boolean facing, onGround;
        public StateSnapshot(long srvTS, long cliTS, double x, double y, double vx, double vy, boolean facing, boolean onGround) {
            this.srvTS = srvTS; this.cliTS = cliTS; this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.facing = facing; this.onGround = onGround;
        }
        public static StateSnapshot lerp(StateSnapshot a, StateSnapshot b, double t) {
            return new StateSnapshot((long)(a.srvTS + (b.srvTS - a.srvTS) * t), (long)(a.cliTS + (b.cliTS - a.cliTS) * t), a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.vx + (b.vx - a.vx) * t, a.vy + (b.vy - a.vy) * t, (t < 0.5 ? a.facing : b.facing), (t < 0.5 ? a.onGround : b.onGround));
        }
    }
    public static final class RateCounter {
        private final int maxPerSecond;
        private long windowStartMs = 0;
        private int count = 0;
        RateCounter(int maxPerSecond) { this.maxPerSecond = maxPerSecond; }
        synchronized boolean allow(long nowMs) {
            if (nowMs - windowStartMs >= 1000) { windowStartMs = nowMs; count = 0; }
            return count++ < maxPerSecond;
        }
    }
    public static final class ClockAlign {
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