package org.csu.pixelstrikebackend.service;

import com.google.gson.Gson;
import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
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
            return; // 如果没有接收者，则直接返回
        }

        // 1. 使用Gson将Java对象转换为JSON字符串
        String jsonSnapshot = gson.toJson(snapshot);
        TextMessage message = new TextMessage(jsonSnapshot);

        // 2. 遍历会话集合，向每个客户端发送消息
        for (WebSocketSession session : sessions) {
            try {
                // 确保会话是打开的，再发送消息
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                // 如果发送失败（例如，客户端突然断开连接），打印一个错误日志。
                // GameRoomManager中的断线逻辑会最终处理这个失效的会话。
                System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            }
        }
    }
}