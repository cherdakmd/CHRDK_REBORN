package ru.example.vkchatgear;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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

import java.util.List;

public class VKChatGearPlugin extends JavaPlugin {
    private static VKChatGearPlugin instance;
    private GearManager gearManager;
    private ru.example.vkchatgear.runes.RuneMarketManager runeMarketManager;

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

        gearManager = new GearManager(this);
        runeMarketManager = new ru.example.vkchatgear.runes.RuneMarketManager(this);
        
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new MechanicsListener(this), this);
        getServer().getPluginManager().registerEvents(new SynthesisListener(this), this);
        ForgeCommand forgeCommand = new ForgeCommand(this);
        getCommand("forge").setExecutor(forgeCommand);
        getServer().getPluginManager().registerEvents(forgeCommand, this);
        
        getCommand("gearadmin").setExecutor(new GearAdminCommand(this));
        getCommand("runes").setExecutor(new RuneCommand(this));
        
        ru.example.vkchatgear.commands.SalvageCommand salvageCmd = new ru.example.vkchatgear.commands.SalvageCommand(this);
        getCommand("salvage").setExecutor(salvageCmd);
        getServer().getPluginManager().registerEvents(salvageCmd, this);
        
        getServer().getPluginManager().registerEvents(new RuneListener(this), this);


        startPassiveTasks();
        
        // Каждые 30 минут запускаем проверку магических событий на руны и кристаллы
        getServer().getScheduler().runTaskTimer(this, () -> checkForMagicEvent(), 1200L, 36000L);

        getLogger().info("VKChatGear (MMO-Крафт, Заточка, Сеты) успешно запущен!");
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
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, false, false, false));
                }

                // Постоянно обновляем бонусы сетов: не зависит от движения игрока и не даёт "рывков" эффектов.
                gearManager.checkSetBonus(p);
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
        if (runeMarketManager != null) {
            runeMarketManager.save();
        }
    }

    public ru.example.vkchatgear.runes.RuneMarketManager getRuneMarketManager() {
        return runeMarketManager;
    }
}
