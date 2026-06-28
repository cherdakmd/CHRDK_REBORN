package ru.example.vkchatoffline.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatoffline.utils.EventGenerator.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Результат одного события в походе.
 * Содержит всю информацию о том, что произошло и какие эффекты были применены.
 */
public class EventResult {
    private final String type;
    private final String description;
    private final int hpLoss;
    private final int hpGain;
    private final int repLoss;
    private final int repGain;
    private final List<ItemStack> items;
    private final List<String> rewards;
    private final String effect;
    private final boolean isBoss;

    public EventResult(String type, String description, int hpLoss, int hpGain, int repLoss,
                      List<ItemStack> items, List<String> rewards, String effect, boolean isBoss) {
        this.type = type;
        this.description = description;
        this.hpLoss = hpLoss;
        this.hpGain = hpGain;
        this.repLoss = repLoss;
        this.repGain = parseRepGain(rewards); // parse from rewards
        this.items = items != null ? items : new ArrayList<>();
        this.rewards = rewards != null ? rewards : new ArrayList<>();
        this.effect = effect;
        this.isBoss = isBoss;
    }

    private int parseRepGain(List<String> rewardsList) {
        int gain = 0;
        if (rewardsList != null) {
            for (String reward : rewardsList) {
                if (reward != null && reward.startsWith("rep:")) {
                    try {
                        gain += Integer.parseInt(reward.substring(4));
                    } catch (NumberFormatException e) {}
                }
            }
        }
        return gain;
    }

    // Геттеры
    public String getType() { return type; }
    public String getDescription() { return description; }
    public int getHpLoss() { return hpLoss; }
    public int getHpGain() { return hpGain; }
    public int getRepLoss() { return repLoss; }
    public int getRepGain() { return repGain; }
    public List<ItemStack> getItems() { return items; }
    public List<String> getRewards() { return rewards; }
    public String getEffect() { return effect; }
    public boolean isBoss() { return isBoss; }

    /**
     * Парсинг наград из строки формата "rep:100;item:emerald;hp:20".
     */
    public static Map<String, Integer> parseRewards(List<String> rewardStrings) {
        Map<String, Integer> results = new HashMap<>();
        if (rewardStrings == null) return results;

        for (String reward : rewardStrings) {
            if (reward == null || reward.isEmpty()) continue;

            String[] parts = reward.split(":");
            if (parts.length != 2) continue;

            String type = parts[0];
            int value;
            try {
                value = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }

            results.merge(type, value, Integer::sum);
        }
        return results;
    }

    /**
     * Получение суммы репутации из наград.
     */
    public int getRepGainFromRewards() {
        int gain = 0;
        for (String reward : rewards) {
            if (reward != null && reward.startsWith("rep:")) {
                try {
                    gain += Integer.parseInt(reward.substring(4));
                } catch (NumberFormatException e) {}
            }
        }
        return gain;
    }

    /**
     * Преобразование строковых предметов в ItemStack.
     */
    public static List<ItemStack> parseItems(List<String> itemStrings) {
        List<ItemStack> items = new ArrayList<>();
        if (itemStrings == null) return items;

        for (String itemStr : itemStrings) {
            if (itemStr == null || itemStr.isEmpty()) continue;

            String[] parts = itemStr.split(";");
            if (parts.length < 2) continue;

            try {
                Material material = Material.valueOf(parts[0].toUpperCase());
                int min = Integer.parseInt(parts[1]);
                int max = parts.length > 2 ? Integer.parseInt(parts[2]) : min;

                if (max < min) {
                    int temp = min;
                    min = max;
                    max = temp;
                }

                if (max > 0) {
                    int amount = min + ThreadLocalRandom.current().nextInt(max - min + 1);
                    items.add(new ItemStack(material, Math.max(1, amount)));
                }
            } catch (Exception e) {
                // Пропускаем некорректные предметы
            }
        }
        return items;
    }

    /**
     * Событие удачи (высокий шанс успеха).
     */
    public static EventResult luckyEvent(String description) {
        return new EventResult("luck", description, 0, 10, 0,
                null, Arrays.asList("rep:50"), null, false);
    }

    /**
     * Событие неудачи (негативный эффект).
     */
    public static EventResult unluckyEvent(String description) {
        return new EventResult("bad_luck", description, 20, 0, -20,
                null, null, "bad_luck", false);
    }

    /**
     * Создание результата события из конфига.
     */
    public static EventResult fromConfigEntry(String type, EventGenerator.LootEntry entry) {
        List<ItemStack> items = parseItems(entry.items);
        List<String> rewards = Arrays.asList(entry.reward);
        return new EventResult("loot", entry.description, 0, 0, 0,
                items, rewards, null, false);
    }

    public static EventResult fromConfigEntry(String type, EventGenerator.DangerEntry entry) {
        return new EventResult("danger", entry.description, entry.hpLoss, 0, entry.repLoss,
                null, null, null, false);
    }

    public static EventResult fromConfigEntry(String type, EventGenerator.EncounterEntry entry) {
        List<String> rewards = new ArrayList<>();
        if (entry.repGain > 0) rewards.add("rep:" + entry.repGain);
        if (entry.reward != null && !entry.reward.isEmpty()) rewards.add(entry.reward);
        return new EventResult("encounter", entry.description, entry.hpLoss, 0, entry.repLoss,
                null, rewards, entry.effect, false);
    }

    public static EventResult fromConfigEntry(String type, EventGenerator.EnvironmentalEntry entry) {
        return new EventResult("environmental", entry.description, entry.hpLoss, 0, entry.repLoss,
                null, null, entry.effect, false);
    }

    public static EventResult fromConfigEntry(String type, EventGenerator.SpecialEntry entry) {
        List<String> rewards = entry.reward != null && !entry.reward.isEmpty()
                ? Arrays.asList(entry.reward)
                : new ArrayList<>();
        return new EventResult("special", entry.description, entry.hpLoss, 0, entry.repLoss,
                null, rewards, null, entry.isBoss);
    }
}
