package xyz.leafing.battleRoyale.world;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTFile;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import xyz.leafing.battleRoyale.BattleRoyale;
import xyz.leafing.battleRoyale.map.GameMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Stream;

public class WorldManager {

    private final BattleRoyale plugin;
    private final Random random = new Random();

    public WorldManager(BattleRoyale plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<World> setupGameWorld(GameMap map) {
        String worldName = "br_" + map.getId() + "_" + System.currentTimeMillis();

        if (map.getWorldSourceType() == GameMap.WorldSourceType.COPY) {
            boolean importSettings = map.getSetting("world-source.import-level-dat-settings", false);
            if (importSettings) {
                // 使用 level.dat 导入模式，支持复制额外文件夹（如 datapacks）
                return createWorldFromLevelDat(map.getSourceFolderName(), worldName, map);
            } else {
                // 完整复制世界文件夹
                return copyWorldFolder(map.getSourceFolderName(), worldName).thenApplyAsync(success -> {
                    if (success) {
                        plugin.getLogger().info("成功完整复制世界 '" + map.getSourceFolderName() + "' 到 '" + worldName + "'。正在加载...");
                        return loadWorld(worldName, map);
                    } else {
                        plugin.getLogger().severe("完整复制世界 '" + map.getSourceFolderName() + "' 失败！");
                        return null;
                    }
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
            }
        } else { // GENERATE
            return CompletableFuture.completedFuture(createProceduralWorld(worldName, map));
        }
    }

    /**
     * 根据源世界的 level.dat 创建一个新世界，并可以复制指定的额外文件夹（如 datapacks）。
     * 这种方式允许使用自定义世界生成器，同时保持每次游戏地图的地形都是全新的。
     * @param sourceFolderName 源世界模板文件夹名
     * @param newWorldName 新游戏世界的名称
     * @param map 游戏地图配置对象
     * @return 一个包含已加载世界的 CompletableFuture
     */
    private CompletableFuture<World> createWorldFromLevelDat(String sourceFolderName, String newWorldName, GameMap map) {
        return CompletableFuture.supplyAsync(() -> {
            File sourceWorldFolder = new File(plugin.getDataFolder(), "maps/worlds/" + sourceFolderName);
            File sourceLevelDat = new File(sourceWorldFolder, "level.dat");

            if (!sourceLevelDat.exists()) {
                plugin.getLogger().severe("源世界的 level.dat 文件不存在: " + sourceLevelDat.getPath());
                return null;
            }

            File newWorldFolder = new File(Bukkit.getWorldContainer(), newWorldName);
            if (!newWorldFolder.mkdirs()) {
                plugin.getLogger().severe("无法创建新的世界文件夹: " + newWorldFolder.getPath());
                return null;
            }
            File newLevelDat = new File(newWorldFolder, "level.dat");

            try {
                // 步骤 1: 复制并修改 level.dat，设置新的随机种子
                NBTFile sourceNbt = new NBTFile(sourceLevelDat);
                NBTCompound data = sourceNbt.getCompound("Data");
                long newSeed = random.nextLong();
                data.setLong("RandomSeed", newSeed);

                NBTFile destNbt = new NBTFile(newLevelDat);
                destNbt.addCompound("Data").mergeCompound(data);
                destNbt.save();
                plugin.getLogger().info("从 '" + sourceFolderName + "' 导入生成器设置，使用新种子: " + newSeed);

                // 步骤 2: 从地图配置中读取并复制指定的额外文件夹
                List<String> foldersToCopy = map.getSetting("world-source.folders-to-copy-on-import", new ArrayList<>());
                if (!foldersToCopy.isEmpty()) {
                    plugin.getLogger().info("正在从 '" + sourceFolderName + "' 复制额外文件夹: " + String.join(", ", foldersToCopy));
                    for (String folderName : foldersToCopy) {
                        File sourceSubFolder = new File(sourceWorldFolder, folderName);
                        File destSubFolder = new File(newWorldFolder, folderName);
                        if (sourceSubFolder.exists() && sourceSubFolder.isDirectory()) {
                            try {
                                copyDirectory(sourceSubFolder, destSubFolder);
                                plugin.getLogger().info("  - 成功复制文件夹: " + folderName);
                            } catch (IOException e) {
                                plugin.getLogger().log(Level.SEVERE, "复制文件夹 '" + folderName + "' 时出错", e);
                            }
                        } else {
                            plugin.getLogger().warning("  - 警告: 在源世界中找不到要复制的文件夹: " + folderName);
                        }
                    }
                }

                // 步骤 3: 切换回主线程加载世界
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> loadWorld(newWorldName, map)).get();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "从 level.dat 创建世界时出错", e);
                return null;
            }
        });
    }

    private World loadWorld(String worldName, GameMap map) {
        World world = new WorldCreator(worldName).createWorld();
        if (world != null) {
            configureWorld(world, map);
        }
        return world;
    }

    private World createProceduralWorld(String worldName, GameMap map) {
        WorldCreator wc = new WorldCreator(worldName);
        World world = wc.createWorld();
        if (world != null) {
            configureWorld(world, map);
        }
        return world;
    }

    private void configureWorld(World world, GameMap map) {
        double borderSize = ((Number) map.getSetting("world.border-initial-size", 1000.0)).doubleValue();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);
        border.setDamageAmount(0.5);
        border.setWarningDistance(10);

        try {
            String difficultyStr = map.getSetting("world.difficulty", "NORMAL");
            Difficulty difficulty = Difficulty.valueOf(difficultyStr.toUpperCase());
            world.setDifficulty(difficulty);
            plugin.getLogger().info("世界 '" + world.getName() + "' 难度已设置为: " + difficulty);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的难度设置 '" + map.getSetting("world.difficulty", "NORMAL") + "'，将使用服务器默认值。");
        }

        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000);
    }

    public void teleportPlayer(Player player, World world, GameMap map) {
        Location safeLocation = null;
        Location configuredSpawn = null;

        if (map.getSpawnPoints() != null && !map.getSpawnPoints().isEmpty()) {
            configuredSpawn = map.getSpawnPoints().get(random.nextInt(map.getSpawnPoints().size())).clone();
            configuredSpawn.setWorld(world);
            safeLocation = findSafeHighestLocation(world, configuredSpawn.getBlockX(), configuredSpawn.getBlockZ());
        } else {
            WorldBorder border = world.getWorldBorder();
            double size = border.getSize();
            int safeSize = (int) (size * 0.9);
            int x = random.nextInt(safeSize) - (safeSize / 2);
            int z = random.nextInt(safeSize) - (safeSize / 2);
            safeLocation = findSafeHighestLocation(world, x, z);
        }

        if (safeLocation != null) {
            if (configuredSpawn != null) {
                safeLocation.setYaw(configuredSpawn.getYaw());
                safeLocation.setPitch(configuredSpawn.getPitch());
            }
            player.teleport(safeLocation);
        } else {
            plugin.getLogger().warning("为玩家 " + player.getName() + " 寻找安全出生点失败！将传送至世界默认出生点。");
            player.teleport(world.getSpawnLocation());
        }
    }

    private CompletableFuture<Boolean> copyWorldFolder(String sourceName, String destName) {
        return CompletableFuture.supplyAsync(() -> {
            File sourceDir = new File(plugin.getDataFolder(), "maps/worlds/" + sourceName);
            File destDir = new File(Bukkit.getWorldContainer(), destName);
            if (!sourceDir.exists()) {
                plugin.getLogger().severe("源世界文件夹不存在: " + sourceDir.getPath());
                return false;
            }
            try {
                copyDirectory(sourceDir, destDir);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "复制世界时出错", e);
                return false;
            }
        });
    }

    /**
     * 递归地将一个目录及其所有内容复制到目标位置。
     * @param source 源文件夹
     * @param destination 目标文件夹
     * @throws IOException 如果发生 I/O 错误
     */
    private void copyDirectory(File source, File destination) throws IOException {
        try (Stream<Path> stream = Files.walk(source.toPath())) {
            stream.forEach(sourcePath -> {
                try {
                    // 排除 session.lock 文件，避免复制时出错
                    if (sourcePath.getFileName().toString().equals("session.lock")) {
                        return;
                    }
                    Path destPath = destination.toPath().resolve(source.toPath().relativize(sourcePath));
                    // 使用 REPLACE_EXISTING 选项确保文件被覆盖，COPY_ATTRIBUTES 保持文件属性
                    Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    // 重新抛出为运行时异常，以便上层 CompletableFuture 的 supplyAsync 捕获
                    throw new RuntimeException("无法复制文件: " + sourcePath, e);
                }
            });
        }
    }

    public void deleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                deleteWorldFolder(worldFolder);
            }
            return;
        }

        for (Player player : world.getPlayers()) {
            player.teleport(Bukkit.getServer().getWorlds().get(0).getSpawnLocation());
        }

        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().severe("严重错误: 无法卸载世界 " + worldName + "!");
            return;
        }

        deleteWorldFolder(worldFolder);
    }

    private void deleteWorldFolder(File worldFolder) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("正在尝试异步删除世界文件夹: " + worldFolder.getPath());
                try (Stream<Path> walk = Files.walk(worldFolder.toPath())) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
                plugin.getLogger().info("成功删除世界文件夹: " + worldFolder.getName());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "删除世界文件夹 " + worldFolder.getName() + " 失败!", e);
            }
        });
    }

    /**
     * 使用广度优先搜索 (BFS) 从指定坐标开始，寻找一个安全的、非液体方块的最高点。
     * @param world 搜索的世界
     * @param startX 起始 X 坐标
     * @param startZ 起始 Z 坐标
     * @return 一个安全的 Location 对象，如果找不到则返回 null
     */
    public static Location findSafeHighestLocation(World world, int startX, int startZ) {
        record Coord(int x, int z) {}

        Queue<Coord> queue = new LinkedList<>();
        Set<Coord> visited = new HashSet<>();
        final int MAX_CHECKS = 10000; // 搜索上限，防止无限循环
        int checks = 0;

        Coord startCoord = new Coord(startX, startZ);
        queue.add(startCoord);
        visited.add(startCoord);

        while (!queue.isEmpty() && checks < MAX_CHECKS) {
            Coord current = queue.poll();
            checks++;

            int y = world.getHighestBlockYAt(current.x(), current.z());

            if (y >= world.getMinHeight()) {
                Block highestBlock = world.getBlockAt(current.x(), y, current.z());
                if (highestBlock.getType().isSolid() && !highestBlock.isLiquid()) {
                    Location safeLoc = highestBlock.getLocation();
                    safeLoc.setX(safeLoc.getBlockX() + 0.5);
                    safeLoc.setY(safeLoc.getBlockY() + 1.0);
                    safeLoc.setZ(safeLoc.getBlockZ() + 0.5);
                    return safeLoc;
                }
            }

            // 将邻近的坐标加入队列
            int[] dx = {0, 0, 1, -1};
            int[] dz = {1, -1, 0, 0};

            for (int i = 0; i < 4; i++) {
                Coord neighbor = new Coord(current.x() + dx[i], current.z() + dz[i]);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        BattleRoyale.getInstance().getLogger().warning("BFS 搜索在 (" + startX + ", " + startZ + ") 附近检查了 " + checks + " 次后，未能找到安全地点。");
        return null;
    }
}