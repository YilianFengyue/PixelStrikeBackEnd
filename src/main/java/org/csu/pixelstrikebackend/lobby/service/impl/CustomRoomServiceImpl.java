package org.csu.pixelstrikebackend.lobby.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.game.GameLobbyBridge;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.CustomRoomDTO;
import org.csu.pixelstrikebackend.lobby.dto.PlayerInRoomDTO;
import org.csu.pixelstrikebackend.lobby.entity.*;
import org.csu.pixelstrikebackend.lobby.enums.UserStatus;
import org.csu.pixelstrikebackend.lobby.mapper.*;
import org.csu.pixelstrikebackend.lobby.service.CustomRoomService;
import org.csu.pixelstrikebackend.lobby.service.OnlineUserService;
import org.csu.pixelstrikebackend.lobby.websocket.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CustomRoomServiceImpl implements CustomRoomService {

    private final Map<String, CustomRoom> activeRooms = new ConcurrentHashMap<>();
    private final Map<Integer, String> playerToRoomMap = new ConcurrentHashMap<>();

    @Autowired
    private WebSocketSessionManager webSocketSessionManager;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private OnlineUserService onlineUserService;
    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private GameLobbyBridge gameLobbyBridge;
    @Autowired
    private FriendMapper friendMapper; // 注入FriendMapper
    @Autowired
    private MapMapper mapMapper;
    @Autowired
    private CharacterMapper characterMapper;


    @Override
    public CommonResponse<?> createRoom(Integer userId, Integer mapId) {
        if (mapMapper.selectById(mapId) == null) {
            return CommonResponse.createForError("选择的地图不存在");
        }
        if (playerToRoomMap.containsKey(userId)) {
            return CommonResponse.createForError("你已经在另一个房间里了");
        }
        CustomRoom newRoom = new CustomRoom(userId, mapId);
        activeRooms.put(newRoom.getRoomId(), newRoom);
        playerToRoomMap.put(userId, newRoom.getRoomId());
        onlineUserService.updateUserStatus(userId, UserStatus.IN_ROOM);
        notifyFriendsAboutStatusChange(userId, UserStatus.IN_ROOM);
        broadcastRoomUpdate(newRoom.getRoomId());
        return CommonResponse.createForSuccess("房间创建成功", newRoom.getRoomId());
    }

    @Override
    public CommonResponse<?> changeCharacter(Integer userId, Integer characterId) {
        String roomId = playerToRoomMap.get(userId);
        if (roomId == null) {
            return CommonResponse.createForError("你不在任何房间中");
        }
        if (characterMapper.selectById(characterId) == null) {
            return CommonResponse.createForError("所选角色不存在");
        }
        CustomRoom room = activeRooms.get(roomId);
        if (room != null) {
            room.changeCharacter(userId, characterId);
            broadcastRoomUpdate(roomId); // 广播房间状态更新
            return CommonResponse.createForSuccessMessage("更换角色成功");
        }
        return CommonResponse.createForError("房间不存在");
    }

    @Override
    public CommonResponse<?> joinRoom(Integer userId, String roomId) {
        if (playerToRoomMap.containsKey(userId)) {
            return CommonResponse.createForError("你已经在另一个房间里了");
        }
        CustomRoom room = activeRooms.get(roomId);
        if (room == null) {
            return CommonResponse.createForError("房间不存在");
        }
        if (room.isFull()) {
            return CommonResponse.createForError("房间已满");
        }
        if (!room.getStatus().equals("WAITING")) {
            return CommonResponse.createForError("游戏已经开始，无法加入");
        }

        room.addPlayer(userId);
        playerToRoomMap.put(userId, roomId);
        onlineUserService.updateUserStatus(userId, UserStatus.IN_ROOM);
        // 新增: 通知好友状态变更
        notifyFriendsAboutStatusChange(userId, UserStatus.IN_ROOM);
        broadcastRoomUpdate(roomId);
        return CommonResponse.createForSuccess("加入成功", roomId);
    }

    @Override
    public CommonResponse<?> acceptInvite(Integer userId, String roomId) {
        // 接受邀请的逻辑本质上就是加入房间
        return joinRoom(userId, roomId);
    }

    @Override
    public CommonResponse<?> rejectInvite(Integer rejectorId, Integer inviterId) {
        // 校验邀请人是否在线，只有在线才能收到通知
        if (!onlineUserService.isUserOnline(inviterId)) {
            return CommonResponse.createForSuccessMessage("操作成功"); // 即使对方不在线，也告诉前端操作成功
        }

        // 获取拒绝者的昵称
        UserProfile rejectorProfile = userProfileMapper.selectById(rejectorId);
        if (rejectorProfile == null) {
            return CommonResponse.createForError("发生未知错误");
        }

        // 构建通知消息
        Map<String, Object> notification = Map.of(
                "type", "invitation_rejected",
                "rejectorId", rejectorId,
                "rejectorNickname", rejectorProfile.getNickname()
        );

        // 通过WebSocket将通知发送给邀请人
        webSocketSessionManager.sendMessageToUser(inviterId, notification);

        return CommonResponse.createForSuccessMessage("已拒绝邀请");
    }

    @Override
    public CommonResponse<?> leaveRoom(Integer userId) {
        String roomId = playerToRoomMap.remove(userId);
        if (roomId == null) {
            return CommonResponse.createForError("你不在任何房间中");
        }
        CustomRoom room = activeRooms.get(roomId);
        if (room == null) {
            return CommonResponse.createForError("发生内部错误，房间不存在");
        }

        room.removePlayer(userId);
        onlineUserService.updateUserStatus(userId, UserStatus.ONLINE);
        notifyFriendsAboutStatusChange(userId, UserStatus.ONLINE);

        // 如果房间空了，直接解散
        if (room.isEmpty()) {
            activeRooms.remove(roomId);
            System.out.println("Room " + roomId + " is empty and has been disbanded.");
        } else {
            // 如果房主离开了，自动移交房主给下一个人
            if (room.getHostId().equals(userId)) {
                Integer newHostId = room.getPlayers().stream().findFirst().orElse(null);
                room.setHostId(newHostId);
            }
            broadcastRoomUpdate(roomId);
        }
        return CommonResponse.createForSuccessMessage("已离开房间");
    }

    @Override
    public CommonResponse<?> inviteFriend(Integer inviterId, Integer friendId) {
        String roomId = playerToRoomMap.get(inviterId);
        CustomRoom room = activeRooms.get(roomId); // **修改点 1：获取房间对象**
        if (room == null) { // **修改点 2：用房间对象判断**
            return CommonResponse.createForError("你必须在房间内才能邀请好友");
        }
        // 修复点1: 增加校验
        if (userProfileMapper.selectById(friendId) == null) {
            return CommonResponse.createForError("该用户不存在");
        }
        QueryWrapper<Friend> friendshipQuery = new QueryWrapper<>();
        friendshipQuery.and(wrapper -> wrapper.eq("sender_id", inviterId).eq("addr_id", friendId))
                .or(wrapper -> wrapper.eq("sender_id", friendId).eq("addr_id", inviterId));
        friendshipQuery.eq("status", "accepted");
        if (!friendMapper.exists(friendshipQuery)) {
            return CommonResponse.createForError("对方不是你的好友，无法邀请");
        }

        if (!onlineUserService.isUserOnline(friendId) || playerToRoomMap.containsKey(friendId)) {
            return CommonResponse.createForError("好友不在线或已在其他房间中");
        }

        UserProfile inviterProfile = userProfileMapper.selectById(inviterId);
        GameMap map = mapMapper.selectById(room.getMapId()); // **新增：查询地图信息**
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("type", "room_invitation");
        invitation.put("roomId", roomId);
        invitation.put("inviterId", inviterId);
        invitation.put("inviterNickname", inviterProfile.getNickname());
        if (map != null) {
            invitation.put("mapId", map.getId());
            invitation.put("mapName", map.getName());
        }
        webSocketSessionManager.sendMessageToUser(friendId, invitation);
        return CommonResponse.createForSuccessMessage("邀请已发送");
    }

    @Override
    public CommonResponse<?> kickPlayer(Integer hostId, Integer targetId) {
        // 修复点3: 房主不能踢自己
        if (hostId.equals(targetId)) {
            return CommonResponse.createForError("不能将自己踢出房间");
        }
        String roomId = playerToRoomMap.get(hostId);
        CustomRoom room = activeRooms.get(roomId);
        if (room == null || !room.getHostId().equals(hostId)) {
            return CommonResponse.createForError("你不是房主，无权操作");
        }
        if (!room.getPlayers().contains(targetId)) {
            return CommonResponse.createForError("该玩家不在房间内");
        }

        // 踢出玩家
        room.removePlayer(targetId);
        playerToRoomMap.remove(targetId);
        onlineUserService.updateUserStatus(targetId, UserStatus.ONLINE);
        notifyFriendsAboutStatusChange(targetId, UserStatus.ONLINE);
        // 发送被踢通知
        webSocketSessionManager.sendMessageToUser(targetId, Map.of("type", "kicked_from_room", "roomId", roomId));
        // 广播房间更新
        broadcastRoomUpdate(roomId);
        return CommonResponse.createForSuccessMessage("已将该玩家踢出房间");
    }

    @Override
    public CommonResponse<?> transferHost(Integer oldHostId, Integer newHostId) {
        // 修复点4: 不能移交给自己
        if (oldHostId.equals(newHostId)) {
            return CommonResponse.createForError("不能将房主移交给自己");
        }
        String roomId = playerToRoomMap.get(oldHostId);
        CustomRoom room = activeRooms.get(roomId);
        if (room == null || !room.getHostId().equals(oldHostId)) {
            return CommonResponse.createForError("你不是房主，无权操作");
        }
        if (!room.getPlayers().contains(newHostId)) {
            return CommonResponse.createForError("该玩家不在房间内");
        }
        room.setHostId(newHostId);
        broadcastRoomUpdate(roomId);
        return CommonResponse.createForSuccessMessage("房主已移交");
    }

    @Override
    public CommonResponse<?> disbandRoom(Integer hostId) {
        String roomId = playerToRoomMap.get(hostId);
        CustomRoom room = activeRooms.get(roomId);
        if (room == null || !room.getHostId().equals(hostId)) {
            return CommonResponse.createForError("你不是房主，无权操作");
        }

        // 通知所有玩家房间已解散
        broadcastToRoom(roomId, Map.of("type", "room_disbanded"));

        // 清理所有玩家的状态
        room.getPlayers().forEach(playerId -> {
            playerToRoomMap.remove(playerId);
            onlineUserService.updateUserStatus(playerId, UserStatus.ONLINE);
            notifyFriendsAboutStatusChange(playerId, UserStatus.ONLINE);
        });

        activeRooms.remove(roomId);
        return CommonResponse.createForSuccessMessage("房间已解散");
    }

    @Override
    public CommonResponse<?> startGame(Integer hostId) {
        String roomId = playerToRoomMap.get(hostId);
        CustomRoom room = activeRooms.get(roomId);
        if (room == null || !room.getHostId().equals(hostId)) {
            return CommonResponse.createForError("你不是房主，无法开始游戏");
        }
        if (!room.isFull()) {
            return CommonResponse.createForError("房间未满员，无法开始游戏");
        }

        Map<Integer, Integer> selections = room.getPlayerCharacterSelections();

        // 1. 创建对局记录
        Match newMatch = new Match();
        newMatch.setGameMode("自定义房间");
        newMatch.setMapId(room.getMapId());
        newMatch.setStartTime(LocalDateTime.now());
        matchMapper.insert(newMatch);
        Long gameId = newMatch.getId();

        // 2. 更新房间和玩家状态
        room.setStatus("IN_GAME");
        List<Integer> playerIds = room.getPlayers().stream().toList();
        playerIds.forEach(pid -> {
            onlineUserService.updateUserStatus(pid, UserStatus.IN_GAME);
            // 新增: 通知每个玩家的好友其状态变更
            notifyFriendsAboutStatusChange(pid, UserStatus.IN_GAME);
        });
        // 3. 通知游戏模块准备游戏
        gameLobbyBridge.onMatchSuccess(gameId, selections);

        // 4. 通知房间内所有玩家，游戏开始
        String gameServerAddress = "ws://127.0.0.1:8080/game";
        GameMap map = mapMapper.selectById(room.getMapId()); // **新增: 查询地图信息**
        Map<String, Object> startGameMessage = new HashMap<>();
        startGameMessage.put("type", "game_start");
        startGameMessage.put("gameId", gameId);
        startGameMessage.put("serverAddress", gameServerAddress);
        startGameMessage.put("characterSelections", selections);
        if (map != null) {
            startGameMessage.put("mapId", map.getId());
            startGameMessage.put("mapName", map.getName());
        }
        broadcastToRoom(roomId, startGameMessage);
        return CommonResponse.createForSuccess("游戏开始", gameId);
    }

    /**
     * 辅助方法，用于通知好友状态变更
     * @param userId 状态变更的用户ID
     * @param status 新的状态
     */
    private void notifyFriendsAboutStatusChange(Integer userId, UserStatus status) {
        List<UserProfile> friends = friendMapper.selectFriendsProfiles(userId);
        if (friends.isEmpty()) {
            return;
        }

        UserProfile userProfile = userProfileMapper.selectById(userId);
        if (userProfile == null) return;

        Map<String, Object> notification = Map.of(
                "type", "status_update",
                "userId", userId,
                "nickname", userProfile.getNickname(),
                "status", status.toString() // 使用枚举的字符串形式
        );

        System.out.println("Notifying online friends of user " + userId + " about status change to " + status);
        for (UserProfile friend : friends) {
            if (onlineUserService.isUserOnline(friend.getUserId())) {
                webSocketSessionManager.sendMessageToUser(friend.getUserId(), notification);
            }
        }
    }

    private void broadcastRoomUpdate(String roomId) {
        CustomRoom room = activeRooms.get(roomId);
        if (room == null) return;
        CustomRoomDTO roomDTO = buildRoomDTO(room);
        broadcastToRoom(roomId, Map.of("type", "room_update", "room", roomDTO));
    }

    private CustomRoomDTO buildRoomDTO(CustomRoom room) {
        List<UserProfile> profiles = userProfileMapper.selectBatchIds(room.getPlayers());
        Map<Integer, String> idToNicknameMap = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getUserId, UserProfile::getNickname));

        Map<Integer, String> characterIdToNameMap = characterMapper.selectList(null).stream()
                .collect(Collectors.toMap(GameCharacter::getId, GameCharacter::getName));
        GameMap map = mapMapper.selectById(room.getMapId());

        List<PlayerInRoomDTO> playerDTOs = room.getPlayers().stream()
                .map(pid -> new PlayerInRoomDTO(
                        pid,
                        idToNicknameMap.getOrDefault(pid, "未知玩家"),
                        pid.equals(room.getHostId()),
                        room.getPlayerCharacterSelections().get(pid), // 获取角色ID
                        characterIdToNameMap.get(room.getPlayerCharacterSelections().get(pid)) // 获取角色名
                ))
                .collect(Collectors.toList());

        CustomRoomDTO dto = new CustomRoomDTO();
        dto.setRoomId(room.getRoomId());
        dto.setHostId(room.getHostId());
        dto.setPlayers(playerDTOs);
        dto.setRoomStatus(room.getStatus());
        if (map != null) {
            dto.setMapId(map.getId());
            dto.setMapName(map.getName());
        }
        return dto;
    }

    private void broadcastToRoom(String roomId, Object payload) {
        CustomRoom room = activeRooms.get(roomId);
        if (room != null) {
            room.getPlayers().forEach(playerId -> {
                if (onlineUserService.isUserOnline(playerId)) { // 增加一个在线判断，更稳健
                    webSocketSessionManager.sendMessageToUser(playerId, payload);
                }
            });
        }
    }
}
