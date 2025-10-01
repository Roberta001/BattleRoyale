package xyz.leafing.battleRoyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import xyz.leafing.battleRoyale.map.GameMap;
import xyz.leafing.battleRoyale.map.MapManager;
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
    private final MapManager mapManager;
    private final Random random = new Random();

    private final Map<UUID, GamePlayer> participants = new ConcurrentHashMap<>();
    private final Map<UUID, Location> spectators = new ConcurrentHashMap<>();

    private GameState gameState = GameState.IDLE;
    private GameMap currentMap = null;
    private double entryFee = 0;
    private String gameWorldName = null;

    private BukkitTask lobbyUpdateTask;
    private BukkitTask gameUpdateTask;
    private BukkitTask preparationTask;
    private boolean isPvpEnabled = false;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private BossBar bossBar;
    private long gameStartTime;
    private int lobbyCountdown;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();

    public GameManager(BattleRoyale plugin, MiniGameAPI miniGameAPI, MapManager mapManager) {
        this.plugin = plugin;
        this.worldManager = new WorldManager(plugin);
        this.miniGameAPI = miniGameAPI;
        this.airdropManager = new AirdropManager(plugin);
        this.mapManager = mapManager;
    }

    // ... createGame, handleJoinLobby, startGame, etc. methods remain unchanged ...

    // [UNCHANGED]
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

    // [UNCHANGED]
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
        participants.put(player.getUniqueId(), gamePlayer);

        broadcastMessage(NamedTextColor.GREEN, player.getName() + " 已加入战斗！ (" + getParticipantCount() + " 人)");
        SoundManager.playSound(player, SoundManager.GameSound.JOIN_LOBBY);

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        int minPlayers = plugin.getGlobalConfig().getInt("game-settings.min-players", 2);
        if (getParticipantCount() >= minPlayers && lobbyUpdateTask == null) {
            startLobbyCountdown();
        }
    }

    // [UNCHANGED]
    private void startGame() {
        setGameState(GameState.PREPARING);

        this.currentMap = mapManager.selectRandomMap();
        if (this.currentMap == null) {
            broadcastMessage(NamedTextColor.RED, "错误: 没有可用的地图！游戏取消。");
            forceEndGame();
            return;
        }

        Component mapNameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(currentMap.getDisplayName());
        Bukkit.broadcast(Component.text("[大逃杀] ", NamedTextColor.GOLD)
                .append(Component.text("本场游戏将在地图 '", NamedTextColor.AQUA))
                .append(mapNameComponent)
                .append(Component.text("' 上进行！", NamedTextColor.AQUA)));

        getOnlineParticipants().forEach(p -> {
            if (!miniGameAPI.savePlayerData(p)) {
                p.sendMessage(Component.text("错误：无法备份你的数据，已将你移出游戏。", NamedTextColor.RED));
                handleLeave(p);
            }
        });

        broadcastMessage(NamedTextColor.BLUE, "正在准备世界...");
        worldManager.setupGameWorld(currentMap).thenAcceptAsync(gameWorld -> {
            if (gameWorld == null) {
                broadcastMessage(NamedTextColor.RED, "严重错误：无法创建游戏世界，游戏取消。");
                forceEndGame();
                return;
            }
            this.gameWorldName = gameWorld.getName();

            for (Player p : getOnlineParticipants()) {
                worldManager.teleportPlayer(p, gameWorld, currentMap);
                miniGameAPI.clearPlayerData(p);
                frozenPlayers.add(p.getUniqueId());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1));
                p.showTitle(Title.title(
                        Component.text("准备战斗", NamedTextColor.RED),
                        Component.text("寻找物资，准备厮杀！", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1))
                ));
            }

            startPreparationCountdown();

        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "创建世界时发生异常", ex);
            broadcastMessage(NamedTextColor.RED, "创建世界失败，游戏取消。");
            forceEndGame();
            return null;
        });
    }

    // [UNCHANGED]
    private void startPreparationCountdown() {
        broadcastGameConfiguration();

        long freezeDuration = ((Number) currentMap.getSetting("game-settings.preparation-freeze-duration", 20)).longValue();
        AtomicInteger countdown = new AtomicInteger((int) freezeDuration);

        preparationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int currentSecond = countdown.getAndDecrement();

            if (currentSecond <= 0) {
                if (preparationTask != null) preparationTask.cancel();
                runGameLoop();
                return;
            }

            if (bossBar != null) {
                bossBar.setTitle("战斗将在 " + currentSecond + " 秒后正式开始！");
                bossBar.setProgress((double) currentSecond / freezeDuration);
                bossBar.setColor(BarColor.PURPLE);
            }
        }, 0L, 20L);
    }

    // [UNCHANGED]
    private void runGameLoop() {
        setGameState(GameState.INGAME);
        isPvpEnabled = false;
        long pvpGracePeriod = ((Number) currentMap.getSetting("game-settings.pvp-grace-period", 60)).longValue();

        for (Player p : getOnlineAlivePlayers()) {
            frozenPlayers.remove(p.getUniqueId());
            p.sendMessage(Component.text("战斗开始！PVP将在" + pvpGracePeriod + "秒后开启！", NamedTextColor.GREEN));
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        gameStartTime = System.currentTimeMillis();
        scheduleGameEvents();
        gameUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateGame, 0L, 20L);
    }

    // [UNCHANGED]
    private void scheduleGameEvents() {
        long pvpGracePeriod = ((Number) currentMap.getSetting("game-settings.pvp-grace-period", 60)).longValue();
        scheduledTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isPvpEnabled = true;
            broadcastMessage(NamedTextColor.RED, "PVP已开启！");
            SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.PVP_ENABLE);
        }, pvpGracePeriod * 20L));

        List<Map<?, ?>> shrinks = currentMap.getSetting("world.border-shrinks", new ArrayList<>());
        for (Map<?, ?> shrink : shrinks) {
            long triggerTime = ((Number) shrink.get("trigger-time")).longValue();
            double targetSize = ((Number) shrink.get("target-size")).doubleValue();
            long duration = ((Number) shrink.get("duration")).longValue();
            scheduledTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World world = Bukkit.getWorld(gameWorldName);
                if (world != null) {
                    world.getWorldBorder().setSize(targetSize, duration);
                    broadcastMessage(NamedTextColor.DARK_RED, "警告！边界正在缩小！");
                    SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.BORDER_SHRINK_WARN);
                }
            }, triggerTime * 20L));
        }

        List<Map<?, ?>> airdrops = currentMap.getSetting("airdrops.phases", new ArrayList<>());
        for (Map<?, ?> airdrop : airdrops) {
            long triggerTime = ((Number) airdrop.get("trigger-time")).longValue();
            scheduledTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> spawnAirdropPhase(airdrop), triggerTime * 20L));
        }
    }

    // [UNCHANGED]
    private void spawnAirdropPhase(Map<?, ?> airdropConfig) {
        World world = Bukkit.getWorld(gameWorldName);
        if (world == null) return;

        String lootTableStr = (String) airdropConfig.get("loot-table");
        LootTable lootTable = getLootTableFromString(lootTableStr);
        if (lootTable == null) {
            plugin.getLogger().warning("无效的战利品表: " + lootTableStr);
            return;
        }

        int count = ((Number) airdropConfig.get("count")).intValue();
        for (int i = 0; i < count; i++) {
            Location loc = findRandomLocationInBorder();
            if (loc != null) {
                airdropManager.spawnAirdrop(loc, lootTable);
            }
        }

        String message = ChatColor.translateAlternateColorCodes('&', (String) airdropConfig.get("message"));
        broadcastMessage(null, message);
    }

    // [UNCHANGED]
    private LootTable getLootTableFromString(String key) {
        for (LootTables table : LootTables.values()) {
            if (table.getKey().toString().equalsIgnoreCase(key)) {
                return table.getLootTable();
            }
        }
        return null;
    }

    // [UNCHANGED]
    private void updateGame() {
        if (getAlivePlayerCount() <= 1 && gameState == GameState.INGAME) {
            endGame();
            return;
        }

        long survivalInterval = ((Number) currentMap.getSetting("rewards.survival-points-interval", 3)).longValue();
        int survivalAmount = ((Number) currentMap.getSetting("rewards.survival-points-amount", 1)).intValue();
        if (gameStartTime > 0 && (System.currentTimeMillis() - gameStartTime) / 1000 % survivalInterval == 0) {
            getAliveGamePlayers().forEach(gp -> gp.addScore(survivalAmount));
        }

        updateBossBar();
    }

    // [UNCHANGED]
    private void updateBossBar() {
        if (bossBar == null || bossBar.getPlayers().isEmpty()) return;

        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        long pvpGracePeriod = ((Number) currentMap.getSetting("game-settings.pvp-grace-period", 60)).longValue();
        int aliveCount = getAlivePlayerCount();

        bossBar.getPlayers().forEach(p -> {
            String scoreSuffix = " | 积分: " + getPlayerScore(p);
            String title;
            if (elapsedSeconds < pvpGracePeriod) {
                long pvpTime = pvpGracePeriod - elapsedSeconds;
                title = "PVP 将在 " + pvpTime + " 秒后开启" + scoreSuffix;
                bossBar.setProgress((double) pvpTime / pvpGracePeriod);
                bossBar.setColor(BarColor.GREEN);
            } else {
                title = aliveCount + " 人存活" + scoreSuffix;
                bossBar.setProgress(getParticipantCount() > 0 ? (double) aliveCount / getParticipantCount() : 0);
                bossBar.setColor(BarColor.RED);
            }
            bossBar.setTitle(title);
        });
    }

    // [修改] 淘汰逻辑改为立即切换到观察者模式
    public void handleElimination(Player victim, @Nullable Player killer) {
        GamePlayer victimGP = participants.get(victim.getUniqueId());
        if (victimGP == null || !victimGP.isAlive()) return;

        victimGP.setAlive(false);
        SoundManager.playSound(victim, SoundManager.GameSound.PLAYER_DEATH);

        int killPoints = ((Number) currentMap.getSetting("rewards.kill-points", 200)).intValue();
        String deathMessage;
        if (killer != null && isPlayerAlive(killer)) {
            GamePlayer killerGP = participants.get(killer.getUniqueId());
            if (killerGP != null) {
                killerGP.incrementKills();
                killerGP.addScore(killPoints);
                killer.sendMessage(Component.text("击杀成功！", NamedTextColor.GREEN).append(Component.text(" +" + killPoints + " 积分 (总积分: " + killerGP.getScore() + ")", NamedTextColor.GOLD)));
                SoundManager.playSound(killer, SoundManager.GameSound.KILL_PLAYER);
                deathMessage = victim.getName() + " 被 " + killer.getName() + " 淘汰。";
            } else {
                deathMessage = victim.getName() + " 被 " + killer.getName() + " 淘汰。";
            }
        } else {
            deathMessage = victim.getName() + " 被淘汰。";
        }
        broadcastMessage(NamedTextColor.AQUA, deathMessage + " [" + (getAlivePlayerCount()) + "/" + getParticipantCount() + " 剩余]");

        // 立即将玩家切换到内部观察者模式
        applyInternalSpectatorMode(victim);

        if (getAlivePlayerCount() <= 1) {
            Bukkit.getScheduler().runTaskLater(plugin, this::endGame, 1L);
        }
    }

    // [新增] 将被淘汰的玩家切换为游戏内观察者的方法
    public void applyInternalSpectatorMode(Player player) {
        if (!player.isOnline()) return;

        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear(); // 清空背包以防万一
        player.sendMessage(Component.text("你已被淘汰，进入观战模式。使用 /br leave 离开游戏。", NamedTextColor.YELLOW));
    }

    // [UNCHANGED]
    public void handleSpectate(Player player) {
        if (gameState != GameState.INGAME) {
            player.sendMessage(Component.text("当前没有可以观战的游戏。", NamedTextColor.RED));
            return;
        }
        if (participants.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("你是参赛者，无法观战。", NamedTextColor.RED));
            return;
        }
        if (spectators.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("你已在观战模式中。", NamedTextColor.YELLOW));
            return;
        }

        spectators.put(player.getUniqueId(), player.getLocation());
        player.setGameMode(GameMode.SPECTATOR);

        World gameWorld = Bukkit.getWorld(gameWorldName);
        if (gameWorld != null) {
            List<Player> alivePlayers = getOnlineAlivePlayers();
            if (!alivePlayers.isEmpty()) {
                player.teleport(alivePlayers.get(random.nextInt(alivePlayers.size())));
            } else {
                player.teleport(gameWorld.getSpawnLocation());
            }
        }

        player.sendMessage(Component.text("你已进入观战模式。使用 /br leave 离开。", NamedTextColor.GREEN));
        if (bossBar != null) bossBar.addPlayer(player);
    }

    // [修改] 离开逻辑现在需要处理已被淘汰的内部旁观者
    public void handleLeave(Player player) {
        UUID uuid = player.getUniqueId();

        if (spectators.containsKey(uuid)) { // 外部旁观者
            Location originalLoc = spectators.remove(uuid);
            player.setGameMode(GameMode.SURVIVAL);
            if (originalLoc != null) player.teleport(originalLoc);
            if (bossBar != null) bossBar.removePlayer(player);
            player.sendMessage(Component.text("你已退出观战。", NamedTextColor.GREEN));
            return;
        }

        GamePlayer gp = participants.get(uuid);
        if (gp != null) {
            if (gameState == GameState.LOBBY || gameState == GameState.PREPARING) { // 大厅或准备阶段离开
                plugin.getEconomy().depositPlayer(player, entryFee);
                participants.remove(uuid);
                frozenPlayers.remove(uuid);
                repatriatePlayer(player, gp.getOriginalLocation());

                if (gameState == GameState.LOBBY) {
                    broadcastMessage(NamedTextColor.GRAY, player.getName() + " 已离开大厅。");
                    int minPlayers = plugin.getGlobalConfig().getInt("game-settings.min-players", 2);
                    if (getParticipantCount() < minPlayers && lobbyUpdateTask != null) {
                        lobbyUpdateTask.cancel();
                        lobbyUpdateTask = null;
                        if (bossBar != null) bossBar.removeAll();
                        bossBar = null;
                        broadcastMessage(NamedTextColor.YELLOW, "人数不足，游戏开始倒计时已暂停。");
                    }
                }
            } else if (gp.isAlive()) { // 存活时离开
                handleElimination(player, null);
            } else { // 已被淘汰（内部旁观者）离开
                repatriatePlayer(player, gp.getOriginalLocation());
                participants.remove(uuid); // 提前离开，不再参与最终结算
                player.sendMessage(Component.text("你已离开游戏。", NamedTextColor.YELLOW));
            }
        }
    }

    // [UNCHANGED]
    private void endGame() {
        if (gameState != GameState.INGAME && gameState != GameState.PREPARING) return;
        setGameState(GameState.CLEANUP);

        Map<UUID, GamePlayer> finalParticipants = new HashMap<>(participants);

        long survivalInterval = ((Number) currentMap.getSetting("rewards.survival-points-interval", 3)).longValue();
        int survivalAmount = ((Number) currentMap.getSetting("rewards.survival-points-amount", 1)).intValue();
        if (gameStartTime > 0 && (System.currentTimeMillis() - gameStartTime) / 1000 % survivalInterval != 0) {
            finalParticipants.values().stream()
                    .filter(GamePlayer::isAlive)
                    .forEach(gp -> gp.addScore(survivalAmount));
        }

        double totalPot = entryFee * finalParticipants.size();
        long totalPointsScored = finalParticipants.values().stream().mapToLong(GamePlayer::getScore).sum();
        if (totalPointsScored == 0) totalPointsScored = 1;

        List<GamePlayer> sortedScores = finalParticipants.values().stream()
                .sorted(Comparator.comparingInt(GamePlayer::getScore).reversed())
                .collect(Collectors.toList());

        Bukkit.broadcast(Component.text(""));
        Bukkit.broadcast(Component.text("================[ 游戏结束 ]================", NamedTextColor.GOLD));
        Component mapNameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(currentMap.getDisplayName());
        Bukkit.broadcast(Component.text(" 地图: ", NamedTextColor.GRAY).append(mapNameComponent));
        Bukkit.broadcast(Component.text(" 总奖池: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", totalPot), NamedTextColor.YELLOW)));

        for (int i = 0; i < sortedScores.size(); i++) {
            GamePlayer gp = sortedScores.get(i);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(gp.getUuid());
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "未知玩家";
            double payout = totalPot * ((double) gp.getScore() / totalPointsScored);
            plugin.getEconomy().depositPlayer(offlinePlayer, payout);

            Component rank = Component.text("#" + (i + 1), NamedTextColor.AQUA);
            Component playerText = Component.text(" " + playerName, NamedTextColor.WHITE);
            Component scoreText = Component.text(" - " + gp.getScore() + " 积分", NamedTextColor.GRAY);
            Component payoutText = Component.text(" (奖金: " + String.format("%.2f", payout) + ")", NamedTextColor.GREEN);
            Bukkit.broadcast(rank.append(playerText).append(scoreText).append(payoutText));

            if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                Player onlinePlayer = offlinePlayer.getPlayer();
                onlinePlayer.sendMessage(Component.text("你获得了 ", NamedTextColor.GOLD)
                        .append(Component.text(String.format("%.2f", payout), NamedTextColor.YELLOW))
                        .append(Component.text(" 奖金！", NamedTextColor.GOLD)));
                if (i == 0 && gp.isAlive()) {
                    SoundManager.playSound(onlinePlayer, SoundManager.GameSound.GAME_WIN);
                }
            }
        }
        Bukkit.broadcast(Component.text("==========================================", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(""));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            repatriateAllPlayers();
            participants.clear();

            spectators.forEach((uuid, loc) -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.teleport(loc);
                    if (bossBar != null) bossBar.removePlayer(p);
                } else {
                    plugin.addPlayerToResetQueue(uuid);
                }
            });
            spectators.clear();
            broadcastMessage(NamedTextColor.GRAY, "正在清理场地...");
        }, 100L);

        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupAndReset, 160L);
    }

    // [UNCHANGED]
    public void forceEndGame() {
        if (gameState == GameState.IDLE) return;
        broadcastMessage(NamedTextColor.RED, "游戏被强制中止，将退还所有报名费并恢复数据。");
        participants.forEach((uuid, gp) -> plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), entryFee));

        repatriateAllPlayers();
        participants.clear();

        spectators.keySet().forEach(plugin::addPlayerToResetQueue);
        spectators.clear();

        cleanupAndReset();
    }

    // [UNCHANGED]
    private void cleanupAndReset() {
        if (bossBar != null) bossBar.removeAll();
        bossBar = null;
        airdropManager.cleanup();

        if (gameWorldName != null) worldManager.deleteWorld(gameWorldName);
        if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
        if (gameUpdateTask != null) gameUpdateTask.cancel();
        if (preparationTask != null) preparationTask.cancel();
        scheduledTasks.forEach(BukkitTask::cancel);
        scheduledTasks.clear();

        participants.clear();
        spectators.clear();
        frozenPlayers.clear();

        entryFee = 0;
        isPvpEnabled = false;
        gameStartTime = 0;
        currentMap = null;
        setGameState(GameState.IDLE);
    }

    // [UNCHANGED]
    public void repatriatePlayer(Player player, Location originalLocation) {
        if (player.isOnline()) {
            player.setGameMode(GameMode.SURVIVAL);
            if (originalLocation != null) {
                player.teleport(originalLocation);
            } else {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            miniGameAPI.restorePlayerData(player);
            miniGameAPI.leaveGame(player, plugin);
            if (bossBar != null) bossBar.removePlayer(player);
        }
    }

    // [UNCHANGED]
    private void repatriateAllPlayers() {
        new HashMap<>(participants).forEach((uuid, gp) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                repatriatePlayer(p, gp.getOriginalLocation());
            }
        });
    }

    // [UNCHANGED]
    private void startLobbyCountdown() {
        lobbyCountdown = plugin.getGlobalConfig().getInt("game-settings.lobby-countdown", 120);
        bossBar = Bukkit.createBossBar("游戏即将开始...", BarColor.GREEN, BarStyle.SOLID);
        getOnlineParticipants().forEach(bossBar::addPlayer);

        lobbyUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (lobbyCountdown > 0) {
                bossBar.setTitle("游戏将在 " + lobbyCountdown + " 秒后开始");
                bossBar.setProgress((double) lobbyCountdown / plugin.getGlobalConfig().getInt("game-settings.lobby-countdown", 120));
                if (lobbyCountdown <= 5 && lobbyCountdown > 0) {
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
                startGame();
            }
        }, 0L, 20L);
    }

    // [UNCHANGED]
    public void forceStartLobby(CommandSender starter) {
        if (gameState != GameState.LOBBY) {
            starter.sendMessage(Component.text("游戏当前未处于大厅等待状态。", NamedTextColor.RED));
            return;
        }
        int minPlayers = plugin.getGlobalConfig().getInt("game-settings.min-players", 2);
        if (getParticipantCount() < minPlayers) {
            starter.sendMessage(Component.text("玩家人数不足 " + minPlayers + " 人，无法强制开始。", NamedTextColor.RED));
            return;
        }

        broadcastMessage(NamedTextColor.YELLOW, "一名管理员强制开始了游戏！");
        if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
        lobbyUpdateTask = null;

        SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.GAME_START);
        if (bossBar != null) {
            bossBar.setTitle("正在初始化世界...");
            bossBar.setColor(BarColor.PURPLE);
            bossBar.setProgress(1.0);
        }
        startGame();
    }

    // [UNCHANGED]
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

    // [UNCHANGED]
    private void broadcastMessage(NamedTextColor color, String message) {
        Component component = Component.text("[大逃杀] ", NamedTextColor.GOLD).append(Component.text(message, color == null ? NamedTextColor.GRAY : color));
        Bukkit.broadcast(component);
    }

    // [UNCHANGED]
    private void broadcastGameConfiguration() {
        List<Component> summary = new ArrayList<>();
        long pvpTime = ((Number) currentMap.getSetting("game-settings.pvp-grace-period", 60)).longValue();

        Component mapNameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(currentMap.getDisplayName());
        summary.add(Component.text("--- [ 游戏规则: ", NamedTextColor.GOLD).append(mapNameComponent).append(Component.text(" ] ---", NamedTextColor.GOLD)));

        summary.add(Component.text(" > ", NamedTextColor.DARK_GRAY)
                .append(Component.text("PVP将于 ", NamedTextColor.GRAY))
                .append(Component.text(formatTime(pvpTime), NamedTextColor.YELLOW))
                .append(Component.text(" 后开启。", NamedTextColor.GRAY)));

        List<Map<?, ?>> shrinks = currentMap.getSetting("world.border-shrinks", new ArrayList<>());
        for (int i = 0; i < shrinks.size(); i++) {
            Map<?, ?> shrink = shrinks.get(i);
            long triggerTime = ((Number) shrink.get("trigger-time")).longValue();
            double targetSize = ((Number) shrink.get("target-size")).doubleValue();
            summary.add(Component.text(" > ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("边界收缩 #" + (i + 1) + ": ", NamedTextColor.GRAY))
                    .append(Component.text(formatTime(triggerTime), NamedTextColor.YELLOW))
                    .append(Component.text(" 缩小至 " + (int)targetSize + "x" + (int)targetSize, NamedTextColor.RED)));
        }

        List<Map<?, ?>> airdrops = currentMap.getSetting("airdrops.phases", new ArrayList<>());
        for (int i = 0; i < airdrops.size(); i++) {
            Map<?, ?> airdrop = airdrops.get(i);
            long triggerTime = ((Number) airdrop.get("trigger-time")).longValue();
            summary.add(Component.text(" > ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("空投 #" + (i + 1) + " 将于 ", NamedTextColor.GRAY))
                    .append(Component.text(formatTime(triggerTime), NamedTextColor.YELLOW))
                    .append(Component.text(" 降落。", NamedTextColor.GRAY)));
        }
        summary.add(Component.text("------------------------------------", NamedTextColor.GOLD));

        getOnlineParticipants().forEach(p -> summary.forEach(p::sendMessage));
    }

    // [UNCHANGED]
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    // [UNCHANGED]
    public GameState getGameState() { return gameState; }
    private void setGameState(GameState newState) { this.gameState = newState; }
    public boolean isPvpEnabled() { return isPvpEnabled; }
    public boolean isPlayerFrozen(Player player) { return frozenPlayers.contains(player.getUniqueId()); }
    public String getGameWorldName() { return gameWorldName; }
    public long getParticipantCount() {
        return participants.size();
    }
    public int getAlivePlayerCount() { return (int) participants.values().stream().filter(GamePlayer::isAlive).count(); }
    public boolean isPlayerInGame(Player player) { return participants.containsKey(player.getUniqueId()); }
    public boolean isPlayerAlive(Player player) {
        GamePlayer gp = participants.get(player.getUniqueId());
        return gp != null && gp.isAlive();
    }
    public boolean isSpectating(Player player) { return spectators.containsKey(player.getUniqueId()); }
    private List<GamePlayer> getAliveGamePlayers() {
        return participants.values().stream().filter(GamePlayer::isAlive).collect(Collectors.toList());
    }
    private List<Player> getOnlineParticipants() {
        return participants.keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList());
    }
    public List<Player> getOnlineAlivePlayers() {
        return participants.entrySet().stream()
                .filter(entry -> entry.getValue().isAlive())
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    private int getPlayerScore(Player player) {
        if (player == null) return 0;
        GamePlayer gp = participants.get(player.getUniqueId());
        return gp != null ? gp.getScore() : 0;
    }
    public GamePlayer getGamePlayer(UUID uuid) {
        return participants.get(uuid);
    }
}