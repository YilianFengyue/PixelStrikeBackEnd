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

    private final Gson gson = new Gson();

    public GameRoom(String roomId,
                    GameRoomManager roomManager,
                    InputSystem inputSystem,
                    CombatSystem combatSystem,
                    PhysicsSystem physicsSystem,
                    GameStateSystem gameStateSystem,
                    GameConditionSystem gameConditionSystem,
                    WebSocketBroadcastService broadcaster,
                    List<Integer> playerIds) {
        this.roomId = roomId;
        this.roomManager = roomManager;
        this.inputSystem = inputSystem;
        this.combatSystem = combatSystem;
        this.physicsSystem = physicsSystem;
        this.gameStateSystem = gameStateSystem;
        this.gameConditionSystem = gameConditionSystem;
        this.broadcaster = broadcaster;
        this.gameStartTime = System.currentTimeMillis();
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
        System.out.printf("Player %s joined room %s\n", session.getId(), roomId);
        sendWelcomeMessage(session);
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
            tickNumber++;
            List<GameEvent> currentTickEvents = new ArrayList<>();

            // 1. 处理输入
            inputSystem.processCommands(commandQueue, playerStates);

            // 2. 处理战斗
            combatSystem.update(playerStates, currentTickEvents);

            // 3. 更新物理
            physicsSystem.update(playerStates, currentTickEvents);

            // 4. 更新游戏状态 (复活等)
            gameStateSystem.update(playerStates, deadPlayerTimers);

            // 5. 检查游戏是否结束
            if (gameConditionSystem.shouldGameEnd(playerStates, gameStartTime)) {
                this.stop(); // 触发游戏结束
            }

            // 6. 广播快照
            broadcastSnapshot(tickNumber, currentTickEvents);

            // 7. 控制Tick率
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
        notifyAndCloseConnections();
        System.out.printf("Game room %s has been shut down.\n", roomId);
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

    private void broadcastSnapshot(long tickNumber, List<GameEvent> events) {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.setTickNumber(tickNumber);
        snapshot.setPlayers(new ConcurrentHashMap<>(playerStates));
        snapshot.setEvents(events);
        broadcaster.broadcast(this.sessions.values(), snapshot);
    }

    private void reportGameResults() {
        List<MatchParticipant> results = new ArrayList<>();
        for (PlayerState playerState : playerStates.values()) {
            MatchParticipant participant = new MatchParticipant();
            participant.setMatchId(Long.parseLong(this.roomId));
            participant.setUserId(sessionToUserIdMap.get(playerState.getPlayerId()));
            participant.setKills(playerState.getKills());
            participant.setDeaths(playerState.getDeaths());
            // TODO: 计算排名
            results.add(participant);
        }

        this.roomManager.onGameConcluded(Long.parseLong(this.roomId), results);
    }
}