package ru.example.vkchatoffline.combat;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Результат боя
 */
public class CombatResult {
    public final boolean success;
    public final String message;
    public final List<ItemStack> loot;
    public final CombatEncounter encounter;

    public CombatResult(boolean success, String message, List<ItemStack> loot, CombatEncounter encounter) {
        this.success = success;
        this.message = message;
        this.loot = loot;
        this.encounter = encounter;
    }

    /**
     * Получить награды за победу
     */
    public int getReputationReward() {
        if (!success || encounter == null) return 0;
        int base = encounter.enemyLevel * 5;
        if (encounter.isBoss) base *= 3;
        return base;
    }

    /**
     * Получить опыт за победу
     */
    public int getXpReward() {
        if (!success || encounter == null) return 0;
        int base = encounter.enemyLevel * 10;
        if (encounter.isBoss) base *= 2;
        return base;
    }

    /**
     * Получить описание результата
     */
    public String getResultDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(message).append("\n\n");

        if (success && encounter != null) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("🏆 НАГРАДЫ\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append("• Репутация: +").append(getReputationReward()).append("\n");
            sb.append("• Опыт: +").append(getXpReward()).append("\n");

            if (loot != null && !loot.isEmpty()) {
                sb.append("• Предметы:\n");
                for (ItemStack item : loot) {
                    sb.append("  - ").append(item.getType().name()).append(" x").append(item.getAmount()).append("\n");
                }
            }
        } else if (encounter != null) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("💀 ПОРАЖЕНИЕ\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append("• Вы потеряли все припасы\n");
            sb.append("• Требуется лечение перед следующим походом\n");
        }

        return sb.toString();
    }
}
