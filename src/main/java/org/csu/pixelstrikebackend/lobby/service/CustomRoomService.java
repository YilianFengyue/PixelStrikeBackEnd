package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;

import java.util.Map;

public interface CustomRoomService {

    CommonResponse<Map<String, String>> createRoom(Integer hostId);

    CommonResponse<Map<String, String>> joinRoom(String roomId, Integer userId);

    CommonResponse<?> startRoom(String roomId, Integer userId);

    CommonResponse<?> leaveRoom(Integer userId);
}
