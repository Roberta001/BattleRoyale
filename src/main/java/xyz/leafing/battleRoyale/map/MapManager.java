package xyz.leafing.battleRoyale.map;

import xyz.leafing.battleRoyale.BattleRoyale;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapManager {

    private final BattleRoyale plugin;
    private final List<GameMap> availableMaps = new ArrayList<>();
    private int totalWeight = 0;
    private final Random random = new Random();

    public MapManager(BattleRoyale plugin) {
        this.plugin = plugin;
    }

    public void loadMaps() {
        availableMaps.clear();
        totalWeight = 0;

        File mapsDir = new File(plugin.getDataFolder(), "maps");
        if (!mapsDir.exists() || !mapsDir.isDirectory()) {
            mapsDir.mkdirs();
            // 可以考虑在这里创建一个示例地图文件
        }

        File[] mapFiles = mapsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (mapFiles == null || mapFiles.length == 0) {
            plugin.getLogger().warning("未在 'maps' 文件夹中找到任何地图配置文件 (.yml)！");
            return;
        }

        for (File mapFile : mapFiles) {
            GameMap map = new GameMap(mapFile, plugin);
            availableMaps.add(map);
            totalWeight += map.getSelectionWeight();
            plugin.getLogger().info("已加载地图: " + map.getDisplayName() + " (权重: " + map.getSelectionWeight() + ")");
        }
    }

    public GameMap selectRandomMap() {
        if (availableMaps.isEmpty()) return null;
        if (totalWeight <= 0) return availableMaps.get(0);

        int pick = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (GameMap map : availableMaps) {
            cumulativeWeight += map.getSelectionWeight();
            if (pick < cumulativeWeight) {
                return map;
            }
        }
        return null; // Should not happen
    }
}