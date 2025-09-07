package org.csu.pixelstrikebackend.game.system;

import org.csu.pixelstrikebackend.dto.GameStateSnapshot;
import org.springframework.stereotype.Component;

@Component
public class GameCountdownSystem {

    private static final int COUNTDOWN_SECONDS = 5; // 倒计时5秒

    /**
     * 处理倒计时逻辑。
     * @param roomState 游戏房间的当前状态
     * @param snapshot 要发送给客户端的快照
     * @return 如果倒计时结束，返回 true，否则返回 false
     */
    public boolean update(RoomState roomState, GameStateSnapshot snapshot) {
        long elapsedTime = System.currentTimeMillis() - roomState.countdownStartTime;
        int secondsRemaining = COUNTDOWN_SECONDS - (int)(elapsedTime / 1000);

        // 将剩余秒数放入快照，以便客户端显示
        snapshot.setCountdownSeconds(secondsRemaining);

        if (secondsRemaining <= 0) {
            System.out.println("房间 " + roomState.roomId + " 倒计时结束，游戏开始！");
            return true; // 倒计时结束
        }

        return false; // 倒计时仍在进行
    }

    // 辅助内部类，用于传递房间状态
    public static class RoomState {
        public String roomId;
        public long countdownStartTime;

        public RoomState(String roomId) {
            this.roomId = roomId;
            this.countdownStartTime = System.currentTimeMillis();
        }
    }
}