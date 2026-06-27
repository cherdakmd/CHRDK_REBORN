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
            case "dragon_whelp": return "🐲 Детёныш Дракона";
            case "bear": return "🐻 Медведь";
            case "owl": return "🦉 Сова";
            case "snake": return "🐍 Змея";
            default: return "без спутника";
        }
    }

    public static int companionRiskModifier(String comp, String type, int choice) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("ambush") || type.equals("boss"))) return -5;
        if ("raven".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("gathering"))) return -5;
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("survival") || type.equals("shrine"))) return -4;
        if ("mule".equals(comp) && advSafeSupplyTypes(type)) return -3;
        if ("dragon_whelp".equals(comp) && (type.equals("combat") || type.equals("boss"))) return -7;
        if ("bear".equals(comp) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) return -6;
        if ("owl".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("riddle"))) return -6;
        if ("snake".equals(comp) && (type.equals("combat") || type.equals("ambush"))) return -4;
        return 0;
    }

    public static int companionCheckModifier(String comp, String type, int choice) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("boss"))) return 2;
        if ("raven".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("riddle"))) return 2;
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("shrine"))) return 2;
        if ("mule".equals(comp) && type.equals("survival")) return 1;
        if ("dragon_whelp".equals(comp) && (type.equals("combat") || type.equals("boss"))) return 3;
        if ("bear".equals(comp) && (type.equals("combat") || type.equals("boss"))) return 2;
        if ("owl".equals(comp) && (type.equals("trap") || type.equals("treasure") || type.equals("riddle"))) return 3;
        if ("snake".equals(comp) && (type.equals("combat") || type.equals("ambush"))) return 2;
        return 0;
    }

    public static boolean advSafeSupplyTypes(String type) {
        return type.equals("survival") || type.equals("gathering") || type.equals("camp");
    }

    public static int applyCompanionDamageReduction(int damage, String comp, String type) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("ambush") || type.equals("boss"))) damage = (int) Math.round(damage * 0.88);
        if ("alchemist".equals(comp) && (type.equals("curse") || type.equals("survival"))) damage = (int) Math.round(damage * 0.90);
        if ("dragon_whelp".equals(comp) && (type.equals("combat") || type.equals("boss"))) damage = (int) Math.round(damage * 0.80);
        if ("bear".equals(comp) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) damage = (int) Math.round(damage * 0.75);
        if ("snake".equals(comp) && type.equals("combat")) damage = (int) Math.round(damage * 0.90);
        return Math.max(1, damage);
    }

    public static void applyCompanionPositive(Random random, ActiveAdventure adv, String comp, String type, int choice, StringBuilder msg) {
        if ("wolf".equals(comp) && (type.equals("combat") || type.equals("boss")) && random.nextInt(100) < 35) {
            int extra = 8 + random.nextInt(12);
            VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, extra);
            msg.append("🐺 Волк добил цель: +").append(extra).append(" репутации\n");
        } else if ("raven".equals(comp) && (type.equals("treasure") || type.equals("trap")) && random.nextInt(100) < 35) {
            msg.append("🦅 Ворон заметил скрытый знак. Риск следующих ловушек ниже.\n");
            adv.morale = Math.min(100, adv.morale + 5);
        } else if ("alchemist".equals(comp) && random.nextInt(100) < 30) {
            int heal = 5 + random.nextInt(8);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            msg.append("🧪 Алхимик дал эликсир: +").append(heal).append(" HP\n");
        } else if ("mule".equals(comp) && random.nextInt(100) < 28) {
            adv.supplies++;
            msg.append("🐴 Мул донёс запас: припасы +1\n");
        } else if ("dragon_whelp".equals(comp) && (type.equals("combat") || type.equals("boss")) && random.nextInt(100) < 40) {
            int fireDmg = 10 + random.nextInt(15);
            VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, fireDmg);
            msg.append("🐲 Детёныш Дракона дыхнул огнём: +").append(fireDmg).append(" репутации\n");
        } else if ("bear".equals(comp) && (type.equals("combat") || type.equals("boss")) && random.nextInt(100) < 35) {
            int tankDmg = 6 + random.nextInt(10);
            VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, tankDmg);
            msg.append("🐻 Медведь сбил врага с ног: +").append(tankDmg).append(" репутации\n");
        } else if ("owl".equals(comp) && (type.equals("treasure") || type.equals("riddle")) && random.nextInt(100) < 40) {
            adv.morale = Math.min(100, adv.morale + 10);
            msg.append("🦉 Сова нашла скрытую подсказку: мораль +10\n");
        } else if ("snake".equals(comp) && type.equals("combat") && random.nextInt(100) < 30) {
            int poisonDmg = 5 + random.nextInt(8);
            VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, poisonDmg);
            msg.append("🐍 Змея укусила врага: +").append(poisonDmg).append(" репутации\n");
        }
    }

    public static void applyCompanionNegative(Random random, ActiveAdventure adv, String comp, String type, int choice, StringBuilder msg) {
        if ("wolf".equals(comp) && type.equals("ambush") && random.nextInt(100) < 25) {
            adv.hp = Math.min(adv.maxHp, adv.hp + 6);
            msg.append("🐺 Волк отвлёк засаду: часть урона предотвращена.\n");
        } else if ("raven".equals(comp) && type.equals("trap") && random.nextInt(100) < 25) {
            adv.morale = Math.min(100, adv.morale + 8);
            msg.append("🦅 Ворон предупредил об опасности слишком поздно, но спас мораль.\n");
        } else if ("mule".equals(comp) && adv.supplies <= 0 && random.nextInt(100) < 40) {
            adv.supplies = 1;
            msg.append("🐴 Мул нашёл последний сухарь: припасы = 1\n");
        } else if ("bear".equals(comp) && type.equals("ambush") && random.nextInt(100) < 30) {
            adv.hp = Math.min(adv.maxHp, adv.hp + 10);
            msg.append("🐻 Медведь прикрыл тебя от засады: +10 HP\n");
        } else if ("owl".equals(comp) && type.equals("trap") && random.nextInt(100) < 35) {
            adv.morale = Math.min(100, adv.morale + 5);
            msg.append("🦉 Сова предупредила о ловушке заранее: мораль +5\n");
        }
    }

    public static String normalizeCompanion(String raw) {
        if (raw == null) return "";
        String comp = raw.toLowerCase(Locale.ROOT);
        if (comp.equals("волк")) comp = "wolf";
        if (comp.equals("ворон")) comp = "raven";
        if (comp.equals("алхимик")) comp = "alchemist";
        if (comp.equals("мул")) comp = "mule";
        if (comp.equals("дракон")) comp = "dragon_whelp";
        if (comp.equals("медведь")) comp = "bear";
        if (comp.equals("сова")) comp = "owl";
        if (comp.equals("змея")) comp = "snake";
        return comp;
    }

    public static boolean isValidCompanion(String comp) {
        return Arrays.asList("wolf", "raven", "alchemist", "mule", "dragon_whelp", "bear", "owl", "snake").contains(comp);
    }

    public static String chooseText() {
        return "🐾 Спутник походника\n\n" +
                "Выбери спутника:\n" +
                "🐺 Волк — бой и боссы\n" +
                "🦅 Ворон — ловушки, тайники, загадки\n" +
                "🧪 Алхимик — лечение, проклятия, выживание\n" +
                "🐴 Мул — больше припасов\n" +
                "🐲 Детёныш Дракона — AoE огонь, страх, +50% наград\n" +
                "🐻 Медведь — танк, +40% защиты, урон по боссам\n" +
                "🦉 Сова — разведка, +30% к обнаружению тайников\n" +
                "🐍 Змея — яд на врагов, +25% к урону от дебаффов";
    }
}
