package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.FriendDetailDTO;
import org.csu.pixelstrikebackend.lobby.dto.FriendListDTO;

import java.util.List;

public interface FriendService {

    CommonResponse<List<FriendListDTO>> searchUsersByNickname(String nickname, Integer currentUserId);
    CommonResponse<?> sendFriendRequest(Integer senderId, Integer addrId);
    CommonResponse<List<FriendListDTO>> getPendingRequests(Integer userId);
    CommonResponse<?> acceptFriendRequest(Integer userId, Integer senderId);
    CommonResponse<?> rejectFriendRequest(Integer userId, Integer senderId);
    CommonResponse<?> deleteFriend(Integer userId, Integer friendId);

    // 接口 1: 获取好友列表 (只返回基本资料)
    CommonResponse<List<FriendListDTO>> getFriendList(Integer userId);

    // 接口 2: 获取指定好友的详细信息 (包含在线状态)
    CommonResponse<FriendDetailDTO> getFriendDetails(Integer userId, Integer friendId);
}