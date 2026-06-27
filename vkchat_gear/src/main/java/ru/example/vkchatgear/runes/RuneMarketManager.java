package ru.example.vkchatgear.runes;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RuneMarketManager {
    private final VKChatGearPlugin plugin;
    private File file;
    private FileConfiguration data;

    private final Map<String, Integer> basePrices = new HashMap<>();
    private final Map<String, Double> currentPrices = new HashMap<>();
    private final Map<String, Integer> salesCount = new HashMap<>();

    public RuneMarketManager(VKChatGearPlugin plugin) {
        this.plugin = plugin;
        setupBases();
        load();
    }

    private void setupBases() {
        // Базовые цены на руны
        basePrices.put("vampirism", 1000);
        basePrices.put("poison_cloud", 800);
        basePrices.put("armor_piercing", 1200);
        basePrices.put("dodge", 1100);
        basePrices.put("fire_aura", 900);
        basePrices.put("reflect_magic", 1400);
        basePrices.put("execute", 1600);
        basePrices.put("meteor", 1300);
        basePrices.put("berserk", 1200);
        basePrices.put("soul_reaper", 1700);
        basePrices.put("shield", 1500);
        basePrices.put("second_wind", 2000);
        basePrices.put("life_steal", 1000);
        basePrices.put("fire_punch", 800);
        basePrices.put("paralyze", 1200);
        basePrices.put("absorption", 1100);
        basePrices.put("haste_aura", 1500);
        basePrices.put("rarity_seal", 1200);
        
        // Новые руны
        basePrices.put("vampire_aoe", 1500);
        basePrices.put("disintegration", 1800);
        basePrices.put("wind_glide", 1600);
        basePrices.put("frozen_touch", 1000);
        basePrices.put("soul_bond", 1200);
        basePrices.put("thunder_strike", 1100);
        basePrices.put("critical_strike", 1200);
        basePrices.put("golem_skin", 1100);
        basePrices.put("healing_aura", 1400);
        basePrices.put("ore_magnet", 1000);
        
        // Дополнительные супер-руны
        basePrices.put("spider_reflexes", 1600);
        basePrices.put("magma_walker", 1500);
        basePrices.put("meteor_shower", 1900);

        // Кристаллы и Свиток
        basePrices.put("crystal_common", 400);
        basePrices.put("crystal_rare", 900);
        basePrices.put("crystal_legendary", 1800);
        basePrices.put("safety_scroll", 1500);
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "runes_market.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);

        for (String id : basePrices.keySet()) {
            salesCount.put(id, data.getInt("runes." + id + ".sales", 0));
            currentPrices.put(id, data.getDouble("runes." + id + ".price", basePrices.get(id)));
        }
    }

    public void save() {
        for (String id : basePrices.keySet()) {
            data.set("runes." + id + ".sales", salesCount.getOrDefault(id, 0));
            data.set("runes." + id + ".price", currentPrices.getOrDefault(id, (double) basePrices.get(id)));
        }
        try { data.save(file); } catch (IOException ignored) {}
    }

    public int getPrice(String id) {
        double current = currentPrices.getOrDefault(id, (double) basePrices.getOrDefault(id, 1000));
        return (int) Math.round(current);
    }

    public int getSales(String id) {
        return salesCount.getOrDefault(id, 0);
    }

    public void recordPurchase(String boughtId) {
        // Увеличиваем продажи купленной руны
        salesCount.put(boughtId, getSales(boughtId) + 1);

        double base = basePrices.getOrDefault(boughtId, 1000);
        double current = currentPrices.getOrDefault(boughtId, base);

        // Повышаем цену купленного товара на +5% от базы за покупку (высокий спрос)
        // Максимальный предел цены — +200% от базовой цены
        double newPrice = Math.min(base * 3.0, current + (base * 0.05));
        currentPrices.put(boughtId, newPrice);

        // Понижаем цены на ВСЕ ОСТАЛЬНЫЕ руны на -1% от базы (спрос падает)
        // Минимальный порог цены — -50% от базовой цены (скидка до половины стоимости!)
        for (String id : basePrices.keySet()) {
            if (!id.equals(boughtId)) {
                double bOther = basePrices.get(id);
                double cOther = currentPrices.getOrDefault(id, bOther);
                double decreased = Math.max(bOther * 0.5, cOther - (bOther * 0.01));
                currentPrices.put(id, decreased);
            }
        }

        save();
    }
}
