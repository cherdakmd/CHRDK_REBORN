package ru.example.vkchatoffline.managers;

import java.util.Arrays;
import java.util.Locale;

/**
 * Классы оффлайн-походника: названия, нормализация, риск, снижение урона.
 * Вынесено из AdventureManager без изменения баланса.
 */
public final class OfflineClassManager {
    private OfflineClassManager() {}

    public static String className(String cls) {
        switch (cls) {
            case "warrior": return "⚔ Воин";
            case "scout": return "🏹 Следопыт";
            case "mage": return "🔮 Маг";
            case "cleric": return "🕯 Жрец";
            case "rogue": return "🗡 Разбойник";
            case "paladin": return "🛡 Паладин";
            case "ranger": return "🎯 Рейнджер";
            default: return "Новичок";
        }
    }

    public static int classRiskModifier(String cls, String type, int choice) {
        if ("warrior".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) return -6;
        if ("scout".equals(cls) && (type.equals("trap") || type.equals("survival") || type.equals("gathering"))) return -8;
        if ("mage".equals(cls) && (type.equals("curse") || type.equals("riddle") || type.equals("shrine"))) return -7;
        if ("cleric".equals(cls) && (type.equals("curse") || type.equals("survival") || type.equals("camp"))) return -5;
        if ("rogue".equals(cls) && (type.equals("trap") || type.equals("treasure") || type.equals("heist"))) return -10;
        if ("paladin".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("curse"))) return -5;
        if ("ranger".equals(cls) && (type.equals("combat") || type.equals("gathering") || type.equals("survival"))) return -6;
        return 0;
    }

    public static int applyClassDamageReduction(int damage, String cls, String type) {
        if ("warrior".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) damage = (int) Math.round(damage * 0.82);
        if ("scout".equals(cls) && type.equals("trap")) damage = (int) Math.round(damage * 0.75);
        if ("mage".equals(cls) && type.equals("curse")) damage = (int) Math.round(damage * 0.75);
        if ("cleric".equals(cls)) damage = (int) Math.round(damage * 0.90);
        if ("rogue".equals(cls) && type.equals("trap")) damage = (int) Math.round(damage * 0.60);
        if ("paladin".equals(cls) && (type.equals("combat") || type.equals("boss"))) damage = (int) Math.round(damage * 0.70);
        if ("ranger".equals(cls) && type.equals("combat")) damage = (int) Math.round(damage * 0.80);
        return Math.max(1, damage);
    }

    public static String normalizeClass(String raw) {
        if (raw == null) return "";
        String cls = raw.toLowerCase(Locale.ROOT);
        if (cls.equals("воин")) cls = "warrior";
        if (cls.equals("следопыт")) cls = "scout";
        if (cls.equals("маг")) cls = "mage";
        if (cls.equals("жрец")) cls = "cleric";
        if (cls.equals("разбойник")) cls = "rogue";
        if (cls.equals("паладин")) cls = "paladin";
        if (cls.equals("рейнджер")) cls = "ranger";
        return cls;
    }

    public static boolean isValidClass(String cls) {
        return Arrays.asList("warrior", "scout", "mage", "cleric", "rogue", "paladin", "ranger").contains(cls);
    }

    public static String chooseText() {
        return "╔══════════════════════╗\n        🧙 КЛАСС ПОХОДНИКА\n╚══════════════════════╝\n\n" +
                "Выбери класс командой или кнопкой:\n" +
                "⚔ Воин — лучше в боях и против боссов\n" +
                "🏹 Следопыт — меньше риск ловушек и выживания\n" +
                "🔮 Маг — сильнее против проклятий и загадок\n" +
                "🕯 Жрец — стабильнее, лечится и меньше страдает от проклятий\n" +
                "🗡 Разбойник — криты x2, обход ловушек, двойной лут\n" +
                "🛡 Паладин — +30% HP, лечение, сопротивление проклятиям\n" +
                "🎯 Рейнджер — +20% урона, отслеживание, выносливость";
    }
}
