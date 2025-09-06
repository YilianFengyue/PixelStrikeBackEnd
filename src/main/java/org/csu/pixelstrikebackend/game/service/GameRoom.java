package org.csu.pixelstrikebackend.game.service;

import com.google.gson.Gson;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot.GameEvent;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.csu.pixelstrikebackend.game.system.*;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameRoom implements Runnable {

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
    private volatile boolean isRunning = true;
    private final long gameStartTime;
    private final int expectedPlayerCount;
    private final GameCountdownSystem.RoomState countdownRoomState;

    private enum RoomPhase { WAITING_FOR_PLAYERS, COUNTDOWN, RUNNING, CONCLUDED }
    private RoomPhase currentPhase = RoomPhase.WAITING_FOR_PLAYERS;

    private final Gson gson = new Gson();

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
        if (sessions.size() == expectedPlayerCount) {
            this.currentPhase = RoomPhase.COUNTDOWN;
            System.out.println("玩家已满，房间 " + roomId + " 进入倒计时阶段...");
        }
    }

    private void sendWelcomeMessage(WebSocketSession session) {
        Map<String, String> welcomeMessage = Map.of(
                "type", "welcome",
                "playerId", session.getId()
        );
        try {
            session.sendMessage(new TextMessage(gson.toJson(welcomeMessage)));
        } catch (IOException e) {
            System.err.println("Failed to send welcome message to " + session.getId());
        }
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
        long tickNumber = 0;

        while (isRunning) {
            try {
                tickNumber++;
                List<GameEvent> currentTickEvents = new ArrayList<>();
                GameStateSnapshot snapshot = new GameStateSnapshot(); // 在循环开始时创建snapshot

                // --- 使用状态机 ---
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
                        this.stop();
                    }
                }

                broadcastSnapshot(tickNumber, currentTickEvents, snapshot); // 【修复】传递snapshot

                // 控制Tick率
                nextGameTick += SKIP_TICKS;
                long sleepTime = nextGameTick - System.currentTimeMillis();
                if (sleepTime >= 0) {
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) { // <--- 捕获中断异常
                Thread.currentThread().interrupt();
                isRunning = false;
                System.err.println("Game room " + roomId + " thread was interrupted.");
            } catch (Exception e) { // <--- 捕获所有其他异常
                // 关键：打印错误日志，但不要让线程死掉！
                System.err.println("!!!!!! An error occurred in game loop for room " + roomId + " !!!!!!");
                e.printStackTrace();
                // 循环会继续，游戏房间不会因此崩溃
            }
        }
        reportGameResults();
        notifyAndCloseConnections();
        System.out.printf("Game room %s has been shut down.\n", roomId);
    }

    private void broadcastSnapshot(long tickNumber, List<GameEvent> events, GameStateSnapshot snapshot) {
        snapshot.setTickNumber(tickNumber);
        snapshot.setPlayers(new ConcurrentHashMap<>(playerStates));
        snapshot.setEvents(events);
        broadcaster.broadcast(this.sessions.values(), snapshot);
    }

    private void notifyAndCloseConnections() {
        // （可选）在关闭前发送一条游戏结束的消息
        Map<String, Object> gameOverMessage = Map.of("type", "game_over", "reason", "Match finished");
        TextMessage finalMessage = new TextMessage(gson.toJson(gameOverMessage));

        System.out.println("游戏结束，正在关闭所有玩家连接...");
        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) {
                    // session.sendMessage(finalMessage); // (可选) 发送最后的消息
                    session.close(CloseStatus.NORMAL.withReason("Game Over"));
                }
            } catch (IOException e) {
                System.err.println("关闭 session " + session.getId() + " 出错: " + e.getMessage());
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