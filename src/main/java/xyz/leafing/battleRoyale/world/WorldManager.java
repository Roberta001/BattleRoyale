package xyz.leafing.battleRoyale.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import xyz.leafing.battleRoyale.BattleRoyale;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;

public class WorldManager {

    private final BattleRoyale plugin;
    private final Random random = new Random();

    public WorldManager(BattleRoyale plugin) {
        this.plugin = plugin;
    }

    public World createWorld(String worldName) {
        plugin.getLogger().info("Creating world: " + worldName);
        WorldCreator wc = new WorldCreator(worldName);
        World world = wc.createWorld();
        if (world != null) {
            // 设置世界边界
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(1000); // 初始1000x1000
            border.setDamageAmount(0.5);
            border.setWarningDistance(10);

            // 设置游戏规则
            world.setGameRuleValue("keepInventory", "true"); // [修改] 启用保留物品栏
            world.setGameRuleValue("doDaylightCycle", "false"); // 可选
            world.setTime(6000); // 正午
        }
        return world;
    }

    public void teleportPlayerToRandomLocation(Player player, World world) {
        WorldBorder border = world.getWorldBorder();
        double size = border.getSize();
        // 稍微缩小范围，防止玩家生成在边界上
        int safeSize = (int) (size * 0.9);

        int x = random.nextInt(safeSize) - (safeSize / 2);
        int z = random.nextInt(safeSize) - (safeSize / 2);
        int y = world.getHighestBlockYAt(x, z) + 1;

        player.teleport(new Location(world, x + 0.5, y, z + 0.5));
    }

    public void deleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Tried to delete a world that doesn't exist: " + worldName);
            return;
        }

        // 确保没有玩家在里面
        for (Player player : world.getPlayers()) {
            // 把玩家传送到主世界
            player.teleport(Bukkit.getServer().getWorlds().get(0).getSpawnLocation());
        }

        File worldFolder = world.getWorldFolder();
        Bukkit.unloadWorld(world, false);

        // 异步删除文件，防止卡服
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("Attempting to delete world folder: " + worldFolder.getPath());
                Files.walk(worldFolder.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                plugin.getLogger().info("Successfully deleted world: " + worldName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}