package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.model.ServerProjectile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ProjectileManager {

    private final List<ServerProjectile> projectiles = new CopyOnWriteArrayList<>();

    public void addProjectile(ServerProjectile projectile) {
        projectiles.add(projectile);
    }

    public void removeProjectile(ServerProjectile projectile) {
        projectiles.remove(projectile);
    }

    public List<ServerProjectile> getProjectiles() {
        return projectiles;
    }
}