package org.csu.pixelstrikebackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GameRoomManager {

    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // 玩家ID到房间ID的映射，方便快速查找
    private final Map<String, String> playerToRoomMap = new ConcurrentHashMap<>();
    
    // 使用线程池来管理所有房间的线程
    private final ExecutorService roomExecutor = Executors.newCachedThreadPool();

    @Autowired
    private WebSocketBroadcastService broadcaster; // 注入广播服务

    // 这个方法将由后端B(Dev2)的业务逻辑来调用
    public void createAndStartRoom(String roomId) {
        activeRooms.computeIfAbsent(roomId, id -> {
            System.out.println("Creating and starting new room: " + id);
            GameRoom newRoom = new GameRoom(id, broadcaster);
            roomExecutor.submit(newRoom); // 只有在创建新房间时才启动新线程
            return newRoom;
        });
    }

    public void addPlayerToRoom(String roomId, WebSocketSession session) {
        GameRoom room = activeRooms.get(roomId);
        if (room != null) {
            room.addPlayer(session);
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