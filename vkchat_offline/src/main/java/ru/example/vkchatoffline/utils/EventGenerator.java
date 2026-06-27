package ru.example.vkchatoffline.utils;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatoffline.data.Expedition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Генератор случайных событий для походов.
 * Поддерживает разные категории событий: лут, враги, встречи, стихии, спецсобытия.
 */
public class EventGenerator {
    private final FileConfiguration config;
    private final Random random;

    public EventGenerator(FileConfiguration config) {
        this.config = config;
        this.random = new Random();
    }

    /**
     * Генерирует случайное событие на основе текущего этапа и локации.
     */
    public EventResult generateEvent(Expedition expedition) {
        int stage = expedition.getStage();
        String dungeonType = expedition.getDungeonType();

        // Выбираем категорию события на основе шансов
        String category = selectCategory();

        // ⛈️ Синхронизация с реальной погодой на сервере
        try {
            org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
            if ((world.hasStorm() || world.isThundering()) && stage > 1 && stage < expedition.getMaxStages()) {
                if (random.nextInt(100) < 60) {
                    category = "environmental";
                }
            }
        } catch (Exception ignored) {}

        // ⚔️ Офлайн-PvP дуэли (Призраки других игроков)
        if (random.nextInt(100) < 10 && stage >= 2 && stage < expedition.getMaxStages()) {
            return new EventResult(
                    "pvp",
                    "На твоем пути появляется таинственная полупрозрачная фигура...",
                    0, 0, 0, null, null, null, false
            );
        }

        switch (category) {
            case "loot":
                return generateLootEvent(stage, dungeonType);
            case "enemy":
                return generateEnemyEvent(stage, dungeonType);
            case "encounter":
                return generateEncounterEvent(stage, dungeonType);
            case "environmental":
                return generateEnvironmentalEvent(stage, dungeonType);
            case "special":
                return generateSpecialEvent(stage, dungeonType);
            case "trap":
                return generateTrapEvent(stage, dungeonType);
            case "npc":
                return generateNPCEncounter(stage, dungeonType);
            case "robbery":
                return generateRobberyEvent(stage, dungeonType);
            default:
                return generateDefaultEvent();
        }
    }

    /**
     * Выбор категории события по шансам из конфига.
     */
    private String selectCategory() {
        int lootChance = config.getInt("chances.loot", 20);
        int enemyChance = config.getInt("chances.enemy", 15);
        int encounterChance = config.getInt("chances.encounter", 15);
        int environmentalChance = config.getInt("chances.environmental", 10);
        int specialChance = config.getInt("chances.special", 10);
        int trapChance = config.getInt("chances.trap", 15);
        int robberyChance = config.getInt("chances.robbery", 15);

        int total = lootChance + enemyChance + encounterChance + environmentalChance + specialChance + trapChance + robberyChance;
        if (total <= 0) {
            return "loot";
        }

        int roll = random.nextInt(total);

        if (roll < lootChance) return "loot";
        roll -= lootChance;
        if (roll < enemyChance) return "enemy";
        roll -= enemyChance;
        if (roll < encounterChance) return "encounter";
        roll -= encounterChance;
        if (roll < environmentalChance) return "environmental";
        roll -= environmentalChance;
        if (roll < specialChance) return "special";
        roll -= specialChance;
        if (roll < trapChance) return "trap";
        return "robbery";
    }

    /**
     * Генерация события грабежа/разбоя.
     */
    private EventResult generateRobberyEvent(int stage, String dungeonType) {
        String[] banditTypes = {
            "Шайка лесных разбойников",
            "Беглые каторжники с рудников",
            "Профессиональная банда наемников",
            "Призрачный пират-мародер",
            "Орда злобных гоблинов-грабителей"
        };
        
        String[] banditDescs = {
            "Из лесной чащи с дикими криками выскакивают оборванцы с ржавыми топорами! Они плотным кольцом окружают тебя, требуя отдать ценности.",
            "На дороге преграждают путь изможденные, но свирепые беглые преступники в кандалах. В руках у них тяжелые кирки и цепи. Их намерения предельно ясны.",
            "Профессионально слаженный отряд наемников в темных плащах бесшумно перекрывает все пути отхода. Их предводитель делает шаг вперед и сухо предлагает сдать рюкзак.",
            "Воздух внезапно холодеет, и из тумана материализуется полупрозрачный силуэт древнего пирата с полуторной саблей. Он жаждет забрать твое золото и снаряжение в свою призрачную казну!",
            "С криками и гиканьем из кустов вываливается дюжина уродливых гоблинов с мешками. Они размахивают факелами и кинжалами, нагло пытаясь вырвать твою сумку с добычей прямо на ходу!"
        };

        int rIndex = random.nextInt(banditTypes.length);
        String title = "robbery: " + banditTypes[rIndex];
        String desc = banditDescs[rIndex];

        return new EventResult(
                title,
                desc,
                0, 0, 0,
                null, null,
                null,
                false
        );
    }

    /**
     * Генерация события лута.
     */
    private EventResult generateLootEvent(int stage, String dungeonType) {
        List<LootEntry> basicLoot = getLootEntries("loot.basic");
        List<LootEntry> rareLoot = getLootEntries("loot.rare");

        List<LootEntry> availableRare = rareLoot.stream()
                .filter(e -> stage >= e.minStage)
                .collect(Collectors.toList());

        List<LootEntry> pool = new ArrayList<>(basicLoot);
        if (!availableRare.isEmpty() && random.nextInt(100) >= 70) {
            pool = availableRare;
        }

        LootEntry selected = pickRandom(pool);
        if (selected == null) {
            return generateDefaultEvent();
        }

        List<ItemStack> items = EventResult.parseItems(selected.items);

        return new EventResult(
                "loot: " + selected.name,
                selected.description.isEmpty() ? "Ты нашел что-то интересное..." : selected.description,
                0, 0, 0,
                items,
                null,
                null,
                false
        );
    }

    /**
     * Генерация события врага/опасности.
     */
    private EventResult generateEnemyEvent(int stage, String dungeonType) {
        List<DangerEntry> minor = getDangerEntries("danger.minor");
        List<DangerEntry> medium = getDangerEntries("danger.medium");
        List<DangerEntry> major = getDangerEntries("danger.major");

        List<DangerEntry> available = new ArrayList<>();
        available.addAll(minor);
        if (stage >= 2) available.addAll(medium);
        if (stage >= 4) available.addAll(major);

        DangerEntry selected = pickRandom(available);
        if (selected == null) {
            return generateDefaultEvent();
        }

        return new EventResult(
                "danger: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repLoss,
                null, null,
                null,
                false
        );
    }

    /**
     * Генерация нейтрального события (NPC, загадка).
     */
    private EventResult generateEncounterEvent(int stage, String dungeonType) {
        List<EncounterEntry> helpful = getEncounterEntries("encounter.helpful");
        List<EncounterEntry> puzzle = getEncounterEntries("encounter.puzzle");
        List<EncounterEntry> temporary = getEncounterEntries("encounter.temporary");

        List<EncounterEntry> available = new ArrayList<>();
        available.addAll(helpful);
        available.addAll(puzzle);
        available.addAll(temporary);

        // Фильтр по минимальному этапу
        if (stage >= 2) {
            available = available.stream()
                    .filter(e -> stage >= e.minStage)
                    .collect(Collectors.toList());
        }

        EncounterEntry selected = pickRandom(available);
        if (selected == null) {
            return generateDefaultEvent();
        }

        // Для загадок - шанс успеха
        boolean success = random.nextInt(100) < 50;

        if (selected.type.equals("puzzle")) {
            return new EventResult(
                    "riddle",
                    selected.description,
                    selected.hpLoss, 0, selected.repLoss,
                    null, null,
                    selected.effect,
                    false
            );
        }

        return new EventResult(
                "encounter: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repGain,
                null, Arrays.asList(selected.reward),
                selected.effect,
                false
        );
    }

    /**
     * Генерация события стихии/погоды.
     */
    private EventResult generateEnvironmentalEvent(int stage, String dungeonType) {
        List<EnvironmentalEntry> entries = getEnvironmentalEntries("environmental");

        // Фильтр по минимальному этапу
        List<EnvironmentalEntry> available = entries.stream()
                .filter(e -> stage >= e.minStage)
                .collect(Collectors.toList());

        EnvironmentalEntry selected = pickRandom(available);
        if (selected == null) {
            return generateDefaultEvent();
        }

        return new EventResult(
                "environmental: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repLoss,
                null, null,
                selected.effect,
                false
        );
    }

    /**
     * Генерация специального события (босс, уникальный лут).
     */
    private EventResult generateSpecialEvent(int stage, String dungeonType) {
        List<SpecialEntry> boss = getSpecialEntries("special.boss");
        List<SpecialEntry> unique = getSpecialEntries("special.unique");

        List<SpecialEntry> available = new ArrayList<>();
        available.addAll(boss);
        available.addAll(unique);

        // Фильтр по минимальному этапу
        if (stage >= 3) {
            available = available.stream()
                    .filter(e -> stage >= e.minStage)
                    .collect(Collectors.toList());
        }

        SpecialEntry selected = pickRandom(available);
        if (selected == null) {
            return generateDefaultEvent();
        }

        boolean isBoss = boss.stream().anyMatch(entry -> entry.name.equals(selected.name));

        return new EventResult(
                "special: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repLoss,
                null, Arrays.asList(selected.reward),
                null,
                isBoss
        );
    }

    /**
     * Событие по умолчанию (если что-то пошло не так).
     */
    private EventResult generateDefaultEvent() {
        return new EventResult(
                "default:平淡的一天",
                "Сегодня ничего особенного не произошло. Продолжаем путь...",
                0, 0, 5,
                null, Arrays.asList("rep:10"), null, false
        );
    }

    /**
     * Генерация ловушки.
     */
    private EventResult generateTrapEvent(int stage, String dungeonType) {
        List<DangerEntry> traps = getDangerEntries("danger.traps");
        if (traps.isEmpty()) {
            return generateDefaultEvent();
        }

        DangerEntry selected = pickRandom(traps);
        if (selected == null) {
            return generateDefaultEvent();
        }
        return new EventResult(
                "trap: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repLoss,
                null, null,
                null,
                false
        );
    }

    /**
     * Генерация NPC-встречи.
     */
    private EventResult generateNPCEncounter(int stage, String dungeonType) {
        List<EncounterEntry> npcs = getEncounterEntries("encounter.npc");
        if (npcs.isEmpty()) {
            return generateDefaultEvent();
        }

        EncounterEntry selected = pickRandom(npcs);
        if (selected == null) {
            return generateDefaultEvent();
        }
        return new EventResult(
                "npc: " + selected.name,
                selected.description,
                selected.hpLoss, 0, selected.repLoss,
                null, Arrays.asList(selected.reward),
                selected.effect,
                false
        );
    }

    // --- Методы получения данных из конфига ---

    private <T> T pickRandom(List<T> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(random.nextInt(items.size()));
    }

    private List<Map<?, ?>> readEntryMaps(String path) {
        List<Map<?, ?>> maps = config.getMapList(path);
        if (maps != null && !maps.isEmpty()) {
            return maps;
        }

        if (!config.isConfigurationSection(path)) {
            return Collections.emptyList();
        }

        List<Map<?, ?>> fromSection = new ArrayList<>();
        for (String key : config.getConfigurationSection(path).getKeys(false)) {
            String base = path + "." + key;
            if (!config.isConfigurationSection(base)) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (String field : config.getConfigurationSection(base).getKeys(false)) {
                map.put(field, config.get(base + "." + field));
            }
            fromSection.add(map);
        }
        return fromSection;
    }

    private String str(Map<?, ?> map, String key, String def) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : def;
    }

    private int num(Map<?, ?> map, String key, int def) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private List<LootEntry> getLootEntries(String path) {
        List<LootEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : readEntryMaps(path)) {
            entries.add(new LootEntry(
                    str(map, "name", "Находка"),
                    str(map, "type", "item"),
                    str(map, "description", ""),
                    num(map, "chance", 100),
                    num(map, "min_stage", 1),
                    stringList(map, "items"),
                    str(map, "reward", "")
            ));
        }
        return entries;
    }

    private List<DangerEntry> getDangerEntries(String path) {
        List<DangerEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : readEntryMaps(path)) {
            entries.add(new DangerEntry(
                    str(map, "name", "Опасность"),
                    num(map, "hp_loss", 10),
                    num(map, "rep_loss", 0),
                    num(map, "chance", 100),
                    num(map, "min_stage", 1),
                    str(map, "description", "")
            ));
        }
        return entries;
    }

    private List<EncounterEntry> getEncounterEntries(String path) {
        List<EncounterEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : readEntryMaps(path)) {
            entries.add(new EncounterEntry(
                    str(map, "name", "Встреча"),
                    str(map, "type", "helpful"),
                    num(map, "hp_loss", 0),
                    num(map, "rep_gain", 0),
                    num(map, "rep_loss", 0),
                    num(map, "chance", 100),
                    num(map, "min_stage", 1),
                    str(map, "description", ""),
                    str(map, "reward", ""),
                    str(map, "success", ""),
                    str(map, "fail", ""),
                    str(map, "effect", "")
            ));
        }
        return entries;
    }

    private List<EnvironmentalEntry> getEnvironmentalEntries(String path) {
        List<EnvironmentalEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : readEntryMaps(path)) {
            entries.add(new EnvironmentalEntry(
                    str(map, "name", "Стихия"),
                    num(map, "hp_loss", 0),
                    num(map, "rep_loss", 0),
                    num(map, "chance", 100),
                    num(map, "min_stage", 1),
                    str(map, "description", ""),
                    str(map, "effect", "")
            ));
        }
        return entries;
    }

    private List<SpecialEntry> getSpecialEntries(String path) {
        List<SpecialEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : readEntryMaps(path)) {
            entries.add(new SpecialEntry(
                    str(map, "name", "Особое событие"),
                    num(map, "hp_loss", 0),
                    num(map, "rep_loss", 0),
                    num(map, "chance", 100),
                    num(map, "min_stage", 1),
                    str(map, "description", ""),
                    str(map, "reward", ""),
                    Boolean.TRUE.equals(map.get("is_boss"))
            ));
        }
        return entries;
    }

    // --- Классы данных ---

    public static class LootEntry {
        public final String name;
        public final String type;
        public final String description;
        public final int chance;
        public final int minStage;
        public final List<String> items;
        public final String reward;

        public LootEntry(String name, String type, String description, int chance, int minStage,
                        List<String> items, String reward) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.chance = chance;
            this.minStage = minStage;
            this.items = items;
            this.reward = reward;
        }
    }

    public static class DangerEntry {
        public final String name;
        public final int hpLoss;
        public final int repLoss;
        public final int chance;
        public final int minStage;
        public final String description;

        public DangerEntry(String name, int hpLoss, int repLoss, int chance, int minStage, String description) {
            this.name = name;
            this.hpLoss = hpLoss;
            this.repLoss = repLoss;
            this.chance = chance;
            this.minStage = minStage;
            this.description = description;
        }
    }

    public static class EncounterEntry {
        public final String name;
        public final String type;
        public final int hpLoss;
        public final int repGain;
        public final int repLoss;
        public final int chance;
        public final int minStage;
        public final String description;
        public final String reward;
        public final String successReward;
        public final String failReward;
        public final String effect;

        public EncounterEntry(String name, String type, int hpLoss, int repGain, int repLoss, int chance,
                             int minStage, String description, String reward, String successReward,
                             String failReward, String effect) {
            this.name = name;
            this.type = type;
            this.hpLoss = hpLoss;
            this.repGain = repGain;
            this.repLoss = repLoss;
            this.chance = chance;
            this.minStage = minStage;
            this.description = description;
            this.reward = reward;
            this.successReward = successReward;
            this.failReward = failReward;
            this.effect = effect;
        }
    }

    public static class EnvironmentalEntry {
        public final String name;
        public final int hpLoss;
        public final int repLoss;
        public final int chance;
        public final int minStage;
        public final String description;
        public final String effect;

        public EnvironmentalEntry(String name, int hpLoss, int repLoss, int chance, int minStage,
                                  String description, String effect) {
            this.name = name;
            this.hpLoss = hpLoss;
            this.repLoss = repLoss;
            this.chance = chance;
            this.minStage = minStage;
            this.description = description;
            this.effect = effect;
        }
    }

    public static class SpecialEntry {
        public final String name;
        public final int hpLoss;
        public final int repLoss;
        public final int chance;
        public final int minStage;
        public final String description;
        public final String reward;
        public final boolean isBoss;

        public SpecialEntry(String name, int hpLoss, int repLoss, int chance, int minStage,
                           String description, String reward, boolean isBoss) {
            this.name = name;
            this.hpLoss = hpLoss;
            this.repLoss = repLoss;
            this.chance = chance;
            this.minStage = minStage;
            this.description = description;
            this.reward = reward;
            this.isBoss = isBoss;
        }
    }
}
