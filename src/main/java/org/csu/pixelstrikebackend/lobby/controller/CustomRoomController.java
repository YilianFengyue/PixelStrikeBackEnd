package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.service.CustomRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/custom-room")
public class CustomRoomController {

    @Autowired
    private CustomRoomService customRoomService;

    @PostMapping("/create")
    public CommonResponse<?> createRoom(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return customRoomService.createRoom(userId);
    }

    @PostMapping("/join")
    public CommonResponse<?> joinRoom(@RequestParam String roomId, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return customRoomService.joinRoom(userId, roomId);
    }

    @PostMapping("/leave")
    public CommonResponse<?> leaveRoom(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return customRoomService.leaveRoom(userId);
    }

    @PostMapping("/invite")
    public CommonResponse<?> inviteFriend(@RequestParam Integer friendId, HttpServletRequest request) {
        Integer inviterId = (Integer) request.getAttribute("userId");
        return customRoomService.inviteFriend(inviterId, friendId);
    }

    // 新增: 接受邀请的API
    @PostMapping("/accept-invite")
    public CommonResponse<?> acceptInvite(@RequestParam String roomId, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return customRoomService.acceptInvite(userId, roomId);
    }

    @PostMapping("/kick")
    public CommonResponse<?> kickPlayer(@RequestParam Integer targetId, HttpServletRequest request) {
        Integer hostId = (Integer) request.getAttribute("userId");
        return customRoomService.kickPlayer(hostId, targetId);
    }

    @PostMapping("/transfer-host")
    public CommonResponse<?> transferHost(@RequestParam Integer newHostId, HttpServletRequest request) {
        Integer oldHostId = (Integer) request.getAttribute("userId");
        return customRoomService.transferHost(oldHostId, newHostId);
    }

    @PostMapping("/disband")
    public CommonResponse<?> disbandRoom(HttpServletRequest request) {
        Integer hostId = (Integer) request.getAttribute("userId");
        return customRoomService.disbandRoom(hostId);
    }

    @PostMapping("/start-game")
    public CommonResponse<?> startGame(HttpServletRequest request) {
        Integer hostId = (Integer) request.getAttribute("userId");
        return customRoomService.startGame(hostId);
    }
}