package xyz.leafing.battleRoyale.map;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.leafing.battleRoyale.BattleRoyale;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameMap {

    public enum WorldSourceType { GENERATE, COPY }

    private final String id;
    private final FileConfiguration mapConfig;
    private final FileConfiguration defaultConfig;

    private String displayName;
    private int selectionWeight;
    private WorldSourceType worldSourceType;
    private String sourceFolderName;
    private List<Location> spawnPoints;

    public GameMap(File mapFile, BattleRoyale plugin) {
        this.id = mapFile.getName().replace(".yml", "");
        this.mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        this.defaultConfig = plugin.getGlobalConfig();
        loadConfig();
    }

    private void loadConfig() {
        displayName = getSetting("display-name", id);
        selectionWeight = getSetting("selection-weight", 10);
        worldSourceType = WorldSourceType.valueOf(getSetting("world-source.type", "GENERATE").toUpperCase());
        sourceFolderName = getSetting("world-source.source-folder-name", "");

        List<String> spawnStrings = mapConfig.getStringList("spawn-points");
        if (!spawnStrings.isEmpty()) {
            spawnPoints = spawnStrings.stream().map(s -> {
                String[] parts = s.split(",");
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                float yaw = (parts.length > 3) ? Float.parseFloat(parts[3].trim()) : 0;
                float pitch = (parts.length > 4) ? Float.parseFloat(parts[4].trim()) : 0;
                return new Location(null, x, y, z, yaw, pitch);
            }).collect(Collectors.toList());
        }
    }

    // 优先从地图配置获取，如果没有则从默认配置获取
    @SuppressWarnings("unchecked")
    public <T> T getSetting(String path, T def) {
        return (T) mapConfig.get(path, defaultConfig.get(path, def));
    }

    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getSelectionWeight() { return selectionWeight; }
    public WorldSourceType getWorldSourceType() { return worldSourceType; }
    public String getSourceFolderName() { return sourceFolderName; }
    public List<Location> getSpawnPoints() { return spawnPoints; }
}