package org.csu.pixelstrikebackend.game.geom;

public final class HitMath {
    private HitMath() {}

    /** 线段 p(t)= (ox,oy) + t*(dx,dy), t∈[0,1] 与 AABB [minX,maxX]×[minY,maxY] 是否相交；返回 tEnter(命中最近点)，未命中返回 +INF */
    public static double raySegmentVsAABB(double ox, double oy, double dx, double dy,
                                          double minX, double minY, double maxX, double maxY) {
        // Liang–Barsky / Slab method
        double t0 = 0.0;
        double t1 = 1.0;

        if (!updateInterval(-dx, ox - minX, t0, t1)) return Double.POSITIVE_INFINITY;
        t0 = tmpT0; t1 = tmpT1;
        if (!updateInterval( dx, maxX - ox, t0, t1)) return Double.POSITIVE_INFINITY;
        t0 = tmpT0; t1 = tmpT1;

        if (!updateInterval(-dy, oy - minY, t0, t1)) return Double.POSITIVE_INFINITY;
        t0 = tmpT0; t1 = tmpT1;
        if (!updateInterval( dy, maxY - oy, t0, t1)) return Double.POSITIVE_INFINITY;
        t0 = tmpT0; t1 = tmpT1;

        return (t0 <= t1) ? Math.max(0.0, t0) : Double.POSITIVE_INFINITY;
    }

    // --- 内部：为避免频繁创建 Pair，这里用静态线程不安全临时变量（本服务单线程处理消息即可）
    private static double tmpT0, tmpT1;
    private static boolean updateInterval(double p, double q, double t0, double t1) {
        if (p == 0.0) {
            if (q < 0.0) return false;
            tmpT0 = t0; tmpT1 = t1;
            return true;
        }
        double r = q / p;
        if (p < 0.0) {
            if (r > t1) return false;
            if (r > t0) t0 = r;
        } else {
            if (r < t0) return false;
            if (r < t1) t1 = r;
        }
        tmpT0 = t0; tmpT1 = t1;
        return true;
    }
}
