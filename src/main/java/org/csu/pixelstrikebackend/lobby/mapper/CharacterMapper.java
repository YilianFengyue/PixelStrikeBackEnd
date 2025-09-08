package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.pixelstrikebackend.lobby.entity.GameCharacter;

@Mapper
public interface CharacterMapper extends BaseMapper<GameCharacter> {
}