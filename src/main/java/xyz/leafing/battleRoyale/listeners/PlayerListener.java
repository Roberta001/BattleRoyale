package xyz.leafing.battleRoyale.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import xyz.leafing.battleRoyale.*;
import xyz.leafing.battleRoyale.ui.MenuManager;

import java.util.Arrays;
import java.util.List;

public class PlayerListener implements Listener {

    private final GameManager gameManager;
    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            "spawn", "home", "tpa", "tpaccept", "warp", "back", "tpaall", "call", "tphere"
    );

    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerInGame(player)) {
            gameManager.handleLeave(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (gameManager.getGameState() == GameState.INGAME && gameManager.isPlayerInGame(victim)) {
            event.deathMessage(null);
            // keepInventory is true, so we don't need to worry about drops
            gameManager.handleElimination(victim, victim.getKiller());
        }
    }

    /**
     * [新增 & Bug修复] 监听玩家重生事件，确保旁观者在游戏世界内重生。
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // 检查这个玩家是否在我们的游戏中，并且状态应该是旁观者
        if (gameManager.getPlayerState(player) == PlayerState.SPECTATOR) {
            World gameWorld = Bukkit.getWorld(gameManager.getGameWorldName());
            if (gameWorld != null) {
                // 获取玩家死亡地点作为重生点。如果获取失败，则使用世界出生点作为备用。
                Location respawnLocation = player.getLastDeathLocation();
                if (respawnLocation == null || !respawnLocation.getWorld().equals(gameWorld)) {
                    respawnLocation = gameWorld.getSpawnLocation();
                }

                // 强制设置重生地点在游戏世界内
                event.setRespawnLocation(respawnLocation);

                // 在玩家重生后的下一个tick再将他们设置为观察者模式，确保他们已经到达了正确的位置
                Bukkit.getScheduler().runTask(BattleRoyale.getInstance(), () -> {
                    gameManager.applySpectatorMode(player);
                });
            }
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getPlayerState(player) == PlayerState.SPECTATOR) {
            ItemStack item = event.getItem();
            if (item != null && item.isSimilar(MenuManager.getLeaveItem())) {
                gameManager.handleLeave(player);
            }
            event.setCancelled(true); // 旁观者不能进行任何交互
        }
        if (gameManager.isPlayerFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameManager.isPlayerFrozen(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        PlayerState state = gameManager.getPlayerState(event.getPlayer());
        if (gameManager.isPlayerFrozen(event.getPlayer()) || state == PlayerState.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        PlayerState state = gameManager.getPlayerState(event.getPlayer());
        if (gameManager.isPlayerFrozen(event.getPlayer()) || state == PlayerState.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerAlive(player)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            if (BLOCKED_COMMANDS.contains(command)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot use this command during the game.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerAlive(player)) {
            if (event.getTo().getWorld() != null && !event.getTo().getWorld().getName().equals(gameManager.getGameWorldName())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot teleport out of the game world.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        // 旁观者不能受伤
        if (gameManager.getPlayerState(victim) == PlayerState.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

        // 旁观者不能攻击
        if (gameManager.getPlayerState(attacker) == PlayerState.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        if (gameManager.getGameState() == GameState.INGAME &&
                gameManager.isPlayerAlive(attacker) && gameManager.isPlayerAlive(victim)) {
            if (!gameManager.isPvpEnabled()) {
                event.setCancelled(true);
            }
        }
    }
}