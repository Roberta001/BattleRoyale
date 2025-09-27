package xyz.leafing.battleRoyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.loot.LootTable; // 正确的 LootTable 接口导入
import org.bukkit.loot.LootTables; // [修复] 导入包含原版战利品表的枚举
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import xyz.leafing.battleRoyale.ui.MenuManager;
import xyz.leafing.battleRoyale.world.AirdropManager;
import xyz.leafing.battleRoyale.world.WorldManager;
import xyz.leafing.miniGameManager.api.MiniGameAPI;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameManager {

    private final BattleRoyale plugin;
    private final WorldManager worldManager;
    private final MiniGameAPI miniGameAPI;
    private final AirdropManager airdropManager;
    private final Random random = new Random();

    private final Map<UUID, GamePlayer> gamePlayers = new ConcurrentHashMap<>();

    private GameState gameState = GameState.IDLE;
    private double entryFee = 0;
    private String gameWorldName = null;

    private static final int KILL_POINTS = 200;
    private static final int SURVIVAL_POINT_INTERVAL_SECONDS = 3;
    private static final int SURVIVAL_POINTS = 1;

    private BukkitTask lobbyUpdateTask;
    private BukkitTask gameUpdateTask;
    private BukkitTask preparationTask;
    private boolean isPvpEnabled = false;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private BossBar bossBar;
    private long gameStartTime;
    private int lobbyCountdown = 120;

    private boolean phase1AirdropSpawned, phase2AirdropSpawned, phase3AirdropSpawned, phase4AirdropSpawned;

    public GameManager(BattleRoyale plugin, MiniGameAPI miniGameAPI) {
        this.plugin = plugin;
        this.worldManager = new WorldManager(plugin);
        this.miniGameAPI = miniGameAPI;
        this.airdropManager = new AirdropManager(plugin);
    }

    private void resetGameFlags() {
        phase1AirdropSpawned = false;
        phase2AirdropSpawned = false;
        phase3AirdropSpawned = false;
        phase4AirdropSpawned = false;
    }

    private void startGame() {
        setGameState(GameState.PREPARING);
        resetGameFlags();
        broadcastMessage(NamedTextColor.BLUE, "游戏开始！正在准备世界...");

        getOnlineParticipants().forEach(p -> {
            if (!miniGameAPI.savePlayerData(p)) {
                p.sendMessage(Component.text("错误：无法备份你的数据，已将你移出游戏。", NamedTextColor.RED));
                handleLeave(p);
            } else {
                GamePlayer gp = gamePlayers.get(p.getUniqueId());
                if(gp != null) gp.setState(PlayerState.ALIVE);
            }
        });

        gameWorldName = "br_" + System.currentTimeMillis();
        World gameWorld = worldManager.createWorld(gameWorldName);
        if (gameWorld == null) {
            broadcastMessage(NamedTextColor.RED, "严重错误：无法创建游戏世界，游戏取消。");
            forceEndGame();
            return;
        }

        for (Player p : getOnlineAlivePlayers()) {
            worldManager.teleportPlayerToRandomLocation(p, gameWorld);
            miniGameAPI.clearPlayerData(p);
            frozenPlayers.add(p.getUniqueId());
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 20, 255, false, false));
            p.showTitle(Title.title(
                    Component.text("准备战斗", NamedTextColor.RED),
                    Component.text("寻找物资，准备厮杀！", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1))
            ));
        }
        startPreparationCountdown();
    }

    private void updateGame() {
        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        World gameWorld = Bukkit.getWorld(gameWorldName);
        if (gameWorld == null) {
            forceEndGame();
            return;
        }

        // 空投触发逻辑
        if (!phase1AirdropSpawned && elapsedSeconds >= 60) {
            phase1AirdropSpawned = true;
            spawnPhase1Airdrop();
        }
        if (!phase2AirdropSpawned && elapsedSeconds >= 300) {
            phase2AirdropSpawned = true;
            spawnPhase2Airdrop();
        }
        if (!phase3AirdropSpawned && elapsedSeconds >= 600) {
            phase3AirdropSpawned = true;
            spawnPhase3Airdrops();
        }
        if (!phase4AirdropSpawned && elapsedSeconds >= 1020) {
            phase4AirdropSpawned = true;
            spawnPhase4Airdrops();
        }


        if (elapsedSeconds > 0 && elapsedSeconds % SURVIVAL_POINT_INTERVAL_SECONDS == 0) {
            getAliveGamePlayers().forEach(gp -> gp.addScore(SURVIVAL_POINTS));
        }

        if (!isPvpEnabled && elapsedSeconds >= 60) {
            isPvpEnabled = true;
            broadcastMessage(NamedTextColor.RED, "PVP已开启！");
            SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.PVP_ENABLE);
        }

        if (bossBar != null && !bossBar.getPlayers().isEmpty()) {
            bossBar.getPlayers().forEach(p -> {
                String scoreSuffix = " | 积分: " + getPlayerScore(p);
                int aliveCount = getAlivePlayerCount();
                if (elapsedSeconds < 60) {
                    long pvpTime = 60 - elapsedSeconds;
                    bossBar.setTitle("PVP 将在 " + pvpTime + " 秒后开启" + scoreSuffix);
                } else if (elapsedSeconds < 600) {
                    long borderTime = 600 - elapsedSeconds;
                    bossBar.setTitle("边界将在 " + formatTime(borderTime) + " 后缩小 | " + aliveCount + " 人存活" + scoreSuffix);
                } else if (elapsedSeconds < 1200) {
                    bossBar.setTitle("边界缩小中！ | " + aliveCount + " 人存活" + scoreSuffix);
                } else {
                    bossBar.setTitle("最终决战！ | " + aliveCount + " 人存活" + scoreSuffix);
                }
            });

            if (elapsedSeconds < 60) {
                bossBar.setProgress((60.0 - elapsedSeconds) / 60.0);
                bossBar.setColor(BarColor.GREEN);
            } else if (elapsedSeconds < 600) {
                bossBar.setProgress((600.0 - elapsedSeconds) / 540.0);
                bossBar.setColor(BarColor.YELLOW);
            } else if (elapsedSeconds == 600) {
                broadcastMessage(NamedTextColor.DARK_RED, "警告！边界将在10分钟内缩小至 100x100！");
                gameWorld.getWorldBorder().setSize(100, 600);
                SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.BORDER_SHRINK_WARN);
            } else if (elapsedSeconds < 1200) {
                bossBar.setProgress(gameWorld.getWorldBorder().getSize() / 1000.0);
                bossBar.setColor(BarColor.RED);
            } else if (elapsedSeconds == 1200) {
                broadcastMessage(NamedTextColor.DARK_RED, "决赛圈！边界将在5分钟内完全闭合！");
                gameWorld.getWorldBorder().setSize(1, 300);
            } else {
                bossBar.setProgress(gameWorld.getWorldBorder().getSize() / 100.0);
                bossBar.setColor(BarColor.PURPLE);
            }
        }

        List<Player> toDisqualify = new ArrayList<>();
        for (Player p : getOnlineAlivePlayers()) {
            if (!p.getWorld().getName().equals(gameWorldName)) {
                toDisqualify.add(p);
                p.sendMessage(Component.text("你因离开游戏区域而被淘汰。", NamedTextColor.RED));
            }
        }
        toDisqualify.forEach(p -> handleElimination(p, null));
    }

    private void spawnPhase1Airdrop() {
        List<Player> alive = getOnlineAlivePlayers();
        if (alive.isEmpty()) return;

        for (Player player : alive) {
            if (player.getName().equals("Herobrine")) {
                broadcastMessage(NamedTextColor.DARK_PURPLE, "§k!!! §rGoodLuck... §k!!!");
                for (int i = 0; i < 15; i++) {
                    Location bonusLoc = findRandomLocationNear(player.getLocation(), 15);
                    if (bonusLoc != null) {
                        airdropManager.spawnAirdrop(bonusLoc, LootTables.VILLAGE_WEAPONSMITH.getLootTable());
                    }
                }
                break; // 找到并处理后就跳出循环
            }
        }
        // [新增] 后门/彩蛋逻辑结束

        // 正常的空投逻辑继续执行
        Player targetPlayer = alive.get(random.nextInt(alive.size()));
        Location loc = findRandomLocationNear(targetPlayer.getLocation(), 50);
        if (loc != null) {
            airdropManager.spawnAirdrop(loc, LootTables.SPAWN_BONUS_CHEST.getLootTable());
            broadcastMessage(NamedTextColor.YELLOW, "一个空投补给箱已在某处降落！");
        }
    }

    private void spawnPhase2Airdrop() {
        List<Player> alive = getOnlineAlivePlayers();
        if (alive.isEmpty()) return;

        Player targetPlayer = alive.get(random.nextInt(alive.size()));
        Location loc = findRandomLocationNear(targetPlayer.getLocation(), 50);
        if (loc != null) {
            // [修复] 使用 LootTables 枚举获取战利品表
            airdropManager.spawnAirdrop(loc, LootTables.VILLAGE_WEAPONSMITH.getLootTable());
            broadcastMessage(NamedTextColor.GOLD, "一个更精良的空投已降落，快去寻找！");
        }
    }

    private void spawnPhase3Airdrops() {
        int count = Math.max(1, getAlivePlayerCount() * 2);
        broadcastMessage(NamedTextColor.RED, "警告！" + count + "个高级空投正在圈内随机位置降落！");

        for (int i = 0; i < count; i++) {
            Location loc = findRandomLocationInBorder();
            if (loc == null) continue;

            // [修复] 使用 LootTables 枚举获取战利品表
            LootTable loot = (random.nextDouble() < 0.3) ? LootTables.END_CITY_TREASURE.getLootTable() : LootTables.VILLAGE_WEAPONSMITH.getLootTable();
            airdropManager.spawnAirdrop(loc, loot);
        }
    }

    private void spawnPhase4Airdrops() {
        int count = getAlivePlayerCount() * 2;
        if (count == 0) return;
        broadcastMessage(NamedTextColor.DARK_PURPLE, "最终决战！" + count + "个终极空投正在战场中心降落！");

        World world = Bukkit.getWorld(gameWorldName);
        if (world == null) return;
        Location center = world.getWorldBorder().getCenter();

        for (int i = 0; i < count; i++) {
            Location loc = findRandomLocationNear(center, 50);
            if (loc == null) continue;

            // [修复] 使用 LootTables 枚举获取战利品表
            LootTable loot = (random.nextDouble() < 0.7) ? LootTables.END_CITY_TREASURE.getLootTable() : LootTables.VILLAGE_WEAPONSMITH.getLootTable();
            airdropManager.spawnAirdrop(loc, loot);
        }
    }

    private Location findRandomLocationNear(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2.0;
        Location borderCenter = border.getCenter();

        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;

            if (Math.abs(x - borderCenter.getX()) < borderSize && Math.abs(z - borderCenter.getZ()) < borderSize) {
                return new Location(world, x, center.getY(), z);
            }
        }
        return null;
    }

    private Location findRandomLocationInBorder() {
        World world = Bukkit.getWorld(gameWorldName);
        if (world == null) return null;

        WorldBorder border = world.getWorldBorder();
        double size = border.getSize();
        Location center = border.getCenter();

        int halfSize = (int) (size / 2.0 * 0.95);

        int x = center.getBlockX() + random.nextInt(halfSize * 2) - halfSize;
        int z = center.getBlockZ() + random.nextInt(halfSize * 2) - halfSize;

        return new Location(world, x, center.getY(), z);
    }

    private void cleanupWorldAndReset() {
        if (bossBar != null) bossBar.removeAll();
        bossBar = null;

        airdropManager.cleanup();

        if (gameWorldName != null) worldManager.deleteWorld(gameWorldName);
        if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
        if (gameUpdateTask != null) gameUpdateTask.cancel();
        if (preparationTask != null) preparationTask.cancel();

        gamePlayers.clear();
        frozenPlayers.clear();
        entryFee = 0;
        isPvpEnabled = false;
        setGameState(GameState.IDLE);
    }

    // ... (其他方法保持不变)
    public void createGame(Player initiator, double fee) {
        if (gameState != GameState.IDLE) {
            initiator.sendMessage(Component.text("当前已有游戏正在进行！", NamedTextColor.RED));
            return;
        }
        if (plugin.getEconomy().getBalance(initiator) < fee) {
            initiator.sendMessage(Component.text("你的余额不足以支付报名费。", NamedTextColor.RED));
            return;
        }

        this.entryFee = fee;
        setGameState(GameState.LOBBY);
        handleJoinLobby(initiator);

        Component joinMessage = Component.text("[点击加入]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/br join"));
        Component broadcastMessage = Component.text("[大逃杀] ", NamedTextColor.GOLD)
                .append(Component.text(initiator.getName(), NamedTextColor.AQUA))
                .append(Component.text(" 发起了一场游戏！报名费: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.2f", fee), NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(joinMessage);
        Bukkit.broadcast(broadcastMessage);
    }

    public void handleJoinLobby(Player player) {
        if (gameState != GameState.LOBBY) {
            player.sendMessage(Component.text("现在无法加入游戏。", NamedTextColor.RED));
            return;
        }
        if (isPlayerInGame(player)) {
            player.sendMessage(Component.text("你已经在大厅中了。", NamedTextColor.YELLOW));
            return;
        }
        if (plugin.getEconomy().getBalance(player) < entryFee) {
            player.sendMessage(Component.text("你的余额不足以支付 " + entryFee + " 的报名费。", NamedTextColor.RED));
            return;
        }
        if (!miniGameAPI.enterGame(player, plugin)) {
            player.sendMessage(Component.text("你已在另一场游戏中，无法加入！", NamedTextColor.RED));
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, entryFee);

        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), player.getLocation());
        gamePlayers.put(player.getUniqueId(), gamePlayer);

        broadcastMessage(NamedTextColor.GREEN, player.getName() + " 已加入战斗！ (" + getParticipantCount() + " 人)");
        SoundManager.playSound(player, SoundManager.GameSound.JOIN_LOBBY);

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        if (getParticipantCount() >= 2 && lobbyUpdateTask == null) {
            startLobbyCountdown();
        }
    }

    public void handleJoinAsSpectator(Player player) {
        if (gameState != GameState.INGAME && gameState != GameState.PREPARING) {
            player.sendMessage(Component.text("当前游戏未在进行中，无法观战。", NamedTextColor.YELLOW));
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }
        if (isPlayerInGame(player)) return;

        if (!miniGameAPI.enterGame(player, plugin)) {
            player.sendMessage(Component.text("你已在另一场游戏中，无法观战！", NamedTextColor.RED));
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }
        if (!miniGameAPI.savePlayerData(player)) {
            player.sendMessage(Component.text("错误：无法备份你的数据，无法进入观战。", NamedTextColor.RED));
            miniGameAPI.leaveGame(player, plugin);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }

        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), player.getLocation());
        gamePlayer.setState(PlayerState.SPECTATOR);
        gamePlayers.put(player.getUniqueId(), gamePlayer);

        player.sendMessage(Component.text("你已进入观战模式。", NamedTextColor.GREEN));
        applySpectatorMode(player);
    }

    public void handleLeave(Player player) {
        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        if (gamePlayer == null) {
            player.sendMessage(Component.text("你没有参与当前的游戏。", NamedTextColor.RED));
            return;
        }

        switch (gamePlayer.getState()) {
            case PARTICIPANT:
                plugin.getEconomy().depositPlayer(player, entryFee);
                player.sendMessage(Component.text("你已离开大厅，报名费已退还。", NamedTextColor.YELLOW));
                cleanupPlayerData(player, gamePlayer.getOriginalLocation());
                broadcastMessage(NamedTextColor.GRAY, player.getName() + " 已离开大厅。");
                if (getParticipantCount() < 2 && lobbyUpdateTask != null) {
                    lobbyUpdateTask.cancel();
                    lobbyUpdateTask = null;
                    if (bossBar != null) bossBar.removeAll();
                    bossBar = null;
                    broadcastMessage(NamedTextColor.YELLOW, "人数不足，游戏开始倒计时已暂停。");
                }
                break;
            case ALIVE:
                player.sendMessage(Component.text("你已主动退出游戏，被视为淘汰。", NamedTextColor.YELLOW));
                cleanupPlayerData(player, gamePlayer.getOriginalLocation());
                broadcastMessage(NamedTextColor.AQUA, player.getName() + " 主动退出了游戏。 [" + (getAlivePlayerCount() - 1) + "/" + getParticipantCount() + " 剩余]");
                if (getAlivePlayerCount() <= 1) {
                    endGame();
                }
                break;
            case SPECTATOR:
                player.sendMessage(Component.text("你已离开观战。", NamedTextColor.GREEN));
                cleanupPlayerData(player, gamePlayer.getOriginalLocation());
                break;
        }
    }

    public void handleElimination(Player victim, @Nullable Player killer) {
        GamePlayer victimGP = gamePlayers.get(victim.getUniqueId());
        if (victimGP == null || victimGP.getState() != PlayerState.ALIVE) return;

        SoundManager.playSound(victim, SoundManager.GameSound.PLAYER_DEATH);

        String deathMessage;
        if (killer != null && isPlayerAlive(killer)) {
            GamePlayer killerGP = gamePlayers.get(killer.getUniqueId());
            if (killerGP != null) {
                killerGP.incrementKills();
                killerGP.addScore(KILL_POINTS);
                killer.sendMessage(Component.text("击杀成功！", NamedTextColor.GREEN).append(Component.text(" +" + KILL_POINTS + " 积分 (总积分: " + killerGP.getScore() + ")", NamedTextColor.GOLD)));
                SoundManager.playSound(killer, SoundManager.GameSound.KILL_PLAYER);
                deathMessage = victim.getName() + " 被 " + killer.getName() + " 淘汰 (" + killerGP.getKills() + " 击杀)。";
            } else {
                deathMessage = victim.getName() + " 被 " + killer.getName() + " 淘汰。";
            }
        } else {
            deathMessage = victim.getName() + " 被淘汰。";
        }
        broadcastMessage(NamedTextColor.AQUA, deathMessage + " [" + (getAlivePlayerCount() - 1) + "/" + getParticipantCount() + " 剩余]");

        victimGP.setState(PlayerState.SPECTATOR);

        if (getAlivePlayerCount() <= 1) {
            endGame();
        }
    }

    public void applySpectatorMode(Player player) {
        if (!player.isOnline()) return;
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.getInventory().setItem(8, MenuManager.getLeaveItem());
        player.sendMessage(Component.text("你已进入观战模式。点击物品栏中的屏障方块或使用 /br leave 离开。", NamedTextColor.YELLOW));
        if (bossBar != null && !bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
    }

    private void endGame() {
        if (gameState != GameState.INGAME) return;

        if (gameUpdateTask != null) gameUpdateTask.cancel();
        setGameState(GameState.CLEANUP);

        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        if (elapsedSeconds > 0 && elapsedSeconds % SURVIVAL_POINT_INTERVAL_SECONDS != 0) {
            getAliveGamePlayers().forEach(gp -> gp.addScore(SURVIVAL_POINTS));
        }

        double totalPot = entryFee * getParticipantCount();
        long totalPointsScored = gamePlayers.values().stream().mapToLong(GamePlayer::getScore).sum();
        if (totalPointsScored == 0) totalPointsScored = 1;

        List<GamePlayer> sortedScores = gamePlayers.values().stream()
                .sorted(Comparator.comparingInt(GamePlayer::getScore).reversed())
                .collect(Collectors.toList());

        Bukkit.broadcast(Component.text(""));
        Bukkit.broadcast(Component.text("================[ 游戏结束 ]================", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(" 总奖池: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", totalPot), NamedTextColor.YELLOW)));

        for (int i = 0; i < sortedScores.size(); i++) {
            GamePlayer gp = sortedScores.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(gp.getUuid());
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "未知玩家";
            double payout = (totalPointsScored > 0) ? totalPot * ((double) gp.getScore() / totalPointsScored) : 0;

            double finalPayout = Math.floor(payout * 10) / 10.0;
            plugin.getEconomy().depositPlayer(offlinePlayer, finalPayout);

            Component rank = Component.text("#" + (i + 1), NamedTextColor.AQUA);
            Component playerText = Component.text(" " + playerName, NamedTextColor.WHITE);
            Component scoreText = Component.text(" - " + gp.getScore() + " 积分", NamedTextColor.GRAY);
            Component payoutText = Component.text(" (奖金: " + String.format("%.1f", finalPayout) + ")", NamedTextColor.GREEN);
            Bukkit.broadcast(rank.append(playerText).append(scoreText).append(payoutText));

            if (offlinePlayer.isOnline()) {
                Player onlinePlayer = offlinePlayer.getPlayer();
                onlinePlayer.sendMessage(Component.text("你获得了 ", NamedTextColor.GOLD)
                        .append(Component.text(String.format("%.1f", finalPayout), NamedTextColor.YELLOW))
                        .append(Component.text(" 奖金！", NamedTextColor.GOLD)));
                if (i == 0 && gp.getState() == PlayerState.ALIVE) {
                    SoundManager.playSound(onlinePlayer, SoundManager.GameSound.GAME_WIN);
                }
            }
        }
        Bukkit.broadcast(Component.text("==========================================", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(""));

        repatriateAllPlayers();
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupWorldAndReset, 20L * 2);
    }

    private void repatriateAllPlayers() {
        new HashSet<>(gamePlayers.keySet()).forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                GamePlayer gp = gamePlayers.get(uuid);
                if (gp != null) {
                    cleanupPlayerData(p, gp.getOriginalLocation());
                }
            }
        });
    }

    public void forceEndGame() {
        if (gameState == GameState.IDLE) return;
        broadcastMessage(NamedTextColor.RED, "游戏被强制中止，将退还所有报名费并恢复数据。");
        new HashSet<>(gamePlayers.keySet()).forEach(uuid -> {
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), entryFee);
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            if (onlinePlayer != null) {
                GamePlayer gp = gamePlayers.get(uuid);
                if (gp != null) {
                    cleanupPlayerData(onlinePlayer, gp.getOriginalLocation());
                }
            }
        });
        cleanupWorldAndReset();
    }

    private void cleanupPlayerData(Player player, Location originalLocation) {
        if (player.isOnline()) {
            player.setGameMode(GameMode.SURVIVAL);
            if (originalLocation != null) player.teleport(originalLocation);
            miniGameAPI.restorePlayerData(player);
        }
        miniGameAPI.leaveGame(player, plugin);
        gamePlayers.remove(player.getUniqueId());
        if (bossBar != null) bossBar.removePlayer(player);
    }

    private void startLobbyCountdown() {
        lobbyCountdown = 120;
        bossBar = Bukkit.createBossBar("游戏即将开始...", BarColor.GREEN, BarStyle.SOLID);
        getOnlineParticipants().forEach(bossBar::addPlayer);

        lobbyUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (lobbyCountdown > 0) {
                bossBar.setTitle("游戏将在 " + lobbyCountdown + " 秒后开始");
                bossBar.setProgress((double) lobbyCountdown / 120.0);

                if (lobbyCountdown <= 4 && lobbyCountdown > 0) {
                    SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.COUNTDOWN_TICK);
                }

                lobbyCountdown--;
            } else {
                SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.GAME_START);
                bossBar.setTitle("正在初始化世界...");
                bossBar.setColor(BarColor.PURPLE);
                bossBar.setProgress(1.0);

                if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
                lobbyUpdateTask = null;
                Bukkit.getScheduler().runTask(plugin, this::startGame);
            }
        }, 0L, 20L);
    }

    public void forceStartLobby(CommandSender starter) {
        if (gameState != GameState.LOBBY) {
            starter.sendMessage(Component.text("游戏当前未处于大厅等待状态。", NamedTextColor.RED));
            return;
        }
        if (getParticipantCount() < 2) {
            starter.sendMessage(Component.text("玩家人数不足2人，无法强制开始。", NamedTextColor.RED));
            return;
        }

        broadcastMessage(NamedTextColor.YELLOW, "一名管理员强制开始了游戏！");

        if (lobbyUpdateTask != null) {
            lobbyUpdateTask.cancel();
            lobbyUpdateTask = null;
        }

        SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.GAME_START);
        if (bossBar != null) {
            bossBar.setTitle("正在初始化世界...");
            bossBar.setColor(BarColor.PURPLE);
            bossBar.setProgress(1.0);
        }

        Bukkit.getScheduler().runTask(plugin, this::startGame);
    }

    private void startPreparationCountdown() {
        bossBar.setColor(BarColor.PURPLE);
        bossBar.setStyle(BarStyle.SOLID);
        bossBar.setVisible(false);
        AtomicInteger countdown = new AtomicInteger(30);

        preparationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int currentSecond = countdown.getAndDecrement();

            if (currentSecond <= 0) {
                bossBar.setVisible(true);
                runGameLoop();
                if (preparationTask != null) {
                    preparationTask.cancel();
                }
                return;
            }

            if (currentSecond <= 20) {
                if (currentSecond == 20) {
                    bossBar.setVisible(true);
                }
                if (currentSecond % 5 == 0 && currentSecond > 0) {
                    SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.PREPARATION_PHASE_CHANGE);
                }
                if (currentSecond > 15) {
                    bossBar.setTitle("欢迎来到 [大逃杀] by LeafingXYZ");
                } else if (currentSecond > 10) {
                    bossBar.setTitle("基础玩法: 存活并击杀敌人获取积分");
                } else if (currentSecond > 5) {
                    bossBar.setTitle("奖励: 根据最终积分比例分配总奖池");
                } else {
                    bossBar.setTitle("战斗将在 " + currentSecond + " 秒后开始！祝你好运！");
                }
            }
            bossBar.setProgress((double) currentSecond / 30.0);
        }, 0L, 20L);
    }

    private void runGameLoop() {
        setGameState(GameState.INGAME);
        isPvpEnabled = false;
        for (Player p : getOnlineAlivePlayers()) {
            frozenPlayers.remove(p.getUniqueId());
            p.sendMessage(Component.text("战斗开始！PVP将在1分钟后开启！", NamedTextColor.GREEN));
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        gameStartTime = System.currentTimeMillis();
        gameUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateGame, 0L, 20L);
    }

    private void broadcastMessage(NamedTextColor color, String message) {
        Component component = Component.text("[大逃杀] ", NamedTextColor.GOLD).append(Component.text(message, color));
        Bukkit.broadcast(component);
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPlayerAlive(Player player) {
        if (player == null) return false;
        GamePlayer gp = gamePlayers.get(player.getUniqueId());
        return gp != null && gp.getState() == PlayerState.ALIVE;
    }

    public PlayerState getPlayerState(Player player) {
        if (player == null) return null;
        GamePlayer gp = gamePlayers.get(player.getUniqueId());
        return gp != null ? gp.getState() : null;
    }

    private List<GamePlayer> getAliveGamePlayers() {
        return gamePlayers.values().stream()
                .filter(gp -> gp.getState() == PlayerState.ALIVE)
                .collect(Collectors.toList());
    }

    private List<Player> getOnlineParticipants() {
        return gamePlayers.values().stream()
                .filter(gp -> gp.getState() == PlayerState.PARTICIPANT)
                .map(gp -> Bukkit.getPlayer(gp.getUuid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }



    private List<Player> getOnlineAlivePlayers() {
        return gamePlayers.values().stream()
                .filter(gp -> gp.getState() == PlayerState.ALIVE)
                .map(gp -> Bukkit.getPlayer(gp.getUuid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public int getParticipantCount() {
        return gamePlayers.size();
    }

    public int getAlivePlayerCount() {
        return (int) gamePlayers.values().stream().filter(gp -> gp.getState() == PlayerState.ALIVE).count();
    }

    private int getPlayerScore(Player player) {
        if (player == null) return 0;
        GamePlayer gp = gamePlayers.get(player.getUniqueId());
        return gp != null ? gp.getScore() : 0;
    }

    public boolean isPlayerFrozen(Player player) { return frozenPlayers.contains(player.getUniqueId()); }
    public String getGameWorldName() { return gameWorldName; }
    public GameState getGameState() { return gameState; }
    private void setGameState(GameState newState) { this.gameState = newState; }
    public boolean isPvpEnabled() { return isPvpEnabled; }
}