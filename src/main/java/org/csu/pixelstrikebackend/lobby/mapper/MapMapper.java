package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.csu.pixelstrikebackend.lobby.entity.GameMap;

import java.util.List;

@Mapper
public interface MapMapper extends BaseMapper<GameMap> {
    @Select("SELECT DISTINCT map_type FROM maps")
    List<String> selectAllMapTypes();
}
