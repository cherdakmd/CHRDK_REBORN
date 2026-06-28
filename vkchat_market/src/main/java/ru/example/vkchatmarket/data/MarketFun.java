package ru.example.vkchatmarket.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MarketFun {
    private final VKChatMarketPlugin plugin;

    // Flash Sale
    private String flashSaleItemId = null;
    private double flashSaleDiscount = 0;
    private long flashSaleEndTime = 0;

    // Квесты дня
    private String questDate = "";
    private String questItemId = null;
    private int questTarget = 0;
    private String questType = "sell"; // sell/buy
    private final Map<String, Integer> questProgress = new ConcurrentHashMap<>();
    private final Set<String> questCompleted = ConcurrentHashMap.newKeySet();

    // Рулетка: кулдаун
    private final Map<String, Long> rouletteCooldown = new ConcurrentHashMap<>();

    // Призовая таблица рулетки
    private static final RoulettePrize[] ROULETTE_PRIZES = {
        new RoulettePrize("💎 Алмаз", "DIAMOND", 1, 0.05),
        new RoulettePrize("🔮 Эндер-жемчуг", "ENDER_PEARL", 3, 0.10),
        new RoulettePrize("🔥 Огненный стержень", "BLAZE_ROD", 2, 0.08),
        new RoulettePrize("⚡ Редстоун-блок", "REDSTONE_BLOCK", 5, 0.12),
        new RoulettePrize("🍀 Изумруд", "EMERALD", 2, 0.10),
        new RoulettePrize("💀 Незеритовый лом", "NETHERITE_SCRAP", 1, 0.03),
        new RoulettePrize("🧪 Зелье удачи", "EXPERIENCE_BOTTLE", 10, 0.15),
        new RoulettePrize("🪙 Бонус репу", null, 0, 0.20), // +репутация
        new RoulettePrize("💀 Пусто", null, 0, 0.17), // ничего
    };

    public MarketFun(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ========================================
    // 🎰 РУЛЕТКА
    // ========================================

    public void spinRoulette(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink) для рулетки!");
            return;
        }

        // Кулдаун 5 минут
        long cooldown = plugin.getConfig().getLong("market2.roulette.cooldown-ms", 300000);
        Long last = rouletteCooldown.get(p.getName());
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - last)) / 1000;
            p.sendMessage(ChatColor.RED + "Рулетка перезаряжается! Подожди " + remaining + " сек.");
            return;
        }

        int cost = plugin.getConfig().getInt("market2.roulette.cost", 500);
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        rouletteCooldown.put(p.getName(), System.currentTimeMillis());

        // Анимация крутки
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        p.sendMessage(ChatColor.GOLD + "🎰 Рулетка крутится...");

        // Определяем приз через 2 секунды
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RoulettePrize prize = rollPrize();
            givePrize(p, prize, vkId);
        }, 40L);
    }

    private RoulettePrize rollPrize() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (RoulettePrize prize : ROULETTE_PRIZES) {
            cumulative += prize.chance;
            if (roll < cumulative) return prize;
        }
        return ROULETTE_PRIZES[ROULETTE_PRIZES.length - 1];
    }

    private void givePrize(Player p, RoulettePrize prize, int vkId) {
        if (prize.material == null && prize.amount == 0) {
            if (prize.name.contains("репу")) {
                // Бонус репутация
                int bonus = 100 + ThreadLocalRandom.current().nextInt(400);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
                p.sendMessage(ChatColor.GREEN + "🪙 Выиграл " + bonus + " репутации!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return;
        }

        // Предмет
        Material mat;
        try { mat = Material.valueOf(prize.material); } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "Ошибка приза!");
            return;
        }

        if (p.getInventory().addItem(new ItemStack(mat, prize.amount)).isEmpty()) {
            p.sendMessage(ChatColor.GREEN + "🎉 Выиграл: " + prize.name + " x" + prize.amount + "!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🎰 " + p.getName() + " выиграл в рулетке: " + prize.name + " x" + prize.amount + "!");
        } else {
            p.sendMessage(ChatColor.RED + "Инвентарь полон! Приз потерян...");
        }

        plugin.getMarketManager().addHistory("🎰 " + p.getName() + " выиграл: " + prize.name + " x" + prize.amount);
    }

    static class RoulettePrize {
        final String name;
        final String material;
        final int amount;
        final double chance;

        RoulettePrize(String name, String material, int amount, double chance) {
            this.name = name;
            this.material = material;
            this.amount = amount;
            this.chance = chance;
        }
    }

    // ========================================
    // ⚡ FLASH SALE
    // ========================================

    public void checkFlashSale() {
        if (System.currentTimeMillis() < flashSaleEndTime) return;

        double chance = plugin.getConfig().getDouble("market2.flash-sale.chance", 0.10);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        flashSaleItemId = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        flashSaleDiscount = 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4; // 30-70%
        long duration = plugin.getConfig().getLong("market2.flash-sale.duration-ms", 300000);
        flashSaleEndTime = System.currentTimeMillis() + duration;

        String name = plugin.getConfig().getString("items." + flashSaleItemId + ".name", flashSaleItemId);
        int percent = (int) (flashSaleDiscount * 100);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "⚡ [Flash Sale] " + ChatColor.YELLOW + name +
                ChatColor.LIGHT_PURPLE + " -" + percent + "% на 5 минут!");
        plugin.getMarketManager().addHistory("⚡ Flash Sale: " + name + " -" + percent + "%");
    }

    public boolean isFlashSaleActive(String itemId) {
        return flashSaleItemId != null && flashSaleItemId.equals(itemId) && System.currentTimeMillis() < flashSaleEndTime;
    }

    public double getFlashSaleDiscount() {
        return System.currentTimeMillis() < flashSaleEndTime ? flashSaleDiscount : 0;
    }

    public String getFlashSaleItemId() { return flashSaleItemId; }
    public long getFlashSaleEndTime() { return flashSaleEndTime; }

    // ========================================
    // 📋 КВЕСТЫ ДНЯ
    // ========================================

    public void ensureDailyQuest() {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (today.equals(questDate) && questItemId != null) return;

        questDate = today;
        questProgress.clear();
        questCompleted.clear();

        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        questItemId = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        questTarget = 16 + ThreadLocalRandom.current().nextInt(48); // 16-63
        questType = ThreadLocalRandom.current().nextBoolean() ? "sell" : "buy";

        String name = plugin.getConfig().getString("items." + questItemId + ".name", questItemId);
        Bukkit.broadcastMessage(ChatColor.AQUA + "📋 [Квест Дня] " + ChatColor.YELLOW +
                (questType.equals("sell") ? "Продай" : "Купи") + " " + name + " x" + questTarget +
                ChatColor.AQUA + " → награда 1000 реп!");
        plugin.getMarketManager().addHistory("📋 Квест: " + questType + " " + questItemId + " x" + questTarget);
    }

    public void recordQuestProgress(Player p, String itemId, int amount, String type) {
        if (!itemId.equals(questItemId) || !type.equals(questType)) return;
        if (questCompleted.contains(p.getName())) return;

        int current = questProgress.getOrDefault(p.getName(), 0) + amount;
        questProgress.put(p.getName(), current);

        if (current >= questTarget) {
            questCompleted.add(p.getName());
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                int reward = plugin.getConfig().getInt("market2.quest.reward", 1000);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
                p.sendMessage(ChatColor.GREEN + "📋 Квест выполнен! +" + reward + " репутации!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Bukkit.broadcastMessage(ChatColor.AQUA + "📋 " + p.getName() + " выполнил квест дня!");
            }
        } else {
            p.sendMessage(ChatColor.GRAY + "📋 Квест: " + current + "/" + questTarget);
        }
    }

    public String getQuestInfo() {
        if (questItemId == null) return "Нет активного квеста";
        String name = plugin.getConfig().getString("items." + questItemId + ".name", questItemId);
        return (questType.equals("sell") ? "Продай" : "Купи") + " " + name + " x" + questTarget + " → 1000 реп";
    }

    public String getQuestItemId() { return questItemId; }
    public String getQuestType() { return questType; }
    public int getQuestTarget() { return questTarget; }

    public int getPlayerQuestProgress(String playerName) {
        return questProgress.getOrDefault(playerName, 0);
    }

    public boolean isQuestCompleted(String playerName) {
        return questCompleted.contains(playerName);
    }

    // ========================================
    // УТИЛИТЫ
    // ========================================

    public void saveAll() {
        // Quest data is transient, no need to save
    }

    private static class ItemStack extends org.bukkit.inventory.ItemStack {
        ItemStack(Material material, int amount) {
            super(material, amount);
        }
    }
}
