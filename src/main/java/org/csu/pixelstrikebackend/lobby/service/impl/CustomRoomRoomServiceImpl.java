package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchmakingRoom; // 复用该实体
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.service.CustomRoomService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CustomRoomRoomServiceImpl implements CustomRoomService {

    private final Map<String, MatchmakingRoom> customRooms = new ConcurrentHashMap<>();
    private final Map<Integer, String> playerInRoomMap = new ConcurrentHashMap<>();

    @Autowired private OnlineUserService onlineUserService;
    @Autowired private WebSocketSessionManager webSocketSessionManager;
    @Autowired private GameLobbyBridge gameLobbyBridge;
    @Autowired private MatchMapper matchMapper;
    @Autowired private GameConfig gameConfig;
    @Autowired private PlayerSessionService playerSessionService;

    public CommonResponse<Map<String, String>> createRoom(Integer hostId) {
        if (playerInRoomMap.containsKey(hostId)) {
            return CommonResponse.createForError("您已经在另一个房间中了");
        }

        MatchmakingRoom newRoom = new MatchmakingRoom(gameConfig.getMatchmaking().getRoomMaxSize());
        newRoom.addPlayer(hostId);
        customRooms.put(newRoom.getRoomId(), newRoom);
        playerInRoomMap.put(hostId, newRoom.getRoomId());
        onlineUserService.updateUserStatus(hostId, UserStatus.IN_ROOM);

        System.out.println("玩家 " + hostId + " 创建了自定义房间: " + newRoom.getRoomId());
        return CommonResponse.createForSuccess("创建成功", Map.of("roomId", newRoom.getRoomId()));
    }

    public CommonResponse<Map<String, String>> joinRoom(String roomId, Integer userId) {
        if (playerInRoomMap.containsKey(userId)) {
            return CommonResponse.createForError("您已经在另一个房间中了");
        }

        MatchmakingRoom room = customRooms.get(roomId);
        if (room == null) {
            return CommonResponse.createForError("房间不存在");
        }
        if (room.isFull()) {
            return CommonResponse.createForError("房间已满");
        }

        // 通知房间内其他玩家有新人加入
        notifyRoomMembers(roomId, Map.of("type", "player_joined", "userId", userId));

        room.addPlayer(userId);
        playerInRoomMap.put(userId, room.getRoomId());
        onlineUserService.updateUserStatus(userId, UserStatus.IN_ROOM);

        System.out.println("玩家 " + userId + " 加入了自定义房间: " + roomId);

        // 将当前房间内所有玩家的信息返回给新加入者
        List<Integer> playerIds = room.getPlayers().stream().toList();
        return CommonResponse.createForSuccess("加入成功", Map.of("roomId", roomId, "players", playerIds.toString()));
    }

    // 开始自定义房间游戏
    @Override
    public CommonResponse<?> startRoom(String roomId, Integer userId) {
        MatchmakingRoom room = customRooms.get(roomId);
        if (room == null) {
            return CommonResponse.createForError("房间不存在");
        }
        // (可以增加只有房主才能开始的逻辑)

        // 1. 创建数据库对战记录
        Match newMatch = new Match();
        newMatch.setGameMode("自定义");
        newMatch.setMapName("默认地图");
        newMatch.setStartTime(LocalDateTime.now());
        matchMapper.insert(newMatch);
        Long gameId = newMatch.getId();

        // 2. 通知游戏模块准备房间
        List<Integer> playerIds = room.getPlayers().stream().toList();
        gameLobbyBridge.onMatchSuccess(gameId, playerIds);

        for (Integer playerId : playerIds) {
            playerSessionService.registerPlayerInGame(playerId, gameId);
        }

        // 3. 通知所有房间内玩家进入游戏
        Map<String, Object> successMessage = Map.of(
                "type", "match_success",
                "gameId", gameId,
                "serverAddress", "ws://127.0.0.1:8080/game"
        );
        notifyRoomMembers(roomId, successMessage);

        // 4. 清理自定义房间
        customRooms.remove(roomId);
        for(Integer pid : playerIds) {
            playerInRoomMap.remove(pid);
        }

        return CommonResponse.createForSuccessMessage("游戏开始");
    }

    @Override
    public CommonResponse<?> leaveRoom(Integer userId) {
        String roomId = playerInRoomMap.get(userId);
        if (roomId == null) {
            return CommonResponse.createForError("您不在任何房间中");
        }

        MatchmakingRoom room = customRooms.get(roomId);
        if (room == null) {
            // 数据不一致的边界情况，清理脏数据
            playerInRoomMap.remove(userId);
            return CommonResponse.createForError("房间不存在，已将您移除");
        }

        // --- 核心修改：检查是否是房主退出 ---
        if (userId.equals(room.getHostId())) {
            System.out.println("房主 " + userId + " 退出，解散房间 " + roomId);
            // 通知所有成员，房间已解散
            notifyRoomMembers(roomId, Map.of("type", "room_disbanded", "reason", "房主已离开"));

            // 将所有玩家移出房间映射并更新状态
            for (Integer memberId : room.getPlayers()) {
                playerInRoomMap.remove(memberId);
                onlineUserService.updateUserStatus(memberId, UserStatus.ONLINE);
            }
            // 销毁房间
            customRooms.remove(roomId);
            return CommonResponse.createForSuccessMessage("您是房主，房间已解散");
        }

        // --- 如果不是房主，执行普通退出逻辑 ---
        room.removePlayer(userId);
        playerInRoomMap.remove(userId);
        onlineUserService.updateUserStatus(userId, UserStatus.ONLINE);

        System.out.println("玩家 " + userId + " 离开了房间 " + roomId);
        notifyRoomMembers(roomId, Map.of("type", "player_left", "userId", userId));

        return CommonResponse.createForSuccessMessage("已成功退出房间");
    }

    private void notifyRoomMembers(String roomId, Object payload) {
        MatchmakingRoom room = customRooms.get(roomId);
        if (room != null) {
            for (Integer memberId : room.getPlayers()) {
                webSocketSessionManager.sendMessageToUser(memberId, payload);
            }
        }
    }
}