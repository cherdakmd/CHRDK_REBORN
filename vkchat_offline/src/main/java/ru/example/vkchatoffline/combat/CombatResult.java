package ru.example.vkchatoffline.combat;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

public class CombatResult {
    public final boolean success;
    public final String message;
    public final List<ItemStack> loot;
    public final Map<String, Integer> rewards;
    public final CombatEncounter encounter;

    public CombatResult(boolean success, String message, List<ItemStack> loot, Map<String, Integer> rewards, CombatEncounter encounter) {
        this.success = success;
        this.message = message;
        this.loot = loot;
        this.rewards = rewards;
        this.encounter = encounter;
    }

    public String getResultDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(message).append("\n\n");
        if (success && rewards != null) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("🏆 НАГРАДЫ\n");
            sb.append("═══════════════════════════════════════\n");
            if (rewards.containsKey("reputation")) sb.append("• Репутация: +").append(rewards.get("reputation")).append("\n");
            if (rewards.containsKey("xp")) sb.append("• Опыт: +").append(rewards.get("xp")).append("\n");
            if (rewards.containsKey("gold")) sb.append("• Золото: +").append(rewards.get("gold")).append("\n");
            if (loot != null && !loot.isEmpty()) {
                sb.append("• Предметы:\n");
                for (ItemStack item : loot) sb.append("  - ").append(item.getType().name()).append(" x").append(item.getAmount()).append("\n");
            }
        } else if (!success) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("💀 ПОРАЖЕНИЕ\n");
            sb.append("═══════════════════════════════════════\n");
        }
        return sb.toString();
    }
}
