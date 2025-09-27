package xyz.leafing.battleRoyale.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.ui.MenuManager;

public class BRCommand implements CommandExecutor {

    private final GameManager gameManager;
    private final MenuManager menuManager;

    public BRCommand(GameManager gameManager, MenuManager menuManager) {
        this.gameManager = gameManager;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            menuManager.openMainMenu(player); // 默认打开菜单
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu":
                menuManager.openMainMenu(player);
                break;
            case "create":
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /br create <金额>", NamedTextColor.RED));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (amount <= 0) {
                        player.sendMessage(Component.text("金额必须为正数。", NamedTextColor.RED));
                        return true;
                    }
                    gameManager.createGame(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("无效的金额。", NamedTextColor.RED));
                }
                break;
            case "join":
                gameManager.handleJoinLobby(player);
                break;
            case "leave":
                // [重构] 统一调用 handleLeave，所有逻辑都在 GameManager 中处理
                gameManager.handleLeave(player);
                break;
            case "forcestart":
                if (!player.hasPermission("br.admin")) {
                    player.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
                    return true;
                }
                gameManager.forceStartLobby(player);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- 大逃杀 帮助 ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/br menu", NamedTextColor.AQUA).append(Component.text(" - 打开游戏菜单。", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/br create <金额>", NamedTextColor.AQUA).append(Component.text(" - 开始一场新游戏。", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/br join", NamedTextColor.AQUA).append(Component.text(" - 加入当前游戏。", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/br leave", NamedTextColor.AQUA).append(Component.text(" - 离开游戏或大厅。", NamedTextColor.GRAY)));
        if (player.hasPermission("br.admin")) {
            player.sendMessage(Component.text("/br forcestart", NamedTextColor.RED).append(Component.text(" - (管理员) 强制开始游戏。", NamedTextColor.GRAY)));
        }
    }
}