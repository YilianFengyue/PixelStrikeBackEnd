// 文件路径: src/main/java/org/csu/pixelstrikebackend/controller/RoomController.java

package org.csu.pixelstrikebackend.lobby.controller;

import org.csu.pixelstrikebackend.game.service.GameRoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private GameRoomManager roomManager;

    /**
     * 客户端通过此接口请求“快速开始”以获取一个房间ID。
     * Dev2将在这里实现更复杂的匹配逻辑。
     * @return 包含roomId的响应
     */
    @PostMapping("/quick-join")
    public ResponseEntity<?> quickJoin() {
        // Dev2的逻辑: 查找一个未满员的房间或创建一个新房间
        // Dev1的临时实现: 永远返回 "room1"
        String roomId = "room1"; 

        // 确保这个房间的线程已经启动
        //roomManager.createAndStartRoom(roomId);

        // 将roomId返回给客户端
        return ResponseEntity.ok(Map.of("roomId", roomId));
    }
}