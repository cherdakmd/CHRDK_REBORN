package ru.example.vkchat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import ru.example.vkchat.config.ConfigManager;
import ru.example.vkchat.vk.VKLongPollManager;
import ru.example.vkchat.listeners.*;
import ru.example.vkchat.commands.MCCommands;
import ru.example.vkchat.tasks.*;
import ru.example.vkchat.vk.VKFeaturesManager;
import ru.example.vkchat.managers.CoreManagers;
import ru.example.vkchat.hardcore.BloodMoonManager;
import ru.example.vkchat.api.VKChatAPI;
import ru.example.vkchat.moderation.WarnManager;
import ru.example.vkchat.database.DatabaseManager;
import ru.example.vkchat.hardcore.BleedingTask;
import ru.example.vkchat.auth.MembershipManager;

public class VKChatPlugin extends JavaPlugin {

    private static VKChatPlugin instance;

    private ConfigManager configManager;
    private VKLongPollManager vkLongPollManager;
    private VKFeaturesManager vkFeaturesManager;
    private CoreManagers coreManagers;
    private GuiListener guiListener;
    private BloodMoonManager bloodMoonManager;
    private VKChatAPI api;
    private WarnManager warnManager;
    private DatabaseManager databaseManager;
    private MembershipManager membershipManager;

    private boolean vaultEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=========================================");
        getLogger().info("VKChat 2.0.7 успешно запущен!");
        getLogger().info("СОЗДАНО ДЛЯ https://vk.com/chrdk_reborn и https://t.me/cherdakmd");
        getLogger().info("=========================================");

        configManager = new ConfigManager(this);

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultEnabled = true;
            getLogger().info("Vault найден! Экономика включена.");
        }

        databaseManager = new DatabaseManager(this);
        vkLongPollManager = new VKLongPollManager(this);
        coreManagers = new CoreManagers(this);
        coreManagers.initialize();
        vkFeaturesManager = new VKFeaturesManager(this);
        vkFeaturesManager.initialize();
        guiListener = new GuiListener(this);
        bloodMoonManager = new BloodMoonManager(this);
        api = new VKChatAPI(this);
        warnManager = new WarnManager(this);
        membershipManager = new MembershipManager(this);

        registerListeners();
        registerCommands();

        if (coreManagers.getStatsManager() != null) coreManagers.getStatsManager().setupEconomy();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VKChatExpansion(this).register();
            getLogger().info("PlaceholderAPI найден! Плейсхолдеры %vkchat_reputation% загружены.");
        }

        startTasks();
        vkLongPollManager.start();
        vkLongPollManager.sendToMainChat("✅ Сервер запущен!");

        coreManagers.getAuthManager().startSessionTimeoutTask();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        // EventsListener removed — dead code
        getServer().getPluginManager().registerEvents(new RandomSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinNotificationListener(this), this);
    }

    private void registerCommands() {
        MCCommands mcCmds = new MCCommands(this);

        getCommand("vklink").setExecutor(mcCmds);
        getCommand("vkunlink").setExecutor(mcCmds);
        getCommand("vkchat").setExecutor(mcCmds);
        getCommand("register").setExecutor(mcCmds);
        getCommand("2fa").setExecutor(mcCmds);
        getCommand("login").setExecutor(mcCmds);
        getCommand("changepass").setExecutor(mcCmds);
        getCommand("logout").setExecutor(mcCmds);
        getCommand("rep").setExecutor(mcCmds);
        getCommand("pay").setExecutor(mcCmds);
        getCommand("vk").setExecutor(mcCmds);
        getCommand("menu").setExecutor(mcCmds);
        getCommand("clearwarns").setExecutor(mcCmds);
        getCommand("warns").setExecutor(mcCmds);
        getCommand("unwarn").setExecutor(mcCmds);
        getCommand("warn").setExecutor(mcCmds);

        getCommand("bal").setExecutor(mcCmds);
        getCommand("online").setExecutor(mcCmds);
        getCommand("lastseen").setExecutor(mcCmds);
        getCommand("mute").setExecutor(mcCmds);
        getCommand("unmute").setExecutor(mcCmds);
        getCommand("ignore").setExecutor(mcCmds);

        getCommand("vklink").setTabCompleter(mcCmds);
        getCommand("vkunlink").setTabCompleter(mcCmds);
        getCommand("vkchat").setTabCompleter(mcCmds);
        getCommand("register").setTabCompleter(mcCmds);
        getCommand("2fa").setTabCompleter(mcCmds);
        getCommand("login").setTabCompleter(mcCmds);
        getCommand("changepass").setTabCompleter(mcCmds);
        getCommand("logout").setTabCompleter(mcCmds);
        getCommand("rep").setTabCompleter(mcCmds);
        getCommand("pay").setTabCompleter(mcCmds);
        getCommand("vk").setTabCompleter(mcCmds);
        getCommand("menu").setTabCompleter(mcCmds);
        getCommand("clearwarns").setTabCompleter(mcCmds);
        getCommand("warns").setTabCompleter(mcCmds);
        getCommand("unwarn").setTabCompleter(mcCmds);
        getCommand("warn").setTabCompleter(mcCmds);
        getCommand("bal").setTabCompleter(mcCmds);
        getCommand("online").setTabCompleter(mcCmds);
        getCommand("lastseen").setTabCompleter(mcCmds);
        getCommand("mute").setTabCompleter(mcCmds);
        getCommand("unmute").setTabCompleter(mcCmds);
        getCommand("ignore").setTabCompleter(mcCmds);
    }

    private void startTasks() {
        int wallInterval = getConfig().getInt("wall-check-interval", 60) * 20;
        new WallCheckerTask(this).runTaskTimerAsynchronously(this, wallInterval, wallInterval);

        int topInterval = getConfig().getInt("stats.top-broadcast-interval", 3600) * 20;
        new TopBroadcastTask(this).runTaskTimerAsynchronously(this, topInterval, topInterval);

        new AuthTimerTask(this).runTaskTimerAsynchronously(this, 20L, 20L);

        int playTimeInterval = getConfig().getInt("reputation.play-time-interval", 60) * 20 * 60;
        if (playTimeInterval > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (coreManagers.getAuthManager().isFullyAuthorized(p)) {
                        int vkId = coreManagers.getAuthManager().getLinkedVkId(p);
                        if (vkId != -1) {
                            int reward = getConfig().getInt("reputation.play-time-reward", 1);
                            coreManagers.getReputationManager().addPoints(vkId, reward);
                        }
                    }
                }
            }, playTimeInterval, playTimeInterval);
        }

        new BleedingTask(this).runTaskTimer(this, 20L, 20L);
        bloodMoonManager.runTaskTimer(this, 100L, 200L);
    }

    @Override
    public void onDisable() {
        getLogger().info("=========================================");
        getLogger().info("VKChat 2.0.7 остановлен!");
        getLogger().info("СОЗДАНО ДЛЯ https://vk.com/chrdk_reborn и https://t.me/cherdakmd");
        getLogger().info("=========================================");

        if (vkLongPollManager != null) {
            vkLongPollManager.stop();
            vkLongPollManager.sendToMainChat("❌ Сервер остановлен!");
        }

        if (coreManagers != null) {
            if (coreManagers.getAuthManager() != null) coreManagers.getAuthManager().save();
            if (coreManagers.getStatsManager() != null) coreManagers.getStatsManager().save();
        }
        if (warnManager != null) warnManager.save();
        if (databaseManager != null) databaseManager.close();
    }

    public static VKChatPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public VKLongPollManager getVkLongPollManager() {
        return vkLongPollManager;
    }

    public CoreManagers getCoreManagers() {
        return coreManagers;
    }

    public VKFeaturesManager getVkFeaturesManager() {
        return vkFeaturesManager;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public GuiListener getGuiListener() {
        return guiListener;
    }

    public BloodMoonManager getBloodMoonManager() {
        return bloodMoonManager;
    }

    public VKChatAPI getApi() {
        return api;
    }

    public WarnManager getWarnManager() {
        return warnManager;
    }

    public MembershipManager getMembershipManager() {
        return membershipManager;
    }

    public ru.example.vkchat.auth.SessionManager getSessionManager() {
        return coreManagers != null ? coreManagers.getAuthManager().getSessionManager() : null;
    }

    public ru.example.vkchat.auth.TwoFactorManager getTwoFactorManager() {
        return coreManagers != null ? coreManagers.getAuthManager().getTwoFactorManager() : null;
    }

    public ru.example.vkchat.auth.AuthManager getAuthManager() {
        return coreManagers != null ? coreManagers.getAuthManager() : null;
    }

    public ru.example.vkchat.reputation.ReputationManager getReputationManager() {
        return coreManagers != null ? coreManagers.getReputationManager() : null;
    }

    public ru.example.vkchat.managers.ChatManager getChatManager() {
        return coreManagers != null ? coreManagers.getChatManager() : null;
    }

    public ru.example.vkchat.stats.StatsManager getStatsManager() {
        return coreManagers != null ? coreManagers.getStatsManager() : null;
    }

    public VKLongPollManager getVkManager() {
        return vkLongPollManager;
    }

    public ru.example.vkchat.vk.GamesManager getGamesManager() {
        return vkFeaturesManager != null ? vkFeaturesManager.getGamesManager() : null;
    }

    public ru.example.vkchat.vk.MiniGamesManager getMiniGamesManager() {
        return vkFeaturesManager != null ? vkFeaturesManager.getMiniGamesManager() : null;
    }

    public ru.example.vkchat.vk.RiddleManager getRiddleManager() {
        return vkFeaturesManager != null ? vkFeaturesManager.getRiddleManager() : null;
    }

    public void reloadAll() {
        reloadConfig();
        configManager = new ConfigManager(this);
    }
}
