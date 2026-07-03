package ru.example.vkchatevents;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatevents.managers.*;
import ru.example.vkchatevents.tasks.*;
import ru.example.vkchatevents.commands.*;

/**
 * VKChatEvents v3.0 — система серверных событий
 * 
 * ═══ УПРАВЛЯЮЩИЕ МЕНЕДЖЕРЫ ═══
 * 1. BountyManager — контракты на убийство
 * 2. QuestManager — сюжетные квесты
 * 3. InvasionManager — вторжение из Бездны
 * 4. WrathManager — катаклизмы и боссы
 * 5. DailyRewardManager — ежедневные награды
 * 6. ChallengeManager — ежедневные/недельные испытания
 * 7. EventShopManager — магазин событий
 * 8. AchievementManager — достижения событий
 * 9. LeaderboardManager — таблицы лидеров
 * 10. StatisticsManager — статистика событий
 * 11. VotingManager — голосование за события
 * 12. ComboManager — комбо-система
 * 13. ActivityManager — активность игрока
 * 14. CombatManager — боевая статистика
 * 15. SocialManager — социальные взаимодействия
 * 16. EvolutionManager — эволюция персонажа
 */
public class VKChatEventsPlugin extends JavaPlugin {
    private static VKChatEventsPlugin instance;
    
    // Существующие менеджеры
    private BountyManager bountyManager;
    private QuestManager questManager;
    private InvasionManager invasionManager;
    private WrathManager wrathManager;
    
    // Новые менеджеры
    private DailyRewardManager dailyRewardManager;
    private ChallengeManager challengeManager;
    private EventShopManager eventShopManager;
    private AchievementManager achievementManager;
    private LeaderboardManager leaderboardManager;
    private StatisticsManager statisticsManager;
    private VotingManager votingManager;
    private ComboManager comboManager;
    private ActivityManager activityManager;
    private CombatManager combatManager;
    private SocialManager socialManager;
    private EvolutionManager evolutionManager;

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

        // Инициализация существующих менеджеров
        bountyManager = new BountyManager(this);
        questManager = new QuestManager(this);
        invasionManager = new InvasionManager(this);
        wrathManager = new WrathManager(this);

        // Инициализация новых менеджеров
        dailyRewardManager = new DailyRewardManager(this);
        challengeManager = new ChallengeManager(this);
        eventShopManager = new EventShopManager(this);
        achievementManager = new AchievementManager(this);
        leaderboardManager = new LeaderboardManager(this);
        statisticsManager = new StatisticsManager(this);
        votingManager = new VotingManager(this);
        comboManager = new ComboManager(this);
        activityManager = new ActivityManager(this);
        combatManager = new CombatManager(this);
        socialManager = new SocialManager(this);
        evolutionManager = new EvolutionManager(this);

        // Регистрация событий
        getServer().getPluginManager().registerEvents(wrathManager, this);
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(bountyManager, this);
        getServer().getPluginManager().registerEvents(invasionManager, this);
        getServer().getPluginManager().registerEvents(dailyRewardManager, this);
        getServer().getPluginManager().registerEvents(challengeManager, this);
        getServer().getPluginManager().registerEvents(statisticsManager, this);
        getServer().getPluginManager().registerEvents(activityManager, this);
        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(socialManager, this);
        getServer().getPluginManager().registerEvents(evolutionManager, this);

        // Регистрация команд
        EventsCommand eventsCommand = new EventsCommand(this);
        getCommand("events").setExecutor(eventsCommand);
        getCommand("events").setTabCompleter(eventsCommand);

        // Запуск задач
        int reminderSec = getConfig().getInt("reminders.interval", 600);
        long reminderTicks = reminderSec * 20L;
        new ReminderTask(this).runTaskTimer(this, reminderTicks, reminderTicks);

        getLogger().info("--------------------------------------------------");
        getLogger().info("VKChatEvents v3.0 (35 обновлений) ЗАПУЩЕН!");
        getLogger().info("Проект: https://vk.com/chrdk_reborn");
        getLogger().info("--------------------------------------------------");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        if (statisticsManager != null) statisticsManager.save();
    }

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
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Бэкап конфига: " + backup.getName());
            }

            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml обновлён.");
        } catch (Exception e) {
            getLogger().warning("Ошибка миграции config.yml: " + e.getMessage());
        }
    }

    // Геттеры
    public static VKChatEventsPlugin getInstance() { return instance; }
    public BountyManager getBountyManager() { return bountyManager; }
    public QuestManager getQuestManager() { return questManager; }
    public InvasionManager getInvasionManager() { return invasionManager; }
    public WrathManager getWrathManager() { return wrathManager; }
    public DailyRewardManager getDailyRewardManager() { return dailyRewardManager; }
    public ChallengeManager getChallengeManager() { return challengeManager; }
    public EventShopManager getEventShopManager() { return eventShopManager; }
    public AchievementManager getAchievementManager() { return achievementManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public StatisticsManager getStatisticsManager() { return statisticsManager; }
    public VotingManager getVotingManager() { return votingManager; }
    public ComboManager getComboManager() { return comboManager; }
    public ActivityManager getActivityManager() { return activityManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public SocialManager getSocialManager() { return socialManager; }
    public EvolutionManager getEvolutionManager() { return evolutionManager; }
}
