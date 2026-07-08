package ru.example.vkchatgear;

import org.bukkit.Bukkit;
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
import ru.example.vkchatgear.donate.DonateStatusResolver;
import ru.example.vkchatgear.forge.ForgeLogger;
import ru.example.vkchatgear.forge.SetBonusManager;
import ru.example.vkchatgear.runes.RuneRegistry;
import ru.example.vkchat.util.VKChatBridge;

import java.util.List;

public class VKChatGearPlugin extends JavaPlugin {
    private static VKChatGearPlugin instance;
    private GearManager gearManager;
    private ru.example.vkchatgear.runes.RuneMarketManager runeMarketManager;
    private SetBonusManager setBonusManager;
    private ForgeLogger forgeLogger;
    private RuneRegistry runeRegistry;
    private int magicEventTaskId = -1;

    // Магические события
    private String activeMagicEventName = null;
    private double activeMagicEventMultiplier = 1.0;
    private long activeMagicEventExpireTime = 0L;

    public String getActiveMagicEventName() { return activeMagicEventName; }
    public double getActiveMagicEventMultiplier() { return activeMagicEventMultiplier; }
    public long getActiveMagicEventExpireTime() { return activeMagicEventExpireTime; }


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
        VKChatBridge.init();

        gearManager = new GearManager(this);
        runeMarketManager = new ru.example.vkchatgear.runes.RuneMarketManager(this);
        setBonusManager = new SetBonusManager(this);
        forgeLogger = new ForgeLogger(this);
        runeRegistry = new RuneRegistry(this);
        
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

        // Чистка кулдаунов каждые 5 минут
        getServer().getScheduler().runTaskTimer(this, () -> {
            combatListener.cleanupCooldowns(System.currentTimeMillis());
        }, 6000L, 6000L);

        startPassiveTasks();
        
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

    private void startPassiveTasks() {
        // Пассивная Спешка и Аура Спешки
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    List<String> lore = item.getItemMeta().getLore();
                    boolean hasHaste = false;
                    for (String line : lore) {
                        if (org.bukkit.ChatColor.stripColor(line).contains("Спешка")) {
                            hasHaste = true;
                            break;
                        }
                    }
                    if (hasHaste) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 40, 1, false, false, false));
                    }

                    // --- Постоянные шлейфы частиц для оружия высокого уровня заточки (+15 / +20) ---
                    try {
                        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                        org.bukkit.NamespacedKey upgradeKey = new org.bukkit.NamespacedKey(this, "upgrade_level");
                        if (pdc.has(upgradeKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            int upgradeLvl = pdc.get(upgradeKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                            if (upgradeLvl >= 15 && gearManager.isGear(item.getType())) {
                                org.bukkit.Location loc = p.getLocation().add(0, 1.0, 0);
                                if (upgradeLvl >= 20) {
                                    // Эндгейм-искры текущего максимума (+20)
                                    p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, loc, 2, 0.2, 0.2, 0.2, 0.05);
                                    p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, loc, 1, 0.2, 0.2, 0.2, 0.02);
                                } else {
                                    // Пламя (+15)
                                    p.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc, 1, 0.2, 0.2, 0.2, 0.02);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Аура Спешки (проверка брони)
                boolean hasHasteAura = false;
                for (ItemStack armor : p.getInventory().getArmorContents()) {
                    if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                        for (String l : armor.getItemMeta().getLore()) {
                            if (org.bukkit.ChatColor.stripColor(l).contains("Аура Спешки")) {
                                hasHasteAura = true;
                                break;
                            }
                        }
                    }
                }
                if (hasHasteAura) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, false, false, false));
                    for (org.bukkit.entity.Entity ent : p.getNearbyEntities(10, 10, 10)) {
                        if (ent instanceof Player) {
                            ((Player) ent).addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, false, false, false));
                        }
                    }
                }

                // Дефект "Тяжёлый": постоянная лёгкая медлительность, пока дефектный предмет надет/в руке.
                boolean heavyDefect = false;
                if (gearManager.hasDefect(p.getInventory().getItemInMainHand(), "heavy")) heavyDefect = true;
                for (ItemStack armor : p.getInventory().getArmorContents()) {
                    if (gearManager.hasDefect(armor, "heavy")) { heavyDefect = true; break; }
                }
                if (heavyDefect) {
                    long now = System.currentTimeMillis();
                    long lastHeavy = p.hasMetadata("heavy_defect_last") ? p.getMetadata("heavy_defect_last").get(0).asLong() : 0;
                    if (now - lastHeavy >= 5000) {
                        p.setMetadata("heavy_defect_last", new org.bukkit.metadata.FixedMetadataValue(this, now));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 120, 0, false, false, false));
                    }
                }

                // Пассивные чары брони
                boolean hasAquatic = false, hasMagma = false, hasWind = false, hasGolem = false, hasSpider = false, hasHealingAura = false;
                for (ItemStack armor : p.getInventory().getArmorContents()) {
                    if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                        for (String l : armor.getItemMeta().getLore()) {
                            String stripped = org.bukkit.ChatColor.stripColor(l);
                            if (stripped.contains("Подводная Жизнь")) hasAquatic = true;
                            if (stripped.contains("Магматический Шаг")) hasMagma = true;
                            if (stripped.contains("Поступь Ветра")) hasWind = true;
                            if (stripped.contains("Кожа Голема")) hasGolem = true;
                            if (stripped.contains("Рефлексы Паука")) hasSpider = true;
                            if (stripped.contains("Аура Исцеления")) hasHealingAura = true;
                        }
                    }
                }
                if (hasAquatic) p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false, false));
                if (hasMagma) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false, false, false));
                if (hasWind) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, false));
                if (hasGolem) p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, false, false, false));
                if (hasSpider) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 1, false, false, false));
                if (hasHealingAura) {
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
        if (new java.util.Random().nextInt(100) >= 20) {
            return;
        }
        
        int roll = new java.util.Random().nextInt(4);
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
    }

    public ru.example.vkchatgear.runes.RuneMarketManager getRuneMarketManager() {
        return runeMarketManager;
    }

    public SetBonusManager getSetBonusManager() { return setBonusManager; }
    public ForgeLogger getForgeLogger() { return forgeLogger; }
    public RuneRegistry getRuneRegistry() { return runeRegistry; }
}
