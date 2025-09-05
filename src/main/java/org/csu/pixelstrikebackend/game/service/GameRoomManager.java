package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.game.system.*;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GameRoomManager implements GameLobbyBridge {

    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // 玩家ID到房间ID的映射，方便快速查找
    private final Map<String, String> playerToRoomMap = new ConcurrentHashMap<>();

    // 使用线程池来管理所有房间的线程
    private final ExecutorService roomExecutor = Executors.newCachedThreadPool();



    private final MatchmakingService matchmakingService;

    // 使用构造器注入，并用@Lazy解决可能的循环依赖问题
    public GameRoomManager(@Lazy MatchmakingService matchmakingService, WebSocketBroadcastService broadcaster) {
        this.matchmakingService = matchmakingService;
        this.broadcaster = broadcaster;
    }

    @Autowired
    private WebSocketBroadcastService broadcaster; // 注入广播服务

    @Autowired private InputSystem inputSystem;
    @Autowired private CombatSystem combatSystem;
    @Autowired private PhysicsSystem physicsSystem;
    @Autowired private GameStateSystem gameStateSystem;
    @Autowired private GameConditionSystem gameConditionSystem;

    // --- 实现桥接接口的方法 ---
    @Override
    public void onMatchSuccess(Long gameId, List<Integer> playerIds) {
        String roomId = gameId.toString();
        System.out.println("游戏模块收到通知: 创建房间 " + roomId);

        // 这部分逻辑和之前的 createAndStartRoom 类似
        activeRooms.computeIfAbsent(roomId, id -> {
            // 【修改】创建GameRoom时，将所有依赖注入，包括新的gameConditionSystem
            GameRoom newRoom = new GameRoom(id, this, inputSystem, combatSystem, physicsSystem, gameStateSystem, gameConditionSystem, broadcaster, playerIds);
            roomExecutor.submit(newRoom);
            return newRoom;
        });
    }

    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        System.out.println("游戏模块上报战绩给大厅，游戏ID: " + gameId);
        // 通过桥接调用大厅服务来持久化数据
        matchmakingService.processGameResults(gameId, results);

        // （可选）游戏结束后，可以从activeRooms中移除GameRoom实例
        activeRooms.remove(gameId.toString());
    }

    public void addPlayerToRoom(String roomId, WebSocketSession session) {
        GameRoom room = activeRooms.get(roomId);
        if (room != null) {
            // 【重要修改】将 userId 传递给 addPlayer 方法
            Integer userId = (Integer) session.getAttributes().get("userId");
            if (userId != null) {
                room.addPlayer(session, userId);
                playerToRoomMap.put(session.getId(), roomId);
            } else {
                System.err.println("错误：未认证的用户尝试加入游戏房间！Session ID: " + session.getId());
            }
        }
    }

    public void removePlayerFromRoom(WebSocketSession session) {
        String roomId = playerToRoomMap.remove(session.getId());
        if (roomId != null) {
            GameRoom room = activeRooms.get(roomId);
            if (room != null) {
                room.removePlayer(session);
            }
        }
    }

    public GameRoom getRoomForPlayer(String playerId) {
        String roomId = playerToRoomMap.get(playerId);
        return (roomId != null) ? activeRooms.get(roomId) : null;
    }

    // ... 其他管理方法，如销毁房间等
}