package ru.example.vkchatoffline.combat;

import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер пошаговых боёв (Bloodyworld-style)
 */
public class CombatManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, CombatEncounter> activeCombats = new ConcurrentHashMap<>();

    // Действия в бою
    public enum CombatAction {
        ATTACK("⚔️ Урон", "Нанести физический урон"),
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

    public CombatManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Начать бой
     */
    public CombatEncounter startCombat(int vkId, String enemyName, int enemyLevel, int playerLevel, boolean isBoss) {
        CombatEncounter encounter = new CombatEncounter(vkId, enemyName, enemyLevel, playerLevel, isBoss);
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
    public CombatResult processAction(int vkId, CombatAction action, String skillId) {
        CombatEncounter encounter = activeCombats.get(vkId);
        if (encounter == null) {
            return new CombatResult(false, "Нет активного боя!", null, null);
        }

        // Ход игрока
        int playerDamage = 0;
        String playerAction = "";
        boolean isDefending = false;

        switch (action) {
            case ATTACK:
                playerDamage = calculatePlayerDamage(encounter);
                playerAction = "⚔️ Вы наносите удар!";
                break;
            case DEFEND:
                isDefending = true;
                playerAction = "🛡️ Вы принимаете защитную стойку!";
                break;
            case SKILL:
                playerDamage = calculateSkillDamage(encounter, skillId);
                playerAction = "🔥 Вы используете способность!";
                break;
            case ITEM:
                playerAction = "🧪 Вы используете предмет!";
                break;
            case FLEE:
                if (attemptFlee(encounter)) {
                    activeCombats.remove(vkId);
                    return new CombatResult(true, "🏃 Вы успешно сбежали!", null, encounter);
                }
                playerAction = "🏃 Побег не удался!";
                break;
        }

        // Применить урон игрока
        if (playerDamage > 0) {
            encounter.enemyHp -= playerDamage;
            playerAction += "\n→ " + playerDamage + " урона!";
        }

        // Проверить смерть врага
        if (encounter.enemyHp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(true, "☠️ Враг повержен!", null, encounter);
        }

        // Ход врага
        int enemyDamage = calculateEnemyDamage(encounter);
        String enemyAction = encounter.enemyName + " атакует!";

        if (isDefending) {
            enemyDamage /= 2;
            enemyAction += " (урон снижен защитой)";
        }

        encounter.playerHp -= enemyDamage;
        enemyAction += "\n→ " + enemyDamage + " урона вам!";

        // Проверить смерть игрока
        if (encounter.playerHp <= 0) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "💀 Вы погибли в бою!", null, encounter);
        }

        // Обновить фазу босса
        if (encounter.isBoss) {
            updateBossPhase(encounter);
        }

        // Обновить раунд
        encounter.round++;

        // Проверить лимит раундов
        if (encounter.round > encounter.maxRounds) {
            activeCombats.remove(vkId);
            return new CombatResult(false, "⏰ Время боя истекло!", null, encounter);
        }

        return new CombatResult(true, playerAction + "\n\n" + enemyAction, null, encounter);
    }

    /**
     * Рассчитать урон игрока
     */
    private int calculatePlayerDamage(CombatEncounter encounter) {
        int baseDamage = 10 + (encounter.playerLevel * 2);
        int variance = new Random().nextInt(5) + 1;
        return baseDamage + variance;
    }

    /**
     * Рассчитать урон способности
     */
    private int calculateSkillDamage(CombatEncounter encounter, String skillId) {
        int baseDamage = calculatePlayerDamage(encounter);
        // Умножаем на множитель способности
        return (int) (baseDamage * 1.5);
    }

    /**
     * Рассчитать урон врага
     */
    private int calculateEnemyDamage(CombatEncounter encounter) {
        int baseDamage = 5 + (encounter.enemyLevel * 2);
        int variance = new Random().nextInt(3) + 1;
        double phaseMultiplier = encounter.bossPhase != null ? encounter.bossPhase.damageMultiplier : 1.0;
        return (int) ((baseDamage + variance) * phaseMultiplier);
    }

    /**
     * Попытка побега
     */
    private boolean attemptFlee(CombatEncounter encounter) {
        if (encounter.isBoss) return false; // Нельзя сбежать от босса
        return new Random().nextInt(100) < 30; // 30% шанс
    }

    /**
     * Обновить фазу босса
     */
    private void updateBossPhase(CombatEncounter encounter) {
        double hpPercent = (double) encounter.enemyHp / encounter.maxEnemyHp;

        if (hpPercent <= 0.25) {
            encounter.bossPhase = BossPhase.DESPERATE;
        } else if (hpPercent <= 0.5) {
            encounter.bossPhase = BossPhase.FRENZIED;
        } else if (hpPercent <= 0.75) {
            encounter.bossPhase = BossPhase.ENRAGED;
        } else {
            encounter.bossPhase = BossPhase.NORMAL;
        }
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
