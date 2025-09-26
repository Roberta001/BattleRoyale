package xyz.leafing.battleRoyale.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.leafing.battleRoyale.PlayerDataManager;

public class DataRestoreListener implements Listener {

    private final PlayerDataManager playerDataManager;

    public DataRestoreListener(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 检查玩家是否有未恢复的备份数据
        if (playerDataManager.hasPendingData(player)) {
            playerDataManager.restorePlayerData(player);
            player.sendMessage(Component.text("[大逃杀] 检测到您上次游戏数据未正常恢复，现已为您恢复。", NamedTextColor.GREEN));
        }
    }
}