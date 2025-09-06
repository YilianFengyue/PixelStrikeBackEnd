package org.csu.pixelstrikebackend.game.service;


import com.google.gson.Gson;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.game.system.*;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class GameRoom {

    // --- 核心服务 ---
    private final InputSystem inputSystem;
    private final CombatSystem combatSystem;
    private final PhysicsSystem physicsSystem;
    private final GameStateSystem gameStateSystem;
    private final GameConditionSystem gameConditionSystem;
    private final GameCountdownSystem gameCountdownSystem;
    private final GameTimerSystem gameTimerSystem;
    private final WebSocketBroadcastService broadcaster;
    private final GameRoomManager roomManager;

    // --- 房间状态 ---
    private final String roomId;
    private final Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Queue<UserCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> deadPlayerTimers = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionToUserIdMap = new ConcurrentHashMap<>();
    private final long gameStartTime;
    private final int expectedPlayerCount;
    private final GameCountdownSystem.RoomState countdownRoomState;

    private enum RoomPhase { WAITING_FOR_PLAYERS, COUNTDOWN, RUNNING, CONCLUDED }
    private volatile RoomPhase currentPhase = RoomPhase.WAITING_FOR_PLAYERS;
    private final Gson gson = new Gson();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Disposable gameLoopSubscription;

    public GameRoom(String roomId,
                    GameRoomManager roomManager,
                    InputSystem inputSystem,
                    CombatSystem combatSystem,
                    PhysicsSystem physicsSystem,
                    GameStateSystem gameStateSystem,
                    GameConditionSystem gameConditionSystem,
                    GameCountdownSystem gameCountdownSystem,
                    GameTimerSystem gameTimerSystem,
                    WebSocketBroadcastService broadcaster,
                    List<Integer> playerIds) {
        this.roomId = roomId;
        this.roomManager = roomManager;
        this.inputSystem = inputSystem;
        this.combatSystem = combatSystem;
        this.physicsSystem = physicsSystem;
        this.gameStateSystem = gameStateSystem;
        this.gameConditionSystem = gameConditionSystem;
        this.gameCountdownSystem = gameCountdownSystem;
        this.gameTimerSystem = gameTimerSystem;
        this.broadcaster = broadcaster;
        this.gameStartTime = System.currentTimeMillis();
        this.expectedPlayerCount = playerIds.size();
        this.countdownRoomState = new GameCountdownSystem.RoomState(roomId);
    }

    public void addPlayer(WebSocketSession session, Integer userId) {
        sessions.put(session.getId(), session);
        sessionToUserIdMap.put(session.getId(), userId);
        PlayerState initialState = new PlayerState();
        initialState.setPlayerId(session.getId());
        initialState.setX(100);
        initialState.setY(100);
        initialState.setHealth(100);
        initialState.setAmmo(30);
        initialState.setKills(0);
        initialState.setDeaths(0);
        initialState.setCurrentAction(PlayerState.PlayerActionState.IDLE);
        playerStates.put(session.getId(), initialState);
        System.out.printf("Player %s (userId: %d) joined room %s\n", session.getId(), userId, roomId);
        sendWelcomeMessage(session);

        // 当所有预期玩家都加入后，开始倒计时
        if (sessions.size() == expectedPlayerCount && isRunning.compareAndSet(false, true)) {
            this.currentPhase = RoomPhase.COUNTDOWN;
            System.out.println("玩家已满，房间 " + roomId + " 进入倒计时阶段，启动游戏循环...");
            startGameLoop();
        }
    }

    private void sendWelcomeMessage(WebSocketSession session) {
        Map<String, String> welcomeMessage = Map.of(
                "type", "welcome",
                "playerId", session.getId()
        );
        String message = gson.toJson(welcomeMessage);
        // 使用 session.send 发送消息，并调用 subscribe() 触发
        session.send(Mono.just(session.textMessage(message))).subscribe();
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
        if (isRunning.compareAndSet(true, false)) {
            if (gameLoopSubscription != null && !gameLoopSubscription.isDisposed()) {
                gameLoopSubscription.dispose(); // 取消订阅，停止循环
            }
            // 确保在循环结束后执行清理工作
            onGameConcluded();
        }
    }

    private void startGameLoop() {
        final long TICK_RATE_MS = 50; // 20Hz
        AtomicLong tickNumber = new AtomicLong(0);

        this.gameLoopSubscription = Flux.interval(Duration.ofMillis(TICK_RATE_MS))
                .doOnNext(tick -> gameTick(tickNumber.incrementAndGet()))
                .doOnError(e -> System.err.println("Game loop error in room " + roomId + ": " + e.getMessage()))
                .subscribe();
    }

    private void gameTick(long tickNumber) {
        if (!isRunning.get()) {
            return;
        }

        try {
            List<GameStateSnapshot.GameEvent> currentTickEvents = new ArrayList<>();
            GameStateSnapshot snapshot = new GameStateSnapshot();

            if (currentPhase == RoomPhase.COUNTDOWN) {
                boolean countdownFinished = gameCountdownSystem.update(countdownRoomState, snapshot);
                if (countdownFinished) {
                    currentPhase = RoomPhase.RUNNING;
                }
            } else if (currentPhase == RoomPhase.RUNNING) {
                gameTimerSystem.update(snapshot, gameStartTime);
                inputSystem.processCommands(commandQueue, playerStates);
                combatSystem.update(playerStates, currentTickEvents);
                physicsSystem.update(playerStates, currentTickEvents);
                gameStateSystem.update(playerStates, deadPlayerTimers);

                if (gameConditionSystem.shouldGameEnd(playerStates, gameStartTime)) {
                    currentPhase = RoomPhase.CONCLUDED;
                    stop(); // 触发游戏结束流程
                }
            }

            broadcastSnapshot(tickNumber, currentTickEvents, snapshot);
        } catch (Exception e) {
            System.err.println("!!!!!! An error occurred in game tick for room " + roomId + " !!!!!!");
            e.printStackTrace();
        }
    }

    private void onGameConcluded() {
        System.out.printf("Game room %s has concluded.\n", roomId);
        reportGameResults();
        notifyAndCloseConnections();
        // 通知Manager移除自己
        roomManager.removeGameRoom(this.roomId);
    }

    private void broadcastSnapshot(long tickNumber, List<GameStateSnapshot.GameEvent> events, GameStateSnapshot snapshot) {
        snapshot.setTickNumber(tickNumber);
        snapshot.setPlayers(new ConcurrentHashMap<>(playerStates));
        snapshot.setEvents(events);
        broadcaster.broadcast(this.sessions.values(), snapshot);
    }

    private void notifyAndCloseConnections() {
        Map<String, Object> gameOverMessage = Map.of("type", "game_over", "reason", "Match finished");
        String finalMessage = gson.toJson(gameOverMessage);

        System.out.println("游戏结束，正在关闭所有玩家连接...");
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                // 先发送消息，.then() 表示发送完成后，再执行关闭操作
                session.send(Mono.just(session.textMessage(finalMessage)))
                        .then(session.close(CloseStatus.NORMAL.withReason("Game Over")))
                        .subscribe();
            }
        }
    }


    private void reportGameResults() {
        // 1. 将所有玩家状态转为列表并按击杀数排序
        List<PlayerState> sortedPlayers = new ArrayList<>(playerStates.values());
        // 击杀多者在前，死亡少者在前
        sortedPlayers.sort((p1, p2) -> {
            int killComparison = Integer.compare(p2.getKills(), p1.getKills());
            if (killComparison != 0) {
                return killComparison;
            }
            return Integer.compare(p1.getDeaths(), p2.getDeaths());
        });

        // 2. 收集战绩并赋予排名
        List<MatchParticipant> results = new ArrayList<>();
        for (int i = 0; i < sortedPlayers.size(); i++) {
            PlayerState playerState = sortedPlayers.get(i);
            MatchParticipant participant = new MatchParticipant();
            participant.setMatchId(Long.parseLong(this.roomId));
            participant.setUserId(sessionToUserIdMap.get(playerState.getPlayerId()));
            participant.setKills(playerState.getKills());
            participant.setDeaths(playerState.getDeaths());
            participant.setRanking(i + 1); // 设置排名
            results.add(participant);
        }

        this.roomManager.onGameConcluded(Long.parseLong(this.roomId), results);
    }
}