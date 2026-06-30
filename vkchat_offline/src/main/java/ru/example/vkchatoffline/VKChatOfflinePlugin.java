package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.managers.*;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.combat.CombatManager;
import ru.example.vkchatoffline.character.CharacterManager;
import ru.example.vkchatoffline.character.SkillTreeManager;
import ru.example.vkchatoffline.loot.LootManager;
import ru.example.vkchatoffline.campaign.CampaignManager;
import ru.example.vkchatoffline.rewards.RewardManager;

/**
 * VKChatOffline v3.0 — Текстовая MMORPG через ВК
 * 
 * Полностью переписанная система офлайн-походов с:
 * - Пошаговыми боями (3-5 раундов)
 * - Системой характеристик (STR, DEX, INT, WIS, CON, CHA)
 * - Древом навыков (3 ветки, 15 способностей)
 * - Системой лута с предметами для сервера
 * - Кампанией из 13 глав
 * - 50+ типами событий
 * - 8 классами и 8 спутниками
 */
public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    
    // Основные менеджеры
    private StashManager stashManager;
    private AdventureManager adventureManager;
    private ShiftManager shiftManager;
    private OfflineListener offlineListener;
    
    // Новые MMORPG системы
    private CombatManager combatManager;
    private CharacterManager characterManager;
    private SkillTreeManager skillTreeManager;
    private LootManager lootManager;
    private CampaignManager campaignManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! VKChatOffline выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация основных менеджеров
        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);
        shiftManager = new ShiftManager(this);
        offlineListener = new OfflineListener(this);

        // Инициализация MMORPG систем
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
        getLogger().info("VKChatOffline v3.0 — Текстовая MMORPG!");
        getLogger().info("Персонажей загружено: " + characterManager.getCharacterCount());
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

    // Геттеры
    public static VKChatOfflinePlugin getInstance() { return instance; }
    public StashManager getStashManager() { return stashManager; }
    public AdventureManager getAdventureManager() { return adventureManager; }
    public ShiftManager getShiftManager() { return shiftManager; }
    public OfflineListener getOfflineListener() { return offlineListener; }
    public CombatManager getCombatManager() { return combatManager; }
    public CharacterManager getCharacterManager() { return characterManager; }
    public SkillTreeManager getSkillTreeManager() { return skillTreeManager; }
    public LootManager getLootManager() { return lootManager; }
    public CampaignManager getCampaignManager() { return campaignManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
