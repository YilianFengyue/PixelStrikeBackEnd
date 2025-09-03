package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.pixelstrikebackend.entity.Friend;
import org.csu.pixelstrikebackend.entity.UserProfile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendMapper extends BaseMapper<Friend> {
    // 自定义 SQL 查询，获取一个用户的所有好友的个人资料
    @Select("SELECT up.* FROM user_profiles up " +
            "JOIN friends f ON (up.user_id = f.sender_id OR up.user_id = f.addr_id) " +
            "WHERE (f.sender_id = #{userId} OR f.addr_id = #{userId}) " +
            "AND f.status = 'accepted' AND up.user_id != #{userId}")
    List<UserProfile> selectFriendsProfiles(@Param("userId") Integer userId);
}