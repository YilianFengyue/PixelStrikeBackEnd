// src/main/java/org/csu/pixelstrikebackend/game/system/MapData.java
package org.csu.pixelstrikebackend.game.system;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.awt.geom.Rectangle2D;
import java.util.List;

@Component
public class MapData {

    @Getter
    private final List<Rectangle2D.Double> platforms;

    private final double Y_OFFSET = 2189.0; // 这是我们最终确定的精确偏移量
    private final double MAP_H = 3030.0; // 与前端 GameConfig.MAP_H 保持一致

    public MapData() {
        // 从前端 MapBuilder.java 读取平台参数并进行坐标转换
        // 公式: backendY = frontendY - Y_OFFSET

        // 第一个平台: buildAirPlatform(800, MAP_H - 470, 925, ...)
        double platform1_frontend_y = MAP_H - 470; // = 2560
        double platform1_backend_y = platform1_frontend_y - Y_OFFSET; // = 371

        // 第二个平台: buildAirPlatform(2000, MAP_H - 600, 925, ...)
        double platform2_frontend_y = MAP_H - 600; // = 2430
        double platform2_backend_y = platform2_frontend_y - Y_OFFSET; // = 241

        platforms = List.of(
                // new Rectangle2D.Double(x, y, width, height)
                new Rectangle2D.Double(800, platform1_backend_y, 925, 30),
                new Rectangle2D.Double(2000, platform2_backend_y, 925, 30)
        );
    }

}