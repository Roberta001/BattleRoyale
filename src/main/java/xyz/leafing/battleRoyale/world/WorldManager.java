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
import java.util.logging.Level;

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
            plugin.getLogger().warning("尝试删除一个不存在或已卸载的世界: " + worldName);
            // 即使世界未加载，也尝试删除文件夹，以防上次失败残留
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                deleteWorldFolder(worldFolder);
            }
            return;
        }

        // 确保没有玩家在里面
        for (Player player : world.getPlayers()) {
            player.teleport(Bukkit.getServer().getWorlds().get(0).getSpawnLocation());
        }

        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().severe("严重错误: 无法卸载世界 " + worldName + "! 文件可能不会被删除。");
            return;
        }

        deleteWorldFolder(worldFolder);
    }

    /**
     * [新增] 将文件删除逻辑提取到一个单独的方法中，并增强错误处理
     * @param worldFolder 要删除的世界文件夹
     */
    private void deleteWorldFolder(File worldFolder) {
        // 异步删除文件，防止卡服
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("正在尝试异步删除世界文件夹: " + worldFolder.getPath());
                Files.walk(worldFolder.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                plugin.getLogger().info("成功删除世界文件夹: " + worldFolder.getName());
            } catch (IOException e) {
                // [修复] 增加详细的错误日志
                plugin.getLogger().log(Level.SEVERE, "删除世界文件夹 " + worldFolder.getName() + " 失败! 请手动删除此文件夹以释放磁盘空间。", e);
            }
        });
    }
}