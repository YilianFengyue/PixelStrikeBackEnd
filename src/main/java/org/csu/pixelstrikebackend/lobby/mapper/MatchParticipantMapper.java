package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.pixelstrikebackend.lobby.dto.MatchResultDTO;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;

import java.util.List;

@Mapper
public interface MatchParticipantMapper extends BaseMapper<MatchParticipant> {
    @Select("SELECT up.nickname, mp.kills, mp.deaths, mp.ranking " +
            "FROM match_participants mp " +
            "JOIN user_profiles up ON mp.user_id = up.user_id " +
            "WHERE mp.match_id = #{matchId} " +
            "ORDER BY mp.kills DESC") // 按击杀数降序排列
    List<MatchResultDTO> selectMatchResultsWithNickname(@Param("matchId") Long matchId);
}
