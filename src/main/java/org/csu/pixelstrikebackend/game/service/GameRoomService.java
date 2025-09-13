package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.csu.pixelstrikebackend.game.model.ServerProjectile;
import org.csu.pixelstrikebackend.game.model.SupplyDrop;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;

@Service
public class GameRoomService {

    private final ObjectMapper mapper = new ObjectMapper();

    // --- 注入新的、解耦的Service ---
    @Autowired private GameSessionManager sessionManager;
    @Autowired private PlayerStateManager playerStateManager;
    @Autowired private ClientStateService clientStateService;
    @Autowired private ProjectileManager projectileManager;
    @Autowired private OnlineUserService onlineUserService;
    @Autowired private PlayerSessionService playerSessionService;
    @Autowired private SupplyDropManager supplyDropManager;
    @Autowired private UserProfileMapper userProfileMapper;

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
        Integer userId = (Integer) s.getAttributes().get("userId");
        if (userId != null) {
            // 这个逻辑现在主要处理玩家在游戏中途意外断线的情况
            if(playerSessionService.isPlayerInGame(userId)){
                playerSessionService.removePlayerFromGame(userId);
                onlineUserService.updateUserStatus(userId, UserStatus.ONLINE);
                System.out.println("Cleaned up session for unexpectedly disconnected user: " + userId);
            }
        }
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

        int damage = root.path("damage").asInt(10);
        String weaponType = root.path("weaponType").asText("Pistol"); // 提供一个默认值
        ServerProjectile serverProjectile = new ServerProjectile(
                shooterId, ox, oy, dx, dy, BULLET_SPEED, range,
                damage, // 传入真实的伤害
                weaponType // 传入真实的武器类型
        );
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
        shot.put("weaponType", weaponType);
        sessionManager.broadcast(shot.toString());

    }

    public void handleSupplyPickup(WebSocketSession session, JsonNode root) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;
        long dropId = root.path("dropId").asLong();
        SupplyDrop drop = supplyDropManager.removeDrop(dropId);
        if (drop != null) {
            String dropType = drop.getType();
            UserProfile pickerProfile = userProfileMapper.selectById(userId);
            String pickerNickname = (pickerProfile != null) ? pickerProfile.getNickname() : "一位玩家";

            if ("HEALTH_PACK".equals(dropType)) {
                playerStateManager.applyHeal(userId, 50);

                // 广播血量更新消息 (这个逻辑保持不变)
                int newHp = playerStateManager.getHp(userId);
                ObjectNode healthUpdateMsg = mapper.createObjectNode();
                healthUpdateMsg.put("type", "health_update");
                healthUpdateMsg.put("userId", userId);
                healthUpdateMsg.put("hp", newHp);
                sessionManager.broadcast(healthUpdateMsg.toString());

            } else { // 如果不是血包，那就是武器
                // 1. 在服务器上更新玩家的当前武器状态
                playerStateManager.setWeapon(userId, dropType);

                // 2. 广播一个新的消息，通知所有客户端该玩家已切换武器
                ObjectNode weaponEquipMsg = mapper.createObjectNode();
                weaponEquipMsg.put("type", "weapon_equip");
                weaponEquipMsg.put("userId", userId);
                weaponEquipMsg.put("weaponType", dropType);
                sessionManager.broadcast(weaponEquipMsg.toString());
            }

            // 广播一个全局的拾取通知
            ObjectNode pickupNotification = mapper.createObjectNode();
            pickupNotification.put("type", "pickup_notification");
            pickupNotification.put("pickerNickname", pickerNickname);
            pickupNotification.put("itemType", dropType);
            sessionManager.broadcast(pickupNotification.toString());

            // 统一广播移除消息
            ObjectNode removeMsg = mapper.createObjectNode();
            removeMsg.put("type", "supply_removed");
            removeMsg.put("dropId", dropId);
            sessionManager.broadcast(removeMsg.toString());
        }
        // 如果 drop 为 null，说明这个物品已经被别人抢先了，服务器不做任何事。
    }

    public void handleLeave(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            ObjectNode leave = mapper.createObjectNode();
            leave.put("type", "leave");
            leave.put("id", userId);
            sessionManager.broadcastToOthers(session, leave.toString());
            // 点击“返回大厅”按钮会触发此方法，最终会调用 removeSession，所以这里无需重复操作
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