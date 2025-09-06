package org.csu.pixelstrikebackend.lobby.controller;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.service.CustomRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

@RestController
@RequestMapping("/custom-rooms")
public class CustomRoomController {

    @Autowired
    private CustomRoomService customRoomService;

    // 创建房间
    @PostMapping("/create")
    public CommonResponse<Map<String, String>> createRoom(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return customRoomService.createRoom(userId);
    }

    // 加入房间
    @PostMapping("/join/{roomId}")
    public CommonResponse<Map<String, String>> joinRoom(@PathVariable String roomId, ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return customRoomService.joinRoom(roomId, userId);
    }

    // 开始游戏
    @PostMapping("/{roomId}/start")
    public CommonResponse<?> startRoom(@PathVariable String roomId, ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return customRoomService.startRoom(roomId, userId);
    }

    // 离开房间
    @PostMapping("/leave")
    public CommonResponse<?> leaveRoom(ServerWebExchange exchange) {
        Integer userId = exchange.getAttribute("userId");
        return customRoomService.leaveRoom(userId);
    }
}