package ru.example.vkchatoffline.combat;

import ru.example.vkchatoffline.character.CharacterManager;

public class CombatEncounter {
    public final int vkId;
    public final CombatManager.Enemy enemy;
    public final CharacterManager.CharacterData character;
    public final int maxRounds;
    public int round;

    public CombatEncounter(int vkId, CombatManager.Enemy enemy, CharacterManager.CharacterData character, int maxRounds) {
        this.vkId = vkId;
        this.enemy = enemy;
        this.character = character;
        this.maxRounds = maxRounds;
        this.round = 1;
    }

    public String getHpBar(int current, int max, int length) {
        int filled = (int)((double) current / max * length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) bar.append(i < filled ? "█" : "░");
        return bar.toString();
    }

    public String getCombatDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append(enemy.isBoss ? "☠️ БОСС: " : "⚔️ БОЙ: ");
        sb.append(enemy.name).append(" [Ур. ").append(enemy.level).append("]\n");
        sb.append("═══════════════════════════════════════\n\n");
        sb.append("❤️ Вы: ").append(character.hp).append("/").append(character.maxHp).append(" HP\n");
        sb.append("   ").append(getHpBar(character.hp, character.maxHp, 20)).append("\n\n");
        sb.append("❤️ Враг: ").append(enemy.hp).append("/").append(enemy.maxHp).append(" HP\n");
        sb.append("   ").append(getHpBar(enemy.hp, enemy.maxHp, 20)).append("\n");
        if (enemy.isBoss) sb.append("\n🏷️ Фаза: ").append(enemy.phase.displayName);
        sb.append("\n\n┌─────────────────────────────────────┐\n");
        sb.append("│ Раунд ").append(round).append("/").append(maxRounds).append("                           │\n");
        sb.append("│ [1] ⚔️ Атака  [2] 🛡️ Защита        │\n");
        sb.append("│ [3] 🔥 Способность  [4] 🧪 Зелье   │\n");
        sb.append("│ [5] 🏃 Побег                        │\n");
        sb.append("└─────────────────────────────────────┘\n");
        return sb.toString();
    }
}
