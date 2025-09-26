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

        // 检查是否是我们的UI
        String title = event.getView().getTitle();
        if (!title.equals(MenuManager.MAIN_MENU_TITLE) && !title.equals(MenuManager.ADMIN_MENU_TITLE)) {
            return;
        }

        // 阻止玩家从UI中拿出物品
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 根据标题分发事件
        if (title.equals(MenuManager.MAIN_MENU_TITLE)) {
            handleMainMenuClick(player, clickedItem);
        } else if (title.equals(MenuManager.ADMIN_MENU_TITLE)) {
            handleAdminMenuClick(player, clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case EMERALD_BLOCK: // 创建游戏
                if (gameManager.getGameState() == GameState.IDLE) {
                    player.closeInventory();
                    player.sendMessage(Component.text("请在聊天框中输入报名费金额 (例如: 100.5)", NamedTextColor.GREEN));
                    // 这里需要一个机制来监听玩家的下一次聊天输入
                    // 为了简化，我们暂时只提示，让玩家手动输入 /br create <金额>
                    // 一个更高级的实现会使用 Conversation API 或一个临时的 Map 来捕获输入
                }
                break;
            case DIAMOND_BLOCK: // 加入游戏
                player.closeInventory();
                gameManager.addPlayer(player);
                break;
            case BARRIER: // 离开游戏
                player.closeInventory();
                gameManager.removePlayer(player, true);
                break;
            case COMMAND_BLOCK: // 打开管理员面板
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
            case LIME_WOOL: // 强制开始
                if (gameManager.getGameState() == GameState.LOBBY) {
                    player.closeInventory();
                    gameManager.forceStartLobby(player);
                }
                break;
            case ARROW: // 返回主菜单
                menuManager.openMainMenu(player);
                break;
            default:
                break;
        }
    }
}