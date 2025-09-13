package org.csu.pixelstrikebackend.game.service;

import org.csu.pixelstrikebackend.game.model.SupplyDrop;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SupplyDropManager {

    private final Map<Long, SupplyDrop> activeDrops = new ConcurrentHashMap<>();

    public void addDrop(SupplyDrop drop) {
        activeDrops.put(drop.getId(), drop);
    }

    public SupplyDrop removeDrop(long dropId) {
        return activeDrops.remove(dropId);
    }

    public Collection<SupplyDrop> getActiveDrops() {
        return activeDrops.values();
    }
    
    public void clearAllDrops() {
        activeDrops.clear();
    }
}