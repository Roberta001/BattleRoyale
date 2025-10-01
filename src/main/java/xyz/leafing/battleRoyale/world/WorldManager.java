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

    public static Location findSafeHighestLocation(World world, int startX, int startZ) {
        record Coord(int x, int z) {}

        Queue<Coord> queue = new LinkedList<>();
        Set<Coord> visited = new HashSet<>();
        final int MAX_CHECKS = 10000;
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


    public CompletableFuture<World> setupGameWorld(GameMap map) {
        String worldName = "br_" + map.getId() + "_" + System.currentTimeMillis();

        if (map.getWorldSourceType() == GameMap.WorldSourceType.COPY) {
            boolean importSettings = map.getSetting("world-source.import-level-dat-settings", false);
            if (importSettings) {
                return createWorldFromLevelDat(map.getSourceFolderName(), worldName, map);
            } else {
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

    // [BUG修复] 此方法已重写，以避免移动或修改源文件
    private CompletableFuture<World> createWorldFromLevelDat(String sourceFolderName, String newWorldName, GameMap map) {
        return CompletableFuture.supplyAsync(() -> {
            File sourceLevelDat = new File(plugin.getDataFolder(), "maps/worlds/" + sourceFolderName + "/level.dat");
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
                // 1. 从源文件加载 NBT 数据到内存
                NBTFile sourceNbt = new NBTFile(sourceLevelDat);
                NBTCompound data = sourceNbt.getCompound("Data");

                // 2. 在内存中修改数据（设置新种子）
                long newSeed = random.nextLong();
                data.setLong("RandomSeed", newSeed);
                plugin.getLogger().info("从 '" + sourceFolderName + "' 导入生成器设置，使用新种子: " + newSeed);

                // 3. 创建一个新的 NBT 文件对象指向目标路径
                NBTFile destNbt = new NBTFile(newLevelDat);

                // 4. 将修改后的数据合并到新的 NBT 对象中
                destNbt.addCompound("Data").mergeCompound(data);

                // 5. 将新的 NBT 对象保存到目标文件，这会创建一个新的 level.dat
                destNbt.save();

                // 异步任务完成后，回到主线程加载世界
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
            plugin.getLogger().warning("无效的难度设置，将使用服务器默认值。");
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
            try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
                stream.forEach(sourcePath -> {
                    try {
                        // 排除 session.lock 文件，避免复制时出错
                        if (sourcePath.getFileName().toString().equals("session.lock")) {
                            return;
                        }
                        Path destPath = destDir.toPath().resolve(sourceDir.toPath().relativize(sourcePath));
                        // 如果是目录，则创建
                        if (Files.isDirectory(sourcePath)) {
                            if (!Files.exists(destPath)) {
                                Files.createDirectories(destPath);
                            }
                        } else {
                            // 如果是文件，则复制
                            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("无法复制文件: " + sourcePath, e);
                    }
                });
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "复制世界时出错", e);
                return false;
            }
        });
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
}