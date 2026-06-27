package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.managers.AdventureManager;
import ru.example.vkchatoffline.managers.AdventureCommandManager;
import ru.example.vkchatoffline.managers.ShiftManager;
import ru.example.vkchatoffline.listeners.OfflineListener;

public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private StashManager stashManager;
    private AdventureManager adventureManager;
    private AdventureCommandManager adventureCommandManager;
    private ShiftManager shiftManager;
    private OfflineListener offlineListener;


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
            getLogger().severe("VKChat не найден! VKChatOffline выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);
        adventureCommandManager = new AdventureCommandManager(this);
        shiftManager = new ShiftManager(this);
        offlineListener = new OfflineListener(this);

        getServer().getPluginManager().registerEvents(adventureManager, this);
        getServer().getPluginManager().registerEvents(offlineListener, this);
        getCommand("stash").setExecutor(new StashCommand(this));

        getLogger().info("VKChatOffline перезаписан с нуля: VK-only DM походы + stash наград.");
    }

    @Override
    public void onDisable() {
        if (adventureManager != null) adventureManager.saveAll();
        if (stashManager != null) stashManager.save();
        getServer().getScheduler().cancelTasks(this);
    }

    public static VKChatOfflinePlugin getInstance() { return instance; }
    public StashManager getStashManager() { return stashManager; }
    public AdventureManager getAdventureManager() { return adventureManager; }
    public AdventureCommandManager getAdventureCommandManager() { return adventureCommandManager; }
    public ShiftManager getShiftManager() { return shiftManager; }
    public OfflineListener getOfflineListener() { return offlineListener; }
}
