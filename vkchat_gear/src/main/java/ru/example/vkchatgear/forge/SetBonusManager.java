package ru.example.vkchatgear.forge;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SetBonusManager — конфиг-управляемая система сет-бонусов.
 *
 * FIX #4: Выделен из GearManager.checkSetBonus() (200+ строк хардкода)
 * IMPROVE #2: Сет-бонусы полностью настраиваются из config.yml
 * IMPROVE #5: Поддержка upgradeLevelAvg-based усилений баффов сета
 */
public class SetBonusManager {

    private final JavaPlugin plugin;
    private final NamespacedKey setKey;
    private final NamespacedKey upgradeKey;

    /** setId → SetDef */
    private final Map<String, SetDef> setDefs = new LinkedHashMap<>();

    /** Кеширование активных сетов для визуального ActionBar */
    private final Set<UUID> playersWithSynergy = ConcurrentHashMap.newKeySet();

    public static final class SetDef {
        private final String id;
        private final String name;
        private final String bonusDescription;
        private final List<BuffEntry> buffs;
        private final List<BuffEntry> debuffs;
        /** Усиление на avgLvl >= 15 */
        private final List<BuffEntry> buffs15;
        /** Усиление на avgLvl >= 20 */
        private final List<BuffEntry> buffs20;

        public SetDef(String id, String name, String bonusDescription,
                      List<BuffEntry> buffs, List<BuffEntry> debuffs,
                      List<BuffEntry> buffs15, List<BuffEntry> buffs20) {
            this.id = id;
            this.name = name;
            this.bonusDescription = bonusDescription;
            this.buffs = buffs;
            this.debuffs = debuffs;
            this.buffs15 = buffs15;
            this.buffs20 = buffs20;
        }
    }

    public record BuffEntry(org.bukkit.potion.PotionEffectType type, int amplifier, int duration) {}

    public SetBonusManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.setKey = new NamespacedKey(plugin, "gear_set");
        this.upgradeKey = new NamespacedKey(plugin, "upgrade_level");
        loadFromConfig();
    }

    /**
     * Загрузить определения сетов из config.yml → sets.*.bonus
     */
    public void loadFromConfig() {
        setDefs.clear();
        ConfigurationSection setsSection = plugin.getConfig().getConfigurationSection("sets");
        if (setsSection == null) return;

        for (String setId : setsSection.getKeys(false)) {
            ConfigurationSection sec = setsSection.getConfigurationSection(setId);
            if (sec == null) continue;

            String name = sec.getString("name", setId);
            String bonus = sec.getString("bonus", "");

            // Парсим баффы из bonus-строки
            List<BuffEntry> buffs = parseBonusEffects(sec, "effects");
            List<BuffEntry> debuffs = parseBonusEffects(sec, "debuffs");
            List<BuffEntry> buffs15 = parseBonusEffects(sec, "effects-15");
            List<BuffEntry> buffs20 = parseBonusEffects(sec, "effects-20");

            // Если нет конфиг-эффектов — парсим из старого bonus-описания
            if (buffs.isEmpty()) {
                buffs = parseLegacyBonus(bonus);
                debuffs = parseLegacyDebuffs(bonus);
            }

            setDefs.put(setId, new SetDef(setId, name, bonus, buffs, debuffs, buffs15, buffs20));
        }

        plugin.getLogger().info("[SetBonus] Загружено " + setDefs.size() + " сетов из конфига");
    }

    /**
     * Применить сет-бонусы к игроку.
     * Заменяет 200+ строк хардкода в GearManager.checkSetBonus().
     */
    public void applySetBonuses(Player p) {
        if (p == null) return;

        Map<String, Integer> setCounts = new HashMap<>();
        Map<String, Set<String>> setPieceTypes = new HashMap<>();
        int totalLvl = 0;
        int pieceCount = 0;

        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            if (!((VKChatGearPlugin) plugin).getGearManager().isLegalSetPiece(armor)) continue;
            String set = armor.getItemMeta().getPersistentDataContainer()
                    .get(setKey, PersistentDataType.STRING);
            if (set == null) continue;

            setCounts.put(set, setCounts.getOrDefault(set, 0) + 1);
            String pieceType = getArmorPieceType(armor.getType());
            setPieceTypes.computeIfAbsent(set, k -> new HashSet<>()).add(pieceType);

            int lvl = armor.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(upgradeKey, PersistentDataType.INTEGER, 0);
            totalLvl += lvl;
            pieceCount++;
        }

        int avgLvl = pieceCount >= 4 ? totalLvl / 4 : 0;
        boolean anyActive = false;

        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            String setId = entry.getKey();
            int count = entry.getValue();
            int uniqueSlots = setPieceTypes.getOrDefault(setId, Collections.emptySet()).size();

            // Полный сет: 4 предмета + 4 разных слота
            if (count >= 4 && uniqueSlots >= 4) {
                SetDef def = setDefs.get(setId);
                if (def == null) continue;

                anyActive = true;

                // Базовые баффы
                for (BuffEntry buff : def.buffs) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            buff.type(), buff.duration(), buff.amplifier(), true, false));
                }

                // Баффы за +15
                if (avgLvl >= 15 && def.buffs15 != null) {
                    for (BuffEntry buff : def.buffs15) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                buff.type(), buff.duration(), buff.amplifier(), true, false));
                    }
                }

                // Баффы за +20
                if (avgLvl >= 20 && def.buffs20 != null) {
                    for (BuffEntry buff : def.buffs20) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                buff.type(), buff.duration(), buff.amplifier(), true, false));
                    }
                }

                // Дебаффы
                for (BuffEntry debuff : def.debuffs) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            debuff.type(), debuff.duration(), debuff.amplifier(), true, false));
                }
            }
        }

        if (anyActive) {
            playersWithSynergy.add(p.getUniqueId());
        } else {
            playersWithSynergy.remove(p.getUniqueId());
        }
    }

    /**
     * Проверить, носит ли игрок полный сет.
     */
    public boolean isWearingSet(Player p, String setName) {
        int count = 0;
        Set<String> pieceTypes = new HashSet<>();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                if (!((VKChatGearPlugin) plugin).getGearManager().isLegalSetPiece(armor)) continue;
                String set = armor.getItemMeta().getPersistentDataContainer()
                        .get(setKey, PersistentDataType.STRING);
                if (setName.equalsIgnoreCase(set)) {
                    count++;
                    pieceTypes.add(getArmorPieceType(armor.getType()));
                }
            }
        }
        return count >= 4 && pieceTypes.size() >= 4;
    }

    /**
     * Очистить все сетовые эффекты у игрока.
     */
    public void clearSetEffects(Player p) {
        playersWithSynergy.remove(p.getUniqueId());
    }

    /**
     * Имеет ли игрок активный сет-бонус.
     */
    public boolean hasActiveSet(Player p) {
        return playersWithSynergy.contains(p.getUniqueId());
    }

    public Collection<SetDef> getSetDefs() {
        return Collections.unmodifiableCollection(setDefs.values());
    }

    public SetDef getSetDef(String id) {
        return setDefs.get(id);
    }

    // ═══════════════════════════════════════
    // ПАРСИНГ КОНФИГА
    // ═══════════════════════════════════════

    private List<BuffEntry> parseBonusEffects(ConfigurationSection sec, String key) {
        List<BuffEntry> result = new ArrayList<>();
        if (sec == null) return result;
        List<String> entries = sec.getStringList(key);
        for (String entry : entries) {
            BuffEntry be = parseEffectString(entry);
            if (be != null) result.add(be);
        }
        return result;
    }

    /**
     * Парсит строку вида "INCREASE_DAMAGE:2:1200" → BuffEntry
     * Формат: EFFECT_TYPE:amplifier:duration_ticks
     */
    private BuffEntry parseEffectString(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] parts = str.split(":");
            org.bukkit.potion.PotionEffectType type =
                    org.bukkit.potion.PotionEffectType.getByName(parts[0].trim());
            if (type == null) return null;
            int amplifier = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            int duration = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1200;
            return new BuffEntry(type, amplifier, duration);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсит legacy-описание бонуса для обратной совместимости.
     * Если нет конфиг-эффектов, конвертирует старый хардкод в BuffEntry.
     */
    private List<BuffEntry> parseLegacyBonus(String bonus) {
        List<BuffEntry> result = new ArrayList<>();
        if (bonus == null) return result;
        String lower = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', bonus)).toLowerCase();

        // Маппинг ключевых слов → эффекты (legacy совместимость)
        if (lower.contains("сила")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 0, 1200));
        if (lower.contains("сопротивление") || lower.contains("резист")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1, 1200));
        if (lower.contains("скорость")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.SPEED, 2, 1200));
        if (lower.contains("прыгучесть") || lower.contains("прыжок")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.JUMP, 2, 1200));
        if (lower.contains("спешка")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.FAST_DIGGING, 4, 1200));
        if (lower.contains("регенерация")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.REGENERATION, 1, 1200));
        if (lower.contains("невидимость")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.INVISIBILITY, 0, 1200));
        if (lower.contains("огнестойкость") || lower.contains("иммунитет к огню")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 0, 1200));
        if (lower.contains("дыхание")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.WATER_BREATHING, 0, 1200));
        if (lower.contains("дельфин") || lower.contains("грация")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, 0, 1200));
        if (lower.contains("плавное падение")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 0, 1200));
        if (lower.contains("герой")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, 0, 1200));
        if (lower.contains("удача")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.LUCK, 1, 1200));
        if (lower.contains("здоровье") || lower.contains("hp")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 1, 1200));
        if (lower.contains("абсорбция")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.ABSORPTION, 1, 1200));

        return result;
    }

    private List<BuffEntry> parseLegacyDebuffs(String bonus) {
        List<BuffEntry> result = new ArrayList<>();
        if (bonus == null) return result;
        String lower = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', bonus)).toLowerCase();

        if (lower.contains("медлительность") || lower.contains("замедление")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.SLOW, 1, 1200));
        if (lower.contains("слабость")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.WEAKNESS, 0, 1200));
        if (lower.contains("утомление")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.SLOW_DIGGING, 1, 1200));
        if (lower.contains("иссушение") || lower.contains("визер")) result.add(new BuffEntry(org.bukkit.potion.PotionEffectType.WITHER, 0, 1200));

        return result;
    }

    private String getArmorPieceType(org.bukkit.Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET")) return "helmet";
        if (n.endsWith("_CHESTPLATE")) return "chestplate";
        if (n.endsWith("_LEGGINGS")) return "leggings";
        if (n.endsWith("_BOOTS")) return "boots";
        return n;
    }
}
