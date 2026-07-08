package ru.example.vkchatevents.cataclysm;

import org.bukkit.ChatColor;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;

/**
 * CataclysmRegistry — реестр катаклизмов и благословений.
 *
 * Извлечён из WrathManager (1268 строк, 16 катаклизмов в if-else).
 * Заменяет:
 * 1. Гигантский if-else в onVKCommand() → alias → ID resolution
 * 2. Хардкод массивов типов → getAllIds() / getRandomWeightedId()
 *
 * Каждый катаклизм регистрируется с:
 * - Уникальным ID (acid_rain, earthquake, ...)
 * - Алиасами для VK-команд (дождь, rain, ...)
 * - Флагом positive (благословение или катаклизм)
 * - Названием для VK-ответов
 */
public class CataclysmRegistry {

    private final VKChatEventsPlugin plugin;

    /** ID → определение катаклизма */
    private final Map<String, CataclysmDef> defs = new LinkedHashMap<>();

    /** Алиас (lowercase) → ID (для VK-команд) */
    private final Map<String, String> aliasToId = new HashMap<>();

    /** Все зарегистрированные ID (для случайного выбора) */
    private final List<String> allIds = new ArrayList<>();

    // ═══════════════════════════════════════
    // Определение катаклизма
    // ═══════════════════════════════════════

    public static class CataclysmDef {
        private final String id;
        private final String displayName;      // Название для VK-ответов
        private final String[] aliases;        // VK-команда алиасы
        private final boolean positive;        // true = благословение

        public CataclysmDef(String id, String displayName, String[] aliases, boolean positive) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = aliases;
            this.positive = positive;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String[] getAliases() { return aliases; }
        public boolean isPositive() { return positive; }
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    public CataclysmRegistry(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        registerAll();
        plugin.getLogger().info("[CataclysmRegistry] Зарегистрировано " + defs.size() +
                " катаклизмов/благословений, " + aliasToId.size() + " алиасов");
    }

    // ═══════════════════════════════════════
    // Регистрация (алиасы → starter)
    // ═══════════════════════════════════════

    private void registerAll() {
        // Все катаклизмы делегируют к WrathManager.startCataclysm(id)
        // Вместо отдельных методов — прямой вызов startCataclysm через реестр
        // Это позволяет CataclysmRegistry управлять алиасами и VK-командами,
        // а логика катаклизмов остаётся в WrathManager

        register("acid_rain", "Кислотный Дождь",
                new String[]{"дождь", "rain"}, false);

        register("earthquake", "Землетрясение",
                new String[]{"земля", "earth"}, false);

        register("tempest", "Грозовой Шторм",
                new String[]{"шторм", "storm"}, false);

        register("meteor_shower", "Метеоритный Дождь",
                new String[]{"метеорит", "meteor"}, false);

        register("blizzard", "Снежный Буран",
                new String[]{"буран", "blizzard"}, false);

        register("eclipse", "Солнечное Затмение",
                new String[]{"затмение", "eclipse"}, false);

        register("reputation_bloom", "Золотой Век",
                new String[]{"золото", "gold", "bloom"}, true);

        register("angelic_grace", "Ангельская Благодать",
                new String[]{"благо", "grace", "angel"}, true);

        register("star_shower", "Звездопад Желаний",
                new String[]{"звезда", "star", "shower"}, true);

        register("geysers", "Гейзеры",
                new String[]{"гейзер", "geyser", "geysers"}, false);

        register("blood_moon_hunt", "Кровавая Луна",
                new String[]{"луна", "moon", "blood", "blood_moon"}, false);

        register("treasure_comet", "Комета Сокровищ",
                new String[]{"комета", "comet", "treasure", "treasure_comet"}, false);

        register("station_fall", "Падение Космической Станции",
                new String[]{"станция", "station", "station_fall"}, false);

        register("fog_shadows", "Туман Теней",
                new String[]{"туман", "fog", "fog_shadows", "тени"}, false);

        register("plasma_storm", "Плазменный Шторм",
                new String[]{"плазма", "plasma", "plasma_storm"}, false);

        register("gravity_anomaly", "Извращение Гравитации",
                new String[]{"гравитация", "gravity", "gravity_anomaly"}, false);
    }

    private void register(String id, String displayName, String[] aliases, boolean positive) {
        CataclysmDef def = new CataclysmDef(id, displayName, aliases, positive);
        defs.put(id, def);
        allIds.add(id);
        for (String alias : aliases) {
            aliasToId.put(alias.toLowerCase(java.util.Locale.ROOT), id);
        }
    }

    // ═══════════════════════════════════════
    // Lookup methods
    // ═══════════════════════════════════════

    /** Разрешить алиас (VK-команда) в ID катаклизма */
    public String resolveAlias(String alias) {
        if (alias == null) return null;
        return aliasToId.get(alias.toLowerCase(java.util.Locale.ROOT));
    }

    /** Получить определение по ID */
    public CataclysmDef getDef(String id) {
        return defs.get(id);
    }

    /** Все ID (для случайного выбора) */
    public List<String> getAllIds() {
        return Collections.unmodifiableList(allIds);
    }

    /** Получить взвешенный случайный ID */
    public String getRandomWeightedId() {
        java.util.List<String> weighted = new java.util.ArrayList<>();
        for (String t : allIds) {
            double w = plugin.getConfig().getDouble(
                    "wrath.cataclysms.auto-spawn.weights." + t, 1.0);
            int count = (int) Math.max(1, Math.round(w * 10));
            for (int i = 0; i < count; i++) weighted.add(t);
        }
        return weighted.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(weighted.size()));
    }

    /** Сформировать список доступных типов для VK-помощи */
    public String getAvailableTypes() {
        StringBuilder sb = new StringBuilder();
        for (CataclysmDef def : defs.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(def.getAliases()[0]); // первый алиас как основной
        }
        sb.append(", босс");
        return sb.toString();
    }
}
