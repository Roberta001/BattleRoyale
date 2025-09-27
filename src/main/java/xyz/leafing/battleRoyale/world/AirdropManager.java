package xyz.leafing.battleRoyale.world;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.loot.LootTable;
import org.bukkit.scheduler.BukkitTask;
import xyz.leafing.battleRoyale.BattleRoyale;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理游戏中的空投生成、特效和清理。
 */
public class AirdropManager {

    private final BattleRoyale plugin;
    private final Set<BukkitTask> particleTasks = new HashSet<>();

    public AirdropManager(BattleRoyale plugin) {
        this.plugin = plugin;
    }

    /**
     * 在指定位置生成一个空投箱，并附带粒子特效。
     * @param location 目标位置
     * @param lootTable 箱子使用的战利品表
     */
    public void spawnAirdrop(Location location, LootTable lootTable) {
        World world = location.getWorld();
        if (world == null) return;

        // 确保位置安全，将箱子放在最高固体方块之上
        Location chestLocation = world.getHighestBlockAt(location).getLocation().add(0, 1, 0);
        chestLocation.getBlock().setType(Material.CHEST);

        // 设置战利品表
        if (chestLocation.getBlock().getState() instanceof Chest) {
            Chest chest = (Chest) chestLocation.getBlock().getState();
            chest.setLootTable(lootTable);
            chest.update();
        }

        // 启动粒子效果
        startParticleEffect(chestLocation);
    }

    /**
     * 启动一个持续3分钟的粒子效果任务。
     * @param chestLocation 箱子的确切位置
     */
    private void startParticleEffect(Location chestLocation) {
        long durationTicks = 3 * 60 * 20; // 3分钟

        // 粒子效果将在箱子中心点生成
        Location particleOrigin = chestLocation.clone().add(0.5, 0.5, 0.5);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World world = particleOrigin.getWorld();
            if (world != null) {
                // 生成向上飘的火焰粒子作为信标
                world.spawnParticle(Particle.FLAME, particleOrigin, 30, 0.3, 2.0, 0.3, 0.05);
            }
        }, 0L, 10L); // 每10 tick (0.5秒) 生成一次粒子

        particleTasks.add(task);

        // 3分钟后自动停止并移除此任务
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
            particleTasks.remove(task);
        }, durationTicks);
    }

    /**
     * 游戏结束时，清理所有正在运行的粒子效果。
     */
    public void cleanup() {
        for (BukkitTask task : new HashSet<>(particleTasks)) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        particleTasks.clear();
    }
}