package ru.example.vkchatoffline.combat;

import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.character.CharacterManager;
import ru.example.vkchatoffline.character.SkillTreeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер пошаговых боёв (Bloodyworld-style)
 * 3-5 раундов, фазы боссов, способности классов
 */
public class CombatManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, CombatEncounter> activeCombats = new ConcurrentHashMap<>();

    // Действия в бою
    public enum CombatAction {
        ATTACK("⚔️ Атака", "Нанести физический урон"),
        DEFEND("🛡️ Защита", "-50% получаемого урона на 1 раунд"),
        SKILL("🔥 Способность", "Использовать способность класса"),
        ITEM("🧪 Предмет", "Использовать предмет из инвентаря"),
        FLEE("🏃 Побег", "Попытка сбежать из боя");

        public final String displayName;
        public final String description;

        CombatAction(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // Фазы босса
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

    // Враги
    public static class Enemy {
        public final String name;
        public final int level;
        public final boolean isBoss;
        public int hp;
        public int maxHp;
        public int attack;
        public int defense;
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

        public double getHpPercent() {
            return (double) hp / maxHp * 100;
        }

        public void updatePhase() {
            double percent = getHpPercent();
            if (percent <= 25) phase = BossPhase.DESPERATE;
            else if (percent <= 50) phase = BossPhase.FRENZIED;
            else if (percent <= 75) phase = BossPhase.ENRAGED;
            else phase = BossPhase.NORMAL;
        }
    }

    public CombatManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Начать бой
     */
    public CombatEncounter startCombat(int vkId, String enemyName, int enemyLevel, boolean isBoss) {
        CharacterManager.CharacterData character = plugin.getCharacterManager().getCharacter(vkId);
        Enemy enemy = new Enemy(enemyName, enemyLevel, isBoss);
        int maxRounds = isBoss ? 10 : 5;
        
        CombatEncounter encounter = new CombatEncounter(vkId, enemy, character, maxRounds);
        activeCombats.put(vkId, encounter);
        return encounter;
    }

    /**
     * Получить активный бой
     */
    public CombatEncounter getActiveCombat(int vkId) {
        return activeCombats.get(vkId);
    }

    /**
     * Проверить, в бою ли игрок
     */
    public boolean isInCombat(int vkId) {
        return activeCombats.containsKey(vkId);
    }

    /**
     * Обработать действие игрока
     */
    public CombatResult processAction(int vkId, CombatAction action) {
        CombatEncounter encounter = activeCombats.get(vkId);
        if (encounter == null) {
            return new CombatResult(false, "Нет активного боя!", null, null, null);
        }

        CharacterManager.CharacterData character = encounter.character;
        Enemy enemy = encounter.enemy;
        Random rand = new Random();

        // Ход игрока
        int playerDamage = 0;
        String playerAction = "";
        boolean isDefending = false;
        int healAmount = 0;

        switch (action) {
            case ATTACK:
                playerDamage = calculatePlayerDamage(character, enemy, rand);
                playerAction = "⚔️ Вы наносите удар!";
                break;
            case DEFEND:
                isDefending = true;
                playerAction = "🛡️ Вы принимаете защитную стойку!";
                break;
            case SKILL:
                SkillTreeManager.Skill activeSkill = getActiveSkill(vkId);
                if (activeSkill != null) {
                    playerDamage = (int)(calculatePlayerDamage(character, enemy, rand) * activeSkill.damageMultiplier);
                    playerAction = "🔥 Вы используете: " + activeSkill.name + "!";
                } else {
                    playerDamage = calculatePlayerDamage(character, enemy, rand);
                    playerAction = "⚔️ Нет активной способности, обычная атака!";
                }
                break;
            case ITEM:
                healAmount = 20 + (character.getStat(CharacterManager.Stat.WIS) / 2);
                playerAction = "🧪 Вы используете зелье лечения!";
                break;
            case FLEE:
                if (!enemy.isBoss && rand.nextInt(100) < 30) {
                    activeCombats.remove(vkId);
                    return new CombatResult(true, "🏃 Вы успешно сбежали!", null, null, encounter);
                }
                playerAction = "🏃 Побег не удался!";
                break;
        }

        // Применить урон игрока
        if (playerDamage > 0) {
            int finalDamage = Math.max(1, playerDamage - enemy.defense);
            enemy.hp -= finalDamage;
            playerAction += "\n→ " + finalDamage + " урона!";
        }

        // Применить лечение
        if (healAmount > 0) {
            character.hp = Math.min(character.maxHp, character.hp + healAmount);
            playerAction += "\n→ +" + healAmount + " HP!";
        }

        // Проверить смерть врага
        if (enemy.hp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(true, "☠️ Враг повержен!", generateLoot(enemy), generateRewards(enemy), encounter);
        }

        // Ход врага
        int enemyDamage = calculateEnemyDamage(enemy, rand);
        String enemyAction = enemy.name + " атакует!";

        if (isDefending) {
            enemyDamage /= 2;
            enemyAction += " (урон снижен защитой)";
        }

        // Уклонение
        if (rand.nextDouble() < character.getDodgeChance()) {
            enemyDamage = 0;
            enemyAction += "\n→ Вы уклонились!";
        }

        character.hp -= enemyDamage;
        if (enemyDamage > 0) {
            enemyAction += "\n→ " + enemyDamage + " урона вам!";
        }

        // Проверить смерть игрока
        if (character.hp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "💀 Вы погибли в бою!", null, null, encounter);
        }

        // Обновить фазу босса
        if (enemy.isBoss) {
            enemy.updatePhase();
        }

        // Обновить раунд
        encounter.round++;

        // Проверить лимит раундов
        if (encounter.round > encounter.maxRounds) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "⏰ Время боя истекло!", null, null, encounter);
        }

        return new CombatResult(true, playerAction + "\n\n" + enemyAction, null, null, encounter);
    }

    /**
     * Рассчитать урон игрока
     */
    private int calculatePlayerDamage(CharacterManager.CharacterData character, Enemy enemy, Random rand) {
        int base = character.getPhysicalDamage();
        int variance = rand.nextInt(5) + 1;
        return base + variance;
    }

    /**
     * Рассчитать урон врага
     */
    private int calculateEnemyDamage(Enemy enemy, Random rand) {
        int base = enemy.attack;
        int variance = rand.nextInt(3) + 1;
        return (int)((base + variance) * enemy.phase.damageMultiplier);
    }

    /**
     * Получить активную способность
     */
    private SkillTreeManager.Skill getActiveSkill(int vkId) {
        List<SkillTreeManager.Skill> activeSkills = plugin.getSkillTreeManager().getActiveSkills(vkId);
        return activeSkills.isEmpty() ? null : activeSkills.get(0);
    }

    /**
     * Сгенерировать лут
     */
    private List<org.bukkit.inventory.ItemStack> generateLoot(Enemy enemy) {
        return plugin.getLootManager().generateLoot(enemy.level, "combat", enemy.isBoss);
    }

    /**
     * Сгенерировать награды
     */
    private Map<String, Integer> generateRewards(Enemy enemy) {
        Map<String, Integer> rewards = new HashMap<>();
        rewards.put("reputation", enemy.level * (enemy.isBoss ? 10 : 3));
        rewards.put("xp", enemy.level * (enemy.isBoss ? 20 : 10));
        rewards.put("gold", enemy.level * (enemy.isBoss ? 5 : 2));
        return rewards;
    }

    /**
     * Завершить бой
     */
    public void endCombat(int vkId) {
        activeCombats.remove(vkId);
    }

    /**
     * Получить количество активных боёв
     */
    public int getActiveCombatCount() {
        return activeCombats.size();
    }
}
