package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.entity.MatchParticipant;

import java.util.List;

public interface MatchService {
    void processMatchResults(Long gameId, List<MatchParticipant> results);
}
