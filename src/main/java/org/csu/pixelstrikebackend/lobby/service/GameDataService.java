package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;

public interface GameDataService {
    CommonResponse<?> getAllCharacters();
    CommonResponse<?> getAllWeapons();
}
