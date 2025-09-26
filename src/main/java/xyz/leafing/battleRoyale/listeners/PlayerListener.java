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
import org.bukkit.potion.PotionEffectType;
import xyz.leafing.battleRoyale.GameManager;
import xyz.leafing.battleRoyale.GameState;

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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isPlayerInGame(player)) {
            return;
        }

        if (gameManager.getGameState() == GameState.INGAME && gameManager.isPlayerFrozen(player)) {
            gameManager.unfreezePlayer(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.sendMessage(Component.text("你已重新连接到战场！", NamedTextColor.GREEN));
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // [修改] 这里也应该使用 isPlayerAlive
        if (gameManager.isPlayerAlive(player) && gameManager.getGameState() == GameState.INGAME) {
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
        // [修改] 将 isPlayerInGame 更改为 isPlayerAlive
        // 这样，只有还存活的玩家才会被阻止传送，而被淘汰的玩家可以被系统正常传送出去
        if (gameManager.isPlayerAlive(player) && gameManager.getGameState() == GameState.INGAME) {
            if (event.getTo().getWorld() != null && !event.getTo().getWorld().getName().equals(gameManager.getGameWorldName())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot teleport out of the game world.", NamedTextColor.RED));
            }
        }
    }

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