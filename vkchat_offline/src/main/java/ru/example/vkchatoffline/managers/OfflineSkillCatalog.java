package ru.example.vkchatoffline.managers;

/**
 * Каталог навыков оффлайн-походника.
 * Вынесен из AdventureManager как безопасный шаг рефакторинга.
 */
public final class OfflineSkillCatalog {
    private OfflineSkillCatalog() {}

    public static String[][] defs() {
        return new String[][]{
                {"tough", "Живучесть", "+15 HP и меньше урона"},
                {"sharp", "Клинок", "+2 к боевым проверкам"},
                {"trap_sense", "Ловушки", "лучше против ловушек"},
                {"lucky", "Удача", "шанс +20 реп. за успех"},
                {"trader", "Торговец", "скидка 10% в лавке и +8% к финальной репе"},
                {"occult", "Оккультизм", "лучше против мистики"},
                {"herbalist", "Травник", "зелья и лечение эффективнее"},
                {"packer", "Носильщик", "+2 припаса в походе"}
        };
    }
}
