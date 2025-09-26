package xyz.leafing.battleRoyale;

// 不再需要 import MultiverseInventories
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.leafing.battleRoyale.commands.BRCommand;
import xyz.leafing.battleRoyale.listeners.PlayerListener;
import xyz.leafing.battleRoyale.listeners.DataRestoreListener;

import java.util.logging.Logger;

public final class BattleRoyale extends JavaPlugin {

    private static BattleRoyale instance;
    private static final Logger log = Logger.getLogger("Minecraft");
    private Economy econ = null;
    private GameManager gameManager;
    private PlayerDataManager playerDataManager; // 新增字段

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            log.severe(String.format("[%s] - 未找到Vault插件，插件已禁用!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化新的管理器
        this.playerDataManager = new PlayerDataManager(this);
        // 将 PlayerDataManager 传入 GameManager
        this.gameManager = new GameManager(this, playerDataManager);

        getCommand("br").setExecutor(new BRCommand(gameManager));
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        // 注册新的数据恢复监听器
        getServer().getPluginManager().registerEvents(new DataRestoreListener(playerDataManager), this);

        getLogger().info("BattleRoyale 插件已成功加载!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.getGameState() != GameState.IDLE) {
            // 这个方法现在会负责恢复所有玩家的数据
            gameManager.forceEndGame();
        }
        getLogger().info("BattleRoyale 插件已卸载。");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    // 移除了 setupMultiverseInventories() 方法

    public static BattleRoyale getInstance() { return instance; }
    public Economy getEconomy() { return econ; }
    public GameManager getGameManager() { return gameManager; }
}