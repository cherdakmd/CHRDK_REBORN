package ru.example.vkchatoffline.managers;

import ru.example.vkchatoffline.data.ActiveAdventure;

import java.util.Random;

/**
 * Состояния, благословения и их численные модификаторы для Offline Adventures.
 * Вынесено из AdventureManager без изменения формул и текстов.
 */
public final class OfflineStatusEffects {
    private OfflineStatusEffects() {}

    public static void applyCondition(Random random, ActiveAdventure adv, String condition, StringBuilder msg) {
        if (condition == null || condition.equals("none")) return;
        if (!"none".equals(adv.condition) && random.nextInt(100) < 50) return;
        adv.condition = condition;
        msg.append("🩸 Новое состояние: ").append(conditionText(condition)).append("\n");
    }

    public static String randomConditionFor(Random random, String type) {
        if (type.equals("trap") || type.equals("ambush")) return randomOf(random, "bleeding", "exhausted");
        if (type.equals("curse")) return "cursed";
        if (type.equals("survival")) return randomOf(random, "poisoned", "exhausted");
        return randomOf(random, "bleeding", "poisoned", "exhausted", "cursed", "burned");
    }

    public static String conditionText(String condition) {
        if (condition == null || condition.equals("none")) return "✅ без состояний";
        switch (condition) {
            case "bleeding": return "🩸 Кровотечение";
            case "poisoned": return "☠ Отравление";
            case "cursed": return "🧿 Проклятие";
            case "exhausted": return "🥱 Истощение";
            case "burned": return "🔥 Ожог";
            default: return "⚠ " + condition;
        }
    }

    public static void tickCondition(Random random, ActiveAdventure adv, StringBuilder msg) {
        if (adv.condition == null || adv.condition.equals("none")) return;
        if (adv.condition.equals("bleeding")) {
            adv.hp -= 3;
            msg.append("🩸 Кровотечение: -3 HP\n");
        } else if (adv.condition.equals("poisoned")) {
            adv.hp -= 2;
            adv.morale = Math.max(0, adv.morale - 3);
            msg.append("☠ Отравление: -2 HP, мораль -3%\n");
        } else if (adv.condition.equals("cursed")) {
            adv.morale = Math.max(0, adv.morale - 5);
            msg.append("🧿 Проклятие давит на разум: мораль -5%\n");
        } else if (adv.condition.equals("exhausted")) {
            adv.supplies = Math.max(0, adv.supplies - 1);
            msg.append("🥱 Истощение: припасы -1\n");
        } else if (adv.condition.equals("burned")) {
            adv.hp -= 2;
            msg.append("🔥 Ожог: -2 HP\n");
        }
        if (random.nextInt(100) < 18) {
            msg.append("✨ Состояние ослабло: ").append(conditionText(adv.condition)).append(" снято.\n");
            adv.condition = "none";
        }
    }

    public static String randomBlessing(Random random) {
        String[] b = {"power", "shadow", "fortune", "life", "wisdom"};
        return b[random.nextInt(b.length)];
    }

    public static String blessingText(String blessing) {
        if (blessing == null || blessing.equals("none")) return "без благословения";
        switch (blessing) {
            case "power": return "🌟 Сила";
            case "shadow": return "🌘 Тень";
            case "fortune": return "🍀 Удача";
            case "life": return "💖 Жизнь";
            case "wisdom": return "📜 Мудрость";
            default: return "🌟 " + blessing;
        }
    }

    public static int blessingRiskModifier(String blessing, String type, int choice) {
        if ("power".equals(blessing) && (type.equals("combat") || type.equals("boss") || type.equals("duel"))) return -5;
        if ("shadow".equals(blessing) && (type.equals("trap") || type.equals("ambush") || type.equals("heist"))) return -5;
        if ("fortune".equals(blessing) && (type.equals("treasure") || type.equals("artifact") || type.equals("rare"))) return -5;
        if ("life".equals(blessing) && (type.equals("survival") || type.equals("disease") || type.equals("curse"))) return -5;
        if ("wisdom".equals(blessing) && (type.equals("riddle") || type.equals("puzzle") || type.equals("shrine"))) return -5;
        return 0;
    }

    public static int blessingCheckModifier(String blessing, String type, int choice) {
        return blessingRiskModifier(blessing, type, choice) < 0 ? 2 : 0;
    }

    private static String randomOf(Random random, String... values) {
        return values[random.nextInt(values.length)];
    }
}
