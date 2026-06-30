package ru.example.vkchatoffline.character;

import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер древа навыков
 */
public class SkillTreeManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, Set<String>> learnedSkills = new ConcurrentHashMap<>();

    // Ветки навыков
    public enum SkillBranch {
        COMBAT("Боевая"),
        SURVIVAL("Выживание"),
        MAGIC("Магия");

        public final String displayName;
        SkillBranch(String displayName) { this.displayName = displayName; }
    }

    // Навыки
    public static class Skill {
        public final String id;
        public final String name;
        public final String description;
        public final SkillBranch branch;
        public final int tier;
        public final int requiredLevel;
        public final String prerequisite;
        public final boolean isActive;
        public final double damageMultiplier;
        public final int cooldown;

        public Skill(String id, String name, String description, SkillBranch branch, int tier,
                     int requiredLevel, String prerequisite, boolean isActive,
                     double damageMultiplier, int cooldown) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.branch = branch;
            this.tier = tier;
            this.requiredLevel = requiredLevel;
            this.prerequisite = prerequisite;
            this.isActive = isActive;
            this.damageMultiplier = damageMultiplier;
            this.cooldown = cooldown;
        }
    }

    // Все навыки
    private static final Skill[] ALL_SKILLS = {
        // Боевая ветка
        new Skill("combat_strike", "Удар", "Базовая атака", SkillBranch.COMBAT, 1, 1, null, true, 1.0, 0),
        new Skill("combat_power", "Мощный удар", "Усиленная атака", SkillBranch.COMBAT, 2, 5, "combat_strike", true, 1.5, 3),
        new Skill("combat_cleave", "Рассечение", "AoE атака", SkillBranch.COMBAT, 3, 10, "combat_power", true, 1.2, 4),
        new Skill("combat_fury", "Ярость", "+30% урон на 2 раунда", SkillBranch.COMBAT, 4, 20, "combat_cleave", true, 0, 5),
        new Skill("combat_berserk", "Берсерк", "x3 урон, -30% HP", SkillBranch.COMBAT, 5, 30, "combat_fury", true, 3.0, 10),

        // Выживание ветка
        new Skill("survival_dodge", "Уклонение", "+10% уклонение", SkillBranch.SURVIVAL, 1, 1, null, false, 0, 0),
        new Skill("survival_heal", "Лечение", "Восстановить 30% HP", SkillBranch.SURVIVAL, 2, 5, "survival_dodge", true, 0, 4),
        new Skill("survival_trap", "Ловушка", "Замедление врага", SkillBranch.SURVIVAL, 3, 10, "survival_heal", true, 0, 5),
        new Skill("survival_stealth", "Невидимость", "Гарантированный крит", SkillBranch.SURVIVAL, 4, 20, "survival_trap", true, 0, 6),
        new Skill("survival_shadow", "Теневой удар", "x7 урон из невидимости", SkillBranch.SURVIVAL, 5, 30, "survival_stealth", true, 7.0, 10),

        // Магия ветка
        new Skill("magic_fire", "Огненный шар", "Огненный урон", SkillBranch.MAGIC, 1, 1, null, true, 2.5, 3),
        new Skill("magic_ice", "Ледяная стрела", "Замедление", SkillBranch.MAGIC, 2, 5, "magic_fire", true, 1.8, 3),
        new Skill("magic_lightning", "Молния", "Оглушение", SkillBranch.MAGIC, 3, 10, "magic_ice", true, 3.0, 5),
        new Skill("magic_meteor", "Метеор", "AoE урон", SkillBranch.MAGIC, 4, 20, "magic_lightning", true, 5.0, 8),
        new Skill("magic_abyss", "Бездна", "x8 урон, -50% HP", SkillBranch.MAGIC, 5, 30, "magic_meteor", true, 8.0, 12),
    };

    public SkillTreeManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить все навыки
     */
    public Skill[] getAllSkills() {
        return ALL_SKILLS;
    }

    /**
     * Получить навыки ветки
     */
    public List<Skill> getSkillsByBranch(SkillBranch branch) {
        List<Skill> skills = new ArrayList<>();
        for (Skill skill : ALL_SKILLS) {
            if (skill.branch == branch) skills.add(skill);
        }
        return skills;
    }

    /**
     * Получить навык по ID
     */
    public Skill getSkillById(String id) {
        for (Skill skill : ALL_SKILLS) {
            if (skill.id.equals(id)) return skill;
        }
        return null;
    }

    /**
     * Проверить, изучен ли навык
     */
    public boolean hasSkill(int vkId, String skillId) {
        return learnedSkills.getOrDefault(vkId, Collections.emptySet()).contains(skillId);
    }

    /**
     * Получить изученные навыки
     */
    public Set<String> getLearnedSkills(int vkId) {
        return learnedSkills.getOrDefault(vkId, Collections.emptySet());
    }

    /**
     * Изучить навык
     */
    public boolean learnSkill(int vkId, String skillId, CharacterManager.CharacterData character) {
        Skill skill = getSkillById(skillId);
        if (skill == null) return false;

        // Проверить уровень
        if (character.level < skill.requiredLevel) return false;

        // Проверить prerequisite
        if (skill.prerequisite != null && !hasSkill(vkId, skill.prerequisite)) return false;

        // Проверить очки навыков
        if (character.statPoints <= 0) return false;

        // Изучить
        learnedSkills.computeIfAbsent(vkId, k -> ConcurrentHashMap.newKeySet()).add(skillId);
        character.statPoints--;

        return true;
    }

    /**
     * Получить активные способности
     */
    public List<Skill> getActiveSkills(int vkId) {
        List<Skill> active = new ArrayList<>();
        for (String skillId : getLearnedSkills(vkId)) {
            Skill skill = getSkillById(skillId);
            if (skill != null && skill.isActive) {
                active.add(skill);
            }
        }
        return active;
    }

    /**
     * Получить информацию о навыке
     */
    public String getSkillInfo(Skill skill, boolean learned) {
        StringBuilder sb = new StringBuilder();
        sb.append(learned ? "✅ " : "❌ ");
        sb.append(skill.name).append(" [Tier ").append(skill.tier).append("]\n");
        sb.append("   ").append(skill.description).append("\n");
        sb.append("   Ветка: ").append(skill.branch.displayName).append("\n");
        sb.append("   Требуется: Ур. ").append(skill.requiredLevel);
        if (skill.prerequisite != null) {
            Skill prereq = getSkillById(skill.prerequisite);
            if (prereq != null) {
                sb.append(", ").append(prereq.name);
            }
        }
        if (skill.isActive) {
            sb.append("\n   Урон: x").append(skill.damageMultiplier);
            sb.append(", КД: ").append(skill.cooldown).append(" раундов");
        }
        return sb.toString();
    }

    /**
     * Получить информацию о дереве навыков
     */
    public String getSkillTreeInfo(int vkId) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("🌳 ДРЕВО НАВЫКОВ\n");
        sb.append("═══════════════════════════════════════\n\n");

        for (SkillBranch branch : SkillBranch.values()) {
            sb.append("▶ ").append(branch.displayName).append("\n");
            for (Skill skill : getSkillsByBranch(branch)) {
                boolean learned = hasSkill(vkId, skill.id);
                sb.append(getSkillInfo(skill, learned)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Получить количество навыков
     */
    public int getSkillCount() {
        return ALL_SKILLS.length;
    }
}
