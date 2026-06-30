package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.managers.AdventureManager;
import ru.example.vkchatoffline.managers.AdventureCommandManager;
import ru.example.vkchatoffline.managers.ShiftManager;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.combat.CombatManager;
import ru.example.vkchatoffline.character.CharacterManager;
import ru.example.vkchatoffline.character.SkillTreeManager;
import ru.example.vkchatoffline.loot.LootManager;
import ru.example.vkchatoffline.campaign.CampaignManager;
import ru.example.vkchatoffline.rewards.RewardManager;

public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private StashManager stashManager;
    private AdventureManager adventureManager;
    private AdventureCommandManager adventureCommandManager;
    private ShiftManager shiftManager;
    private OfflineListener offlineListener;
    private CombatManager combatManager;
    private CharacterManager characterManager;
    private SkillTreeManager skillTreeManager;
    private LootManager lootManager;
    private CampaignManager campaignManager;
    private RewardManager rewardManager;

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

        // Инициализация менеджеров
        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);
        adventureCommandManager = new AdventureCommandManager(this);
        shiftManager = new ShiftManager(this);
        offlineListener = new OfflineListener(this);

        // Новые менеджеры MMORPG
        combatManager = new CombatManager(this);
        characterManager = new CharacterManager(this);
        skillTreeManager = new SkillTreeManager(this);
        lootManager = new LootManager(this);
        campaignManager = new CampaignManager(this);
        rewardManager = new RewardManager(this);

        // Регистрация событий
        getServer().getPluginManager().registerEvents(adventureManager, this);
        getServer().getPluginManager().registerEvents(offlineListener, this);

        // Регистрация команд
        getCommand("stash").setExecutor(new StashCommand(this));

        getLogger().info("═══════════════════════════════════════");
        getLogger().info("VKChatOffline v2.0 — Текстовая MMORPG!");
        getLogger().info("Бои: " + combatManager.getActiveCombatCount());
        getLogger().info("Персонажи: " + characterManager.getCharacterCount());
        getLogger().info("Навыки: " + skillTreeManager.getSkillCount());
        getLogger().info("Кампания: " + campaignManager.getChapterCount() + " глав");
        getLogger().info("Лут: " + lootManager.getLootItemCount() + " предметов");
        getLogger().info("═══════════════════════════════════════");
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
    public CombatManager getCombatManager() { return combatManager; }
    public CharacterManager getCharacterManager() { return characterManager; }
    public SkillTreeManager getSkillTreeManager() { return skillTreeManager; }
    public LootManager getLootManager() { return lootManager; }
    public CampaignManager getCampaignManager() { return campaignManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
