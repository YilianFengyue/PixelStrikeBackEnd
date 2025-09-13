package org.csu.pixelstrikebackend.game.model;

import lombok.Getter;

@Getter
public class SupplyDrop {
    private static long nextId = 0;

    private final long id;
    private final String type; // 例如 "HEALTH_PACK"
    private final double x;
    private final double y;

    public SupplyDrop(String type, double x, double y) {
        this.id = nextId++;
        this.type = type;
        this.x = x;
        this.y = y;
    }
}