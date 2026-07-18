package ru.example.vkchatmobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.core.ConfigMigrationUtil;
import ru.example.vkchatmobs.boss.BossAbilityRegistry;
import ru.example.vkchatmobs.bestiary.BestiaryManager;
import ru.example.vkchatmobs.bestiary.BestiaryListener;
import ru.example.vkchatmobs.bestiary.BestiaryGuiListener;
import ru.example.vkchatmobs.data.ContractManager;
import ru.example.vkchatmobs.drop.MobDropFactory;
import ru.example.vkchatmobs.siege.SiegeManager;
import ru.example.vkchatmobs.commands.MobCommand;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatmobs.managers.HardcoreMobManager;
import ru.example.vkchatmobs.managers.MobsEvents2Manager;
import ru.example.vkchatmobs.managers.MobStormManager;
import ru.example.vkchatmobs.enhancements.MobEnhancements;
import ru.example.vkchatmobs.tracking.CooldownManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VKChatMobsPlugin — главный класс плагина мобов.
 *
 * Фаза 6 рефакторинг:
 * - BossAbilityRegistry: конфиг-управляемые супер-боссы
 * - MobDropFactory: выделенная фабрика лута и репутации
 * - CooldownManager: инкапсулированные кулдауны и антифарм
 * - BloodMoonHelper: единая точка проверки Кровавой Луны
 * - VKChatBridge: поддержка проходки (pass holders)
 * - HardcoreMobManager: архетипы/стихии из конфига
 */
public class VKChatMobsPlugin extends JavaPlugin {
    private static VKChatMobsPlugin instance;
    private ContractManager contractManager;
    private SiegeManager siegeManager;
    private HardcoreMobManager hardcoreMobManager;
    private MobsEvents2Manager events2Manager;
    private MobStormManager mobStormManager;
    private BossAbilityRegistry bossAbilityRegistry;
    private MobDropFactory mobDropFactory;
    private CooldownManager cooldownManager;
    private MobEnhancements mobEnhancements;
    private BestiaryManager bestiaryManager;

    private org.bukkit.plugin.Plugin gearPlugin;
    private org.bukkit.plugin.Plugin artifactsPlugin;
    private org.bukkit.plugin.Plugin nationsPlugin;

    private void migrateConfigDefaults() {
        ConfigMigrationUtil.migrateDefaults(getConfig(), new java.io.File(getDataFolder(), "config.yml"), "config-version", getLogger());
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

        gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        artifactsPlugin = Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
        nationsPlugin = Bukkit.getPluginManager().getPlugin("VKChatNations");

        if (gearPlugin != null && gearPlugin.isEnabled()) {
            getLogger().info("VKChatGear обнаружен — крафт сетов/кристаллов доступен.");
        } else {
            getLogger().warning("VKChatGear не найден — дроп фрагментов сетов и кристаллов отключён.");
        }
        if (artifactsPlugin != null && artifactsPlugin.isEnabled()) {
            getLogger().info("VKChatArtifacts обнаружен — артефакты доступны.");
        } else {
            getLogger().warning("VKChatArtifacts не найден — генерация артефактов отключена.");
        }
        if (nationsPlugin != null && nationsPlugin.isEnabled()) {
            getLogger().info("VKChatNations обнаружен — осады и нации доступны.");
        } else {
            getLogger().warning("VKChatNations не найден — осады на приваты отключены.");
        }

        ru.example.vkchat.util.VKChatBridge.init();

        // --- Фаза 6: Новые компоненты ---
        cooldownManager = new CooldownManager();
        mobEnhancements = new MobEnhancements(this);
        mobDropFactory = new MobDropFactory(this);
        bossAbilityRegistry = new BossAbilityRegistry(this);

        // Бестиарий
        bestiaryManager = new BestiaryManager(this);
        bestiaryManager.load();

        // Попробовать загрузить боссов из конфига (если определены)
        bossAbilityRegistry.loadFromConfig();

        // Настроить кулдауны из конфига
        cooldownManager.setSuperBossCooldownMs(
                getConfig().getLong("scaling.super-boss-cooldown-ms", 600000L));
        cooldownManager.setVkMsgCooldownMs(
                getConfig().getLong("bosses.vk-msg-cooldown-ms", 5000L));

        contractManager = new ContractManager(this);
        siegeManager = new SiegeManager(this);

        // Регистрация команд
        MobCommand cmd = new MobCommand(this, contractManager);
        if (getCommand("mobs") != null) {
            getCommand("mobs").setExecutor(cmd);
            getCommand("mobs").setTabCompleter(cmd);
        }
        if (getCommand("contract") != null) {
            getCommand("contract").setExecutor(cmd);
            getCommand("contract").setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(cmd, this);

        MobListener listener = new MobListener(this, cooldownManager, mobDropFactory, bossAbilityRegistry);
        getServer().getPluginManager().registerEvents(listener, this);
        hardcoreMobManager = new HardcoreMobManager(this);
        getServer().getPluginManager().registerEvents(hardcoreMobManager, this);
        events2Manager = new MobsEvents2Manager(this);
        getServer().getPluginManager().registerEvents(events2Manager, this);
        mobStormManager = new MobStormManager(this);
        getServer().getPluginManager().registerEvents(mobStormManager, this);

        // Бестиарий
        getServer().getPluginManager().registerEvents(new BestiaryListener(this), this);
        getServer().getPluginManager().registerEvents(new BestiaryGuiListener(this), this);

        // Чистка карт памяти каждые 5 минут
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            listener.cleanupMaps(now);
            mobEnhancements.cleanup();
            bestiaryManager.save();
        }, 6000L, 6000L);

        getLogger().info("VKChatMobs v3.2.0 (Hardcore RPG Mobs + Осады + Контракты + Шторм + BossRegistry) успешно запущен!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (siegeManager != null) siegeManager.shutdown();
        getLogger().info("VKChatMobs успешно выключен.");
        instance = null;
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

    public MobStormManager getMobStormManager() {
        return mobStormManager;
    }

    public BossAbilityRegistry getBossAbilityRegistry() {
        return bossAbilityRegistry;
    }

    public MobDropFactory getMobDropFactory() {
        return mobDropFactory;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public MobEnhancements getMobEnhancements() {
        return mobEnhancements;
    }

    public BestiaryManager getBestiaryManager() {
        return bestiaryManager;
    }

    public org.bukkit.plugin.Plugin getGearPlugin() {
        return gearPlugin;
    }

    public org.bukkit.plugin.Plugin getArtifactsPlugin() {
        return artifactsPlugin;
    }

    public org.bukkit.plugin.Plugin getNationsPlugin() {
        return nationsPlugin;
    }

    public boolean isGearAvailable() {
        return gearPlugin != null && gearPlugin.isEnabled();
    }

    public boolean isArtifactsAvailable() {
        return artifactsPlugin != null && artifactsPlugin.isEnabled();
    }

    public boolean isNationsAvailable() {
        return nationsPlugin != null && nationsPlugin.isEnabled();
    }

    public static ItemStack createSetFragment(VKChatMobsPlugin plugin) {
        org.bukkit.plugin.Plugin gear = plugin.getGearPlugin();
        if (gear == null) return null;
        List<String> sets = new ArrayList<>(plugin.getConfig().getStringList("hardcore-mobs.rewards.set-fragments"));
        if (sets.isEmpty()) sets.addAll(Arrays.asList("bogatyr", "sokol", "volhv", "koshchey", "tankist", "udarnik"));
        String set = sets.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(sets.size()));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String setName = plugin.getConfig().getString("hardcore-mobs.rewards.set-fragment-names." + set, set);
        meta.setDisplayName(ChatColor.GOLD + "Фрагмент сета: " + setName);
        meta.setCustomModelData(59);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Трофей элитной охоты.", ChatColor.GRAY + "Используется при ковке брони в VKChatGear."));
        meta.getPersistentDataContainer().set(new NamespacedKey(gear, "set_fragment"), PersistentDataType.STRING, set);
        item.setItemMeta(meta);
        return item;
    }
}
