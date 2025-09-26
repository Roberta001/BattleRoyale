package xyz.leafing.battleRoyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import xyz.leafing.battleRoyale.world.WorldManager;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameManager {

    private final BattleRoyale plugin;
    private final WorldManager worldManager;
    private final PlayerDataManager playerDataManager;

    private GameState gameState = GameState.IDLE;
    private final Map<UUID, Location> participantsOriginalLocations = new HashMap<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private double entryFee = 0;
    private String gameWorldName = null;

    private final Map<UUID, Integer> playerScores = new HashMap<>();
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

    public GameManager(BattleRoyale plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.worldManager = new WorldManager(plugin);
        this.playerDataManager = playerDataManager;
    }

    // ... createGame, addPlayer, removePlayer ... (这些方法保持不变)
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
        addPlayer(initiator);

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

    public void addPlayer(Player player) {
        if (gameState != GameState.LOBBY) {
            player.sendMessage(Component.text("现在无法加入游戏。", NamedTextColor.RED));
            return;
        }
        if (participantsOriginalLocations.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("你已经在大厅中了。", NamedTextColor.YELLOW));
            return;
        }
        if (plugin.getEconomy().getBalance(player) < entryFee) {
            player.sendMessage(Component.text("你的余额不足以支付 " + entryFee + " 的报名费。", NamedTextColor.RED));
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, entryFee);
        playerDataManager.savePlayerData(player);

        participantsOriginalLocations.put(player.getUniqueId(), player.getLocation());
        killCounts.put(player.getUniqueId(), 0);
        playerScores.put(player.getUniqueId(), 0);

        broadcastMessage(NamedTextColor.GREEN, player.getName() + " 已加入战斗！ (" + participantsOriginalLocations.size() + " 人)");
        SoundManager.playSound(player, SoundManager.GameSound.JOIN_LOBBY);

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }

        if (participantsOriginalLocations.size() == 2) {
            startLobbyCountdown();
        }
    }

    public void removePlayer(Player player, boolean refund) {
        if (!participantsOriginalLocations.containsKey(player.getUniqueId())) return;

        if (refund) {
            plugin.getEconomy().depositPlayer(player, entryFee);
            player.sendMessage(Component.text("你已离开大厅，报名费已退还。", NamedTextColor.YELLOW));
        }

        playerDataManager.restorePlayerData(player);
        participantsOriginalLocations.remove(player.getUniqueId());
        playerScores.remove(player.getUniqueId());

        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        if (gameState == GameState.LOBBY) {
            broadcastMessage(NamedTextColor.GRAY, player.getName() + " 已离开大厅。");
            if (participantsOriginalLocations.size() < 2 && lobbyUpdateTask != null) {
                lobbyUpdateTask.cancel();
                if (bossBar != null) bossBar.removeAll();
                bossBar = null;
                broadcastMessage(NamedTextColor.YELLOW, "人数不足，游戏开始倒计时已暂停。");
            }
        }
    }

    private void startLobbyCountdown() {
        lobbyCountdown = 120;
        bossBar = Bukkit.createBossBar("游戏即将开始...", BarColor.GREEN, BarStyle.SOLID);
        getOnlineParticipants().forEach(bossBar::addPlayer);

        lobbyUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (lobbyCountdown > 0) {
                bossBar.setTitle("游戏将在 " + lobbyCountdown + " 秒后开始");
                bossBar.setProgress((double) lobbyCountdown / 120.0);

                if (lobbyCountdown <= 4) {
                    SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.COUNTDOWN_TICK);
                }
            } else {
                SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.GAME_START);
                bossBar.setTitle("正在初始化世界...");
                bossBar.setColor(BarColor.PURPLE);
                bossBar.setProgress(1.0);

                if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
                lobbyUpdateTask = null; // 确保任务被置空
                Bukkit.getScheduler().runTask(plugin, this::startGame);
            }
            lobbyCountdown--;
        }, 0L, 20L);
    }

    /**
     * NEW: 管理员强制开始游戏的方法
     * @param starter 执行此操作的命令发送者（用于接收反馈消息）
     */
    public void forceStartLobby(CommandSender starter) {
        if (gameState != GameState.LOBBY) {
            starter.sendMessage(Component.text("游戏当前未处于大厅等待状态。", NamedTextColor.RED));
            return;
        }
        if (participantsOriginalLocations.size() < 2) {
            starter.sendMessage(Component.text("玩家人数不足2人，无法强制开始。", NamedTextColor.RED));
            return;
        }

        broadcastMessage(NamedTextColor.YELLOW, "一名管理员强制开始了游戏！");

        // 停止倒计时任务
        if (lobbyUpdateTask != null) {
            lobbyUpdateTask.cancel();
            lobbyUpdateTask = null;
        }

        // 复用倒计时结束时的逻辑，确保体验一致
        SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.GAME_START);
        if (bossBar != null) {
            bossBar.setTitle("正在初始化世界...");
            bossBar.setColor(BarColor.PURPLE);
            bossBar.setProgress(1.0);
        }

        Bukkit.getScheduler().runTask(plugin, this::startGame);
    }

    private void startGame() {
        setGameState(GameState.PREPARING);
        broadcastMessage(NamedTextColor.BLUE, "游戏开始！正在准备世界...");
        gameWorldName = "br_" + System.currentTimeMillis();
        World gameWorld = worldManager.createWorld(gameWorldName);

        if (gameWorld == null) {
            broadcastMessage(NamedTextColor.RED, "严重错误：无法创建游戏世界，游戏取消。");
            forceEndGame();
            return;
        }

        alivePlayers.addAll(participantsOriginalLocations.keySet());
        for (Player p : getOnlineParticipants()) {
            worldManager.teleportPlayerToRandomLocation(p, gameWorld);
            playerDataManager.clearPlayerData(p);
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

    // ... startPreparationCountdown, runGameLoop, updateGame, handlePlayerDeath, endGame ... (这些方法保持不变)
    private void startPreparationCountdown() {
        bossBar.setColor(BarColor.PURPLE);
        bossBar.setStyle(BarStyle.SOLID);
        // 将总倒计时设置为30秒
        AtomicInteger countdown = new AtomicInteger(30);

        // 在倒计时开始时，先将BossBar隐藏

        preparationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int currentSecond = countdown.getAndDecrement();

            // 倒计时结束，开始游戏
            if (currentSecond <= 0) {
                // 为确保BossBar在游戏开始时是可见的（如果之前被隐藏），在这里重新设置为可见
                bossBar.setVisible(true);
                runGameLoop();
                if (preparationTask != null) {
                    preparationTask.cancel();
                }
                return;
            }

            // --- 核心逻辑 ---
            // 1. 在前10秒（即秒数 > 20时），我们什么都不做，让BossBar保持隐藏状态

            // 2. 当倒计时进入最后20秒时
            if (currentSecond <= 20) {
                // 在20秒的节点，让BossBar重新显示出来
                if (currentSecond == 20) {
                    bossBar.setVisible(true);
                }

                // 在每个信息阶段开始时播放音效 (20, 15, 10, 5秒)
                if (currentSecond == 20 || currentSecond == 15 || currentSecond == 10 || currentSecond == 5) {
                    SoundManager.broadcastSound(getOnlineParticipants(), SoundManager.GameSound.PREPARATION_PHASE_CHANGE);
                }

                // 根据当前剩余时间，显示对应的文本
                if (currentSecond > 15) {
                    // 第一部分: 游戏名和开发者 (20-16秒)
                    bossBar.setTitle("欢迎来到 [大逃杀] by LeafingXYZ");
                } else if (currentSecond > 10) {
                    // 第二部分: 基础玩法 (15-11秒)
                    bossBar.setTitle("基础玩法: 存活并击杀敌人获取积分");
                } else if (currentSecond > 5) {
                    // 第三部分: 奖励说明 (10-6秒)
                    bossBar.setTitle("奖励: 根据最终积分比例分配总奖池");
                } else { // currentSecond is between 1 and 5
                    // 第四部分: 最终倒计时 (5-1秒)
                    bossBar.setTitle("战斗将在 " + currentSecond + " 秒后开始！祝你好运！");
                }
            }

            // 无论在哪个阶段，进度条都根据总时长30秒来更新
            bossBar.setProgress((double) currentSecond / 30.0);

        }, 0L, 20L); // 每秒 (20 tick) 执行一次
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

    private void updateGame() {
        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        World gameWorld = Bukkit.getWorld(gameWorldName);
        if (gameWorld == null) {
            forceEndGame();
            return;
        }

        if (elapsedSeconds > 0 && elapsedSeconds % SURVIVAL_POINT_INTERVAL_SECONDS == 0) {
            for (UUID uuid : alivePlayers) {
                playerScores.compute(uuid, (key, oldVal) -> (oldVal == null) ? SURVIVAL_POINTS : oldVal + SURVIVAL_POINTS);
            }
        }

        if (!isPvpEnabled && elapsedSeconds >= 60) {
            isPvpEnabled = true;
            broadcastMessage(NamedTextColor.RED, "PVP已开启！");
            SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.PVP_ENABLE);
        }

        if (bossBar != null && !bossBar.getPlayers().isEmpty()) {
            Player firstPlayer = bossBar.getPlayers().get(0);
            String scoreSuffix = " | 积分: " + getPlayerScore(firstPlayer);

            if (elapsedSeconds < 60) {
                long pvpTime = 60 - elapsedSeconds;
                bossBar.setTitle("PVP 将在 " + pvpTime + " 秒后开启" + scoreSuffix);
                bossBar.setProgress((double) pvpTime / 60.0);
                bossBar.setColor(BarColor.GREEN);
            } else if (elapsedSeconds < 600) {
                long borderTime = 600 - elapsedSeconds;
                bossBar.setTitle("边界将在 " + formatTime(borderTime) + " 后缩小 | " + alivePlayers.size() + " 人存活");
                bossBar.setProgress((double) (borderTime) / (540.0));
                bossBar.setColor(BarColor.YELLOW);
            } else if (elapsedSeconds == 600) {
                broadcastMessage(NamedTextColor.DARK_RED, "警告！边界将在10分钟内缩小至 100x100！");
                gameWorld.getWorldBorder().setSize(100, 600);
                SoundManager.broadcastSound(getOnlineAlivePlayers(), SoundManager.GameSound.BORDER_SHRINK_WARN);
            } else if (elapsedSeconds < 1200) {
                bossBar.setTitle("边界缩小中！ | " + alivePlayers.size() + " 人存活");
                bossBar.setProgress(gameWorld.getWorldBorder().getSize() / 1000.0);
                bossBar.setColor(BarColor.RED);
            } else if (elapsedSeconds == 1200) {
                broadcastMessage(NamedTextColor.DARK_RED, "决赛圈！边界将在5分钟内完全闭合！");
                gameWorld.getWorldBorder().setSize(1, 300);
            } else {
                bossBar.setTitle("最终决战！ | " + alivePlayers.size() + " 人存活");
                bossBar.setProgress(gameWorld.getWorldBorder().getSize() / 100.0);
                bossBar.setColor(BarColor.PURPLE);
            }
        }

        List<Player> toDisqualify = new ArrayList<>();
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.getWorld().getName().equals(gameWorldName)) {
                toDisqualify.add(p);
                p.sendMessage(Component.text("你因离开游戏区域而被淘汰。", NamedTextColor.RED));
            }
        }
        toDisqualify.forEach(p -> handlePlayerDeath(p, null));
    }

    public void handlePlayerDeath(Player victim, Player killer) {
        if (!alivePlayers.contains(victim.getUniqueId())) return;
        SoundManager.playSound(victim, SoundManager.GameSound.PLAYER_DEATH);
        alivePlayers.remove(victim.getUniqueId());
        if (bossBar != null) bossBar.removePlayer(victim);
        String deathMessage;
        if (killer != null && alivePlayers.contains(killer.getUniqueId())) {
            int kills = killCounts.merge(killer.getUniqueId(), 1, Integer::sum);
            int newScore = playerScores.merge(killer.getUniqueId(), KILL_POINTS, Integer::sum);
            killer.sendMessage(Component.text("击杀成功！", NamedTextColor.GREEN).append(Component.text(" +" + KILL_POINTS + " 积分 (总积分: " + newScore + ")", NamedTextColor.GOLD)));
            SoundManager.playSound(killer, SoundManager.GameSound.KILL_PLAYER);
            deathMessage = victim.getName() + " 被 " + killer.getName() + " 淘汰 (" + kills + " 击杀)。";
        } else {
            deathMessage = victim.getName() + " 被淘汰。";
        }
        broadcastMessage(NamedTextColor.AQUA, deathMessage + " [" + alivePlayers.size() + "/" + participantsOriginalLocations.size() + " 剩余]");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                victim.spigot().respawn();
                Location originalLocation = participantsOriginalLocations.get(victim.getUniqueId());
                if (originalLocation != null) victim.teleport(originalLocation);
                playerDataManager.restorePlayerData(victim);
                victim.sendMessage(Component.text("你的游戏前数据已恢复。", NamedTextColor.GREEN));
            }
        });
        if (alivePlayers.size() <= 1) {
            endGame();
        }
    }

    private void endGame() {
        if (gameUpdateTask != null) gameUpdateTask.cancel();
        setGameState(GameState.CLEANUP);
        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        if (elapsedSeconds > 0 && elapsedSeconds % SURVIVAL_POINT_INTERVAL_SECONDS == 0) {
            for (UUID uuid : alivePlayers) {
                playerScores.compute(uuid, (key, oldVal) -> (oldVal == null) ? SURVIVAL_POINTS : oldVal + SURVIVAL_POINTS);
            }
        }
        double totalPot = entryFee * participantsOriginalLocations.size();
        long totalPointsScored = playerScores.values().stream().mapToLong(Integer::longValue).sum();
        if (totalPointsScored == 0) totalPointsScored = 1;
        List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        Bukkit.broadcast(Component.text(""));
        Bukkit.broadcast(Component.text("================[ 游戏结束 ]================", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(" 总奖池: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", totalPot), NamedTextColor.YELLOW)));
        for (int i = 0; i < sortedScores.size(); i++) {
            Map.Entry<UUID, Integer> entry = sortedScores.get(i);
            UUID playerUUID = entry.getKey();
            int score = entry.getValue();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "未知玩家";
            double payout = totalPot * ((double) score / totalPointsScored);
            plugin.getEconomy().depositPlayer(offlinePlayer, payout);
            Component rank = Component.text("#" + (i + 1), NamedTextColor.AQUA);
            Component playerText = Component.text(" " + playerName, NamedTextColor.WHITE);
            Component scoreText = Component.text(" - " + score + " 积分", NamedTextColor.GRAY);
            Component payoutText = Component.text(" (奖金: " + String.format("%.2f", payout) + ")", NamedTextColor.GREEN);
            Bukkit.broadcast(rank.append(playerText).append(scoreText).append(payoutText));
            if (offlinePlayer.isOnline()) {
                Player onlinePlayer = offlinePlayer.getPlayer();
                onlinePlayer.sendMessage(Component.text("你获得了 ", NamedTextColor.GOLD)
                        .append(Component.text(String.format("%.2f", payout), NamedTextColor.YELLOW))
                        .append(Component.text(" 奖金！", NamedTextColor.GOLD)));
                if (i == 0) {
                    SoundManager.playSound(onlinePlayer, SoundManager.GameSound.GAME_WIN);
                }
            }
        }
        Bukkit.broadcast(Component.text("==========================================", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(""));
        Bukkit.getScheduler().runTaskLater(plugin, this::endGameCleanup, 20 * 15L);
    }

    private void endGameCleanup() {
        // 恢复胜利者数据 (其他玩家已在死亡时恢复)
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Location loc = participantsOriginalLocations.get(uuid);
                if (loc != null) p.teleport(loc);
                playerDataManager.restorePlayerData(p);
            }
        }

        // MODIFIED: 增加最终清理逻辑，确保所有参与者的yml文件都被删除
        plugin.getLogger().info("游戏结束，开始最终清理玩家数据文件...");
        for (UUID participantUUID : participantsOriginalLocations.keySet()) {
            File dataFile = new File(plugin.getDataFolder() + "/playerdata", participantUUID.toString() + ".yml");
            if (dataFile.exists()) {
                if (dataFile.delete()) {
                    plugin.getLogger().info("已成功删除残留数据文件: " + dataFile.getName());
                } else {
                    plugin.getLogger().warning("警告：无法删除数据文件: " + dataFile.getName());
                }
            }
        }

        if (bossBar != null) bossBar.removeAll();
        bossBar = null;
        if (gameWorldName != null) worldManager.deleteWorld(gameWorldName);
        if (lobbyUpdateTask != null) lobbyUpdateTask.cancel();
        if (gameUpdateTask != null) gameUpdateTask.cancel();
        if (preparationTask != null) preparationTask.cancel();

        // 重置所有状态
        participantsOriginalLocations.clear();
        alivePlayers.clear();
        killCounts.clear();
        frozenPlayers.clear();
        playerScores.clear();
        entryFee = 0;
        isPvpEnabled = false;
        setGameState(GameState.IDLE);
    }

    // ... forceEndGame 和其他辅助方法保持不变 ...
    public void forceEndGame() {
        if (gameState == GameState.IDLE) return;
        broadcastMessage(NamedTextColor.RED, "游戏被强制中止，将退还所有报名费并恢复数据。");
        new HashSet<>(participantsOriginalLocations.keySet()).forEach(uuid -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
            plugin.getEconomy().depositPlayer(p, entryFee);
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            if (onlinePlayer != null) {
                Location loc = participantsOriginalLocations.get(uuid);
                if (loc != null) onlinePlayer.teleport(loc);
                playerDataManager.restorePlayerData(onlinePlayer);
            }
        });
        endGameCleanup();
    }

    private List<Player> getOnlineParticipants() {
        return participantsOriginalLocations.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Player> getOnlineAlivePlayers() {
        return alivePlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private int getPlayerScore(Player player) {
        if (player == null) return 0;
        return playerScores.getOrDefault(player.getUniqueId(), 0);
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

    public boolean isPlayerInGame(Player player) { return participantsOriginalLocations.containsKey(player.getUniqueId()); }
    public boolean isPlayerFrozen(Player player) { return frozenPlayers.contains(player.getUniqueId()); }
    public String getGameWorldName() { return gameWorldName; }
    public GameState getGameState() { return gameState; }
    private void setGameState(GameState newState) { this.gameState = newState; }
    public boolean isPvpEnabled() { return isPvpEnabled; }

}