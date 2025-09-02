package org.csu.pixelstrikebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.csu.pixelstrikebackend.common.CommonResponse;
import org.csu.pixelstrikebackend.dto.FriendDetailDTO;
import org.csu.pixelstrikebackend.dto.FriendListDTO;
import org.csu.pixelstrikebackend.entity.Friend;
import org.csu.pixelstrikebackend.entity.UserProfile;
import org.csu.pixelstrikebackend.mapper.FriendMapper;
import org.csu.pixelstrikebackend.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.service.FriendService;
import org.csu.pixelstrikebackend.service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service("friendService")
public class FriendServiceImpl implements FriendService {

    // ... (Autowired 字段和 search, send, accept, delete 方法保持不变)
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private FriendMapper friendMapper;
    @Autowired
    private OnlineUserService onlineUserService;

    @Override
    public CommonResponse<List<FriendListDTO>> searchUsersByNickname(String nickname, Integer currentUserId) {
        QueryWrapper<UserProfile> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .like("nickname", nickname) // 按昵称模糊查询
                .ne("user_id", currentUserId); // **核心改动1: 排除当前用户自己**

        List<UserProfile> userProfiles = userProfileMapper.selectList(queryWrapper);

        if (userProfiles.isEmpty()) {
            return CommonResponse.createForSuccess("没有找到相关用户", new ArrayList<>());
        }

        // **核心改动2: 将查询结果转换为 FriendListDTO 格式**
        List<FriendListDTO> resultList = userProfiles.stream().map(profile -> {
            FriendListDTO dto = new FriendListDTO();
            dto.setUserId(profile.getUserId());
            dto.setNickname(profile.getNickname());
            dto.setAvatarUrl(profile.getAvatarUrl());
            // 获取并设置在线状态
            dto.setOnlineStatus(onlineUserService.getUserStatus(profile.getUserId()));
            return dto;
        }).collect(Collectors.toList());

        return CommonResponse.createForSuccess("搜索成功", resultList);
    }

    @Override
    public CommonResponse<?> sendFriendRequest(Integer senderId, Integer addrId) {
        if (senderId.equals(addrId)) {
            return CommonResponse.createForError("不能添加自己为好友");
        }
        QueryWrapper<Friend> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq("sender_id", senderId).eq("addr_id", addrId))
                .or(wrapper -> wrapper.eq("sender_id", addrId).eq("addr_id", senderId));
        if (friendMapper.exists(queryWrapper)) {
            return CommonResponse.createForError("你们已经是好友或已发送过请求");
        }
        Friend friendRequest = new Friend();
        friendRequest.setSenderId(senderId);
        friendRequest.setAddrId(addrId);
        friendRequest.setStatus("pending");
        friendMapper.insert(friendRequest);
        return CommonResponse.createForSuccessMessage("好友请求已发送");
    }

    @Override
    public CommonResponse<List<FriendListDTO>> getPendingRequests(Integer userId) {
        QueryWrapper<Friend> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("addr_id", userId).eq("status", "pending");
        List<Friend> requests = friendMapper.selectList(queryWrapper);
        if (requests.isEmpty()) {
            return CommonResponse.createForSuccess("没有待处理的好友请求", new ArrayList<>());
        }
        List<Integer> senderIds = requests.stream().map(Friend::getSenderId).toList();
        List<UserProfile> senderProfiles = userProfileMapper.selectBatchIds(senderIds);
        List<FriendListDTO> resultList = senderProfiles.stream().map(profile -> {
            FriendListDTO dto = new FriendListDTO();
            dto.setUserId(profile.getUserId());
            dto.setNickname(profile.getNickname());
            dto.setAvatarUrl(profile.getAvatarUrl());
            // 获取并设置在线状态
            dto.setOnlineStatus(onlineUserService.getUserStatus(profile.getUserId()));
            return dto;
        }).collect(Collectors.toList());

        return CommonResponse.createForSuccess("查询成功", resultList);
    }

    @Override
    public CommonResponse<?> acceptFriendRequest(Integer userId, Integer senderId) {
        UpdateWrapper<Friend> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("sender_id", senderId).eq("addr_id", userId).eq("status", "pending");
        updateWrapper.set("status", "accepted");
        int result = friendMapper.update(null, updateWrapper);
        return result > 0 ? CommonResponse.createForSuccessMessage("已同意好友请求") : CommonResponse.createForError("请求不存在或已处理");
    }

    @Override
    public CommonResponse<?> deleteFriend(Integer userId, Integer friendId) {
        QueryWrapper<Friend> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq("sender_id", userId).eq("addr_id", friendId))
                .or(wrapper -> wrapper.eq("sender_id", friendId).eq("addr_id", userId));
        queryWrapper.eq("status", "accepted");
        int result = friendMapper.delete(queryWrapper);
        return result > 0 ? CommonResponse.createForSuccessMessage("好友已删除") : CommonResponse.createForError("删除失败，对方不是你的好友");
    }


    @Override
    public CommonResponse<List<FriendListDTO>> getFriendList(Integer userId) {
        // 1. 使用自定义 Mapper 方法获取所有好友的个人资料
        List<UserProfile> friendProfiles = friendMapper.selectFriendsProfiles(userId);
        if (friendProfiles.isEmpty()) {
            return CommonResponse.createForSuccess("好友列表为空", new ArrayList<>());
        }

        // 2. 将 UserProfile 转换为轻量的 FriendListDTO
        List<FriendListDTO> friendList = new ArrayList<>();
        for (UserProfile profile : friendProfiles) {
            FriendListDTO dto = new FriendListDTO();
            dto.setUserId(profile.getUserId());
            dto.setNickname(profile.getNickname());
            dto.setAvatarUrl(profile.getAvatarUrl());
            // 从 OnlineUserService 获取并设置在线状态
            dto.setOnlineStatus(onlineUserService.getUserStatus(profile.getUserId()));
            friendList.add(dto);
        }
        return CommonResponse.createForSuccess("获取成功", friendList);
    }

    @Override
    public CommonResponse<FriendDetailDTO> getFriendDetails(Integer currentUserId, Integer friendId) {
        // 1. 验证他们是否是好友关系
        QueryWrapper<Friend> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq("sender_id", currentUserId).eq("addr_id", friendId))
                .or(wrapper -> wrapper.eq("sender_id", friendId).eq("addr_id", currentUserId));
        queryWrapper.eq("status", "accepted");
        if (!friendMapper.exists(queryWrapper)) {
            return CommonResponse.createForError("对方不是你的好友，无法查看详情");
        }

        // 2. 获取好友的完整个人资料
        UserProfile profile = userProfileMapper.selectById(friendId);
        if (profile == null) {
            return CommonResponse.createForError("未找到该好友的信息");
        }

        // 3. 组装 FriendDetailDTO 并获取在线状态
        FriendDetailDTO detailDTO = new FriendDetailDTO();
        detailDTO.setProfile(profile);
        detailDTO.setOnlineStatus(onlineUserService.getUserStatus(friendId));

        return CommonResponse.createForSuccess("获取详情成功", detailDTO);
    }
}