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
            default: return "Новичок";
        }
    }

    public static int classRiskModifier(String cls, String type, int choice) {
        if ("warrior".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) return -6;
        if ("scout".equals(cls) && (type.equals("trap") || type.equals("survival") || type.equals("gathering"))) return -8;
        if ("mage".equals(cls) && (type.equals("curse") || type.equals("riddle") || type.equals("shrine"))) return -7;
        if ("cleric".equals(cls) && (type.equals("curse") || type.equals("survival") || type.equals("camp"))) return -5;
        return 0;
    }

    public static int applyClassDamageReduction(int damage, String cls, String type) {
        if ("warrior".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) damage = (int) Math.round(damage * 0.82);
        if ("scout".equals(cls) && type.equals("trap")) damage = (int) Math.round(damage * 0.75);
        if ("mage".equals(cls) && type.equals("curse")) damage = (int) Math.round(damage * 0.75);
        if ("cleric".equals(cls)) damage = (int) Math.round(damage * 0.90);
        return Math.max(1, damage);
    }

    public static String normalizeClass(String raw) {
        if (raw == null) return "";
        String cls = raw.toLowerCase(Locale.ROOT);
        if (cls.equals("воин")) cls = "warrior";
        if (cls.equals("следопыт")) cls = "scout";
        if (cls.equals("маг")) cls = "mage";
        if (cls.equals("жрец")) cls = "cleric";
        return cls;
    }

    public static boolean isValidClass(String cls) {
        return Arrays.asList("warrior", "scout", "mage", "cleric").contains(cls);
    }

    public static String chooseText() {
        return "╔══════════════════════╗\n        🧙 КЛАСС ПОХОДНИКА\n╚══════════════════════╝\n\n" +
                "Выбери класс командой или кнопкой:\n" +
                "⚔ Воин — лучше в боях и против боссов\n" +
                "🏹 Следопыт — меньше риск ловушек и выживания\n" +
                "🔮 Маг — сильнее против проклятий и загадок\n" +
                "🕯 Жрец — стабильнее, лечится и меньше страдает от проклятий";
    }
}
