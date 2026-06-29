package ru.example.vkchatevents;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatevents.managers.BountyManager;
import ru.example.vkchatevents.managers.QuestManager;
import ru.example.vkchatevents.managers.InvasionManager;
import ru.example.vkchatevents.managers.WrathManager;
import ru.example.vkchatevents.tasks.ReminderTask;


public class VKChatEventsPlugin extends JavaPlugin {
    private static VKChatEventsPlugin instance;
    private BountyManager bountyManager;
    private QuestManager questManager;
    private InvasionManager invasionManager;
    private WrathManager wrathManager;


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

        bountyManager = new BountyManager(this);
        questManager = new QuestManager(this);
        invasionManager = new InvasionManager(this);
        wrathManager = new WrathManager(this);
        
        getServer().getPluginManager().registerEvents(wrathManager, this);
        
        // Напоминания об активных событиях на сервере и в ВК
        int reminderSec = getConfig().getInt("reminders.interval", 600);
        long reminderTicks = reminderSec * 20L;
        new ReminderTask(this).runTaskTimer(this, reminderTicks, reminderTicks);
        
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(bountyManager, this);
        getServer().getPluginManager().registerEvents(invasionManager, this);

        // Регистрация интерактивного дашборда событий /events
        ru.example.vkchatevents.commands.EventsCommand eventsCommand = new ru.example.vkchatevents.commands.EventsCommand(this);
        getCommand("events").setExecutor(eventsCommand);

        getLogger().info("--------------------------------------------------");
        getLogger().info("VKChatEvents (Квесты, Баунти, Вторжения) ЗАПУЩЕН!");
        getLogger().info("Проект: https://vk.com/chrdk_reborn");
        getLogger().info("Телеграм: https://t.me/cherdakmd");
        getLogger().info("--------------------------------------------------");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
    }

    public static VKChatEventsPlugin getInstance() { return instance; }
    public BountyManager getBountyManager() { return bountyManager; }
    public QuestManager getQuestManager() { return questManager; }
    public InvasionManager getInvasionManager() { return invasionManager; }
    public WrathManager getWrathManager() { return wrathManager; }
}
