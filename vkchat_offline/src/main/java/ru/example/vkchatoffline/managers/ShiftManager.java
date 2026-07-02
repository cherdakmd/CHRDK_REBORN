package ru.example.vkchatoffline.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
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
    private final File shiftsFile;
    private FileConfiguration shiftsCfg;

    public static class ShiftData {
        public String shiftKey;
        public long startTime;
        public long endTime;
        public boolean completed;
        public boolean claimed;
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
                }
            }
        }, 600L, 600L); // Каждые 30 сек
    }

    private void notifyPlayer(int vkId, String shiftKey) {
        try {
            VKChatPlugin.getInstance().getApi().sendMessage(vkId,
                    "⛏ Смена '" + getShiftName(shiftKey) + "' завершена! Напиши !шахта чтобы забрать награды.");
            VKChatPlugin.getInstance().getApi().sendKeyboard(vkId,
                    "Смена завершена!", Keyboards.shiftDone());
        } catch (Exception ignored) {}
    }

    public boolean startShift(int vkId, String shiftKey) {
        if (activeShifts.containsKey(vkId)) {
            ShiftData existing = activeShifts.get(vkId);
            if (!existing.completed) return false; // Уже в смене
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
        if (sd == null) return "Нет активной смены";
        if (!sd.completed) {
            long left = sd.endTime - System.currentTimeMillis();
            if (left <= 0) { sd.completed = true; return "Смена завершена! Забери награды."; }
            long hrs = left / 3600000;
            long mins = (left % 3600000) / 60000;
            return "⛏ В шахте | " + getShiftName(sd.shiftKey) + " | Осталось: " + hrs + "ч " + mins + "мин";
        }
        if (!sd.claimed) return "✅ Смена завершена! Забери награды.";
        return "Нет активной смены";
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

        try {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
        } catch (Exception ignored) {}

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
                    items.add(new ItemStack(mat, amount));
                } catch (Exception ignored) {}
            }
        }

        try {
            VKChatPlugin.getInstance().getApi().sendMessage(vkId,
                    "⛏ Награда за смену '" + getShiftName(key) + "': +" + rep + " репутации. Ресурсы в /stash.");
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
        for (String key : plugin.getConfig().getConfigurationSection("shifts").getKeys(false)) {
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
            for (String key : shiftsCfg.getConfigurationSection("shifts").getKeys(false)) {
                int vkId = Integer.parseInt(key);
                ShiftData sd = new ShiftData();
                sd.shiftKey = shiftsCfg.getString("shifts." + key + ".key");
                sd.startTime = shiftsCfg.getLong("shifts." + key + ".start");
                sd.endTime = shiftsCfg.getLong("shifts." + key + ".end");
                sd.completed = shiftsCfg.getBoolean("shifts." + key + ".done");
                sd.claimed = shiftsCfg.getBoolean("shifts." + key + ".claimed");
                // Проверка: если смена должна была завершиться — отмечаем
                if (!sd.completed && System.currentTimeMillis() >= sd.endTime) sd.completed = true;
                activeShifts.put(vkId, sd);
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
        try { shiftsCfg.save(shiftsFile); } catch (IOException ignored) {}
    }
}
