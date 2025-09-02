package org.csu.pixelstrikebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.pixelstrikebackend.entity.UserProfile;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
