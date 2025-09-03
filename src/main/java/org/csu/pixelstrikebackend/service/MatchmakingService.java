package org.csu.pixelstrikebackend.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;

public interface MatchmakingService {
    CommonResponse<?> startMatchmaking(Integer userId);
    CommonResponse<?> cancelMatchmaking(Integer userId);
}
