package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.service.MatchService;
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
    private final MatchService matchService; // 新增注入

    // 使用构造器注入，并用@Lazy解决可能的循环依赖问题
    public GameRoomManager(@Lazy MatchmakingService matchmakingService, WebSocketBroadcastService broadcaster,MatchService matchService) {
        this.matchmakingService = matchmakingService;
        this.broadcaster = broadcaster;
        this.matchService = matchService;
    }


    @Autowired
    private WebSocketBroadcastService broadcaster; // 注入广播服务

    // --- 实现桥接接口的方法 ---
    @Override
    public void onMatchSuccess(Long gameId, List<Integer> playerIds) {
        String roomId = gameId.toString();
        System.out.println("游戏模块收到通知: 创建房间 " + roomId);

        // 这部分逻辑和之前的 createAndStartRoom 类似
        activeRooms.computeIfAbsent(roomId, id -> {
            GameRoom newRoom = new GameRoom(id, broadcaster, this); // 把自己传进去
            roomExecutor.submit(newRoom);
            return newRoom;
        });
    }

    @Override
    public void onGameConcluded(Long gameId, List<MatchParticipant> results) {
        System.out.println("游戏模块收到战绩: " + gameId + "，准备上报给大厅模块...");
        // 直接调用 MatchService 处理
        matchService.processMatchResults(gameId, results);
        // TODO: 调用大厅的服务来持久化数据
        // matchmakingService.processResults(gameId, results);
    }

    // 这个方法将由后端B(Dev2)的业务逻辑来调用
    /*public void createAndStartRoom(String roomId) {
        activeRooms.computeIfAbsent(roomId, id -> {
            System.out.println("Creating and starting new room: " + id);
            GameRoom newRoom = new GameRoom(id, broadcaster);
            roomExecutor.submit(newRoom); // 只有在创建新房间时才启动新线程
            return newRoom;
        });
    }*/

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
    
    public GameRoom getRoomForPlayer(String playerId) {
        String roomId = playerToRoomMap.get(playerId);
        return (roomId != null) ? activeRooms.get(roomId) : null;
    }
    
    // ... 其他管理方法，如销毁房间等
}