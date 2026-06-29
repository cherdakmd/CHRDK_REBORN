package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.data.MarketFun;
import ru.example.vkchatmarket.data.MarketManager;
import ru.example.vkchatmarket.listeners.MarketGuiListener;
import ru.example.vkchatmarket.listeners.VKRouletteListener;

public class VKChatMarketPlugin extends JavaPlugin {
    private static VKChatMarketPlugin instance;
    private MarketManager marketManager;
    private MarketFun marketFun;
    private VKRouletteListener vkRouletteListener;


    private void migrateConfigDefaults() {
        try {
            reloadConfig();
            java.io.InputStream defStream = getResource("config.yml");
            if (defStream == null) return;
            org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
            getConfig().setDefaults(defConfig);
            boolean hasMissing = false;
            for (String key : defConfig.getKeys(true)) {
                if (!getConfig().isSet(key)) { hasMissing = true; break; }
            }
            if (!hasMissing) return;
            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-before-migration-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml автоматически обновлён: недостающие ключи Market 2.0 добавлены.");
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить авто-миграцию config.yml: " + e.getMessage());
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
        vkRouletteListener = new VKRouletteListener(this);
        getServer().getPluginManager().registerEvents(vkRouletteListener, this);

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

    public VKRouletteListener getVKRouletteListener() {
        return vkRouletteListener;
    }
}
