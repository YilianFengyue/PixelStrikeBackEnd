package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.MatchmakingRoom;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.FriendMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchParticipantMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.MatchmakingService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.service.PlayerSessionService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Autowired private OnlineUserService onlineUserService;
    @Autowired private WebSocketSessionManager webSocketSessionManager;
    @Autowired private FriendMapper friendMapper;
    @Autowired private MatchMapper matchMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private GameLobbyBridge gameLobbyBridge; // 注入桥接实现
    @Autowired private MatchParticipantMapper matchParticipantMapper;
    @Autowired private GameConfig gameConfig;
    @Autowired private PlayerSessionService playerSessionService;

    @Override
    public synchronized CommonResponse<?> startMatchmaking(Integer userId) {
        if (playerSessionService.isPlayerInGame(userId)) {
            return CommonResponse.createForError("您已在游戏中，无法开始新的匹配");
        }
        if (playerRoomMap.containsKey(userId)) {
            return CommonResponse.createForError("您已经在匹配队列中");
        }

        // 查找一个未满的房间
        MatchmakingRoom targetRoom = findAvailableRoom();

        // 如果没有找到，则创建一个新房间
        if (targetRoom == null) {
            targetRoom = new MatchmakingRoom(gameConfig.getMatchmaking().getRoomMaxSize());
            rooms.add(targetRoom);
            System.out.println("No available rooms. Created a new room: " + targetRoom.getRoomId());
        }

        // 将玩家加入房间
        targetRoom.addPlayer(userId);
        playerRoomMap.put(userId, targetRoom.getRoomId());
        onlineUserService.updateUserStatus(userId, UserStatus.MATCHING);
        notifyFriendsAboutStatusChange(userId, "MATCHING");

        System.out.println("Player " + userId + " joined room " + targetRoom.getRoomId() +
                ". Room size: " + targetRoom.getCurrentSize());

        // 检查房间是否已满
        if (targetRoom.isFull()) {
            System.out.println("Room " + targetRoom.getRoomId() + " is full. Match successful!");
            notifyMatchSuccess(targetRoom);
            // 从匹配队列中移除已满的房间
            rooms.remove(targetRoom);
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

    private MatchmakingRoom findRoomById(String roomId) {
        for (MatchmakingRoom room : rooms) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        return null;
    }

    private void notifyMatchSuccess(MatchmakingRoom room) {
        // **核心改动1: 先在 matches 表中创建对局记录，获取唯一的 BIGINT gameId**
        Match newMatch = new Match();
        newMatch.setGameMode("匹配");
        newMatch.setMapName("默认地图"); // 可以在后续逻辑中随机选择
        newMatch.setStartTime(LocalDateTime.now());
        matchMapper.insert(newMatch); // 插入后，newMatch 对象的 id 会被自动填充
        Long gameId = newMatch.getId(); // 这就是我们需要的、类型正确的 gameId

        // --- 核心修改 ---
        // 2. 通过桥接器通知游戏模块，对局已创建
        List<Integer> playerIds = room.getPlayers().stream().toList();
        gameLobbyBridge.onMatchSuccess(gameId, playerIds);

        for (Integer playerId : playerIds) {
            playerSessionService.registerPlayerInGame(playerId, gameId);
        }

        // 模拟游戏服务器信息
        String gameServerAddress = "ws://127.0.0.1:8080/game";

        // 构建成功消息
        Map<String, Object> successMessage = Map.of(
                "type", "match_success",
                "gameId", gameId, // **核心改动2: 使用新的 BIGINT gameId**
                "serverAddress", gameServerAddress
        );

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
