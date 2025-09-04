package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;

public interface MatchHistoryService {
    CommonResponse<?> getMatchHistory(Integer userId);
    CommonResponse<?> getMatchDetails(Long matchId);
}
