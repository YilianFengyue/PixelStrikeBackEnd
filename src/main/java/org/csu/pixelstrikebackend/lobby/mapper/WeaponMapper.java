package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.pixelstrikebackend.lobby.entity.Weapon;

@Mapper
public interface WeaponMapper extends BaseMapper<Weapon> {
}
