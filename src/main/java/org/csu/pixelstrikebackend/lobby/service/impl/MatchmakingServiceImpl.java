package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.*;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.*;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service("matchmakingService")
public class MatchmakingServiceImpl implements MatchmakingService {

    // 存储所有正在匹配中的房间
    private final List<MatchmakingRoom> rooms = new CopyOnWriteArrayList<>();
    // 存储玩家ID和其所在房间ID的映射，方便快速查找和取消
    private final Map<Integer, String> playerRoomMap = new ConcurrentHashMap<>();
    //存储地图ID到其对应下面的房间映射，方便分地图匹配
    private final Map<Integer, List<MatchmakingRoom>> roomsByMap = new ConcurrentHashMap<>();

    @Autowired private OnlineUserService onlineUserService;
    @Autowired private WebSocketSessionManager webSocketSessionManager;
    @Autowired private FriendMapper friendMapper;
    @Autowired private MatchMapper matchMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private GameLobbyBridge gameLobbyBridge; // 注入桥接实现
    @Autowired private MatchParticipantMapper matchParticipantMapper;
    @Autowired private GameConfig gameConfig;
    @Autowired private PlayerSessionService playerSessionService;
    @Autowired private MapMapper mapMapper;
    @Autowired private CharacterMapper characterMapper;

    // **核心改动1：重写 startMatchmaking 方法**
    @Override
    public synchronized CommonResponse<?> startMatchmaking(Integer userId, Integer mapId, Integer characterId) {
        if (playerSessionService.isPlayerInGame(userId) || playerRoomMap.containsKey(userId)) {
            return CommonResponse.createForError("您已在游戏或匹配队列中");
        }
        if (mapMapper.selectById(mapId) == null || characterMapper.selectById(characterId) == null) {
            return CommonResponse.createForError("选择的地图或角色不存在");
        }

        // 按地图ID查找或创建房间列表
        List<MatchmakingRoom> mapRooms = roomsByMap.computeIfAbsent(mapId, k -> new CopyOnWriteArrayList<>());
        MatchmakingRoom targetRoom = findAvailableRoomInList(mapRooms);

        if (targetRoom == null) {
            targetRoom = new MatchmakingRoom(gameConfig.getMatchmaking().getRoomMaxSize());
            mapRooms.add(targetRoom);
            System.out.println("No available rooms for map " + mapId + ". Created a new room: " + targetRoom.getRoomId());
        }

        // 将玩家和其选择的角色加入房间
        targetRoom.addPlayer(userId, characterId);
        playerRoomMap.put(userId, targetRoom.getRoomId());
        onlineUserService.updateUserStatus(userId, UserStatus.MATCHING);
        notifyFriendsAboutStatusChange(userId, "MATCHING");

        System.out.println("Player " + userId + " (char: " + characterId + ") joined room " + targetRoom.getRoomId() + " for map " + mapId);

        if (targetRoom.isFull()) {
            System.out.println("Room " + targetRoom.getRoomId() + " is full. Match successful!");
            notifyMatchSuccess(targetRoom, mapId); // **传递 mapId**
            mapRooms.remove(targetRoom);
        }
        return CommonResponse.createForSuccessMessage("开始匹配成功");
    }

    @Override
    public synchronized CommonResponse<?> cancelMatchmaking(Integer userId) {
        String roomId = playerRoomMap.get(userId);
        if (roomId == null) {
            return CommonResponse.createForError("您不在匹配队列中");
        }

        // 从房间中移除玩家
        MatchmakingRoom room = findRoomById(roomId);
        if (room != null) {
            room.removePlayer(userId);
            System.out.println("Player " + userId + " cancelled matchmaking from room " + roomId);
            // 如果房间空了，则销毁房间
            if (room.isEmpty()) {
                rooms.remove(room);
                for (List<MatchmakingRoom> mapRooms : roomsByMap.values()) {
                    if (mapRooms.remove(room)) {
                        break; // 找到并移除后即可跳出循环
                    }
                }
                System.out.println("Room " + roomId + " is empty and has been destroyed.");
            }
        }
        // 移除玩家的映射
        playerRoomMap.remove(userId);
        onlineUserService.updateUserStatus(userId, UserStatus.ONLINE);
        notifyFriendsAboutStatusChange(userId, "ONLINE");

        return CommonResponse.createForSuccessMessage("取消匹配成功");
    }

    private MatchmakingRoom findAvailableRoom() {
        for (MatchmakingRoom room : rooms) {
            if (!room.isFull()) {
                return room;
            }
        }
        return null;
    }

    // **新增：在指定地图的房间列表中查找可用房间**
    private MatchmakingRoom findAvailableRoomInList(List<MatchmakingRoom> roomList) {
        for (MatchmakingRoom room : roomList) {
            if (!room.isFull()) {
                return room;
            }
        }
        return null; // 如果都满了，返回 null
    }

    private MatchmakingRoom findRoomById(String roomId) {
        // 优先从总列表查找（效率更高）
        for (MatchmakingRoom room : rooms) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        // 如果总列表中没有，则遍历 roomsByMap 作为备用查找方案
        for (List<MatchmakingRoom> mapRooms : roomsByMap.values()) {
            for (MatchmakingRoom room : mapRooms) {
                if (room.getRoomId().equals(roomId)) {
                    return room;
                }
            }
        }
        return null;
    }

    // **核心改动2：修改 notifyMatchSuccess 方法**
    private void notifyMatchSuccess(MatchmakingRoom room, Integer mapId) {
        Match newMatch = new Match();
        newMatch.setGameMode("匹配");
        newMatch.setMapId(mapId); // **使用传入的 mapId**
        newMatch.setStartTime(LocalDateTime.now());
        matchMapper.insert(newMatch);
        Long gameId = newMatch.getId();

        // **获取玩家及其角色选择**
        Map<Integer, Integer> playerSelections = room.getPlayerCharacterSelections();

        // **通过桥接器通知游戏模块，对局已创建，并传递角色选择**
        gameLobbyBridge.onMatchSuccess(gameId, playerSelections);

        for (Integer playerId : playerSelections.keySet()) {
            playerSessionService.registerPlayerInGame(playerId, gameId);
        }

        String gameServerAddress = "ws://127.0.0.1:8080/game";
        GameMap map = mapMapper.selectById(mapId); // **新增: 查询地图信息**

        // **修改点: 使用可变的 HashMap 并添加地图信息**
        Map<String, Object> successMessage = new HashMap<>();
        successMessage.put("type", "match_success");
        successMessage.put("gameId", gameId);
        successMessage.put("serverAddress", gameServerAddress);
        successMessage.put("characterSelections", playerSelections);
        if (map != null) {
            successMessage.put("mapId", map.getId());
            successMessage.put("mapName", map.getName());
        }

        for (Integer playerId : room.getPlayers()) {
            playerRoomMap.remove(playerId);
            onlineUserService.updateUserStatus(playerId, UserStatus.IN_GAME);
            notifyFriendsAboutStatusChange(playerId, "IN_GAME");
            webSocketSessionManager.sendMessageToUser(playerId, successMessage);
        }
    }


    /**
     * 辅助方法，用于通知好友状态变更
     * @param userId 状态变更的用户ID
     * @param status 新的状态 (例如 "IN_GAME")
     */
    private void notifyFriendsAboutStatusChange(Integer userId, String status) {
        List<UserProfile> friends = friendMapper.selectFriendsProfiles(userId);
        if (friends.isEmpty()) {
            return;
        }

        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) return;

        Map<String, Object> notification = Map.of(
                "type", "status_update",
                "userId", userId,
                "nickname", userProfile.getNickname(), // **新增 nickname 字段**
                "status", status
        );

        System.out.println("Notifying online friends of user " + userId + " about status change to " + status);
        for (UserProfile friend : friends) {
            if (onlineUserService.isUserOnline(friend.getUserId())) {
                webSocketSessionManager.sendMessageToUser(friend.getUserId(), notification);
            }
        }
    }

    @Override
    @Transactional
    public void processGameResults(Long gameId, List<MatchParticipant> results) {
        System.out.println("大厅模块正在处理战绩，游戏ID: " + gameId);

        for (MatchParticipant participant : results) {
            matchParticipantMapper.insert(participant);

            UserProfile profile = userProfileMapper.selectById(participant.getUserId());
            if (profile != null) {
                profile.setTotalMatches(profile.getTotalMatches() + 1);
                if (participant.getRanking() != null && participant.getRanking() == 1) {
                    profile.setWins(profile.getWins() + 1);
                }
                userProfileMapper.updateById(profile);
            }
        }

        Match match = matchMapper.selectById(gameId);
        if (match != null) {
            match.setEndTime(LocalDateTime.now());
            matchMapper.updateById(match);
        }
        System.out.println("战绩处理完毕并已存入数据库。");
    }
}
