package xyz.leafing.battleRoyale;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

public class PlayerDataManager {

    private final BattleRoyale plugin;
    private final File dataFolder;

    public PlayerDataManager(BattleRoyale plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 保存玩家的完整数据到文件。如果文件已存在，则会强制覆写。
     * @param player 要保存数据的玩家
     */
    public void savePlayerData(Player player) {
        File playerFile = new File(dataFolder, player.getUniqueId().toString() + ".yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(playerFile);

        data.set("inventory.main", player.getInventory().getContents());
        data.set("inventory.armor", player.getInventory().getArmorContents());
        data.set("enderchest", player.getEnderChest().getContents());
        data.set("stats.health", player.getHealth());
        data.set("stats.food", player.getFoodLevel());
        data.set("stats.saturation", player.getSaturation());
        data.set("exp.level", player.getLevel());
        data.set("exp.progress", player.getExp());
        data.set("gamemode", player.getGameMode().name());
        data.set("potioneffects", player.getActivePotionEffects());

        try {
            data.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存玩家 " + player.getName() + " 的数据！", e);
        }
    }

    /**
     * 从文件恢复玩家数据，并在成功后删除该文件。
     * @param player 要恢复数据的玩家
     */
    public void restorePlayerData(Player player) {
        File playerFile = new File(dataFolder, player.getUniqueId().toString() + ".yml");
        if (!playerFile.exists()) {
            plugin.getLogger().warning("找不到玩家 " + player.getName() + " 的数据文件，无法恢复。");
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(playerFile);

        try {
            ItemStack[] main = ((List<ItemStack>) data.get("inventory.main")).toArray(new ItemStack[0]);
            ItemStack[] armor = ((List<ItemStack>) data.get("inventory.armor")).toArray(new ItemStack[0]);
            ItemStack[] enderchest = ((List<ItemStack>) data.get("enderchest")).toArray(new ItemStack[0]);
            player.getInventory().setContents(main);
            player.getInventory().setArmorContents(armor);
            player.getEnderChest().setContents(enderchest);

            player.setHealth(data.getDouble("stats.health"));
            player.setFoodLevel(data.getInt("stats.food"));
            player.setSaturation((float) data.getDouble("stats.saturation"));
            player.setLevel(data.getInt("exp.level"));
            player.setExp((float) data.getDouble("exp.progress"));
            player.setGameMode(GameMode.valueOf(data.getString("gamemode")));

            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            Collection<PotionEffect> effects = (Collection<PotionEffect>) data.get("potioneffects");
            if (effects != null) {
                player.addPotionEffects(effects);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "恢复玩家 " + player.getName() + " 的数据时发生严重错误！数据文件将暂时保留以供排查。", e);
            // MODIFIED: If an error occurs during restoration, we explicitly DO NOT delete the file.
            return;
        }

        // MODIFIED: 在成功恢复数据后，删除该持久化文件。
        try {
            if (!playerFile.delete()) {
                // 如果删除失败，记录一个警告。这通常是权限问题。
                plugin.getLogger().warning("无法删除玩家 " + player.getName() + " 的数据文件: " + playerFile.getPath());
            }
        } catch (SecurityException e) {
            plugin.getLogger().log(Level.SEVERE, "删除玩家数据文件时发生安全错误！", e);
        }
    }

    /**
     * 检查玩家是否有待恢复的数据
     * @param player 玩家
     * @return 如果存在备份文件则返回 true
     */
    public boolean hasPendingData(Player player) {
        return new File(dataFolder, player.getUniqueId().toString() + ".yml").exists();
    }

    /**
     * 清空玩家的数据和状态，为进入游戏做准备
     * @param player 要被清空的玩家
     */
    public void clearPlayerData(Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.getInventory().setArmorContents(null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setGameMode(GameMode.SURVIVAL);
    }
}