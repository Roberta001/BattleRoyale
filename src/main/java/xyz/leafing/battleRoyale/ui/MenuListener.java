package xyz.leafing.battleRoyale.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.GameState;

public class MenuListener implements Listener {

    private final GameManager gameManager;
    private final MenuManager menuManager;

    public MenuListener(GameManager gameManager, MenuManager menuManager) {
        this.gameManager = gameManager;
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.equals(MenuManager.MAIN_MENU_TITLE) && !title.equals(MenuManager.ADMIN_MENU_TITLE)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (title.equals(MenuManager.MAIN_MENU_TITLE)) {
            handleMainMenuClick(player, clickedItem);
        } else if (title.equals(MenuManager.ADMIN_MENU_TITLE)) {
            handleAdminMenuClick(player, clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case EMERALD_BLOCK:
                if (gameManager.getGameState() == GameState.IDLE) {
                    player.closeInventory();
                    player.sendMessage(Component.text("请在聊天框中输入报名费金额, 例如: /br create 100", NamedTextColor.GREEN));
                }
                break;
            case DIAMOND_BLOCK:
                player.closeInventory();
                gameManager.handleJoinLobby(player);
                break;
            case BARRIER: // 离开游戏按钮
                player.closeInventory();
                // [重构] 统一调用 handleLeave
                gameManager.handleLeave(player);
                break;
            case COMMAND_BLOCK:
                if (player.hasPermission("br.admin")) {
                    menuManager.openAdminMenu(player);
                }
                break;
            default:
                break;
        }
    }

    private void handleAdminMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case LIME_WOOL:
                if (gameManager.getGameState() == GameState.LOBBY) {
                    player.closeInventory();
                    gameManager.forceStartLobby(player);
                }
                break;
            case ARROW:
                menuManager.openMainMenu(player);
                break;
            default:
                break;
        }
    }
}