package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;

import java.util.List;

public interface MatchmakingService {
    CommonResponse<?> startMatchmaking(Integer userId, Integer mapId, Integer characterId);
    CommonResponse<?> cancelMatchmaking(Integer userId);
    void processGameResults(Long gameId, List<MatchParticipant> results);
}
