package ru.example.vkchatoffline.character;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterManager {
    private final Map<Integer, CharacterData> characters = new ConcurrentHashMap<>();

    public enum Stat {
        STR("СИЛ", "+2% физ. урон"),
        DEX("ЛОВ", "+1% уклонение"),
        INT("ИНТ", "+2% маг. урон"),
        WIS("МДР", "+1% лечение"),
        CON("ТЕЛ", "+5 HP"),
        CHA("ХАР", "-2% цены");

        public final String displayName, effect;
        Stat(String displayName, String effect) { this.displayName = displayName; this.effect = effect; }
    }

    public static class CharacterData {
        public int level = 1, xp = 0, xpToNext = 100, statPoints = 3;
        public Map<Stat, Integer> stats = new HashMap<>();
        public String className = "", companionId = "";
        public int hp = 100, maxHp = 100, gold = 0, reputation = 0, sanity = 100, morale = 100, supplies = 5;

        public CharacterData() { for (Stat s : Stat.values()) stats.put(s, 10); }

        public int getStat(Stat s) { return stats.getOrDefault(s, 10); }
        public int getMaxHp() { return 100 + (getStat(Stat.CON) * 5); }
        public int getPhysicalDamage() { return (int)((10 + (level * 2)) * (1 + getStat(Stat.STR) * 0.02)); }
        public int getMagicDamage() { return (int)((10 + (level * 2)) * (1 + getStat(Stat.INT) * 0.02)); }
        public double getDodgeChance() { return Math.min(0.5, getStat(Stat.DEX) * 0.01); }
        public double getHealMultiplier() { return 1.0 + (getStat(Stat.WIS) * 0.01); }
        public double getDiscount() { return Math.min(0.5, getStat(Stat.CHA) * 0.02); }
        public int getArmor() { return level + (getStat(Stat.CON) / 2); }
    }

    public CharacterData getCharacter(int vkId) { return characters.computeIfAbsent(vkId, k -> new CharacterData()); }
    public int getCharacterCount() { return characters.size(); }

    public boolean addXp(int vkId, int amount) {
        CharacterData data = getCharacter(vkId);
        data.xp += amount;
        boolean leveledUp = false;
        while (data.xp >= data.xpToNext && data.level < 88) {
            data.xp -= data.xpToNext;
            data.level++;
            data.statPoints += 3;
            data.xpToNext = 100 + (data.level * 50);
            leveledUp = true;
            if (data.level % 10 == 0) data.statPoints += 5;
        }
        if (leveledUp) { data.maxHp = data.getMaxHp(); data.hp = data.maxHp; }
        return leveledUp;
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
        sb.append("⭐ Репутация: ").append(data.reputation).append("\n\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("📊 ХАРАКТЕРИСТИКИ\n");
        sb.append("═══════════════════════════════════════\n\n");
        for (Stat stat : Stat.values()) {
            sb.append(stat.displayName).append(": ").append(data.getStat(stat));
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
