package ru.example.vkchatgear;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatgear.listeners.CraftListener;
import ru.example.vkchatgear.listeners.CombatListener;
import ru.example.vkchatgear.listeners.MechanicsListener;
import ru.example.vkchatgear.listeners.SynthesisListener;
import ru.example.vkchatgear.commands.ForgeCommand;
import ru.example.vkchatgear.commands.GearAdminCommand;
import ru.example.vkchatgear.runes.RuneCommand;
import ru.example.vkchatgear.runes.RuneListener;
import ru.example.vkchatgear.forge.ForgeLogger;
import ru.example.vkchatgear.forge.SetBonusManager;
import ru.example.vkchatgear.runes.RuneRegistry;
import ru.example.vkchatgear.providers.GearMotdProvider;
import ru.example.vkchatgear.enhancements.GearEnhancements;
import ru.example.vkchatgear.artifacts.ArtifactRegistry;
import ru.example.vkchatgear.artifacts.ArtifactsManager;
import ru.example.vkchatgear.artifacts.ArtifactsCommand;
import ru.example.vkchat.api.MotdProviderRegistry;
import ru.example.vkchat.core.ConfigMigrationUtil;
import ru.example.vkchat.util.VKChatBridge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VKChatGearPlugin extends JavaPlugin {
    private static VKChatGearPlugin instance;
    private GearManager gearManager;
    private ru.example.vkchatgear.runes.RuneMarketManager runeMarketManager;
    private SetBonusManager setBonusManager;
    private ForgeLogger forgeLogger;
    private RuneRegistry runeRegistry;
    private GearEnhancements gearEnhancements;
    private ArtifactRegistry artifactRegistry;
    private ArtifactsManager artifactsManager;
    private int magicEventTaskId = -1;

    // Кэш пассивных эффектов: UUID -> Set<enchantKey>
    private final Map<UUID, Set<String>> passiveEffects = new ConcurrentHashMap<>();
    // Кэш upgrade_level предметов в руке: UUID -> int
    private final Map<UUID, Integer> mainHandUpgradeLevel = new ConcurrentHashMap<>();

    // Маппинг ключей пассивных эффектов на подстроки поиска в лоре
    private static final Map<String, String[]> PASSIVE_EFFECT_LORE_KEYS = new LinkedHashMap<>();
    static {
        PASSIVE_EFFECT_LORE_KEYS.put("haste", new String[]{"Спешка"});
        PASSIVE_EFFECT_LORE_KEYS.put("haste_aura", new String[]{"Аура Спешки"});
        PASSIVE_EFFECT_LORE_KEYS.put("aquatic_life", new String[]{"Подводная Жизнь"});
        PASSIVE_EFFECT_LORE_KEYS.put("magma_walker", new String[]{"Магматический Шаг"});
        PASSIVE_EFFECT_LORE_KEYS.put("wind_step", new String[]{"Поступь Ветра"});
        PASSIVE_EFFECT_LORE_KEYS.put("golem_skin", new String[]{"Кожа Голема"});
        PASSIVE_EFFECT_LORE_KEYS.put("spider_reflexes", new String[]{"Рефлексы Паука"});
        PASSIVE_EFFECT_LORE_KEYS.put("healing_aura", new String[]{"Аура Исцеления"});
    }

    // Sub-config files
    private YamlConfiguration enchantsConfig;
    private YamlConfiguration setsConfig;
    private YamlConfiguration runeMarketConfig;
    private YamlConfiguration forgeAdvancedConfig;

    // Магические события
    private String activeMagicEventName = null;
    private double activeMagicEventMultiplier = 1.0;
    private long activeMagicEventExpireTime = 0L;

    public String getActiveMagicEventName() { return activeMagicEventName; }
    public double getActiveMagicEventMultiplier() { return activeMagicEventMultiplier; }
    public long getActiveMagicEventExpireTime() { return activeMagicEventExpireTime; }


    private void migrateConfigDefaults() {
        ConfigMigrationUtil.migrateDefaults(getConfig(), new java.io.File(getDataFolder(), "config.yml"), "config-version", getLogger());
    }

    private void loadSubConfigs() {
        enchantsConfig = loadSubConfig("enchants.yml");
        setsConfig = loadSubConfig("sets.yml");
        runeMarketConfig = loadSubConfig("rune_market.yml");
        forgeAdvancedConfig = loadSubConfig("forge_advanced.yml");
        migrateOldSections();
    }

    private YamlConfiguration loadSubConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Миграция: если старые секции ещё есть в main config.yml — копируем их в под-файлы.
     */
    private void migrateOldSections() {
        boolean changed = false;

        // custom_enchants -> enchants.yml
        if (getConfig().isConfigurationSection("custom_enchants")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("custom_enchants");
            enchantsConfig.createSection("custom_enchants", sec.getValues(true));
            getConfig().set("custom_enchants", null);
            changed = true;
            getLogger().info("[Migration] custom_enchants перенесён в enchants.yml");
        }

        // sets -> sets.yml
        if (getConfig().isConfigurationSection("sets")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("sets");
            setsConfig.createSection("sets", sec.getValues(true));
            getConfig().set("sets", null);
            changed = true;
            getLogger().info("[Migration] sets перенесён в sets.yml");
        }

        // rune-cleansing, enhancements -> rune_market.yml
        boolean runeMarketChanged = false;
        if (getConfig().isConfigurationSection("rune-cleansing")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("rune-cleansing");
            for (String key : sec.getKeys(false)) {
                runeMarketConfig.set("rune-cleansing." + key, sec.get(key));
            }
            getConfig().set("rune-cleansing", null);
            runeMarketChanged = true;
            getLogger().info("[Migration] rune-cleansing перенесён в rune_market.yml");
        }
        if (getConfig().isConfigurationSection("enhancements")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("enhancements");
            for (String key : sec.getKeys(false)) {
                runeMarketConfig.set("enhancements." + key, sec.get(key));
            }
            getConfig().set("enhancements", null);
            runeMarketChanged = true;
            getLogger().info("[Migration] enhancements перенесён в rune_market.yml");
        }
        changed = changed || runeMarketChanged;

        // forge2 -> forge_advanced.yml
        if (getConfig().isConfigurationSection("forge2")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("forge2");
            for (String key : sec.getKeys(false)) {
                forgeAdvancedConfig.set("forge2." + key, sec.get(key));
            }
            getConfig().set("forge2", null);
            changed = true;
            getLogger().info("[Migration] forge2 перенесён в forge_advanced.yml");
        }

        if (changed) {
            saveConfig();
            reloadConfig();
            saveSubConfigs();
        }
    }

    private void saveSubConfigs() {
        saveSubConfig(enchantsConfig, "enchants.yml");
        saveSubConfig(setsConfig, "sets.yml");
        saveSubConfig(runeMarketConfig, "rune_market.yml");
        saveSubConfig(forgeAdvancedConfig, "forge_advanced.yml");
    }

    private void saveSubConfig(YamlConfiguration config, String fileName) {
        try {
            config.save(new File(getDataFolder(), fileName));
        } catch (Exception e) {
            getLogger().warning("Не удалось сохранить " + fileName + ": " + e.getMessage());
        }
    }

    public YamlConfiguration getEnchantsConfig() { return enchantsConfig; }
    public YamlConfiguration getSetsConfig() { return setsConfig; }
    public YamlConfiguration getRuneMarketConfig() { return runeMarketConfig; }
    public YamlConfiguration getForgeAdvancedConfig() { return forgeAdvancedConfig; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaults();
        loadSubConfigs();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        VKChatBridge.init();

        gearManager = new GearManager(this);
        runeMarketManager = new ru.example.vkchatgear.runes.RuneMarketManager(this);
        setBonusManager = new SetBonusManager(this);
        forgeLogger = new ForgeLogger(this);
        runeRegistry = new RuneRegistry(this);
        gearEnhancements = new GearEnhancements(this);
        artifactRegistry = new ArtifactRegistry(this);
        artifactsManager = new ArtifactsManager(this);
        
        CombatListener combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new MechanicsListener(this), this);
        getServer().getPluginManager().registerEvents(new SynthesisListener(this), this);
        ForgeCommand forgeCommand = new ForgeCommand(this);
        safeCommand("forge", forgeCommand);
        getServer().getPluginManager().registerEvents(forgeCommand, this);
        
        GearAdminCommand gearAdminCmd = new GearAdminCommand(this);
        safeCommand("gearadmin", gearAdminCmd);

        RuneCommand runeCmd = new RuneCommand(this);
        safeCommand("runes", runeCmd);
        
        ru.example.vkchatgear.commands.SalvageCommand salvageCmd = new ru.example.vkchatgear.commands.SalvageCommand(this);
        safeCommand("salvage", salvageCmd);
        getServer().getPluginManager().registerEvents(salvageCmd, this);
        
        getServer().getPluginManager().registerEvents(new RuneListener(this), this);

        ArtifactsCommand artifactsCmd = new ArtifactsCommand(this);
        safeCommand("artifacts", artifactsCmd);
        getServer().getPluginManager().registerEvents(artifactsCmd, this);

        // Чистка кулдаунов каждые 5 минут
        getServer().getScheduler().runTaskTimer(this, () -> {
            combatListener.cleanupCooldowns(System.currentTimeMillis());
        }, 6000L, 6000L);

        startPassiveTasks();

        // Регистрация listener'а обновления кэша пассивных эффектов
        getServer().getPluginManager().registerEvents(new PassiveEffectCacheListener(this), this);

        // Предзаполнение кэша для уже онлайн-игроков (при перезагрузке плагина)
        for (Player p : Bukkit.getOnlinePlayers()) {
            rebuildPassiveCache(p);
        }

        // Регистрация MOTD провайдера (без reflection)
        MotdProviderRegistry.register(new GearMotdProvider(this));

        // Каждые 30 минут запускаем проверку магических событий на руны и кристаллы
        magicEventTaskId = getServer().getScheduler().runTaskTimer(this, () -> checkForMagicEvent(), 1200L, 36000L).getTaskId();

        getLogger().info("VKChatGear (MMO-Крафт, Заточка, Сеты) успешно запущен!");
    }

    private void safeCommand(String name, org.bukkit.command.CommandExecutor executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter)
                getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) executor);
        } else {
            getLogger().warning("Команда '" + name + "' не найдена в plugin.yml");
        }
    }

    // ═══════════════════════════════════════════
    // КЭШИРОВАНИЕ ПАССИВНЫХ ЭФФЕКТОВ
    // ═══════════════════════════════════════════

    /**
     * Очистить кэш пассивных эффектов для игрока (при выходе).
     */
    public void clearPassiveCache(UUID uuid) {
        passiveEffects.remove(uuid);
        mainHandUpgradeLevel.remove(uuid);
    }

    /**
     * Пересобрать кэш пассивных эффектов для игрока (при входе/смене экипировки).
     */
    public void rebuildPassiveCache(Player p) {
        Set<String> effects = scanEquipmentForPassiveEffects(p);
        passiveEffects.put(p.getUniqueId(), effects);
        mainHandUpgradeLevel.put(p.getUniqueId(), readUpgradeLevel(p.getInventory().getItemInMainHand()));
    }

    /**
     * Проверить, есть ли у игрока пассивный эффект (из кэша, O(1)).
     */
    public boolean hasPassiveEffect(Player p, String effectKey) {
        Set<String> effects = passiveEffects.get(p.getUniqueId());
        return effects != null && effects.contains(effectKey);
    }

    /**
     * Получить upgrade_level предмета в руке из кэша.
     */
    public int getCachedUpgradeLevel(Player p) {
        return mainHandUpgradeLevel.getOrDefault(p.getUniqueId(), 0);
    }

    /**
     * Сканировать всю экипировку игрока и вернуть множество пассивных эффектов.
     * Пытается PDC-детекцию (custom_enchant_*), fallback — lore.
     */
    private Set<String> scanEquipmentForPassiveEffects(Player p) {
        Set<String> found = new HashSet<>();
        // Оружие/инструмент в руке
        scanItemForPassiveEffects(p.getInventory().getItemInMainHand(), found, true);
        // Броня
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            scanItemForPassiveEffects(armor, found, false);
        }
        return found;
    }

    /**
     * Сканировать один предмет на наличие пассивных эффектов.
     * Сначала проверяет PDC (custom_enchant_*), затем fallback на lore.
     */
    private void scanItemForPassiveEffects(ItemStack item, Set<String> found, boolean isMainHand) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || !item.hasItemMeta()) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // PDC-детекция: ищем ключи custom_enchant_*
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (String effectKey : PASSIVE_EFFECT_LORE_KEYS.keySet()) {
            NamespacedKey nk = new NamespacedKey(this, "custom_enchant_" + effectKey);
            if (pdc.has(nk, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                found.add(effectKey);
            }
        }

        // Lore-детекция (fallback): только если PDC не дал результат для данного эффекта
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String effectKey : PASSIVE_EFFECT_LORE_KEYS.keySet()) {
                if (found.contains(effectKey)) continue; // уже найдено через PDC
                String[] searchKeys = PASSIVE_EFFECT_LORE_KEYS.get(effectKey);
                for (String line : lore) {
                    String stripped = org.bukkit.ChatColor.stripColor(line);
                    for (String key : searchKeys) {
                        if (stripped.contains(key)) {
                            found.add(effectKey);
                            break;
                        }
                    }
                    if (found.contains(effectKey)) break;
                }
            }
        }
    }

    /**
     * Прочитать upgrade_level из PDC предмета.
     */
    private int readUpgradeLevel(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || !item.hasItemMeta()) return 0;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey upgradeKey = new NamespacedKey(this, "upgrade_level");
        return pdc.getOrDefault(upgradeKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
    }

    // ═══════════════════════════════════════════
    // ПАССИВНЫЕ ЗАДАЧИ (ОПТИМИЗИРОВАННЫЕ)
    // ═══════════════════════════════════════════

    private void startPassiveTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Пассивная Спешка (оружие/инструмент в руке) — из кэша
                if (hasPassiveEffect(p, "haste")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 40, 1, false, false, false));
                }

                // --- Постоянные шлейфы частиц для оружия высокого уровня заточки (+15 / +20) ---
                int upgradeLvl = getCachedUpgradeLevel(p);
                if (upgradeLvl >= 15) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand != null && hand.getType() != org.bukkit.Material.AIR && gearManager.isGear(hand.getType())) {
                        org.bukkit.Location loc = p.getLocation().add(0, 1.0, 0);
                        if (upgradeLvl >= 20) {
                            p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, loc, 2, 0.2, 0.2, 0.2, 0.05);
                            p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, loc, 1, 0.2, 0.2, 0.2, 0.02);
                        } else {
                            p.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc, 1, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }

                // Аура Спешки (броня) — из кэша
                if (hasPassiveEffect(p, "haste_aura")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, false, false, false));
                    for (org.bukkit.entity.Entity ent : p.getNearbyEntities(10, 10, 10)) {
                        if (ent instanceof Player) {
                            ((Player) ent).addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, false, false, false));
                        }
                    }
                }

                // Дефект "Тяжёлый" — проверяем PDC (hasDefect уже использует PDC, это быстро)
                boolean heavyDefect = false;
                if (gearManager.hasDefect(p.getInventory().getItemInMainHand(), "heavy")) heavyDefect = true;
                if (!heavyDefect) {
                    for (ItemStack armor : p.getInventory().getArmorContents()) {
                        if (gearManager.hasDefect(armor, "heavy")) { heavyDefect = true; break; }
                    }
                }
                if (heavyDefect) {
                    long now = System.currentTimeMillis();
                    long lastHeavy = p.hasMetadata("heavy_defect_last") ? p.getMetadata("heavy_defect_last").get(0).asLong() : 0;
                    if (now - lastHeavy >= 5000) {
                        p.setMetadata("heavy_defect_last", new org.bukkit.metadata.FixedMetadataValue(this, now));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 120, 0, false, false, false));
                    }
                }

                // Пассивные чары брони — из кэша (все O(1) проверки)
                if (hasPassiveEffect(p, "aquatic_life")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false, false));
                }
                if (hasPassiveEffect(p, "magma_walker")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false, false, false));
                }
                if (hasPassiveEffect(p, "wind_step")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, false));
                }
                if (hasPassiveEffect(p, "golem_skin")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, false, false, false));
                }
                if (hasPassiveEffect(p, "spider_reflexes")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 1, false, false, false));
                }
                if (hasPassiveEffect(p, "healing_aura")) {
                    long now = System.currentTimeMillis();
                    long lastHeal = p.hasMetadata("healing_aura_last") ? p.getMetadata("healing_aura_last").get(0).asLong() : 0;
                    if (now - lastHeal >= 3000) {
                        p.setMetadata("healing_aura_last", new org.bukkit.metadata.FixedMetadataValue(this, now));
                        double maxHp = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                        if (p.getHealth() < maxHp) {
                            p.setHealth(Math.min(p.getHealth() + 1.0, maxHp));
                        }
                    }
                }

                // Звёздная Ковка - Регенерация в темноте (свет <= 7)
                if (setBonusManager.isWearingSet(p, "starforged")) {
                    int light = p.getLocation().getBlock().getLightLevel();
                    if (light <= 7) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, false, false));
                    }
                }

                // Проверка прочности оружия (Action Bar предупреждение)
                if (gearEnhancements != null) {
                    gearEnhancements.checkDurabilityWarning(p);
                }

                // Постоянно обновляем бонусы сетов: не зависит от движения игрока и не даёт "рывков" эффектов.
                setBonusManager.applySetBonuses(p);
            }
        }, 20L, 20L); // Раз в секунду
    }

    public static VKChatGearPlugin getInstance() {
        return instance;
    }

    public GearManager getGearManager() {
        return gearManager;
    }

    public void checkForMagicEvent() {
        if (System.currentTimeMillis() < activeMagicEventExpireTime) {
            return; // Предыдущее событие еще активно
        }
        
        // Шанс 20% на новое событие каждые 30 минут
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }
        
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        String msg = "";
        
        switch (roll) {
            case 0:
                activeMagicEventName = "Двойная Заточка";
                activeMagicEventMultiplier = 0.5; // Скидка 50% на все кристаллы заточки!
                activeMagicEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🔮 [Магия] СОБЫТИЕ: Двойная Заточка! Цены на все Кристаллы Заточки в /runes временно снижены на -50% на 15 минут!";
                break;
            case 1:
                activeMagicEventName = "Неделя Защиты";
                activeMagicEventMultiplier = 0.6; // Скидка 40% на все защитные руны!
                activeMagicEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🔮 [Магия] СОБЫТИЕ: Неделя Защиты! Все руны на Броню (Эгида, Уклонение, Огненная Аура, Зеркало, Поглощение, Второе Дыхание, Аура Спешки) временно со скидкой -40% на 15 минут!";
                break;
            case 2:
                activeMagicEventName = "Неделя Атаки";
                activeMagicEventMultiplier = 0.6; // Скидка 40% на атакующие руны!
                activeMagicEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🔮 [Магия] СОБЫТИЕ: Неделя Атаки! Все руны на Оружие (Вампиризм, Облако, Бронебойность, Казнь, Метеорит, Берсерк, Жнец, Похищение, Огненный Удар, Паралич) со скидкой -40% на 15 минут!";
                break;
            case 3:
                activeMagicEventName = "Магический Коллапс";
                activeMagicEventMultiplier = 1.8; // Цены повышены на 80% (инфляция!)
                activeMagicEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🔮 [Магия] СОБЫТИЕ: Магический Коллапс! Из-за нестабильности эфира цены на все руны и кристаллы в /runes временно возросли на +80% на 15 минут!";
                break;
        }
        
        Bukkit.broadcastMessage(org.bukkit.ChatColor.LIGHT_PURPLE + msg);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (magicEventTaskId != -1) getServer().getScheduler().cancelTask(magicEventTaskId);
        if (runeMarketManager != null) {
            runeMarketManager.save();
        }
        instance = null;
    }

    public ru.example.vkchatgear.runes.RuneMarketManager getRuneMarketManager() {
        return runeMarketManager;
    }

    public SetBonusManager getSetBonusManager() { return setBonusManager; }
    public ForgeLogger getForgeLogger() { return forgeLogger; }
    public RuneRegistry getRuneRegistry() { return runeRegistry; }
    public GearEnhancements getGearEnhancements() { return gearEnhancements; }
    public ArtifactRegistry getArtifactRegistry() { return artifactRegistry; }
    public ArtifactsManager getArtifactsManager() { return artifactsManager; }

    // ═══════════════════════════════════════════
    // LISTENER ОБНОВЛЕНИЯ КЭША ПАССИВНЫХ ЭФФЕКТОВ
    // ═══════════════════════════════════════════

    /**
     * Слушатель событий инвентаря для обновления кэша пассивных эффектов.
     * Обновляет кэш при: входе игрока, смене экипировки (броня/оружие), выходе.
     */
    public static class PassiveEffectCacheListener implements org.bukkit.event.Listener {
        private final VKChatGearPlugin plugin;

        public PassiveEffectCacheListener(VKChatGearPlugin plugin) {
            this.plugin = plugin;
        }

        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
            // Заполняем кэш при входе (с небольшой задержкой, чтобы инвентарь загрузился)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.rebuildPassiveCache(e.getPlayer()), 5L);
        }

        @org.bukkit.event.EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
            plugin.clearPassiveCache(e.getPlayer().getUniqueId());
        }

        @org.bukkit.event.EventHandler
        public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            int slot = e.getRawSlot();
            boolean isArmorSlot = (slot >= 36 && slot <= 39);
            boolean isHotbarSlot = (slot >= 0 && slot <= 8);
            boolean isMainHandSwap = (e.getHotbarButton() >= 0 && e.getHotbarButton() <= 8);

            // Обновляем кэш при клике на броню, хотбар или свап оружия
            if (isArmorSlot || isHotbarSlot || isMainHandSwap) {
                // Отложено до следующего тика, чтобы предмет уже был перемещён
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.rebuildPassiveCache(p), 1L);
            }
        }

        @org.bukkit.event.EventHandler
        public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent e) {
            if (e.getPlayer() == null) return;
            // ПКМ с предметом в руке — может изменить экипировку (надеть броню)
            if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                Player p = e.getPlayer();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.rebuildPassiveCache(p), 1L);
            }
        }
    }
}
