package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
