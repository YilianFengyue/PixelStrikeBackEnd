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

    private static final double GRAVITY = 0.8; // 重力加速度
    private static final double JUMP_STRENGTH = -15.0; // 起跳初速度（负数代表向上）
    private static final double GROUND_Y = 500.0; // 地面Y坐标

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
            }

            // 2. 更新世界状态
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

    /**
     * 新增方法：收集本局对战结果并上报给大厅服务
     */
    private void reportGameResultsToLobby() {
        System.out.println("Reporting results for game " + this.roomId + " to Lobby Service.");
        // TODO:
        // 1. 收集所有玩家的最终战绩 (Kills, Deaths, Score等)。
        // 2. 构建一个包含战绩数据的DTO。
        // 3. 使用HTTP客户端(例如 WebClient 或 RestTemplate)
        //    向大厅服务的一个内部API地址(例如 http://lobby-service/internal/matches/results)
        //    发送一个POST请求，上报数据。
    }
//@Override
//public void run() {
//    final long TICK_RATE = 20;
//    final long SKIP_TICKS = 1000 / TICK_RATE;
//    long nextGameTick = System.currentTimeMillis();
//    long tickNumber = 0;
//
//    while (isRunning) {
//        tickNumber++;
//        // 暂时禁用事件列表
//        // List<GameEvent> currentTickEvents = new ArrayList<>();
//
//        // 1. 处理输入指令 (简化版)
//        while (!commandQueue.isEmpty()) {
//            UserCommand command = commandQueue.poll();
//            if (command == null) continue;
//
//            PlayerState playerState = playerStates.get(command.getPlayerId());
//            if (playerState == null) continue;
//
//            // --- 只保留最基础的移动逻辑 ---
//            double newX = playerState.getX() + command.getMoveInput() * 5.0;
//            playerState.setX(newX);
//        }
//
//        // 2. 更新世界状态 (暂时禁用)
//        // --- D. 处理复活 ---
//        /*
//        long currentTime = System.currentTimeMillis();
//        for (Map.Entry<String, Long> entry : deadPlayerTimers.entrySet()) {
//            if (currentTime >= entry.getValue()) {
//                PlayerState playerToRespawn = playerStates.get(entry.getKey());
//                if (playerToRespawn != null) {
//                    playerToRespawn.setHealth(PLAYER_MAX_HEALTH);
//                    playerToRespawn.setCurrentAction(PlayerState.PlayerActionState.IDLE);
//                    playerToRespawn.setX(100);
//                    playerToRespawn.setY(100);
//                }
//                deadPlayerTimers.remove(entry.getKey());
//            }
//        }
//        */
//
//        // 3. 广播快照
//        GameStateSnapshot snapshot = new GameStateSnapshot();
//        snapshot.setTickNumber(tickNumber);
//
//        // --- 使用Stream和拷贝构造函数创建PlayerStates的深拷贝 ---
//        Map<String, PlayerState> playerStatesCopy = this.playerStates.entrySet().stream()
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        entry -> new PlayerState(entry.getValue()) // <-- 使用拷贝构造函数
//                ));
//        snapshot.setPlayers(playerStatesCopy);
//
////        snapshot.setEvents(currentTickEvents);
//        broadcaster.broadcast(this.sessions.values(), snapshot);
//
//        // 4. 控制Tick率 (保持不变)
//        nextGameTick += SKIP_TICKS;
//        long sleepTime = nextGameTick - System.currentTimeMillis();
//        if (sleepTime >= 0) {
//            try {
//                Thread.sleep(sleepTime);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                isRunning = false;
//            }
//        }
//    }
//    System.out.printf("Game room %s has been shut down.\n", roomId);
//}


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