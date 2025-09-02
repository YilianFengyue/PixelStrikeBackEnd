package org.csu.pixelstrikebackend.service;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.csu.pixelstrikebackend.dto.PlayerState;
import org.csu.pixelstrikebackend.dto.UserCommand;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameRoom implements Runnable {

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
        // 初始化玩家状态，并设置初始位置和ID
        PlayerState initialState = new PlayerState();
        initialState.setPlayerId(session.getId());
        initialState.setX(100); // 设置一个初始x坐标
        initialState.setY(100); // 设置一个初始y坐标
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

        while (isRunning) {
            // 1. 处理输入
            while (!commandQueue.isEmpty()) {
                UserCommand command = commandQueue.poll();
                // TODO: 应用指令，更新 playerStates
                // 确保指令和玩家状态都存在
                if (command != null) {
                    PlayerState stateToUpdate = playerStates.get(command.getPlayerId());
                    if (stateToUpdate != null) {
                        // 【核心逻辑】根据指令更新玩家状态
                        // 比如：根据moveInput更新玩家的X坐标
                        // 乘以一个速度值（比如5）让效果更明显
                        double newX = stateToUpdate.getX() + command.getMoveInput() * 5.0;
                        stateToUpdate.setX(newX);
                    }
                }
            }

            // 2. 更新世界状态
            // TODO: 更新子弹、物理等

            // 3. 广播快照
            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.setPlayers(new ConcurrentHashMap<>(playerStates));
            broadcaster.broadcast(this.sessions.values(), snapshot); // 只向本房间的玩家广播

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
}