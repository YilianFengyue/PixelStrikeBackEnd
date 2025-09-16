package org.csu.pixelstrikebackend.game.model;

import lombok.Getter;

@Getter
public class SupplyDrop {
    private static long nextId = 0;

    private final long id;
    private final String type; // 例如 "HEALTH_PACK"
    private final double x;
    private final double y;
    private final Long gameId; // ★ 新增：补给品所属的游戏ID ★

    public SupplyDrop(String type, double x, double y, Long gameId) {
        this.id = nextId++;
        this.type = type;
        this.x = x;
        this.y = y;
        this.gameId = gameId; // ★ 新增：构造函数中赋值 ★
    }
}