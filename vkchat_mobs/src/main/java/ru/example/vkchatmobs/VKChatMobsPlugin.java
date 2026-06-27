package ru.example.vkchatmobs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmobs.data.ContractManager;
import ru.example.vkchatmobs.siege.SiegeManager;
import ru.example.vkchatmobs.commands.MobCommand;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatmobs.managers.HardcoreMobManager;
import ru.example.vkchatmobs.managers.MobsEvents2Manager;


public class VKChatMobsPlugin extends JavaPlugin {
    private static VKChatMobsPlugin instance;
    private ContractManager contractManager;
    private SiegeManager siegeManager;
    private HardcoreMobManager hardcoreMobManager;
    private MobsEvents2Manager events2Manager;
    

    private void migrateConfigDefaults() {
        try {
            if (getConfig().getDefaults() == null) return;
            boolean hasMissing = false;
            for (String key : getConfig().getDefaults().getKeys(true)) {
                if (!getConfig().isSet(key)) {
                    hasMissing = true;
                    break;
                }
            }
            if (!hasMissing) return;

            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-before-migration-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Создан бэкап старого config.yml: " + backup.getName());
            }

            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml автоматически обновлён: недостающие ключи добавлены, существующие значения сохранены.");
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
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация менеджеров
        contractManager = new ContractManager(this);
        siegeManager = new SiegeManager(this);

        // Регистрация команд
        MobCommand cmd = new MobCommand(this, contractManager);
        getCommand("mobs").setExecutor(cmd);
        getCommand("mobs").setTabCompleter(cmd);
        getCommand("contract").setExecutor(cmd);
        getCommand("contract").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(cmd, this);

        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        hardcoreMobManager = new HardcoreMobManager(this);
        getServer().getPluginManager().registerEvents(hardcoreMobManager, this);
        events2Manager = new MobsEvents2Manager(this);
        getServer().getPluginManager().registerEvents(events2Manager, this);
        getLogger().info("VKChatMobs (Hardcore RPG Mobs + Осады + Контракты) успешно запущен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("VKChatMobs успешно выключен.");
    }

    public static VKChatMobsPlugin getInstance() {
        return instance;
    }

    public ContractManager getContractManager() {
        return contractManager;
    }

    public SiegeManager getSiegeManager() {
        return siegeManager;
    }

    public HardcoreMobManager getHardcoreMobManager() {
        return hardcoreMobManager;
    }

    public MobsEvents2Manager getEvents2Manager() {
        return events2Manager;
    }
}
