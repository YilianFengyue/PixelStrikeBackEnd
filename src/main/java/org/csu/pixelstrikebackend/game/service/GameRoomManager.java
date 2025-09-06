package org.csu.pixelstrikebackend.game.service;


import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.game.system.*;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomManager implements GameLobbyBridge {

    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // 玩家ID到房间ID的映射，方便快速查找
    private final Map<String, String> playerToRoomMap = new ConcurrentHashMap<>();

    private final MatchmakingService matchmakingService;

    // 使用构造器注入，并用@Lazy解决可能的循环依赖问题
    public GameRoomManager(@Lazy MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Autowired
    private WebSocketBroadcastService broadcaster; // 注入广播服务

    @Autowired private InputSystem inputSystem;
    @Autowired private CombatSystem combatSystem;
    @Autowired private PhysicsSystem physicsSystem;
    @Autowired private GameStateSystem gameStateSystem;
    @Autowired private GameConditionSystem gameConditionSystem;
    @Autowired private GameCountdownSystem gameCountdownSystem;
    @Autowired private GameTimerSystem gameTimerSystem;

    // --- 实现桥接接口的方法 ---
    @Override
    public void onMatchSuccess(Long gameId, List<Integer> playerIds) {
        String roomId = gameId.toString();
        System.out.println("游戏模块收到通知: 创建房间 " + roomId);

        activeRooms.computeIfAbsent(roomId, id -> {
            GameRoom newRoom = new GameRoom(id, this,
                    inputSystem, combatSystem, physicsSystem,
                    gameStateSystem, gameConditionSystem, gameCountdownSystem,
                    gameTimerSystem, broadcaster, playerIds);
            // 不再提交到线程池
            // roomExecutor.submit(newRoom);
            return newRoom;
        });
    }

    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        System.out.println("游戏模块上报战绩给大厅，游戏ID: " + gameId);
        matchmakingService.processGameResults(gameId, results);
    }

    // addPlayerToRoom 方法增加一个 userId 参数
    public void addPlayerToRoom(String roomId, WebSocketSession session, Integer userId) {
        GameRoom room = activeRooms.get(roomId);
        if (room != null) {
            room.addPlayer(session, userId);
            playerToRoomMap.put(session.getId(), roomId);
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

    // 新增一个方法，让 GameRoom 在结束时能通知 Manager
    public void removeGameRoom(String roomId) {
        activeRooms.remove(roomId);
        System.out.println("游戏房间 " + roomId + " 已结束，资源已清理。");
    }

    public GameRoom getRoomForPlayer(String playerId) {
        String roomId = playerToRoomMap.get(playerId);
        return (roomId != null) ? activeRooms.get(roomId) : null;
    }
}