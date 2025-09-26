package xyz.leafing.battleRoyale;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.leafing.battleRoyale.commands.BRCommand;
import xyz.leafing.battleRoyale.listeners.PlayerListener;
import xyz.leafing.battleRoyale.ui.MenuListener;
import xyz.leafing.battleRoyale.ui.MenuManager;
import xyz.leafing.miniGameManager.api.MiniGameAPI; // 导入新的API

import java.util.logging.Logger;

public final class BattleRoyale extends JavaPlugin {

    private static BattleRoyale instance;
    private static final Logger log = Logger.getLogger("Minecraft");
    private Economy econ = null;
    private MiniGameAPI miniGameAPI = null; // 新增API字段
    private GameManager gameManager;

    private MenuManager menuManager;

    @Override
    public void onEnable() {
        instance = this;

        // 检查 Vault 依赖
        if (!setupEconomy()) {
            log.severe(String.format("[%s] - 未找到Vault插件，插件已禁用!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 检查 MiniGameManager 依赖
        if (!setupMiniGameManager()) {
            log.severe(String.format("[%s] - 未找到MiniGameManager插件，插件已禁用!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 将 MiniGameAPI 传入 GameManager
        this.gameManager = new GameManager(this, miniGameAPI);

        // 初始化 UI 管理器
        this.menuManager = new MenuManager(gameManager);

        getCommand("br").setExecutor(new BRCommand(gameManager, menuManager)); // 将 menuManager 传入指令处理器
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(gameManager, menuManager), this); // 注册 UI 监听器

        // [修复] 移除了重复的 PlayerListener 注册，避免事件被触发两次
        // getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);

        getLogger().info("BattleRoyale 插件已成功加载并连接到 MiniGameManager 服务!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.getGameState() != GameState.IDLE) {
            // 这个方法现在会负责恢复所有玩家的数据并释放他们的状态
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

    // 新增方法：用于获取 MiniGameManager 的 API
    private boolean setupMiniGameManager() {
        RegisteredServiceProvider<MiniGameAPI> rsp = getServer().getServicesManager().getRegistration(MiniGameAPI.class);
        if (rsp == null) {
            return false;
        }
        miniGameAPI = rsp.getProvider();
        return miniGameAPI != null;
    }

    public static BattleRoyale getInstance() { return instance; }
    public Economy getEconomy() { return econ; }
    public GameManager getGameManager() { return gameManager; }
    public MiniGameAPI getMiniGameAPI() { return miniGameAPI; } // 可选的 getter
}