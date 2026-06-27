package ru.example.vkchatoffline.managers;

import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.data.ActiveAdventure;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Спутники Offline Adventures: названия, модификаторы, реакции.
 * Вынесено из AdventureManager без изменения текущей механики.
 */
public final class OfflineCompanionManager {
    private OfflineCompanionManager() {}

    public static String companionName(String c) {
        switch (c) {
            case "wolf": return "🐺 Волк";
            case "raven": return "🦅 Ворон";
            case "alchemist": return "🧪 Алхимик";
            case "mule": return "🐴 Мул";
            default: return "без спутника";
        }
    }

    public static int companionRiskModifier(String comp, String type, int choice) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("ambush") || type.equals("boss"))) return -5;
        if ("raven".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("gathering"))) return -5;
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("survival") || type.equals("shrine"))) return -4;
        if ("mule".equals(comp) && advSafeSupplyTypes(type)) return -3;
        return 0;
    }

    public static int companionCheckModifier(String comp, String type, int choice) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("boss"))) return 2;
        if ("raven".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("riddle"))) return 2;
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("shrine"))) return 2;
        if ("mule".equals(comp) && type.equals("survival")) return 1;
        return 0;
    }

    public static boolean advSafeSupplyTypes(String type) {
        return type.equals("survival") || type.equals("gathering") || type.equals("camp");
    }

    public static int applyCompanionDamageReduction(int damage, String comp, String type) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("ambush") || type.equals("boss"))) damage = (int) Math.round(damage * 0.88);
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("survival"))) damage = (int) Math.round(damage * 0.90);
        return Math.max(1, damage);
    }

    public static void applyCompanionPositive(Random random, ActiveAdventure adv, String comp, String type, int choice, StringBuilder msg) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("boss")) && random.nextInt(100) < 35) {
            int extra = 8 + random.nextInt(12);
            VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, extra);
            msg.append("🐺 Волк добил цель: +").append(extra).append(" репутации\\n");
        } else if ("raven".equals(comp) && (type.equals("treasure") || type.equals("trap")) && random.nextInt(100) < 35) {
            msg.append("🦅 Ворон заметил скрытый знак. Риск следующих ловушек ниже.\\n");
            adv.morale = Math.min(100, adv.morale + 5);
        } else if ("alchemist".equals(comp) && random.nextInt(100) < 30) {
            int heal = 5 + random.nextInt(8);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            msg.append("🧪 Алхимик дал эликсир: +").append(heal).append(" HP\\n");
        } else if ("mule".equals(comp) && random.nextInt(100) < 28) {
            adv.supplies++;
            msg.append("🐴 Мул донёс запас: припасы +1\\n");
        }
    }

    public static void applyCompanionNegative(Random random, ActiveAdventure adv, String comp, String type, int choice, StringBuilder msg) {
        if ("wolf".equals(comp) && type.equals("ambush") && random.nextInt(100) < 25) {
            adv.hp = Math.min(adv.maxHp, adv.hp + 6);
            msg.append("🐺 Волк отвлёк засаду: часть урона предотвращена.\\n");
        } else if ("raven".equals(comp) && type.equals("trap") && random.nextInt(100) < 25) {
            adv.morale = Math.min(100, adv.morale + 8);
            msg.append("🦅 Ворон предупредил об опасности слишком поздно, но спас мораль.\\n");
        } else if ("mule".equals(comp) && adv.supplies <= 0 && random.nextInt(100) < 40) {
            adv.supplies = 1;
            msg.append("🐴 Мул нашёл последний сухарь: припасы = 1\\n");
        }
    }

    public static String normalizeCompanion(String raw) {
        if (raw == null) return "";
        String comp = raw.toLowerCase(Locale.ROOT);
        if (comp.equals("волк")) comp = "wolf";
        if (comp.equals("ворон")) comp = "raven";
        if (comp.equals("алхимик")) comp = "alchemist";
        if (comp.equals("мул")) comp = "mule";
        return comp;
    }

    public static boolean isValidCompanion(String comp) {
        return Arrays.asList("wolf", "raven", "alchemist", "mule").contains(comp);
    }

    public static String chooseText() {
        return "🐾 Спутник походника\\n\\n" +
                "Выбери спутника:\\n" +
                "🐺 Волк — бой и боссы\\n" +
                "🦅 Ворон — ловушки, тайники, загадки\\n" +
                "🧪 Алхимик — лечение, проклятия, выживание\\n" +
                "🐴 Мул — больше припасов";
    }
}
