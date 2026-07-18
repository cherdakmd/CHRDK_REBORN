package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер шахтёрских смен — отправка игроков на смены, таймеры, награды
 */
public class ShiftManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, ShiftData> activeShifts = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> shiftHistory = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastShiftEnd = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastInGameShiftStart = new ConcurrentHashMap<>();
    private final File shiftsFile;
    private FileConfiguration shiftsCfg;

    public static class ShiftData {
        public String shiftKey;
        public long startTime;
        public long endTime;
        public boolean completed;
        public boolean claimed;
        public boolean notifiedHalfway;
    }

    public ShiftManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.shiftsFile = new File(plugin.getDataFolder(), "shifts.yml");
        startCheckTask();
    }

    private void startCheckTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<Integer, ShiftData> e : activeShifts.entrySet()) {
                ShiftData sd = e.getValue();
                if (!sd.completed && now >= sd.endTime) {
                    sd.completed = true;
                    notifyPlayer(e.getKey(), sd.shiftKey);
                } else if (!sd.completed && !sd.notifiedHalfway) {
                    // Уведомление на середине для длинных смен (≥6 часов)
                    long total = sd.endTime - sd.startTime;
                    long elapsed = now - sd.startTime;
                    if (total >= 21600000 && elapsed >= total / 2) { // ≥6 часов
                        sd.notifiedHalfway = true;
                        long left = sd.endTime - now;
                        long hrsLeft = left / 3600000;
                        notifyProgress(e.getKey(), hrsLeft);
                    }
                }
            }
        }, 600L, 600L); // Каждые 30 сек
    }

    private void notifyPlayer(int vkId, String shiftKey) {
        UUID uuid = getPlayerUuid(vkId);
        Player onlinePlayer = uuid != null ? Bukkit.getPlayer(uuid) : null;
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            List<ItemStack> items = generateShiftItems(shiftKey);
            plugin.getStashManager().addItems(onlinePlayer.getUniqueId(), items);
            notifyInGamePlayer(onlinePlayer, shiftKey, items.size());
        }
        try {
            VKChatBridge.sendMessage(vkId,
                    "⛏ Смена '" + getShiftName(shiftKey) + "' завершена! Напиши !шахта чтобы забрать награды.");
            VKChatBridge.sendKeyboard(vkId,
                    "Смена завершена!", Keyboards.shiftDone());
        } catch (Exception e) { plugin.getLogger().warning("VK notify failed: " + e.getMessage()); }
    }

    private void notifyInGamePlayer(Player player, String shiftKey, int itemCount) {
        try {
            String msg = "§a⛏ Смена '" + getShiftName(shiftKey) + "' завершена! §e+" + itemCount + " предметов в /stash";
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
        } catch (Exception ignored) {}
    }

    private List<ItemStack> generateShiftItems(String key) {
        List<ItemStack> items = new ArrayList<>();
        Random rnd = ThreadLocalRandom.current();
        List<String> resList = plugin.getConfig().getStringList("shifts." + key + ".resources");
        for (String entry : resList) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    Material mat = Material.valueOf(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    int amount = rnd.nextInt(max - min + 1) + min;
                    items.add(new ItemStack(mat, amount));
                } catch (Exception ignored) {}
            }
        }
        return items;
    }

    private void notifyProgress(int vkId, long hoursLeft) {
        try {
            VKChatBridge.sendMessage(vkId,
                    "⛏ Ты на середине смены! Осталось примерно " + hoursLeft + " ч. Продолжай копать!");
        } catch (Exception e) { plugin.getLogger().warning("VK progress notify failed: " + e.getMessage()); }
    }

    public boolean startShift(int vkId, String shiftKey) {
        return startShift(vkId, shiftKey, false);
    }

    public boolean startShift(int vkId, String shiftKey, boolean inGame) {
        if (activeShifts.containsKey(vkId)) {
            ShiftData existing = activeShifts.get(vkId);
            if (!existing.completed) return false;
        }

        int cooldownMinutes = plugin.getConfig().getInt("settings.cooldown-minutes", 0);
        if (cooldownMinutes > 0) {
            Long lastEnd = lastShiftEnd.get(vkId);
            if (lastEnd != null && System.currentTimeMillis() - lastEnd < cooldownMinutes * 60000L) {
                return false;
            }
        }

        if (inGame) {
            lastInGameShiftStart.put(vkId, System.currentTimeMillis());
        }

        int minutes = plugin.getConfig().getInt("shifts." + shiftKey + ".duration-minutes", 60);
        ShiftData sd = new ShiftData();
        sd.shiftKey = shiftKey;
        sd.startTime = System.currentTimeMillis();
        sd.endTime = sd.startTime + (minutes * 60000L);
        sd.completed = false;
        sd.claimed = false;
        activeShifts.put(vkId, sd);
        saveShifts();
        return true;
    }

    public boolean cancelShift(int vkId) {
        ShiftData sd = activeShifts.get(vkId);
        if (sd == null || sd.completed) return false;
        activeShifts.remove(vkId);
        saveShifts();
        return true;
    }

    public ShiftData getShift(int vkId) {
        return activeShifts.get(vkId);
    }

    public boolean hasActiveShift(int vkId) {
        ShiftData sd = activeShifts.get(vkId);
        return sd != null && !sd.completed;
    }

    public boolean hasCompletedShift(int vkId) {
        ShiftData sd = activeShifts.get(vkId);
        return sd != null && sd.completed && !sd.claimed;
    }

    public String getShiftStatus(int vkId) {
        ShiftData sd = activeShifts.get(vkId);
        int history = shiftHistory.getOrDefault(vkId, 0);
        if (sd == null) {
            String base = "Нет активной смены";
            if (history > 0) base += " | Выполнено смен: " + history;
            return base;
        }
        if (!sd.completed) {
            long left = sd.endTime - System.currentTimeMillis();
            if (left <= 0) { sd.completed = true; return "✅ Смена завершена! Забери награды."; }
            long hrs = left / 3600000;
            long mins = (left % 3600000) / 60000;
            return "⛏ В шахте | " + getShiftName(sd.shiftKey) + " | Осталось: " + hrs + "ч " + mins + "мин";
        }
        if (!sd.claimed) return "✅ Смена завершена! Забери награды.";
        return "Нет активной смены | Выполнено смен: " + history;
    }

    public int getShiftHistory(int vkId) {
        return shiftHistory.getOrDefault(vkId, 0);
    }

    public long getCooldownRemaining(int vkId) {
        int cooldownMinutes = plugin.getConfig().getInt("settings.cooldown-minutes", 0);
        if (cooldownMinutes <= 0) return 0;
        Long lastEnd = lastShiftEnd.get(vkId);
        if (lastEnd == null) return 0;
        long elapsed = System.currentTimeMillis() - lastEnd;
        long cooldown = cooldownMinutes * 60000L;
        if (elapsed >= cooldown) return 0;
        return cooldown - elapsed;
    }

    public boolean canStartShift(int vkId) {
        return getCooldownRemaining(vkId) == 0;
    }

    public boolean canStartInGameShift(int vkId) {
        Long lastStart = lastInGameShiftStart.get(vkId);
        if (lastStart == null) return true;
        int cooldownMinutes = plugin.getConfig().getInt("shift.cooldown-minutes", 5);
        return System.currentTimeMillis() - lastStart >= cooldownMinutes * 60000L;
    }

    public long getInGameCooldownRemaining(int vkId) {
        Long lastStart = lastInGameShiftStart.get(vkId);
        if (lastStart == null) return 0;
        int cooldownMinutes = plugin.getConfig().getInt("shift.cooldown-minutes", 5);
        long elapsed = System.currentTimeMillis() - lastStart;
        long cooldown = cooldownMinutes * 60000L;
        if (elapsed >= cooldown) return 0;
        return cooldown - elapsed;
    }

    public boolean hasEnoughRep(int vkId) {
        int cost = plugin.getConfig().getInt("shift.rep-cost", 100);
        if (cost <= 0) return true;
        try {
            return VKChatBridge.getReputation(vkId) >= cost;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deductRepCost(int vkId) {
        int cost = plugin.getConfig().getInt("shift.rep-cost", 100);
        if (cost <= 0) return true;
        try {
            int current = VKChatBridge.getReputation(vkId);
            if (current < cost) return false;
            VKChatBridge.takeReputation(vkId, cost);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String formatCooldown(long ms) {
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        if (mins > 0) return mins + " мин " + secs + " сек";
        return secs + " сек";
    }

    private UUID getPlayerUuid(int vkId) {
        return VKChatBridge.getUuidByVkId(vkId);
    }

    public List<ItemStack> claimRewards(int vkId) {
        ShiftData sd = activeShifts.get(vkId);
        if (sd == null || !sd.completed || sd.claimed) return Collections.emptyList();

        sd.claimed = true;
        String key = sd.shiftKey;
        Random rnd = ThreadLocalRandom.current();

        int rep = rnd.nextInt(
                plugin.getConfig().getInt("shifts." + key + ".rep-max", 100)
                - plugin.getConfig().getInt("shifts." + key + ".rep-min", 50) + 1)
                + plugin.getConfig().getInt("shifts." + key + ".rep-min", 50);

        long now = System.currentTimeMillis();
        long lastEnd = lastShiftEnd.getOrDefault(vkId, 0L);
        long streakResetMs = plugin.getConfig().getLong("shifts.streak-reset-hours", 24) * 3600000L;
        int consecutive = shiftHistory.getOrDefault(vkId, 0);
        if (lastEnd > 0 && now - lastEnd > streakResetMs) {
            consecutive = 0;
        }
        consecutive++;
        shiftHistory.put(vkId, consecutive);
        lastShiftEnd.put(vkId, now);

        int bonusRep = 0;
        if (consecutive >= 5) {
            bonusRep = rep + rep / 2; // +150%
        } else if (consecutive >= 3) {
            bonusRep = rep * 3 / 4;  // +75%
        }
        rep += bonusRep;

        // Донат-множитель
        double donateMult = getDonateShiftMultiplier(vkId);
        if (donateMult > 1.0) rep = (int)(rep * donateMult);

        try {
            VKChatBridge.addPoints(vkId, rep);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка начисления репутации за смену vkId=" + vkId + ": " + e.getMessage());
        }

        List<ItemStack> items = new ArrayList<>();
        List<String> resList = plugin.getConfig().getStringList("shifts." + key + ".resources");
        for (String entry : resList) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    Material mat = Material.valueOf(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    int amount = rnd.nextInt(max - min + 1) + min;
                    if (consecutive >= 3) amount = (int)(amount * 1.25);
                    if (consecutive >= 5) amount = (int)(amount * 1.5);
                    items.add(new ItemStack(mat, amount));
                } catch (Exception ignored) {}
            }
        }

        // Редкий лут
        String rareMsg = "";
        int rareChance = getRareLootChance(key);
        if (rnd.nextInt(100) < rareChance) {
            ItemStack rareItem = rollRareLoot(rnd);
            if (rareItem != null) {
                items.add(rareItem);
                rareMsg = "\n💎 Повезло! Найден редкий лут: " + rareItem.getType().name() + " ×" + rareItem.getAmount();
            }
        }

        String bonusMsg = consecutive >= 5 ? " 🔥 ОГНЕННАЯ СЕРИЯ ×" + consecutive + "! +" + bonusRep + " бонус (+150%)!"
                : consecutive >= 3 ? " ⚡ УДАРНАЯ СЕРИЯ ×" + consecutive + "!" + " +" + bonusRep + " бонус (+75%)!"
                : "";
        try {
            VKChatBridge.sendMessage(vkId,
                    "⛏ Награда за смену '" + getShiftName(key) + "': +" + rep + " репутации." + bonusMsg + rareMsg + " Ресурсы в /stash.");
        } catch (Exception ignored) {}

        saveShifts();
        return items;
    }

    public String getShiftName(String key) {
        return plugin.getConfig().getString("shifts." + key + ".name", key);
    }

    private String getShiftIcon(String key) {
        return plugin.getConfig().getString("shifts." + key + ".icon", "⛏");
    }

    private int getShiftMinutes(String key) {
        return plugin.getConfig().getInt("shifts." + key + ".duration-minutes", 60);
    }

    public String getShiftsInfo() {
        StringBuilder sb = new StringBuilder("⛏ ШАХТЁРСКИЕ СМЕНЫ\n\n");
        ConfigurationSection shiftsSec = plugin.getConfig().getConfigurationSection("shifts");
        if (shiftsSec == null) return sb.append("Нет доступных смен.").toString();
        for (String key : shiftsSec.getKeys(false)) {
            sb.append(getShiftIcon(key)).append(" ").append(getShiftName(key))
                    .append(" — ").append(formatDuration(getShiftMinutes(key))).append("\n");
            sb.append("   ⭐ ").append(plugin.getConfig().getInt("shifts." + key + ".rep-min"))
                    .append("-").append(plugin.getConfig().getInt("shifts." + key + ".rep-max"))
                    .append(" реп\n");
        }
        return sb.toString();
    }

    private String formatDuration(int mins) {
        if (mins < 60) return mins + " мин";
        if (mins < 480) return (mins / 60) + " ч";
        return (mins / 60) + " ч";
    }

    public void loadShifts() {
        if (!shiftsFile.exists()) return;
        shiftsCfg = YamlConfiguration.loadConfiguration(shiftsFile);
        if (shiftsCfg.contains("shifts")) {
            ConfigurationSection sec = shiftsCfg.getConfigurationSection("shifts");
            if (sec != null) for (String key : sec.getKeys(false)) {
                int vkId = Integer.parseInt(key);
                ShiftData sd = new ShiftData();
                sd.shiftKey = shiftsCfg.getString("shifts." + key + ".key");
                sd.startTime = shiftsCfg.getLong("shifts." + key + ".start");
                sd.endTime = shiftsCfg.getLong("shifts." + key + ".end");
                sd.completed = shiftsCfg.getBoolean("shifts." + key + ".done");
                sd.claimed = shiftsCfg.getBoolean("shifts." + key + ".claimed");
                if (!sd.completed && System.currentTimeMillis() >= sd.endTime) sd.completed = true;
                activeShifts.put(vkId, sd);
            }
        }
        if (shiftsCfg.contains("history")) {
            for (String key : shiftsCfg.getConfigurationSection("history").getKeys(false)) {
                shiftHistory.put(Integer.parseInt(key), shiftsCfg.getInt("history." + key));
            }
        }
        if (shiftsCfg.contains("lastend")) {
            for (String key : shiftsCfg.getConfigurationSection("lastend").getKeys(false)) {
                lastShiftEnd.put(Integer.parseInt(key), shiftsCfg.getLong("lastend." + key));
            }
        }
        if (shiftsCfg.contains("ingame-start")) {
            for (String key : shiftsCfg.getConfigurationSection("ingame-start").getKeys(false)) {
                lastInGameShiftStart.put(Integer.parseInt(key), shiftsCfg.getLong("ingame-start." + key));
            }
        }
    }

    public void saveShifts() {
        shiftsCfg = new YamlConfiguration();
        for (Map.Entry<Integer, ShiftData> e : activeShifts.entrySet()) {
            ShiftData sd = e.getValue();
            String path = "shifts." + e.getKey();
            shiftsCfg.set(path + ".key", sd.shiftKey);
            shiftsCfg.set(path + ".start", sd.startTime);
            shiftsCfg.set(path + ".end", sd.endTime);
            shiftsCfg.set(path + ".done", sd.completed);
            shiftsCfg.set(path + ".claimed", sd.claimed);
        }
        for (Map.Entry<Integer, Integer> e : shiftHistory.entrySet()) {
            shiftsCfg.set("history." + e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Long> e : lastShiftEnd.entrySet()) {
            shiftsCfg.set("lastend." + e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Long> e : lastInGameShiftStart.entrySet()) {
            shiftsCfg.set("ingame-start." + e.getKey(), e.getValue());
        }
        try { shiftsCfg.save(shiftsFile); } catch (IOException e) {
            plugin.getLogger().warning("Ошибка сохранения shifts.yml: " + e.getMessage());
        }
    }

    private double getDonateShiftMultiplier(int vkId) {
        java.util.UUID uuid = VKChatBridge.getUuidByVkId(vkId);
        if (uuid == null) return 1.0;
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return 1.0;
        if (p.hasPermission("vkchat.donate.overlord")) return 1.50;
        if (p.hasPermission("vkchat.donate.legend")) return 1.35;
        if (p.hasPermission("vkchat.donate.star")) return 1.20;
        if (p.hasPermission("vkchat.donate.flame")) return 1.10;
        if (p.hasPermission("vkchat.donate.spark")) return 1.05;
        return 1.0;
    }

    private int getRareLootChance(String key) {
        // Шанс из конфига: базовый + бонус за длительность (но не больше 25%)
        int base = plugin.getConfig().getInt("shifts." + key + ".rare-chance", 5);
        int minutes = plugin.getConfig().getInt("shifts." + key + ".duration-minutes", 60);
        int bonus = Math.min(minutes / 120, 10); // +1% за каждые 2 часа, макс +10%
        return Math.min(base + bonus, 25);
    }

    private ItemStack rollRareLoot(Random rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 16) return new ItemStack(Material.DIAMOND, 8 + rnd.nextInt(16));          // 8-23
        if (roll < 30) return new ItemStack(Material.EMERALD, 8 + rnd.nextInt(16));          // 8-23
        if (roll < 42) return new ItemStack(Material.NETHERITE_SCRAP, 3 + rnd.nextInt(7));   // 3-9
        if (roll < 52) return new ItemStack(Material.ANCIENT_DEBRIS, 3 + rnd.nextInt(7));    // 3-9
        if (roll < 62) return new ItemStack(Material.GOLDEN_APPLE, 8 + rnd.nextInt(9));      // 8-16
        if (roll < 69) return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3 + rnd.nextInt(4)); // 3-6
        if (roll < 76) return new ItemStack(Material.ENDER_PEARL, 16 + rnd.nextInt(16));     // 16-31
        if (roll < 81) return new ItemStack(Material.SHULKER_SHELL, 3 + rnd.nextInt(5));     // 3-7
        if (roll < 86) { ItemStack i = createPluginItem("rune_token"); i.setAmount(1+rnd.nextInt(2)); return i; }
        if (roll < 90) { ItemStack i = createPluginItem("artifact_shard"); i.setAmount(1+rnd.nextInt(2)); return i; }
        if (roll < 93) return new ItemStack(Material.EXPERIENCE_BOTTLE, 32 + rnd.nextInt(48)); // 32-79
        if (roll < 96) return createPluginItem("rep_boost");
        if (roll < 98) return createPluginItem("speed_boost");
        if (roll < 100) return createPluginItem("random_artifact");
        return new ItemStack(Material.NETHER_STAR, 1 + rnd.nextInt(3)); // 1-3
    }

    private ItemStack createPluginItem(String type) {
        switch (type) {
            case "rune_token": {
                ItemStack item = new ItemStack(Material.GOLD_NUGGET, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§6✦ Древний Жетон Рун");
                    meta.setCustomModelData(48);
                    meta.setLore(java.util.Arrays.asList("§7Обменяйте в /runes на случайную руну", "§7Дроп из шахтёрских смен"));
                }
                item.setItemMeta(meta);
                return item;
            }
            case "artifact_shard": {
                ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§5✦ Осколок Артефакта");
                    meta.setCustomModelData(49);
                    meta.setLore(java.util.Arrays.asList("§7Обменяйте в /artifacts на артефакт", "§7Дроп из шахтёрских смен"));
                }
                item.setItemMeta(meta);
                return item;
            }
            case "rep_boost": {
                ItemStack item = new ItemStack(Material.PAPER, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e📜 Свиток Репутации");
                    meta.setCustomModelData(60);
                    meta.setLore(java.util.Arrays.asList("§7ПКМ для получения +500 репутации ВК", "§7Дроп из шахтёрских смен"));
                }
                item.setItemMeta(meta);
                return item;
            }
            case "speed_boost": {
                ItemStack item = new ItemStack(Material.SUGAR, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§b⚡ Свиток Скорости");
                    meta.setCustomModelData(80);
                    meta.setLore(java.util.Arrays.asList("§7ПКМ — Speed II на 30 минут", "§7Дроп из шахтёрских смен"));
                }
                item.setItemMeta(meta);
                return item;
            }
            case "random_artifact": {
                try {
                    if (Bukkit.getPluginManager().isPluginEnabled("VKChatArtifacts")) {
                        Class<?> factory = Class.forName("ru.example.vkchatartifacts.items.ArtifactFactory");
                        Object plugin = Class.forName("ru.example.vkchatartifacts.VKChatArtifactsPlugin")
                                .getMethod("getInstance").invoke(null);
                        return (ItemStack) factory.getMethod("generateArtifact", plugin.getClass(), boolean.class)
                                .invoke(null, plugin, false);
                    }
                } catch (Exception ignored) {}
                // Fallback: даём звезду незера
                return new ItemStack(Material.NETHER_STAR, 1);
            }
            default:
                return new ItemStack(Material.DIAMOND, 1);
        }
    }
}
