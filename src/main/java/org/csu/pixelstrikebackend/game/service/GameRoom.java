package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.service.WebSocketBroadcastService;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameRoom implements Runnable {

    private static final double PLAYER_WIDTH = 40.0; // 假设玩家碰撞盒宽度
    private static final double PLAYER_HEIGHT = 40.0; // 假设玩家碰撞盒高度
    private static final int PLAYER_MAX_HEALTH = 100;
    private static final int WEAPON_DAMAGE = 25;
    private static final long RESPAWN_TIME_MS = 3000; // 死亡后3秒复活
    private static final double DEATH_ZONE_Y = 1200.0; // Y坐标超过这个值就死亡


    private static final double GRAVITY = 0.8; // 重力加速度
    private static final double JUMP_STRENGTH = -15.0; // 起跳初速度（负数代表向上）
    private static final double GROUND_Y = 500.0; // 地面Y坐标
    private static final byte JUMP_ACTION = 1; // 约定: actions的第0位(值为1)代表跳跃

    // 临时的死亡玩家计时器 ---
    private final Map<String, Long> deadPlayerTimers = new ConcurrentHashMap<>();

    private final String roomId;
    private final Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<UserCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isRunning = true; // 控制循环是否继续

    private final WebSocketBroadcastService broadcaster;

    public GameRoom(String roomId, WebSocketBroadcastService broadcaster) {
        this.roomId = roomId;
        this.broadcaster = broadcaster;
    }

    public void addPlayer(WebSocketSession session) {
        sessions.put(session.getId(), session);
        PlayerState initialState = new PlayerState();
        initialState.setPlayerId(session.getId());
        initialState.setX(100);
        initialState.setY(100);
        initialState.setHealth(PLAYER_MAX_HEALTH); // <-- 新增: 设置满血
        initialState.setCurrentAction(PlayerState.PlayerActionState.IDLE); // <-- 新增: 设置初始状态
        playerStates.put(session.getId(), initialState);
        System.out.printf("Player %s joined room %s\n", session.getId(), roomId);
    }
    
    public void removePlayer(WebSocketSession session) {
        sessions.remove(session.getId());
        playerStates.remove(session.getId());
        System.out.printf("Player %s left room %s\n", session.getId(), roomId);
    }
    
    public void queueCommand(UserCommand command) {
        commandQueue.add(command);
    }
    
    public void stop() {
        this.isRunning = false;
    }

    @Override
    public void run() {
        final long TICK_RATE = 20;
        final long SKIP_TICKS = 1000 / TICK_RATE;
        long nextGameTick = System.currentTimeMillis();
        long tickNumber = 0; // Tick计数器

        while (isRunning) {
            tickNumber++;
            List<GameEvent> currentTickEvents = new ArrayList<>(); // 存储当前Tick发生的所有事件

            // 1. 处理输入指令
            while (!commandQueue.isEmpty()) {
                UserCommand command = commandQueue.poll();
                if (command == null) continue;

                PlayerState attackerState = playerStates.get(command.getPlayerId());
                // 只有存活的玩家才能操作
                if (attackerState == null || attackerState.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
                    continue;
                }

                // --- A. 处理移动 ---
                double newX = attackerState.getX() + command.getMoveInput() * 5.0;
                attackerState.setX(newX);
                attackerState.setFacingRight(command.getMoveInput() >= 0); // 更新朝向

                // --- B. 处理开火 (Hitscan) ---
                // 约定: actions的第1位(值为2)代表开火
                if ((command.getActions() & 2) != 0) {
                    attackerState.setCurrentAction(PlayerState.PlayerActionState.SHOOT);

                    // 遍历所有其他玩家进行命中判定
                    for (PlayerState targetState : playerStates.values()) {
                        // 不能打自己，也不能打已经死亡的玩家
                        if (targetState.getPlayerId().equals(attackerState.getPlayerId()) || targetState.getCurrentAction() == PlayerState.PlayerActionState.DEAD) {
                            continue;
                        }

                        // 简化的AABB碰撞检测
                        if (isHit(attackerState, targetState)) {
                            // --- C. 处理受击与死亡 ---
                            targetState.setHealth(targetState.getHealth() - WEAPON_DAMAGE);
                            targetState.setCurrentAction(PlayerState.PlayerActionState.HIT);

                            // 施加击退力 (Knockback)
                            double knockbackStrength = 25.0; // 这个值可以后续调整以优化手感
                            targetState.setVelocityX(targetState.getVelocityX() + (attackerState.isFacingRight() ? knockbackStrength : -knockbackStrength));
                            targetState.setVelocityY(targetState.getVelocityY() - 10.0); // 稍微向上击飞
                            // 创建一个击中事件
                            GameEvent hitEvent = new GameEvent();
                            hitEvent.setType(GameEvent.EventType.PLAYER_HIT);
                            hitEvent.setRelatedPlayerId(targetState.getPlayerId());
                            currentTickEvents.add(hitEvent);

                            // 检查是否死亡
                            if (targetState.getHealth() <= 0) {
                                targetState.setCurrentAction(PlayerState.PlayerActionState.DEAD);
                                deadPlayerTimers.put(targetState.getPlayerId(), System.currentTimeMillis() + RESPAWN_TIME_MS);

                                GameEvent dieEvent = new GameEvent();
                                dieEvent.setType(GameEvent.EventType.PLAYER_DIED);
                                dieEvent.setRelatedPlayerId(targetState.getPlayerId());
                                currentTickEvents.add(dieEvent);
                            }
                            // 一次只能击中一个目标，跳出循环
                            break;
                        }
                    }
                }

                // 处理跳跃
                // 约定: actions的第0位(值为1)代表跳跃
                if ((command.getActions() & JUMP_ACTION) != 0) {
                    // a. 在地面上，可以起跳
                    if (attackerState.getY() >= GROUND_Y) {
                        attackerState.setVelocityY(JUMP_STRENGTH);
                        attackerState.setCanDoubleJump(true); // 离开地面后获得二段跳能力
                    }
                    // b. 在空中，且有二段跳能力
                    else if (attackerState.isCanDoubleJump()) {
                        attackerState.setVelocityY(JUMP_STRENGTH); // 再次赋予向上的速度
                        attackerState.setCanDoubleJump(false); // 使用掉二段跳能力
                    }
                }
            }

            // 2. 更新世界状态
            for (PlayerState player : playerStates.values()) {
                // a. 应用重力 (在应用速度之前)
                player.setVelocityY(player.getVelocityY() + GRAVITY);

                // b. 应用速度更新位置 (这部分已在第一步中添加)
                player.setX(player.getX() + player.getVelocityX());
                player.setY(player.getY() + player.getVelocityY());

                // c. 应用空气阻力/摩擦力 (这部分已在第一步中添加)
                player.setVelocityX(player.getVelocityX() * 0.95);

                // d. 地面检测
                if (player.getY() >= GROUND_Y) {
                    player.setY(GROUND_Y);
                    player.setVelocityY(0);
                    // 触地时重置二段跳能力，但这里先不重置，而是在起跳时赋予
                }

                // e. 掉落死亡检测
                // 确保玩家当前不是已经处于死亡状态
                if (player.getY() > DEATH_ZONE_Y && player.getCurrentAction() != PlayerState.PlayerActionState.DEAD) {
                    player.setHealth(0); // 直接致死
                    player.setCurrentAction(PlayerState.PlayerActionState.DEAD);
                    deadPlayerTimers.put(player.getPlayerId(), System.currentTimeMillis() + RESPAWN_TIME_MS);

                    // 创建一个死亡事件广播给客户端
                    GameEvent dieEvent = new GameEvent();
                    dieEvent.setType(GameEvent.EventType.PLAYER_DIED);
                    dieEvent.setRelatedPlayerId(player.getPlayerId());
                    currentTickEvents.add(dieEvent);
                }
            }
            // --- D. 处理复活 ---
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<String, Long>> iterator = deadPlayerTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (currentTime >= entry.getValue()) {
                    PlayerState playerToRespawn = playerStates.get(entry.getKey());
                    if (playerToRespawn != null) {
                        playerToRespawn.setHealth(PLAYER_MAX_HEALTH);
                        playerToRespawn.setCurrentAction(PlayerState.PlayerActionState.IDLE);
                        playerToRespawn.setX(100); // 重置到出生点
                        playerToRespawn.setY(100);
                        playerToRespawn.setVelocityX(0);
                        playerToRespawn.setVelocityY(0);
                    }
                    // 使用迭代器的remove()方法，这是唯一安全的方式
                    iterator.remove();
                }
            }

            // TODO: 更新子弹、物理等

            // 3. 广播快照
            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.setTickNumber(tickNumber); // 设置Tick编号
            snapshot.setPlayers(new ConcurrentHashMap<>(playerStates));
            snapshot.setEvents(currentTickEvents); // <-- 将本轮所有事件打包进快照
            broadcaster.broadcast(this.sessions.values(), snapshot);

            // 4. 控制Tick率
            nextGameTick += SKIP_TICKS;
            long sleepTime = nextGameTick - System.currentTimeMillis();
            if (sleepTime >= 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    isRunning = false;
                }
            }
        }
        System.out.printf("Game room %s has been shut down.\n", roomId);
    }


    // --- 在GameRoom类中新增一个辅助方法用于命中判定 ---
    private boolean isHit(PlayerState attacker, PlayerState target) {
        // 这是一个非常简化的hitscan，只考虑了X轴方向
        // 实际项目中你需要根据 aimAngle 实现更复杂的射线/矩形相交检测
        boolean facingTarget = (attacker.isFacingRight() && target.getX() > attacker.getX()) ||
                (!attacker.isFacingRight() && target.getX() < attacker.getX());

        if (!facingTarget) {
            return false;
        }

        // 检查Y轴是否在同一水平线上
        return Math.abs(attacker.getY() - target.getY()) < PLAYER_HEIGHT;
    }
}