package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmarket.commands.MarketAdminCommand;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.commands.ShopCommand;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.gui.PlayerGuiState;
import ru.example.vkchatmarket.listener.MarketListener;
import ru.example.vkchatmarket.log.TransactionLog;
import ru.example.vkchatmarket.playerShop.PlayerShopManager;
import ru.example.vkchatmarket.playerShop.ShopListener;
import ru.example.vkchatmarket.prompt.PlayerPromptService;
import ru.example.vkchatmarket.service.MarketService;
import ru.example.vkchatmarket.providers.MarketMotdProvider;
import ru.example.vkchat.api.MotdProviderRegistry;

import java.io.File;
import java.io.IOException;

public class VKChatMarketPlugin extends JavaPlugin {
    private static VKChatMarketPlugin instance;
    private MarketService marketService;
    private PlayerPromptService promptService;
    private PlayerGuiState guiState;
    private TransactionLog transactionLog;
    private PlayerShopManager playerShopManager;
    private ShopListener shopListener;

    private FileConfiguration categoriesConfig;
    private FileConfiguration eventsConfig;
    private FileConfiguration settingsConfig;
    private File categoriesFile;
    private File eventsFile;
    private File settingsFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        saveSubConfig("categories.yml");
        saveSubConfig("events.yml");
        saveSubConfig("settings.yml");
        loadSubConfigs();
        migrateOldConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        promptService = new PlayerPromptService();
        guiState = new PlayerGuiState();
        transactionLog = new TransactionLog(new File(getDataFolder(), "transactions"));
        transactionLog.logSystem("Server started");

        marketService = new MarketService(this);
        marketService.setTransactionLog(transactionLog);
        marketService.load();

        MotdProviderRegistry.register(new MarketMotdProvider(this));

        MarketListener listener = new MarketListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        MarketCommand marketCmd = new MarketCommand(this);
        if (getCommand("market") != null) {
            getCommand("market").setExecutor(marketCmd);
            getCommand("market").setTabCompleter(marketCmd);
        }

        MarketAdminCommand adminCmd = new MarketAdminCommand(this);
        if (getCommand("mkta") != null) {
            getCommand("mkta").setExecutor(adminCmd);
            getCommand("mkta").setTabCompleter(adminCmd);
        }

        playerShopManager = new PlayerShopManager(this);
        playerShopManager.load();

        shopListener = new ShopListener(this);
        getServer().getPluginManager().registerEvents(shopListener, this);

        ShopCommand shopCmd = new ShopCommand(this);
        if (getCommand("shop") != null) {
            getCommand("shop").setExecutor(shopCmd);
            getCommand("shop").setTabCompleter(shopCmd);
        }

        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
                if (e.getRightClicked() instanceof org.bukkit.entity.Villager) {
                    org.bukkit.entity.Villager npc = (org.bukkit.entity.Villager) e.getRightClicked();
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(VKChatMarketPlugin.this, "market_npc");
                    if (npc.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                        e.setCancelled(true);
                        MarketGui.openMainMenu(VKChatMarketPlugin.this, e.getPlayer());
                    }
                }
            }
        }, this);

        int eventInterval = getEventsConfig().getInt("events.interval-minutes", 15) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> {
            marketService.prices().tryStartRandomEvent();
            marketService.prices().tickDecay();
            if (marketService.prices().hasActiveEvent()) {
                String name = marketService.prices().getActiveEventName();
                long remaining = (marketService.prices().getActiveEventEnd() - System.currentTimeMillis()) / 1000;
                for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) {
                    pl.sendMessage("§6§lБИРЖА §8▸ " + name + " §7(§e" + (remaining / 60) + " мин.§7)");
                }
            }
            if (marketService.prices().isEventExpiredJustNow()) {
                for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) {
                    pl.sendMessage("§6§lБИРЖА §8▸ §7Событие завершилось. Цены вернулись в норму.");
                }
            }
        }, 200L, eventInterval);

        getLogger().info("VKChatMarket v3.2.0 запущен! Товаров: " + marketService.getAll().size());
    }

    @Override
    public void onDisable() {
        if (transactionLog != null) transactionLog.logSystem("Server stopped");
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        instance = null;
    }

    // ═══ Sub-config management ═══

    private void saveSubConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }

    private void loadSubConfigs() {
        categoriesFile = new File(getDataFolder(), "categories.yml");
        eventsFile = new File(getDataFolder(), "events.yml");
        settingsFile = new File(getDataFolder(), "settings.yml");
        categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);
        eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);
        settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
    }

    public void reloadSubConfigs() {
        categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);
        eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);
        settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
    }

    private void migrateOldConfig() {
        FileConfiguration main = getConfig();
        boolean changed = false;

        // Migrate categories + items from main config
        if (main.contains("categories") || main.contains("items")) {
            if (main.contains("categories")) {
                categoriesConfig.set("categories", main.get("categories"));
                main.set("categories", null);
                changed = true;
            }
            if (main.contains("items")) {
                categoriesConfig.set("items", main.get("items"));
                main.set("items", null);
                changed = true;
            }
        }

        // Migrate events from main config
        if (main.contains("events")) {
            eventsConfig.set("events", main.get("events"));
            main.set("events", null);
            changed = true;
        }

        // Migrate settings + dynamics from main config
        if (main.contains("settings")) {
            settingsConfig.set("settings", main.get("settings"));
            main.set("settings", null);
            changed = true;
        }
        if (main.contains("dynamics")) {
            settingsConfig.set("dynamics", main.get("dynamics"));
            main.set("dynamics", null);
            changed = true;
        }

        if (changed) {
            try {
                categoriesConfig.save(categoriesFile);
                eventsConfig.save(eventsFile);
                settingsConfig.save(settingsFile);
                saveConfig();
                getLogger().info("Конфигурация мигрирована: старые ключи перемещены в под-файлы.");
            } catch (IOException e) {
                getLogger().severe("Ошибка миграции конфигурации: " + e.getMessage());
            }
        }
    }

    public FileConfiguration getCategoriesConfig() { return categoriesConfig; }
    public FileConfiguration getEventsConfig() { return eventsConfig; }
    public FileConfiguration getSettingsConfig() { return settingsConfig; }

    // ═══ Getters ═══

    public static VKChatMarketPlugin getInstance() { return instance; }
    public MarketService getMarketService() { return marketService; }
    public PlayerPromptService getPromptService() { return promptService; }
    public PlayerGuiState getGuiState() { return guiState; }
    public TransactionLog getTransactionLog() { return transactionLog; }
    public PlayerShopManager getPlayerShopManager() { return playerShopManager; }
    public ShopListener getShopListener() { return shopListener; }
}
