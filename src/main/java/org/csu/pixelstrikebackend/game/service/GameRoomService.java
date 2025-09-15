// src/main/java/org/csu/pixelstrikebackend/game/service/GameRoomService.java
package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.csu.pixelstrikebackend.game.geom.HitMath;
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

    @Autowired private GameSessionManager sessionManager;
    @Autowired private PlayerStateManager playerStateManager;
    @Autowired private ProjectileManager projectileManager;
    @Autowired private OnlineUserService onlineUserService;
    @Autowired private PlayerSessionService playerSessionService;
    @Autowired private SupplyDropManager supplyDropManager;
    @Autowired private UserProfileMapper userProfileMapper;

    // --- 【新增】客户端状态服务 (来自 fxdemoBakcend) ---
    // 用于处理时钟同步和消息频率控制
    @Autowired private ClientStateService clientStateService;

    // 击退常量
    private static final double KB_X = 220.0;
    private static final double KB_Y = 0.0;
    private static final double BULLET_SPEED = 1500.0;

    // 命中判定用的Hitbox常量
    private static final double HB_OFF_X = 80.0, HB_OFF_Y = 20.0, HB_W = 86.0, HB_H = 160.0;

    public void prepareGame(Long gameId, List<Integer> playerIds) {
        System.out.println("Preparing new game " + gameId + " with players: " + playerIds);
        for (Integer userId : playerIds) {
            playerStateManager.initializePlayer(userId);
        }
    }

    public void addSession(WebSocketSession s) {
        sessionManager.addSession(s);
        clientStateService.registerSession(s); // 注册会话以进行节流和时钟同步
    }

    public void removeSession(WebSocketSession s) {
        Integer userId = (Integer) s.getAttributes().get("userId");
        if (userId != null) {
            if(playerSessionService.isPlayerInGame(userId)){
                playerSessionService.removePlayerFromGame(userId);
                onlineUserService.updateUserStatus(userId, UserStatus.ONLINE);
                System.out.println("Cleaned up session for unexpectedly disconnected user: " + userId);
            }
        }
        sessionManager.removeSession(s);
        clientStateService.unregisterSession(s); // 注销会话
    }

    public void handleJoin(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;

        // 初始化玩家状态，这步保留
        playerStateManager.initializePlayer(userId);

        // 发送 welcome 消息，现在包含playerId
        ObjectNode welcome = mapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("playerId", userId.toString()); // 使用字符串形式以兼容前端
        welcome.put("serverTime", System.currentTimeMillis());
        sessionManager.sendTo(session, welcome.toString());

        // 广播玩家加入的消息，通知其他客户端
        ObjectNode joined = mapper.createObjectNode();
        joined.put("type", "join_broadcast");
        joined.put("id", userId);
        UserProfile profile = userProfileMapper.selectById(userId);
        joined.put("name", profile != null ? profile.getNickname() : "Player " + userId);
        sessionManager.broadcastToOthers(session, joined.toString());
    }

    // --- 【重大修改】状态处理 (融合 fxdemoBakcend 逻辑) ---
    public void handleState(WebSocketSession session, JsonNode root) {
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;

        long now = System.currentTimeMillis();
        // 1. 消息节流
        if (!clientStateService.allowAnyMessage(session, now) || !clientStateService.allowStateMessage(session, now)) {
            return;
        }

        // 2. 时钟同步
        long cliTS = root.path("ts").asLong(0);
        clientStateService.updateClock(session, cliTS, now);

        // 3. 序列号检查
        long seq = root.path("seq").asLong(0);
        if (!playerStateManager.acceptStateSeq(userId, seq)) {
            return; // 拒绝乱序或重复的包
        }

        double x = root.path("x").asDouble(), y = root.path("y").asDouble();
        double vx = root.path("vx").asDouble(), vy = root.path("vy").asDouble();
        boolean facing = root.path("facing").asBoolean(), onGround = root.path("onGround").asBoolean();

        // 4. 记录权威快照
        playerStateManager.recordStateSnapshot(userId, now, cliTS, x, y, vx, vy, facing, onGround);

        // 5. 广播给其他玩家
        ObjectNode stateToBroadcast = (ObjectNode) root.deepCopy();
        stateToBroadcast.put("id", userId);
        stateToBroadcast.put("serverTime", now);
        sessionManager.broadcastToOthers(session, stateToBroadcast.toString());
    }

    // --- 【重大修改】射击处理 (融合 fxdemoBakcend 逻辑) ---
    public void handleShot(WebSocketSession session, JsonNode root) {
        Integer shooterId = (Integer) session.getAttributes().get("userId");
        if (shooterId == null) return;

        // 1. 从消息中获取射击信息
        double ox = root.path("ox").asDouble(), oy = root.path("oy").asDouble();
        double dx = root.path("dx").asDouble(), dy = root.path("dy").asDouble();
        double range = root.path("range").asDouble(1500); // 默认射程
        int damage = root.path("damage").asInt(10); // 默认伤害
        String weaponType = root.path("weaponType").asText("Pistol"); // 默认武器

        // 2. 【关键融合】创建您项目中的 ServerProjectile，它包含了武器伤害和类型
        ServerProjectile serverProjectile = new ServerProjectile(
                shooterId, ox, oy, dx, dy, BULLET_SPEED, range, damage, weaponType
        );
        // projectileManager.addProjectile(serverProjectile); // 我们现在改为立即校验，不再需要延迟处理

        // 3. 【新增】将客户端射击时间戳转换为服务器时间戳
        long cliTS = root.path("ts").asLong(System.currentTimeMillis());
        long shotSrvTS = clientStateService.toServerTime(session, cliTS);

        // 4. 【新增】进行服务器端命中回放验证
        Optional<HitInfo> hitOpt = validateShot(shooterId, shotSrvTS, ox, oy, dx, dy, range);

        // 5. 如果命中，则处理伤害
        if (hitOpt.isPresent()) {
            HitInfo hit = hitOpt.get();
            // 使用 projectile 自身的伤害值
            handleHit(serverProjectile, hit.victimId, serverProjectile.getDamage());
        }

        // 6. 广播射击视觉效果消息 (这部分保留，让所有客户端能看到子弹飞出)
        ObjectNode shotBroadcast = mapper.createObjectNode();
        shotBroadcast.put("type", "shot");
        shotBroadcast.put("attacker", shooterId);
        shotBroadcast.put("ox", ox); shotBroadcast.put("oy", oy);
        shotBroadcast.put("dx", dx); shotBroadcast.put("dy", dy);
        shotBroadcast.put("range", range);
        shotBroadcast.put("srvTS", System.currentTimeMillis());
        shotBroadcast.put("weaponType", weaponType);
        sessionManager.broadcast(shotBroadcast.toString());
    }

    // --- 【新增】命中校验逻辑 (来自 fxdemoBakcend) ---
    private Optional<HitInfo> validateShot(int shooterId, long shotSrvTS,
                                           double ox, double oy, double dx, double dy, double range) {
        // 规范化方向向量
        double len = Math.hypot(dx, dy);
        if (len < 1e-6 || range <= 0) return Optional.empty();
        dx /= len; dy /= len;

        double bestT = Double.POSITIVE_INFINITY;
        int bestVictim = -1;

        // 遍历所有可能的受害者
        for (Integer victimId : playerStateManager.getHpByPlayer().keySet()) {
            if (victimId.equals(shooterId) || playerStateManager.isDead(victimId)) {
                continue;
            }

            // 获取受害者在射击时刻的插值位置
            Optional<StateSnapshot> sOpt = playerStateManager.interpolateAt(victimId, shotSrvTS);
            if (sOpt.isEmpty()) continue;

            StateSnapshot victimState = sOpt.get();
            // 计算受害者的碰撞盒
            double minX = victimState.x + HB_OFF_X;
            double minY = victimState.y + HB_OFF_Y;
            double maxX = minX + HB_W;
            double maxY = minY + HB_H;

            // 进行射线与AABB的碰撞检测
            double tEnter = HitMath.raySegmentVsAABB(ox, oy, dx * range, dy * range, minX, minY, maxX, maxY);

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

    // --- 【新增】处理命中事件的方法 ---
    private void handleHit(ServerProjectile projectile, int victimId, int damage) {
        int shooterId = projectile.getShooterId();

        DamageResult res = playerStateManager.applyDamage(shooterId, victimId, damage);
        if (res.dead) {
            playerStateManager.recordKill(shooterId, victimId);
        }

        // 计算权威击退
        double sign = projectile.getVelocityX() >= 0 ? 1.0 : -1.0;
        double kx = sign * KB_X;
        double ky = KB_Y;

        // 广播伤害和击退的权威消息
        ObjectNode dmg = mapper.createObjectNode();
        dmg.put("type", "damage");
        dmg.put("attacker", shooterId);
        dmg.put("victim", victimId);
        dmg.put("damage", damage);
        dmg.put("hp", res.hp);
        dmg.put("dead", res.dead);
        dmg.put("kx", kx);
        dmg.put("ky", ky);
        dmg.put("srvTS", System.currentTimeMillis());
        sessionManager.broadcast(dmg.toString());
    }

    // --- 物品拾取逻辑 (完全保留您项目的功能) ---
    public void handleSupplyPickup(WebSocketSession session, JsonNode root) {
        // ... 此处代码与您原项目完全一致，无需修改 ...
        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId == null) return;
        long dropId = root.path("dropId").asLong();
        SupplyDrop drop = supplyDropManager.removeDrop(dropId);
        if (drop != null) {
            String dropType = drop.getType();
            UserProfile pickerProfile = userProfileMapper.selectById(userId);
            String pickerNickname = (pickerProfile != null) ? pickerProfile.getNickname() : "一位玩家";

            switch (dropType) {
                case "HEALTH_PACK":
                    playerStateManager.applyHeal(userId, 50);
                    int newHp = playerStateManager.getHp(userId);
                    ObjectNode healthUpdateMsg = mapper.createObjectNode();
                    healthUpdateMsg.put("type", "health_update");
                    healthUpdateMsg.put("userId", userId);
                    healthUpdateMsg.put("hp", newHp);
                    sessionManager.broadcast(healthUpdateMsg.toString());
                    break;
                case "POISON":
                    playerStateManager.applyPoison(userId, 10000);
                    ObjectNode poisonMsg = mapper.createObjectNode();
                    poisonMsg.put("type", "player_poisoned");
                    poisonMsg.put("userId", userId);
                    poisonMsg.put("duration", 10000);
                    sessionManager.broadcast(poisonMsg.toString());
                    break;
                default: // 默认为武器
                    playerStateManager.setWeapon(userId, dropType);
                    ObjectNode weaponEquipMsg = mapper.createObjectNode();
                    weaponEquipMsg.put("type", "weapon_equip");
                    weaponEquipMsg.put("userId", userId);
                    weaponEquipMsg.put("weaponType", dropType);
                    sessionManager.broadcast(weaponEquipMsg.toString());
                    break;
            }
            ObjectNode pickupNotification = mapper.createObjectNode();
            pickupNotification.put("type", "pickup_notification");
            pickupNotification.put("pickerNickname", pickerNickname);
            pickupNotification.put("itemType", dropType);
            sessionManager.broadcast(pickupNotification.toString());

            ObjectNode removeMsg = mapper.createObjectNode();
            removeMsg.put("type", "supply_removed");
            removeMsg.put("dropId", dropId);
            sessionManager.broadcast(removeMsg.toString());
        }
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

    // --- 内部类定义 ---
    // StateSnapshot, RateCounter, ClockAlign, DamageResult, HitInfo
    // 这些内部类直接从 fxdemoBakcend 复制过来即可
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