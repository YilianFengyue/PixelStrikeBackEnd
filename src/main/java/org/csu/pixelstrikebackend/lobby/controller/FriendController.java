package org.csu.pixelstrikebackend.lobby.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.FriendDetailDTO;
import org.csu.pixelstrikebackend.lobby.dto.FriendListDTO;
import org.csu.pixelstrikebackend.lobby.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friends")
public class FriendController {
    @Autowired
    private FriendService friendService;

    // 按昵称模糊搜索用户 (GET /friends/search?nickname=...)
    @GetMapping("/search")
    public CommonResponse<List<FriendListDTO>> searchUsers(@RequestParam String nickname, HttpServletRequest exchange) {
        // 从请求中获取当前登录用户的 ID
        Integer currentUserId = (Integer) exchange.getAttribute("userId");
        return friendService.searchUsersByNickname(nickname, currentUserId);
    }

    // 发送好友请求 (POST /friends/requests/{addrId})
    @PostMapping("/requests/{addrId}")
    public CommonResponse<?> sendFriendRequest(@PathVariable Integer addrId, HttpServletRequest exchange) {
        Integer senderId = (Integer) exchange.getAttribute("userId");
        return friendService.sendFriendRequest(senderId, addrId);
    }

    // 查看我的好友申请 (GET /friends/requests/pending)
    @GetMapping("/requests/pending")
    public CommonResponse<List<FriendListDTO>> getPendingRequests(HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return friendService.getPendingRequests(userId);
    }

    // 同意好友申请 (PUT /friends/requests/{senderId}/accept)
    @PutMapping("/requests/{senderId}/accept")
    public CommonResponse<?> acceptFriendRequest(@PathVariable Integer senderId, HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return friendService.acceptFriendRequest(userId, senderId);
    }

    // 拒绝好友申请 (DELETE /friends/requests/{senderId}/reject)
    @DeleteMapping("/requests/{senderId}/reject")
    public CommonResponse<?> rejectFriendRequest(@PathVariable Integer senderId, HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return friendService.rejectFriendRequest(userId, senderId);
    }

    /**
     * **接口1: 获取好友列表**
     * 用于好友列表页，返回包含昵称、头像和在线状态的摘要信息。
     * GET /friends
     */
    @GetMapping
    public CommonResponse<List<FriendListDTO>> getFriendList(HttpServletRequest exchange) {
        Integer userId = (Integer) exchange.getAttribute("userId");
        return friendService.getFriendList(userId);
    }

    @DeleteMapping("/{friendId}")
    public CommonResponse<?> deleteFriend(@PathVariable Integer friendId, HttpServletRequest exchange) {
        Integer currentUserId = (Integer) exchange.getAttribute("userId");
        return friendService.deleteFriend(currentUserId, friendId);
    }

    /**
     * **接口2: 获取好友详情**
     * 用于好友详情页，当用户从列表点击某个好友时调用。
     * GET /friends/{friendId}/details
     */
    @GetMapping("/{friendId}/details")
    public CommonResponse<FriendDetailDTO> getFriendDetails(@PathVariable Integer friendId, HttpServletRequest exchange) {
        Integer currentUserId = (Integer) exchange.getAttribute("userId");
        return friendService.getFriendDetails(currentUserId, friendId);
    }
}