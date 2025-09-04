package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
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
    // 新增: 胜利条件
    private static final int KILLS_TO_WIN = 5;

    // 新增: 存储 sessionId -> userId 的映射
    private final Map<String, Integer> sessionToUserIdMap = new ConcurrentHashMap<>();
    // 新增: 存储 userId -> 战绩 的映射
    private final Map<Integer, MatchParticipant> statistics = new ConcurrentHashMap<>();

    private final WebSocketBroadcastService broadcaster;

    private final GameRoomManager roomManager; // 保存一个引用

    public GameRoom(String roomId, WebSocketBroadcastService broadcaster, GameRoomManager roomManager) {
        this.roomId = roomId;
        this.broadcaster = broadcaster;
        this.roomManager = roomManager; // 构造时传入
    }

    /*public void addPlayer(WebSocketSession session) {
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
    */

    // 修改 addPlayer 方法以接收 userId
    public void addPlayer(WebSocketSession session, Integer userId) {
        sessions.put(session.getId(), session);
        sessionToUserIdMap.put(session.getId(), userId); // 存映射关系

        // 初始化玩家状态
        PlayerState initialState = new PlayerState();
        initialState.setPlayerId(session.getId());
        initialState.setX(100); // 示例初始位置
        initialState.setY(460); // 示例初始位置
        initialState.setHealth(100);
        initialState.setCurrentAction(PlayerState.PlayerActionState.IDLE);
        playerStates.put(session.getId(), initialState);

        // 初始化战绩统计
        MatchParticipant participant = new MatchParticipant();
        participant.setUserId(userId);
        participant.setKills(0);
        participant.setDeaths(0);
        statistics.put(userId, participant);

        System.out.printf("玩家 UserID:%d (Session:%s) 加入房间 %s\n", userId, session.getId(), roomId);
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

                                // --- 新增: 记录击杀和死亡 ---
                                Integer attackerUserId = sessionToUserIdMap.get(attackerState.getPlayerId());
                                Integer targetUserId = sessionToUserIdMap.get(targetState.getPlayerId());
                                if(attackerUserId != null && targetUserId != null){
                                    statistics.get(attackerUserId).setKills(statistics.get(attackerUserId).getKills() + 1);
                                    statistics.get(targetUserId).setDeaths(statistics.get(targetUserId).getDeaths() + 1);

                                    // --- 新增: 检查胜利条件 ---
                                    if (statistics.get(attackerUserId).getKills() >= KILLS_TO_WIN) {
                                        endGame(attackerUserId); // 游戏结束
                                        break; // 结束当前tick的处理
                                    }
                                }

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
        reportGameResults();
        System.out.printf("Game room %s has been shut down.\n", roomId);
    }

    // --- 新增: 游戏结束方法 ---
    private void endGame(Integer winnerUserId) {
        System.out.printf("游戏 %s 结束! 胜利者是 UserID: %d\n", roomId, winnerUserId);
        this.isRunning = false; // 停止游戏循环

        // 1. 整理最终战绩
        List<MatchParticipant> finalResults = new ArrayList<>();
        statistics.forEach((userId, participant) -> {
            // 设置排名
            participant.setRanking(userId.equals(winnerUserId) ? 1 : 2);
            finalResults.add(participant);
        });

        // 2. 上报战绩
        reportGameResults(finalResults);

        // 3. 通知所有客户端游戏结束，并关闭连接
        GameStateSnapshot finalSnapshot = new GameStateSnapshot();
        finalSnapshot.setTickNumber(-1); // 使用特殊 tick 表示结束
        GameEvent endEvent = new GameEvent();
        endEvent.setType(GameEvent.EventType.GAME_OVER); // (需要在GameStateSnapshot中新增此类型)
        endEvent.setRelatedPlayerId(winnerUserId.toString());
        finalSnapshot.setEvents(List.of(endEvent));
        broadcaster.broadcast(this.sessions.values(), finalSnapshot);

        // 稍作延迟后关闭所有连接，确保结束消息能发出去
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 等待1秒
                for(WebSocketSession session : sessions.values()){
                    if(session.isOpen()){
                        session.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void reportGameResults(List<MatchParticipant> results) {
        Long gameId = Long.parseLong(this.roomId);
        this.roomManager.onGameConcluded(gameId, results);
    }

    private void reportGameResults() {
        // 1. 收集战绩
        List<MatchParticipant> results = new ArrayList<>();
        // ... (填充results)

        // 2. 通过roomManager上报
        Long gameId = Long.parseLong(this.roomId);
        this.roomManager.onGameConcluded(gameId, results);
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