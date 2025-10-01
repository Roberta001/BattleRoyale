package xyz.leafing.battleRoyale.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import xyz.leafing.battleRoyale.BattleRoyale;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.GameState;
import xyz.leafing.battleRoyale.GamePlayer;
import xyz.leafing.battleRoyale.ui.MenuManager;

import java.util.Arrays;
import java.util.List;

public class PlayerListener implements Listener {

    private final GameManager gameManager;
    private final BattleRoyale plugin;

    private static final List<String> ALLOWED_COMMANDS = Arrays.asList(
            "br", "battleroyale", "msg", "tell", "w", "r"
    );

    public PlayerListener(GameManager gameManager, BattleRoyale plugin) {
        this.gameManager = gameManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.needsGamemodeReset(player.getUniqueId())) {
            player.setGameMode(GameMode.SURVIVAL);
            plugin.removePlayerFromResetQueue(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerInGame(player) || gameManager.isSpectating(player)) {
            gameManager.handleLeave(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (gameManager.getGameState() == GameState.INGAME && gameManager.isPlayerAlive(victim)) {
            event.deathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            // 强制玩家立即重生，以便 onPlayerRespawn 事件可以处理他们
            Bukkit.getScheduler().runTask(plugin, () -> victim.spigot().respawn());
            // 处理淘汰逻辑（设置状态、广播消息等）
            gameManager.handleElimination(victim, victim.getKiller());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // [修改] 当一个被淘汰的玩家重生时，将他们设置为内部观察者
        if (gameManager.isPlayerInGame(player) && !gameManager.isPlayerAlive(player)) {
            // 将重生点设置在死亡位置，避免传送到世界出生点
            if (player.getLastDeathLocation() != null) {
                event.setRespawnLocation(player.getLastDeathLocation());
            }
            // 延迟一刻执行，确保重生完成
            Bukkit.getScheduler().runTask(plugin, () -> gameManager.applyInternalSpectatorMode(player));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isSpectating(player) || (gameManager.isPlayerInGame(player) && !gameManager.isPlayerAlive(player))) {
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
        if (gameManager.isPlayerFrozen(event.getPlayer()) || gameManager.isSpectating(event.getPlayer()) || (gameManager.isPlayerInGame(event.getPlayer()) && !gameManager.isPlayerAlive(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.isPlayerFrozen(event.getPlayer()) || gameManager.isSpectating(event.getPlayer()) || (gameManager.isPlayerInGame(event.getPlayer()) && !gameManager.isPlayerAlive(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerAlive(player)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            if (!ALLOWED_COMMANDS.contains(command)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("游戏期间无法使用此指令。", NamedTextColor.RED));
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
            return;
        }

        boolean isLeavingGameWorld = fromWorld.getName().equals(gameWorldName);
        boolean isEnteringGameWorld = toWorld.getName().equals(gameWorldName);

        if (isLeavingGameWorld && gameManager.isPlayerAlive(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("你无法在存活时传送离开游戏世界。", NamedTextColor.RED));
        } else if (isEnteringGameWorld && !gameManager.isPlayerInGame(player) && !gameManager.isSpectating(player)) {
            event.setCancelled(true);
            gameManager.handleSpectate(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        if (gameManager.isSpectating(victim) || (gameManager.isPlayerInGame(victim) && !gameManager.isPlayerAlive(victim))) {
            event.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        }

        if (attacker != null) {
            if (gameManager.isSpectating(attacker) || (gameManager.isPlayerInGame(attacker) && !gameManager.isPlayerAlive(attacker))) {
                event.setCancelled(true);
                return;
            }
            if (gameManager.isPlayerAlive(attacker) && gameManager.isPlayerAlive(victim)) {
                if (!gameManager.isPvpEnabled()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}