package xyz.leafing.battleRoyale.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.GameState;

import java.util.Arrays;
import java.util.List;

public class PlayerListener implements Listener {

    private final GameManager gameManager;
    // 定义需要被拦截的传送类指令
    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            "spawn", "home", "tpa", "tpaccept", "warp", "back", "tpaall", "call", "tphere"
    );

    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // --- 游戏冻结逻辑 ---

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 如果玩家处于冻结状态，并且确实移动了位置（不仅仅是转头）
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
        if (gameManager.isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // --- 防逃跑逻辑 ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isPlayerInGame(player) && gameManager.getGameState() == GameState.INGAME) {
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
        // 如果玩家在游戏中，并且传送的目标世界不是游戏世界
        if (gameManager.isPlayerInGame(player) && gameManager.getGameState() == GameState.INGAME) {
            if (!event.getTo().getWorld().getName().equals(gameManager.getGameWorldName())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot teleport out of the game world.", NamedTextColor.RED));
            }
        }
    }


    // --- 核心游戏逻辑 ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isPlayerInGame(player)) return;

        if (gameManager.getGameState() == GameState.LOBBY) {
            gameManager.removePlayer(player, true);
        } else if (gameManager.getGameState() == GameState.INGAME || gameManager.getGameState() == GameState.PREPARING) {
            gameManager.handlePlayerDeath(player, null);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (gameManager.getGameState() == GameState.INGAME && gameManager.isPlayerInGame(victim)) {
            event.deathMessage(null);
            // 确保掉落物在游戏世界
            event.setKeepInventory(false);
            gameManager.handlePlayerDeath(victim, victim.getKiller());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player && event.getEntity() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        if (gameManager.getGameState() == GameState.INGAME &&
                gameManager.isPlayerInGame(attacker) && gameManager.isPlayerInGame(victim)) {
            if (!gameManager.isPvpEnabled()) {
                event.setCancelled(true);
            }
        }
    }
}