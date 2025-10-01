package xyz.leafing.battleRoyale;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.leafing.battleRoyale.commands.BRCommand;
import xyz.leafing.battleRoyale.listeners.PlayerListener;
import xyz.leafing.battleRoyale.map.MapManager;
import xyz.leafing.battleRoyale.ui.MenuListener;
import xyz.leafing.battleRoyale.ui.MenuManager;
import xyz.leafing.miniGameManager.api.MiniGameAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class BattleRoyale extends JavaPlugin {

    private static BattleRoyale instance;
    private static final Logger log = Logger.getLogger("Minecraft");
    private Economy econ = null;
    private MiniGameAPI miniGameAPI = null;
    private GameManager gameManager;
    private MenuManager menuManager;
    private MapManager mapManager; // 新增地图管理器

    // 用于在游戏结束后重置离线旁观者的游戏模式
    private final Set<UUID> needsGamemodeReset = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        if (!setupEconomy() || !setupMiniGameManager()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化地图管理器
        this.mapManager = new MapManager(this);
        mapManager.loadMaps();

        // 将依赖传入 GameManager
        this.gameManager = new GameManager(this, miniGameAPI, mapManager);
        this.menuManager = new MenuManager(gameManager);

        getCommand("br").setExecutor(new BRCommand(gameManager, menuManager));
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager, this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(gameManager, menuManager), this);

        getLogger().info("BattleRoyale 插件已成功加载！");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.getGameState() != GameState.IDLE) {
            gameManager.forceEndGame();
        }
        getLogger().info("BattleRoyale 插件已卸载。");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            log.severe(String.format("[%s] - 未找到Vault插件!", getDescription().getName()));
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean setupMiniGameManager() {
        RegisteredServiceProvider<MiniGameAPI> rsp = getServer().getServicesManager().getRegistration(MiniGameAPI.class);
        if (rsp == null) {
            log.severe(String.format("[%s] - 未找到MiniGameManager插件!", getDescription().getName()));
            return false;
        }
        miniGameAPI = rsp.getProvider();
        return miniGameAPI != null;
    }

    public static BattleRoyale getInstance() { return instance; }
    public Economy getEconomy() { return econ; }
    public GameManager getGameManager() { return gameManager; }
    public MiniGameAPI getMiniGameAPI() { return miniGameAPI; }
    public FileConfiguration getGlobalConfig() { return getConfig(); }

    // 管理需要重置游戏模式的玩家
    public void addPlayerToResetQueue(UUID uuid) { this.needsGamemodeReset.add(uuid); }
    public boolean needsGamemodeReset(UUID uuid) { return this.needsGamemodeReset.contains(uuid); }
    public void removePlayerFromResetQueue(UUID uuid) { this.needsGamemodeReset.remove(uuid); }
}