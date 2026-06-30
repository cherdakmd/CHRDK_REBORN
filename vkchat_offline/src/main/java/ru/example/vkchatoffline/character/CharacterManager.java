package ru.example.vkchatoffline.character;

import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер характеристик персонажа
 * 6 базовых характеристик, уровни 1-88
 */
public class CharacterManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, CharacterData> characters = new ConcurrentHashMap<>();

    // Характеристики
    public enum Stat {
        STR("СИЛ", "Физический урон", "+2% физ. урон"),
        DEX("ЛОВ", "Ловкость", "+1% уклонение"),
        INT("ИНТ", "Интеллект", "+2% маг. урон"),
        WIS("МДР", "Мудрость", "+1% лечение"),
        CON("ТЕЛ", "Телосложение", "+5 HP"),
        CHA("ХАР", "Харизма", "-2% цены");

        public final String displayName;
        public final String description;
        public final String effect;

        Stat(String displayName, String description, String effect) {
            this.displayName = displayName;
            this.description = description;
            this.effect = effect;
        }
    }

    // Данные персонажа
    public static class CharacterData {
        public int level;
        public int xp;
        public int xpToNext;
        public int statPoints;
        public Map<Stat, Integer> stats;
        public String className;
        public String companionId;
        public int hp;
        public int maxHp;
        public int gold;
        public int reputation;
        public int sanity;
        public int morale;
        public int supplies;

        public CharacterData() {
            this.level = 1;
            this.xp = 0;
            this.xpToNext = 100;
            this.statPoints = 3;
            this.stats = new HashMap<>();
            for (Stat stat : Stat.values()) {
                stats.put(stat, 10);
            }
            this.className = "";
            this.companionId = "";
            this.maxHp = 100;
            this.hp = maxHp;
            this.gold = 0;
            this.reputation = 0;
            this.sanity = 100;
            this.morale = 100;
            this.supplies = 5;
        }

        public int getStat(Stat stat) {
            return stats.getOrDefault(stat, 10);
        }

        public int getMaxHp() {
            return 100 + (getStat(Stat.CON) * 5);
        }

        public int getPhysicalDamage() {
            int base = 10 + (level * 2);
            double bonus = getStat(Stat.STR) * 0.02;
            return (int)(base * (1 + bonus));
        }

        public int getMagicDamage() {
            int base = 10 + (level * 2);
            double bonus = getStat(Stat.INT) * 0.02;
            return (int)(base * (1 + bonus));
        }

        public double getDodgeChance() {
            return Math.min(0.5, getStat(Stat.DEX) * 0.01);
        }

        public double getHealMultiplier() {
            return 1.0 + (getStat(Stat.WIS) * 0.01);
        }

        public double getDiscount() {
            return Math.min(0.5, getStat(Stat.CHA) * 0.02);
        }

        public int getArmor() {
            return level + (getStat(Stat.CON) / 2);
        }
    }

    public CharacterManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    public CharacterData getCharacter(int vkId) {
        return characters.computeIfAbsent(vkId, k -> new CharacterData());
    }

    public boolean addXp(int vkId, int amount) {
        CharacterData data = getCharacter(vkId);
        data.xp += amount;

        boolean leveledUp = false;
        while (data.xp >= data.xpToNext && data.level < 88) {
            data.xp -= data.xpToNext;
            data.level++;
            data.statPoints += 3;
            data.xpToNext = calculateXpToNext(data.level);
            leveledUp = true;

            if (data.level % 10 == 0) {
                data.statPoints += 5;
            }
        }

        if (leveledUp) {
            data.maxHp = data.getMaxHp();
            data.hp = data.maxHp;
        }

        return leveledUp;
    }

    private int calculateXpToNext(int level) {
        return 100 + (level * 50);
    }

    public boolean allocateStat(int vkId, Stat stat) {
        CharacterData data = getCharacter(vkId);
        if (data.statPoints <= 0) return false;

        data.stats.merge(stat, 1, Integer::sum);
        data.statPoints--;

        data.maxHp = data.getMaxHp();
        data.hp = Math.min(data.hp, data.maxHp);

        return true;
    }

    public int getCharacterCount() {
        return characters.size();
    }

    public String getCharacterInfo(int vkId) {
        CharacterData data = getCharacter(vkId);

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("👤 ПРОФИЛЬ ПЕРСОНАЖА\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append("📊 Уровень: ").append(data.level).append("/88\n");
        sb.append("   Опыт: ").append(data.xp).append("/").append(data.xpToNext).append("\n");
        sb.append("   Очки навыков: ").append(data.statPoints).append("\n\n");

        sb.append("❤️ HP: ").append(data.hp).append("/").append(data.maxHp).append("\n");
        sb.append("🛡️ Броня: ").append(data.getArmor()).append("\n");
        sb.append("💰 Золото: ").append(data.gold).append("\n");
        sb.append("⭐ Репутация: ").append(data.reputation).append("\n");
        sb.append("🧠 Рассудок: ").append(data.sanity).append("%\n");
        sb.append("😊 Мораль: ").append(data.morale).append("%\n");
        sb.append("📦 Припасы: ").append(data.supplies).append("\n\n");

        sb.append("📜 Класс: ").append(data.className.isEmpty() ? "Не выбран" : data.className).append("\n");
        sb.append("🐾 Спутник: ").append(data.companionId.isEmpty() ? "Нет" : data.companionId).append("\n\n");

        sb.append("═══════════════════════════════════════\n");
        sb.append("📊 ХАРАКТЕРИСТИКИ\n");
        sb.append("═══════════════════════════════════════\n\n");

        for (Stat stat : Stat.values()) {
            int value = data.getStat(stat);
            sb.append(stat.displayName).append(": ").append(value);
            sb.append(" (").append(stat.effect).append(")\n");
        }

        sb.append("\n═══════════════════════════════════════\n");
        sb.append("⚔️ БОЕВЫЕ ПОКАЗАТЕЛИ\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append("Физ. урон: ").append(data.getPhysicalDamage()).append("\n");
        sb.append("Маг. урон: ").append(data.getMagicDamage()).append("\n");
        sb.append("Уклонение: ").append(String.format("%.1f", data.getDodgeChance() * 100)).append("%\n");
        sb.append("Лечение: x").append(String.format("%.2f", data.getHealMultiplier())).append("\n");
        sb.append("Скидка: ").append(String.format("%.1f", data.getDiscount() * 100)).append("%\n");

        return sb.toString();
    }
}
