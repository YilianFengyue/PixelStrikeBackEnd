package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;

public interface CustomRoomService {
    CommonResponse<?> createRoom(Integer userId);
    CommonResponse<?> joinRoom(Integer userId, String roomId);
    CommonResponse<?> leaveRoom(Integer userId);
    CommonResponse<?> inviteFriend(Integer inviterId, Integer friendId);
    CommonResponse<?> kickPlayer(Integer hostId, Integer targetId);
    CommonResponse<?> transferHost(Integer oldHostId, Integer newHostId);
    CommonResponse<?> disbandRoom(Integer hostId);
    CommonResponse<?> startGame(Integer hostId);
    CommonResponse<?> acceptInvite(Integer userId, String roomId); // 新增接口

}
