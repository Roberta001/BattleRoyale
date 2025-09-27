package xyz.leafing.battleRoyale.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.GameState;

import java.util.ArrayList;
import java.util.List;

public class MenuManager {

    private final GameManager gameManager;
    public static final String MAIN_MENU_TITLE = "§6§l大逃杀菜单";
    public static final String ADMIN_MENU_TITLE = "§c§l管理员面板";

    public MenuManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void openMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, Component.text(MAIN_MENU_TITLE));

        // 填充边框
        fillBorders(menu, Material.GRAY_STAINED_GLASS_PANE);

        GameState currentState = gameManager.getGameState();

        // 1. 创建游戏按钮
        if (currentState == GameState.IDLE) {
            menu.setItem(11, createMenuItem(
                    Material.EMERALD_BLOCK,
                    "§a§l创建游戏",
                    List.of(
                            "§7点击开始一场新的大逃杀游戏。",
                            "§7你需要在聊天框中输入报名费。"
                    )
            ));
        } else {
            menu.setItem(11, createMenuItem(
                    Material.REDSTONE_BLOCK,
                    "§c§l创建游戏",
                    List.of(
                            "§c当前已有游戏正在进行中。",
                            "§c无法创建新游戏。"
                    )
            ));
        }

        // 2. 加入/离开游戏按钮
        if (currentState == GameState.LOBBY) {
            if (gameManager.isPlayerInGame(player)) {
                menu.setItem(13, createMenuItem(
                        Material.BARRIER,
                        "§e§l离开大厅",
                        List.of(
                                "§7你当前正在游戏大厅中。",
                                "§7点击离开并取回报名费。"
                        )
                ));
            } else {
                menu.setItem(13, createMenuItem(
                        Material.DIAMOND_BLOCK,
                        "§b§l加入游戏",
                        List.of(
                                "§7一场游戏正在等待玩家加入！",
                                "§7点击加入战斗！"
                        )
                ));
            }
        } else {
            menu.setItem(13, createMenuItem(
                    Material.COAL_BLOCK,
                    "§8§l加入游戏",
                    List.of(
                            "§c当前没有游戏可以加入。"
                    )
            ));
        }

        // 3. 游戏状态信息按钮
        menu.setItem(15, createGameStatusItem());

        // 4. 管理员按钮 (仅对有权限的玩家显示)
        if (player.hasPermission("br.admin")) {
            menu.setItem(26, createMenuItem(
                    Material.COMMAND_BLOCK,
                    "§c§l管理员面板",
                    List.of("§7点击进入管理员操作界面。")
            ));
        }

        player.openInventory(menu);
    }

    public void openAdminMenu(Player player) {
        Inventory adminMenu = Bukkit.createInventory(null, 27, Component.text(ADMIN_MENU_TITLE));
        fillBorders(adminMenu, Material.RED_STAINED_GLASS_PANE);

        // 强制开始游戏按钮
        if (gameManager.getGameState() == GameState.LOBBY) {
            adminMenu.setItem(13, createMenuItem(
                    Material.LIME_WOOL,
                    "§a§l强制开始游戏",
                    List.of(
                            "§7立即开始当前大厅中的游戏。",
                            "§c警告: 这会跳过倒计时！"
                    )
            ));
        } else {
            adminMenu.setItem(13, createMenuItem(
                    Material.GRAY_WOOL,
                    "§8§l强制开始游戏",
                    List.of(
                            "§c当前游戏不处于大厅状态。"
                    )
            ));
        }

        // 返回主菜单按钮
        adminMenu.setItem(18, createMenuItem(Material.ARROW, "§e§l返回主菜单", null));

        player.openInventory(adminMenu);
    }

    private ItemStack createGameStatusItem() {
        GameState state = gameManager.getGameState();
        Material material;
        String title;
        List<String> lore = new ArrayList<>();

        switch (state) {
            case IDLE:
                material = Material.CLOCK;
                title = "§f§l游戏状态: §7空闲";
                lore.add("§7当前没有正在进行的游戏。");
                lore.add("§a你可以创建一个新游戏！");
                break;
            case LOBBY:
                material = Material.BEACON;
                title = "§f§l游戏状态: §e等待中";
                lore.add("§7玩家正在大厅集结...");
                lore.add("§b当前人数: " + gameManager.getParticipantCount() + " 人");
                break;

            case PREPARING:
            case INGAME:
                material = Material.DIAMOND_SWORD;
                title = "§f§l游戏状态: §c进行中";
                lore.add("§7一场激烈的战斗正在进行！");
                lore.add("§b存活人数: " + gameManager.getAlivePlayerCount() + " / " + gameManager.getParticipantCount());
                break;
            case CLEANUP:
                material = Material.TNT;
                title = "§f§l游戏状态: §6清理中";
                lore.add("§7正在结算上一场游戏并清理场地。");
                break;
            default:
                material = Material.BARRIER;
                title = "§f§l游戏状态: §4未知";
                break;
        }

        return createMenuItem(material, title, lore);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorders(Inventory inventory, Material material) {
        ItemStack item = createMenuItem(material, " ", null);
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                if(inventory.getItem(i) == null) {
                    inventory.setItem(i, item);
                }
            }
        }
    }

    /**
     * [新增] 创建一个标准的“离开游戏”物品，用于旁观者模式。
     * @return “离开游戏”的 ItemStack
     */
    public static ItemStack getLeaveItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§c§l离开游戏", NamedTextColor.RED, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("§7点击以退出观战并返回大厅。", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}