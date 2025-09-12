package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.geom.HitMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class HitValidationService {

    @Autowired
    private PlayerStateManager playerStateManager;

    // 命中判定常量 (可以考虑移到GameConfig)
    private static final double HB_OFF_X = 80.0;
    private static final double HB_OFF_Y = 20.0;
    private static final double HB_W     = 86.0;
    private static final double HB_H     = 160.0;

    public Optional<GameRoomService.HitInfo> validateShot(int shooterId, long shotSrvTS, double ox, double oy, double dx, double dy, double range) {
        dx = clampDir(dx);
        dy = clampDir(dy);
        double len = Math.hypot(dx, dy);
        if (len < 1e-6 || range <= 0) return Optional.empty();
        dx /= len; dy /= len;

        double rx = dx * range, ry = dy * range;

        double bestT = Double.POSITIVE_INFINITY;
        int bestVictim = -1;
        
        // 注意：这里需要一种方式来获取所有当前在游戏中的玩家ID
        // 暂时我们假设playerStateManager可以提供这个
        for (Integer victimId : playerStateManager.hpByPlayer.keySet()) { // 这是一个简化的示例
            if (victimId.equals(shooterId) || playerStateManager.isDead(victimId)) {
                continue;
            }

            Optional<GameRoomService.StateSnapshot> sOpt = playerStateManager.interpolateAt(victimId, shotSrvTS);
            if (sOpt.isEmpty()) continue;
            GameRoomService.StateSnapshot s = sOpt.get();

            double minX = s.x + HB_OFF_X;
            double minY = s.y + HB_OFF_Y;
            double maxX = minX + HB_W;
            double maxY = minY + HB_H;

            double tEnter = HitMath.raySegmentVsAABB(ox, oy, rx, ry, minX, minY, maxX, maxY);
            if (tEnter < bestT) {
                bestT = tEnter;
                bestVictim = victimId;
            }
        }

        if (bestVictim >= 0 && bestT <= 1.0) {
            return Optional.of(new GameRoomService.HitInfo(bestVictim, bestT));
        }
        return Optional.empty();
    }
    
    private static double clampDir(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (Math.abs(v) > 1e4) return Math.signum(v);
        return v;
    }
}