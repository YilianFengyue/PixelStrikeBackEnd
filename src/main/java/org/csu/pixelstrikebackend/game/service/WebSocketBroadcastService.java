package org.csu.pixelstrikebackend.game.service;


import com.google.gson.Gson;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 一个专门负责WebSocket广播的无状态服务。
 * 它将游戏状态对象序列化为JSON，并将其发送给指定的客户端会话集合。
 */
@Service
public class WebSocketBroadcastService {

    private final Gson gson = new Gson();

    /**
     * 向指定的会话集合广播一个游戏世界快照。
     * @param sessions 要接收消息的客户端WebSocket会话集合。
     * @param snapshot 要发送的游戏世界状态快照。
     */
    public void broadcast(Collection<WebSocketSession> sessions, GameStateSnapshot snapshot) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String jsonSnapshot = gson.toJson(snapshot);

        // 2. 为所有会话创建一个发送任务的流 (Flux)
        Flux.fromIterable(sessions)
                .flatMap(session -> {
                    if (session.isOpen()) {
                        // 使用 session.textMessage() 创建消息
                        WebSocketMessage message = session.textMessage(jsonSnapshot);
                        // 返回一个发送操作的 Mono，如果出错则记录并返回一个空的 Mono
                        return session.send(Mono.just(message))
                                .onErrorResume(e -> {
                                    System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
                                    return Mono.empty();
                                });
                    }
                    return Mono.empty();
                })
                .subscribe(); // 订阅并触发所有发送操作
    }
}