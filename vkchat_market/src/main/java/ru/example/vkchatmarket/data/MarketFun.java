package ru.example.vkchatmarket.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MarketFun — Flash Sale и Квесты дня
 */
public class MarketFun {
    private final VKChatMarketPlugin plugin;

    // ═══ FLASH SALE ═══
    private String flashSaleItemId = null;
    private double flashSaleDiscount = 0;
    private long flashSaleEndTime = 0;

    // ═══ КВЕСТЫ ДНЯ ═══
    private String questDate = "";
    private String questItemId = null;
    private int questTarget = 0;
    private String questType = "sell";
    private final Map<String, Integer> questProgress = new ConcurrentHashMap<>();
    private final Set<String> questCompleted = ConcurrentHashMap.newKeySet();

    public MarketFun(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════
    // FLASH SALE
    // ═══════════════════════════════════════

    public void checkFlashSale() {
        if (System.currentTimeMillis() < flashSaleEndTime) return;

        double chance = plugin.getConfig().getDouble("market2.flash-sale.chance", 0.10);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        flashSaleItemId = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        flashSaleDiscount = 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
        long duration = plugin.getConfig().getLong("market2.flash-sale.duration-ms", 300000);
        flashSaleEndTime = System.currentTimeMillis() + duration;

        String name = plugin.getConfig().getString("items." + flashSaleItemId + ".name", flashSaleItemId);
        int percent = (int) (flashSaleDiscount * 100);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "⚡ [Flash Sale] " + ChatColor.YELLOW + name +
                ChatColor.LIGHT_PURPLE + " -" + percent + "% на 5 минут!");
    }

    public boolean isFlashSaleActive(String itemId) {
        return flashSaleItemId != null && flashSaleItemId.equals(itemId) && System.currentTimeMillis() < flashSaleEndTime;
    }

    public double getFlashSaleDiscount() {
        return System.currentTimeMillis() < flashSaleEndTime ? flashSaleDiscount : 0;
    }

    public String getFlashSaleItemId() { return flashSaleItemId; }
    public long getFlashSaleEndTime() { return flashSaleEndTime; }

    // ═══════════════════════════════════════
    // КВЕСТЫ ДНЯ
    // ═══════════════════════════════════════

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
        questTarget = 16 + ThreadLocalRandom.current().nextInt(48);
        questType = ThreadLocalRandom.current().nextBoolean() ? "sell" : "buy";

        String name = plugin.getConfig().getString("items." + questItemId + ".name", questItemId);
        Bukkit.broadcastMessage(ChatColor.AQUA + "📋 [Квест Дня] " + ChatColor.YELLOW +
                (questType.equals("sell") ? "Продай" : "Купи") + " " + name + " x" + questTarget +
                ChatColor.AQUA + " → награда 1000 реп!");
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
    public int getPlayerQuestProgress(String playerName) { return questProgress.getOrDefault(playerName, 0); }
    public boolean isQuestCompleted(String playerName) { return questCompleted.contains(playerName); }
}
