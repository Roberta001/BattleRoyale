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
import java.util.Objects;

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
            gameManager.handleElimination(victim, victim.getKiller());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getPlayerState(player) == PlayerState.SPECTATOR) {
            World gameWorld = Bukkit.getWorld(gameManager.getGameWorldName());
            if (gameWorld != null) {
                Location respawnLocation = player.getLastDeathLocation();
                if (respawnLocation == null || !respawnLocation.getWorld().equals(gameWorld)) {
                    respawnLocation = gameWorld.getSpawnLocation();
                }
                event.setRespawnLocation(respawnLocation);

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
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();
        String gameWorldName = gameManager.getGameWorldName();

        if (toWorld == null || fromWorld == null || gameWorldName == null || fromWorld.equals(toWorld)) {
            return; // 忽略同世界传送或无效世界
        }

        boolean isEnteringGameWorld = toWorld.getName().equals(gameWorldName);
        boolean isLeavingGameWorld = fromWorld.getName().equals(gameWorldName);

        // 处理存活玩家离开游戏世界
        if (isLeavingGameWorld && gameManager.isPlayerAlive(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot teleport out of the game world.", NamedTextColor.RED));
        }
        // [修改] 处理非游戏玩家进入游戏世界
        else if (isEnteringGameWorld && !gameManager.isPlayerInGame(player)) {
            // 取消原传送事件，交由 GameManager 处理，这样可以正确保存玩家传送前的位置
            event.setCancelled(true);
            gameManager.handleJoinAsSpectator(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        if (gameManager.getPlayerState(victim) == PlayerState.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

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