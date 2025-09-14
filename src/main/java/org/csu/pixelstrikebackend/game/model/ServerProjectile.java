package org.csu.pixelstrikebackend.game.model;

import lombok.Getter;

@Getter
public class ServerProjectile {
    private static long nextId = 0;

    private final long id;
    private final int shooterId;
    private double x, y;
    private final double velocityX, velocityY;
    private final long spawnTime;
    private final double range;

    private final int damage;
    private final String weaponType;

    public ServerProjectile(int shooterId, double x, double y, double dx, double dy, double speed, double range, int damage, String weaponType) {
        this.id = nextId++;
        this.shooterId = shooterId;
        this.x = x;
        this.y = y;
        this.velocityX = dx * speed;
        this.velocityY = dy * speed;
        this.spawnTime = System.currentTimeMillis();
        this.range = range;
        this.damage = damage;
        this.weaponType = weaponType;
    }
    public void update(double deltaTime) {
        this.x += velocityX * deltaTime;
        this.y += velocityY * deltaTime;
    }

    public boolean isOutOfRange(double startX, double startY) {
        double distanceSq = (x - startX) * (x - startX) + (y - startY) * (y - startY);
        return distanceSq > range * range;
    }
}