package org.csu.pixelstrikebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.pixelstrikebackend.entity.Match;

@Mapper
public interface MatchMapper extends BaseMapper<Match> {
}
