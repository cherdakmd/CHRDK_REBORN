package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.data.MarketFun;
import ru.example.vkchatmarket.data.MarketManager;
import ru.example.vkchatmarket.listeners.MarketGuiListener;

public class VKChatMarketPlugin extends JavaPlugin {
    private static VKChatMarketPlugin instance;
    private MarketManager marketManager;
    private MarketFun marketFun;


    private static final int CONFIG_VERSION = 2; // Увеличивать при изменении критических значений

    private void migrateConfigDefaults() {
        try {
            reloadConfig();
            java.io.InputStream defStream = getResource("config.yml");
            if (defStream == null) return;
            org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
            getConfig().setDefaults(defConfig);

            int currentVersion = getConfig().getInt("config-version", 0);
            boolean needsUpdate = false;

            // Проверка на новые ключи
            for (String key : defConfig.getKeys(true)) {
                if (!getConfig().isSet(key)) { needsUpdate = true; break; }
            }

            // Проверка версии конфига
            if (currentVersion < CONFIG_VERSION) {
                needsUpdate = true;
                getLogger().info("Конфиг устарел (v" + currentVersion + " -> v" + CONFIG_VERSION + "), обновляю...");
            }

            if (!needsUpdate) return;

            // Создаём бэкап
            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Бэкап конфига: " + backup.getName());
            }

            // Принудительное обновление критических значений
            if (currentVersion < CONFIG_VERSION) {
                forceUpdateCriticalValues(defConfig);
            }

            getConfig().options().copyDefaults(true);
            getConfig().set("config-version", CONFIG_VERSION);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml обновлён до v" + CONFIG_VERSION);
        } catch (Exception e) {
            getLogger().warning("Ошибка миграции config.yml: " + e.getMessage());
        }
    }

    private void forceUpdateCriticalValues(org.bukkit.configuration.file.YamlConfiguration defConfig) {
        // Принудительно обновляем эти ключи при смене версии
        String[] forceUpdateKeys = {
            "market2.donate.sell-multiplier.legend",
            "market2.donate.buy-multiplier.legend",
            "market2.donate.sell-multiplier.star",
            "market2.donate.buy-multiplier.star",
            "market2.trade-impact",
        };

        for (String key : forceUpdateKeys) {
            if (defConfig.isSet(key)) {
                getConfig().set(key, defConfig.get(key));
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaults();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        marketManager = new MarketManager(this);
        marketFun = new MarketFun(this);
        
        MarketCommand marketCmd = new MarketCommand(this);
        getCommand("market").setExecutor(marketCmd);
        getCommand("market").setTabCompleter(marketCmd);
        getServer().getPluginManager().registerEvents(new MarketGuiListener(this), this);

        long interval = getConfig().getLong("settings.recovery-interval", 1200) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> marketManager.recoverMarket(), interval, interval);
        
        // Каждые 30 минут запускаем проверку случайных событий на бирже
        getServer().getScheduler().runTaskTimer(this, () -> marketManager.checkForRandomEvent(), 1200L, 36000L);
        
        // Каждые 10 минут проверяем Flash Sale
        getServer().getScheduler().runTaskTimer(this, () -> marketFun.checkFlashSale(), 1200L, 12000L);
        
        // Квест дня обновляется при старте
        marketFun.ensureDailyQuest();

        getLogger().info("VKChatMarket успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (marketManager != null) {
            marketManager.saveAll();
        }
    }

    public static VKChatMarketPlugin getInstance() {
        return instance;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public MarketFun getMarketFun() {
        return marketFun;
    }
}
