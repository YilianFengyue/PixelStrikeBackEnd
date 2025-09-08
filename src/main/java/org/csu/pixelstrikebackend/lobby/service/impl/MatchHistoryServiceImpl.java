package org.csu.pixelstrikebackend.lobby.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.MatchDetailDTO;
import org.csu.pixelstrikebackend.lobby.dto.MatchHistoryDTO;
import org.csu.pixelstrikebackend.lobby.dto.ParticipantStatsDTO;
import org.csu.pixelstrikebackend.lobby.entity.GameMap;
import org.csu.pixelstrikebackend.lobby.entity.Match;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.csu.pixelstrikebackend.lobby.mapper.MapMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MatchParticipantMapper;
import org.csu.pixelstrikebackend.lobby.mapper.UserProfileMapper;
import org.csu.pixelstrikebackend.lobby.service.MatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("matchHistoryService")
public class MatchHistoryServiceImpl implements MatchHistoryService {

    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private MatchParticipantMapper matchParticipantMapper;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private MapMapper mapMapper;

    @Override
    public CommonResponse<?> getMatchHistory(Integer userId) {
        // 1. 查找该用户参与的所有对局记录
        QueryWrapper<MatchParticipant> participantQuery = new QueryWrapper<>();
        participantQuery.eq("user_id", userId);
        List<MatchParticipant> participations = matchParticipantMapper.selectList(participantQuery);

        if (participations.isEmpty()) {
            return CommonResponse.createForSuccess("暂无历史战绩", List.of());
        }

        // 2. 获取所有相关的Match ID
        List<Long> matchIds = participations.stream().map(MatchParticipant::getMatchId).collect(Collectors.toList());

        // 3. 一次性查询所有相关的Match信息
        List<Match> matches = matchMapper.selectBatchIds(matchIds);
        Map<Long, Match> matchMap = matches.stream().collect(Collectors.toMap(Match::getId, m -> m));

        // 4. 组装成DTO列表
        List<MatchHistoryDTO> historyList = participations.stream().map(p -> {
            Match match = matchMap.get(p.getMatchId());
            if (match == null) return null;
            /**暂未修改MatchHistoryDTO，通过mapId来获取mapName并返回
             *
             */
            int mapId = match.getMapId();
            GameMap map = mapMapper.selectById(mapId);
            MatchHistoryDTO dto = new MatchHistoryDTO();
            dto.setMatchId(match.getId());
            dto.setGameMode(match.getGameMode());
            dto.setMapName(map.getName());
            dto.setCharacterId(p.getCharacterId());
            dto.setStartTime(match.getStartTime());
            dto.setRanking(p.getRanking());
            return dto;
        }).collect(Collectors.toList());

        return CommonResponse.createForSuccess("获取成功", historyList);
    }

    @Override
    public CommonResponse<?> getMatchDetails(Long matchId) {
        // 1. 查询对局基本信息
        Match match = matchMapper.selectById(matchId);
        if (match == null) {
            return CommonResponse.createForError("对局不存在");
        }

        // 2. 查询该对局所有参与者的战绩
        QueryWrapper<MatchParticipant> participantQuery = new QueryWrapper<>();
        participantQuery.eq("match_id", matchId);
        List<MatchParticipant> participants = matchParticipantMapper.selectList(participantQuery);

        // 3. 获取所有参与者的User ID
        List<Integer> userIds = participants.stream().map(MatchParticipant::getUserId).collect(Collectors.toList());

        // 4. 一次性查询所有参与者的个人信息 (昵称等)
        Map<Integer, UserProfile> userProfileMap = userProfileMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, up -> up));

        // 5. 组装每个参与者的战绩DTO
        List<ParticipantStatsDTO> participantStats = participants.stream().map(p -> {
            UserProfile profile = userProfileMap.get(p.getUserId());
            ParticipantStatsDTO statsDTO = new ParticipantStatsDTO();
            statsDTO.setUserId(p.getUserId());
            statsDTO.setNickname(profile != null ? profile.getNickname() : "未知玩家");
            statsDTO.setKills(p.getKills());
            statsDTO.setCharacterId(p.getCharacterId());
            statsDTO.setDeaths(p.getDeaths());
            statsDTO.setRanking(p.getRanking());
            return statsDTO;
        }).collect(Collectors.toList());

        // 6. 组装最终的对局详情DTO
        MatchDetailDTO detailDTO = new MatchDetailDTO();
        /**
         * 暂时未修改MatchDetailDTO，通过mapId来获取name
         */
        int mapId = match.getMapId();
        GameMap map = mapMapper.selectById(mapId);
        detailDTO.setMatchId(match.getId());
        detailDTO.setGameMode(match.getGameMode());
        detailDTO.setMapName(map.getName());
        detailDTO.setStartTime(match.getStartTime());
        detailDTO.setEndTime(match.getEndTime());
        detailDTO.setParticipants(participantStats);

        return CommonResponse.createForSuccess("获取详情成功", detailDTO);
    }
}