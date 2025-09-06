package org.csu.pixelstrikebackend.lobby.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.pixelstrikebackend.lobby.dto.MatchHistoryDTO;
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

    @Select("SELECT m.id as matchId, m.game_mode as gameMode, m.start_time as startTime, " +
            "mp.kills, mp.deaths, mp.ranking " +
            "FROM matches m JOIN match_participants mp ON m.id = mp.match_id " +
            "WHERE mp.user_id = #{userId} " +
            "ORDER BY m.start_time DESC") // 按开始时间降序，最新的比赛在前
    List<MatchHistoryDTO> selectMatchHistoryForUser(@Param("userId") Integer userId);
}
