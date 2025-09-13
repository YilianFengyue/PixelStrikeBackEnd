package org.csu.pixelstrikebackend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.csu.pixelstrikebackend.config.GameConfig;
import org.csu.pixelstrikebackend.game.model.SupplyDrop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class ItemSpawnService {

    @Autowired private SupplyDropManager supplyDropManager;
    @Autowired private GameSessionManager sessionManager;
    @Autowired private GameManager gameManager;
    @Autowired private GameConfig gameConfig;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Random random = new Random();

    private final List<String> weaponDropTypes = Arrays.asList(
            "MachineGun",
            "Shotgun",
            "Railgun",
            "GrenadeLauncher"
    );

    private final List<double[]> spawnPoints = Arrays.asList(
            // 在 X=800 的平台上方
            new double[]{950, 2500},
            // 在 X=2000 的平台上方
            new double[]{2200, 2400},
            // 在地面区域的空中
            new double[]{500, 2700},
            new double[]{3000, 2700}
    );

    /**
     * 每隔20秒执行一次，为所有正在进行的游戏刷新物资。
     */
    @Scheduled(fixedRate = 20000)
    public void spawnItems() {
        if (gameManager.getActiveGames().isEmpty()) {
            return;
        }

        System.out.println("Item spawner triggered...");

        double[] point = spawnPoints.get(random.nextInt(spawnPoints.size()));

        String dropType;
        if (random.nextBoolean()) {
            // 随机选择一种武器
            dropType = weaponDropTypes.get(random.nextInt(weaponDropTypes.size()));
        } else {
            dropType = "HEALTH_PACK";
        }
        SupplyDrop newDrop = new SupplyDrop(dropType, point[0], point[1]);
        supplyDropManager.addDrop(newDrop);

        ObjectNode spawnMsg = mapper.createObjectNode();
        spawnMsg.put("type", "supply_spawn");
        spawnMsg.put("dropId", newDrop.getId());
        spawnMsg.put("dropType", newDrop.getType());
        spawnMsg.put("x", newDrop.getX());
        spawnMsg.put("y", newDrop.getY());

        sessionManager.broadcast(spawnMsg.toString());
        System.out.println("Spawned " + newDrop.getType() + " at (" + newDrop.getX() + ", " + newDrop.getY() + ")");
    }
}