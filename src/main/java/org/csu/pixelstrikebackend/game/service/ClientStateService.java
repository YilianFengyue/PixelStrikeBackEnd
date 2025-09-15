// src/main/java/org/csu/pixelstrikebackend/game/service/ClientStateService.java
package org.csu.pixelstrikebackend.game.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新增的服务，专门管理每个客户端连接的状态
 * 包括消息频率控制 (节流) 和时钟同步
 */
@Service
public class ClientStateService {

    private final Map<String, GameRoomService.RateCounter> anyCounterBySession = new ConcurrentHashMap<>();
    private final Map<String, GameRoomService.RateCounter> stateCounterBySession = new ConcurrentHashMap<>();
    private final Map<String, GameRoomService.ClockAlign> clockBySession = new ConcurrentHashMap<>();

    public void registerSession(WebSocketSession s) {
        anyCounterBySession.put(s.getId(), new GameRoomService.RateCounter(200)); // 每秒最多200条消息
        stateCounterBySession.put(s.getId(), new GameRoomService.RateCounter(120)); // 每秒最多120条状态消息
        clockBySession.put(s.getId(), new GameRoomService.ClockAlign());
    }

    public void unregisterSession(WebSocketSession s) {
        anyCounterBySession.remove(s.getId());
        stateCounterBySession.remove(s.getId());
        clockBySession.remove(s.getId());
    }

    public boolean allowAnyMessage(WebSocketSession s, long nowMs) {
        GameRoomService.RateCounter rc = anyCounterBySession.get(s.getId());
        return rc == null || rc.allow(nowMs);
    }

    public boolean allowStateMessage(WebSocketSession s, long nowMs) {
        GameRoomService.RateCounter rc = stateCounterBySession.get(s.getId());
        return rc == null || rc.allow(nowMs);
    }

    public void updateClock(WebSocketSession s, long clientTs, long srvTs) {
        if (clientTs <= 0) return;
        GameRoomService.ClockAlign ca = clockBySession.get(s.getId());
        if (ca != null) ca.update(clientTs, srvTs);
    }

    public long toServerTime(WebSocketSession s, long clientTs) {
        GameRoomService.ClockAlign ca = clockBySession.get(s.getId());
        return (ca != null) ? ca.toServerTime(clientTs) : System.currentTimeMillis();
    }
}