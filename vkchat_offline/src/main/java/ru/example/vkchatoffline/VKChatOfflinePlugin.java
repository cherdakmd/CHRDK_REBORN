package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.managers.AdventureManager;
import ru.example.vkchatoffline.managers.ShiftManager;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.combat.CombatManager;
import ru.example.vkchatoffline.character.CharacterManager;
import ru.example.vkchatoffline.character.SkillTreeManager;
import ru.example.vkchatoffline.loot.LootManager;
import ru.example.vkchatoffline.campaign.CampaignManager;
import ru.example.vkchatoffline.rewards.RewardManager;

/**
 * VKChatOffline v3.0 — Текстовая MMORPG через ВК
 */
public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private StashManager stashManager;
    private AdventureManager adventureManager;
    private ShiftManager shiftManager;
    private OfflineListener offlineListener;
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

        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);
        shiftManager = new ShiftManager(this);
        offlineListener = new OfflineListener(this);
        combatManager = new CombatManager(this);
        characterManager = new CharacterManager();
        skillTreeManager = new SkillTreeManager();
        lootManager = new LootManager();
        campaignManager = new CampaignManager();
        rewardManager = new RewardManager(this);

        getServer().getPluginManager().registerEvents(adventureManager, this);
        getServer().getPluginManager().registerEvents(offlineListener, this);
        getCommand("stash").setExecutor(new StashCommand(this));

        getLogger().info("VKChatOffline v3.0 — Текстовая MMORPG!");
    }

    @Override
    public void onDisable() {
        if (adventureManager != null) adventureManager.saveAll();
        if (stashManager != null) stashManager.save();
    }

    public static VKChatOfflinePlugin getInstance() { return instance; }
    public StashManager getStashManager() { return stashManager; }
    public AdventureManager getAdventureManager() { return adventureManager; }
    public ShiftManager getShiftManager() { return shiftManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public CharacterManager getCharacterManager() { return characterManager; }
    public SkillTreeManager getSkillTreeManager() { return skillTreeManager; }
    public LootManager getLootManager() { return lootManager; }
    public CampaignManager getCampaignManager() { return campaignManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
