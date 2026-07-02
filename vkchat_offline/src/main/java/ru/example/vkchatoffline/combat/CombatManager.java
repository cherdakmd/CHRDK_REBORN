package ru.example.vkchatoffline.combat;

import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.character.CharacterManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пошаговые бои (Bloodyworld-style)
 * 3-5 раундов, фазы боссов
 */
public class CombatManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, CombatEncounter> activeCombats = new ConcurrentHashMap<>();

    public enum CombatAction {
        ATTACK("⚔️ Атака"),
        DEFEND("🛡️ Защита"),
        SKILL("🔥 Способность"),
        ITEM("🧪 Предмет"),
        FLEE("🏃 Побег");

        public final String displayName;
        CombatAction(String displayName) { this.displayName = displayName; }
    }

    public enum BossPhase {
        NORMAL("Норма", 1.0),
        ENRAGED("Ярость", 1.3),
        FRENZIED("Безумие", 1.5),
        DESPERATE("Отчаяние", 2.0);

        public final String displayName;
        public final double damageMultiplier;
        BossPhase(String displayName, double damageMultiplier) {
            this.displayName = displayName;
            this.damageMultiplier = damageMultiplier;
        }
    }

    public static class Enemy {
        public final String name;
        public final int level;
        public final boolean isBoss;
        public int hp, maxHp, attack, defense;
        public BossPhase phase;

        public Enemy(String name, int level, boolean isBoss) {
            this.name = name;
            this.level = level;
            this.isBoss = isBoss;
            this.maxHp = isBoss ? 200 + (level * 15) : 50 + (level * 5);
            this.hp = maxHp;
            this.attack = isBoss ? 15 + (level * 3) : 5 + (level * 2);
            this.defense = isBoss ? 10 + level : 2 + level;
            this.phase = BossPhase.NORMAL;
        }

        public void updatePhase() {
            double percent = (double) hp / maxHp * 100;
            if (percent <= 25) phase = BossPhase.DESPERATE;
            else if (percent <= 50) phase = BossPhase.FRENZIED;
            else if (percent <= 75) phase = BossPhase.ENRAGED;
            else phase = BossPhase.NORMAL;
        }
    }

    public CombatManager(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    public CombatEncounter startCombat(int vkId, String enemyName, int enemyLevel, boolean isBoss) {
        CharacterManager.CharacterData character = plugin.getCharacterManager().getCharacter(vkId);
        Enemy enemy = new Enemy(enemyName, enemyLevel, isBoss);
        CombatEncounter encounter = new CombatEncounter(vkId, enemy, character, isBoss ? 10 : 5);
        activeCombats.put(vkId, encounter);
        return encounter;
    }

    public CombatEncounter getActiveCombat(int vkId) { return activeCombats.get(vkId); }
    public boolean isInCombat(int vkId) { return activeCombats.containsKey(vkId); }

    public CombatResult processAction(int vkId, CombatAction action) {
        CombatEncounter encounter = activeCombats.get(vkId);
        if (encounter == null) return new CombatResult(false, "Нет активного боя!", null, null, null);

        CharacterManager.CharacterData character = encounter.character;
        Enemy enemy = encounter.enemy;
        Random rand = new Random();

        int playerDamage = 0;
        String playerAction = "";
        boolean isDefending = false;
        int healAmount = 0;

        switch (action) {
            case ATTACK:
                playerDamage = character.getPhysicalDamage() + rand.nextInt(5) + 1;
                playerAction = "⚔️ Вы наносите удар!";
                break;
            case DEFEND:
                isDefending = true;
                playerAction = "🛡️ Вы в защитной стойке!";
                break;
            case SKILL:
                var activeSkill = plugin.getSkillTreeManager().getActiveSkills(vkId);
                if (!activeSkill.isEmpty()) {
                    var skill = activeSkill.get(0);
                    playerDamage = (int)(character.getPhysicalDamage() * skill.damageMultiplier);
                    playerAction = "🔥 " + skill.name + "!";
                } else {
                    playerDamage = character.getPhysicalDamage() + rand.nextInt(5) + 1;
                    playerAction = "⚔️ Обычная атака!";
                }
                break;
            case ITEM:
                healAmount = 20 + (character.getStat(CharacterManager.Stat.WIS) / 2);
                playerAction = "🧪 Зелье лечения!";
                break;
            case FLEE:
                if (!enemy.isBoss && rand.nextInt(100) < 30) {
                    activeCombats.remove(vkId);
                    return new CombatResult(true, "🏃 Вы сбежали!", null, null, encounter);
                }
                playerAction = "🏃 Побег не удался!";
                break;
        }

        if (playerDamage > 0) {
            int finalDamage = Math.max(1, playerDamage - enemy.defense);
            enemy.hp -= finalDamage;
            playerAction += "\n→ " + finalDamage + " урона!";
        }

        if (healAmount > 0) {
            character.hp = Math.min(character.maxHp, character.hp + healAmount);
            playerAction += "\n→ +" + healAmount + " HP!";
        }

        if (enemy.hp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(true, "☠️ Враг повержен!", plugin.getLootManager().generateLoot(enemy.level, "combat", enemy.isBoss), generateRewards(enemy), encounter);
        }

        int enemyDamage = Math.max(1, (int)((enemy.attack + rand.nextInt(3)) * enemy.phase.damageMultiplier));
        String enemyAction = enemy.name + " атакует!";
        if (isDefending) { enemyDamage /= 2; enemyAction += " (урон снижен)"; }
        if (rand.nextDouble() < character.getDodgeChance()) { enemyDamage = 0; enemyAction += "\n→ Уклонились!"; }

        character.hp -= enemyDamage;
        if (enemyDamage > 0) enemyAction += "\n→ " + enemyDamage + " урона вам!";

        if (character.hp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "💀 Вы погибли!", null, null, encounter);
        }

        if (enemy.isBoss) enemy.updatePhase();
        encounter.round++;

        if (encounter.round > encounter.maxRounds) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "⏰ Время боя истекло!", null, null, encounter);
        }

        return new CombatResult(true, playerAction + "\n\n" + enemyAction, null, null, encounter);
    }

    private Map<String, Integer> generateRewards(Enemy enemy) {
        Map<String, Integer> rewards = new HashMap<>();
        rewards.put("reputation", enemy.level * (enemy.isBoss ? 10 : 3));
        rewards.put("xp", enemy.level * (enemy.isBoss ? 20 : 10));
        rewards.put("gold", enemy.level * (enemy.isBoss ? 5 : 2));
        return rewards;
    }

    public void endCombat(int vkId) { activeCombats.remove(vkId); }
    public int getActiveCombatCount() { return activeCombats.size(); }
}
