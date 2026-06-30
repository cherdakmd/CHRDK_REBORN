package ru.example.vkchatoffline.combat;

/**
 * Данные боя
 */
public class CombatEncounter {
    public final int vkId;
    public final String enemyName;
    public final int enemyLevel;
    public final int playerLevel;
    public final boolean isBoss;
    public final int maxRounds;
    public final long startTime;

    public int playerHp;
    public int maxPlayerHp;
    public int enemyHp;
    public int maxEnemyHp;
    public int round;
    public CombatManager.BossPhase bossPhase;

    public CombatEncounter(int vkId, String enemyName, int enemyLevel, int playerLevel, boolean isBoss) {
        this.vkId = vkId;
        this.enemyName = enemyName;
        this.enemyLevel = enemyLevel;
        this.playerLevel = playerLevel;
        this.isBoss = isBoss;
        this.maxRounds = isBoss ? 10 : 5;
        this.startTime = System.currentTimeMillis();

        // Рассчитать HP
        this.maxPlayerHp = 100 + (enemyLevel * 5);
        this.playerHp = maxPlayerHp;
        this.maxEnemyHp = isBoss ? 200 + (enemyLevel * 10) : 50 + (enemyLevel * 5);
        this.enemyHp = maxEnemyHp;

        this.round = 1;
        this.bossPhase = isBoss ? CombatManager.BossPhase.NORMAL : null;
    }

    /**
     * Получить процент HP игрока
     */
    public double getPlayerHpPercent() {
        return (double) playerHp / maxPlayerHp * 100;
    }

    /**
     * Получить процент HP врага
     */
    public double getEnemyHpPercent() {
        return (double) enemyHp / maxEnemyHp * 100;
    }

    /**
     * Получить строку HP
     */
    public String getHpBar(int current, int max, int length) {
        int filled = (int) ((double) current / max * length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }

    /**
     * Получить описание боя
     */
    public String getCombatDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append(isBoss ? "☠️ БОСС: " : "⚔️ БОЙ: ");
        sb.append(enemyName).append(" [Ур. ").append(enemyLevel).append("]\n");
        sb.append("═══════════════════════════════════════\n\n");

        // HP игрока
        sb.append("❤️ Вы: ").append(playerHp).append("/").append(maxPlayerHp).append(" HP\n");
        sb.append("   ").append(getHpBar(playerHp, maxPlayerHp, 20)).append("\n\n");

        // HP врага
        sb.append("❤️ Враг: ").append(enemyHp).append("/").append(maxEnemyHp).append(" HP\n");
        sb.append("   ").append(getHpBar(enemyHp, maxEnemyHp, 20)).append("\n");

        // Фаза босса
        if (isBoss && bossPhase != null) {
            sb.append("\n🏷️ Фаза: ").append(bossPhase.displayName);
        }

        sb.append("\n\n┌─────────────────────────────────────┐\n");
        sb.append("│ Раунд ").append(round).append("/").append(maxRounds).append("                           │\n");
        sb.append("│                                     │\n");
        sb.append("│ [1] ⚔️ Атака                        │\n");
        sb.append("│ [2] 🛡️ Защита                      │\n");
        sb.append("│ [3] 🔥 Способность                  │\n");
        sb.append("│ [4] 🧪 Предмет                      │\n");
        sb.append("│ [5] 🏃 Побег                        │\n");
        sb.append("└─────────────────────────────────────┘\n");
        sb.append("\n► Ваш выбор?");

        return sb.toString();
    }
}
