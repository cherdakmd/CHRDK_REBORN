package ru.example.vkchatoffline.character;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillTreeManager {
    private final Map<Integer, Set<String>> learnedSkills = new ConcurrentHashMap<>();

    public enum SkillBranch { COMBAT, SURVIVAL, MAGIC }

    public static class Skill {
        public final String id, name, description;
        public final SkillBranch branch;
        public final int tier, requiredLevel;
        public final String prerequisite;
        public final boolean isActive;
        public final double damageMultiplier;
        public final int cooldown;

        public Skill(String id, String name, String description, SkillBranch branch, int tier,
                     int requiredLevel, String prerequisite, boolean isActive, double damageMultiplier, int cooldown) {
            this.id = id; this.name = name; this.description = description; this.branch = branch;
            this.tier = tier; this.requiredLevel = requiredLevel; this.prerequisite = prerequisite;
            this.isActive = isActive; this.damageMultiplier = damageMultiplier; this.cooldown = cooldown;
        }
    }

    private static final Skill[] ALL_SKILLS = {
        new Skill("combat_strike", "Удар", "Базовая атака", SkillBranch.COMBAT, 1, 1, null, true, 1.0, 0),
        new Skill("combat_power", "Мощный удар", "Усиленная атака", SkillBranch.COMBAT, 2, 5, "combat_strike", true, 1.5, 3),
        new Skill("combat_cleave", "Рассечение", "AoE атака", SkillBranch.COMBAT, 3, 10, "combat_power", true, 1.2, 4),
        new Skill("combat_fury", "Ярость", "+30% урон", SkillBranch.COMBAT, 4, 20, "combat_cleave", true, 0, 5),
        new Skill("combat_berserk", "Берсерк", "x3 урон", SkillBranch.COMBAT, 5, 30, "combat_fury", true, 3.0, 10),
        new Skill("survival_dodge", "Уклонение", "+10% уклонение", SkillBranch.SURVIVAL, 1, 1, null, false, 0, 0),
        new Skill("survival_heal", "Лечение", "Восстановить HP", SkillBranch.SURVIVAL, 2, 5, "survival_dodge", true, 0, 4),
        new Skill("survival_trap", "Ловушка", "Замедление", SkillBranch.SURVIVAL, 3, 10, "survival_heal", true, 0, 5),
        new Skill("survival_stealth", "Невидимость", "Гарант. крит", SkillBranch.SURVIVAL, 4, 20, "survival_trap", true, 0, 6),
        new Skill("survival_shadow", "Теневой удар", "x7 урон", SkillBranch.SURVIVAL, 5, 30, "survival_stealth", true, 7.0, 10),
        new Skill("magic_fire", "Огненный шар", "Огненный урон", SkillBranch.MAGIC, 1, 1, null, true, 2.5, 3),
        new Skill("magic_ice", "Ледяная стрела", "Замедление", SkillBranch.MAGIC, 2, 5, "magic_fire", true, 1.8, 3),
        new Skill("magic_lightning", "Молния", "Оглушение", SkillBranch.MAGIC, 3, 10, "magic_ice", true, 3.0, 5),
        new Skill("magic_meteor", "Метеор", "AoE урон", SkillBranch.MAGIC, 4, 20, "magic_lightning", true, 5.0, 8),
        new Skill("magic_abyss", "Бездна", "x8 урон", SkillBranch.MAGIC, 5, 30, "magic_meteor", true, 8.0, 12),
    };

    public Skill getSkillById(String id) {
        for (Skill s : ALL_SKILLS) if (s.id.equals(id)) return s;
        return null;
    }

    public boolean hasSkill(int vkId, String skillId) {
        return learnedSkills.getOrDefault(vkId, Collections.emptySet()).contains(skillId);
    }

    public boolean learnSkill(int vkId, String skillId, CharacterManager.CharacterData character) {
        Skill skill = getSkillById(skillId);
        if (skill == null || character.level < skill.requiredLevel) return false;
        if (skill.prerequisite != null && !hasSkill(vkId, skill.prerequisite)) return false;
        if (character.statPoints <= 0) return false;
        learnedSkills.computeIfAbsent(vkId, k -> ConcurrentHashMap.newKeySet()).add(skillId);
        character.statPoints--;
        return true;
    }

    public List<Skill> getActiveSkills(int vkId) {
        List<Skill> active = new ArrayList<>();
        for (String id : learnedSkills.getOrDefault(vkId, Collections.emptySet())) {
            Skill s = getSkillById(id);
            if (s != null && s.isActive) active.add(s);
        }
        return active;
    }

    public int getSkillCount() { return ALL_SKILLS.length; }

    public String getSkillTreeInfo(int vkId) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("🌳 ДРЕВО НАВЫКОВ\n");
        sb.append("═══════════════════════════════════════\n\n");
        for (SkillBranch branch : SkillBranch.values()) {
            sb.append("▶ ").append(branch.name()).append("\n");
            for (Skill s : ALL_SKILLS) {
                if (s.branch == branch) {
                    boolean learned = hasSkill(vkId, s.id);
                    sb.append(learned ? "✅ " : "❌ ");
                    sb.append(s.name).append(" [Tier ").append(s.tier).append("]\n");
                    sb.append("   ").append(s.description).append("\n");
                    sb.append("   Требуется: Ур. ").append(s.requiredLevel);
                    if (s.prerequisite != null) {
                        Skill prereq = getSkillById(s.prerequisite);
                        if (prereq != null) sb.append(", ").append(prereq.name);
                    }
                    if (s.isActive) sb.append("\n   Урон: x").append(s.damageMultiplier).append(", КД: ").append(s.cooldown);
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
