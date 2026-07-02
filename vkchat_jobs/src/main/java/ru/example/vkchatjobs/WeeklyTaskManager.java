package ru.example.vkchatjobs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WeeklyTaskManager {
    private final VKChatJobsPlugin plugin;
    private File file;
    private FileConfiguration data;

    private final Map<UUID, Map<String, Integer>> weeklyProgress = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> weeklyClaimed = new HashMap<>();
    private final Map<UUID, Integer> weeklyCompletedCount = new HashMap<>();
    private final Map<UUID, String> weeklyResetDate = new HashMap<>();

    private static final List<String> TASK_TYPES = Arrays.asList("mine", "kill", "craft", "fish", "build");
    private static final String[] TASK_DESCRIPTIONS = {
        "Добыть 500 блоков",
        "Убить 100 мобов",
        "Скрафтить 50 предметов",
        "Поймать 30 рыб",
        "Поставить 200 блоков"
    };
    private static final int[] TASK_TARGETS = {500, 100, 50, 30, 200};
    private static final int[] TASK_REWARDS = {150, 200, 100, 120, 80};
    private static final String[] TASK_REWARD_ITEMS = {"IRON_INGOT:8", "BONE:12", "EXPERIENCE_BOTTLE:5", "COD:4", "OAK_LOG:16"};

    public WeeklyTaskManager(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "weekly_tasks.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;
                weeklyResetDate.put(uuid, data.getString(path + ".reset-date", getWeekKey()));
                weeklyCompletedCount.put(uuid, data.getInt(path + ".completed-count", 0));
                weeklyProgress.put(uuid, new HashMap<>());
                weeklyClaimed.put(uuid, new HashMap<>());
                if (data.contains(path + ".progress")) {
                    for (String task : data.getConfigurationSection(path + ".progress").getKeys(false)) {
                        weeklyProgress.get(uuid).put(task, data.getInt(path + ".progress." + task, 0));
                        weeklyClaimed.get(uuid).put(task, data.getBoolean(path + ".claimed." + task, false));
                    }
                }
            }
        }
    }

    public void save() {
        data.set("players", null);
        for (UUID uuid : weeklyProgress.keySet()) {
            String path = "players." + uuid.toString();
            data.set(path + ".reset-date", weeklyResetDate.getOrDefault(uuid, getWeekKey()));
            data.set(path + ".completed-count", weeklyCompletedCount.getOrDefault(uuid, 0));
            for (String task : weeklyProgress.getOrDefault(uuid, new HashMap<>()).keySet()) {
                data.set(path + ".progress." + task, weeklyProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(task, 0));
                data.set(path + ".claimed." + task, weeklyClaimed.getOrDefault(uuid, new HashMap<>()).getOrDefault(task, false));
            }
        }
        try { data.save(file); } catch (IOException ignored) {}
    }

    private String getWeekKey() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    private void ensureWeek(UUID uuid) {
        String currentWeek = getWeekKey();
        String stored = weeklyResetDate.getOrDefault(uuid, "");
        if (!currentWeek.equals(stored)) {
            weeklyResetDate.put(uuid, currentWeek);
            weeklyProgress.put(uuid, new HashMap<>());
            weeklyClaimed.put(uuid, new HashMap<>());
            weeklyCompletedCount.put(uuid, 0);
        }
    }

    public void addProgress(Player p, String taskType, int amount) {
        if (!plugin.getConfig().getBoolean("weekly-tasks.enabled", true)) return;
        UUID uuid = p.getUniqueId();
        ensureWeek(uuid);
        weeklyProgress.putIfAbsent(uuid, new HashMap<>());
        int idx = TASK_TYPES.indexOf(taskType);
        if (idx < 0) return;
        int target = TASK_TARGETS[idx];
        int old = weeklyProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(taskType, 0);
        if (old >= target) return;
        int now = Math.min(target, old + amount);
        weeklyProgress.get(uuid).put(taskType, now);
        if (now == target && old < target) {
            p.sendMessage(org.bukkit.ChatColor.GREEN + "☑ Еженедельное задание выполнено: " + TASK_DESCRIPTIONS[idx] + "!");
            p.sendMessage(org.bukkit.ChatColor.YELLOW + "Забери награду: /jobs weekly claim " + taskType);
        } else if (now % Math.max(1, target / 5) == 0 && old != now) {
            p.sendMessage(org.bukkit.ChatColor.YELLOW + "📋 Неделя: " + TASK_DESCRIPTIONS[idx] + " — " + now + "/" + target);
        }
    }

    public int getProgress(UUID uuid, String taskType) {
        ensureWeek(uuid);
        return weeklyProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(taskType, 0);
    }

    public int getTarget(String taskType) {
        int idx = TASK_TYPES.indexOf(taskType);
        return idx >= 0 ? TASK_TARGETS[idx] : 100;
    }

    public boolean isClaimed(UUID uuid, String taskType) {
        ensureWeek(uuid);
        return weeklyClaimed.getOrDefault(uuid, new HashMap<>()).getOrDefault(taskType, false);
    }

    public boolean claimReward(Player p, String taskType) {
        UUID uuid = p.getUniqueId();
        ensureWeek(uuid);
        int idx = TASK_TYPES.indexOf(taskType);
        if (idx < 0) return false;
        if (isClaimed(uuid, taskType)) return false;
        if (getProgress(uuid, taskType) < TASK_TARGETS[idx]) return false;

        weeklyClaimed.putIfAbsent(uuid, new HashMap<>());
        weeklyClaimed.get(uuid).put(taskType, true);
        int count = weeklyCompletedCount.getOrDefault(uuid, 0) + 1;
        weeklyCompletedCount.put(uuid, count);

        int rep = TASK_REWARDS[idx];
        rewardVkRep(p, rep, "Еженедельное задание: " + taskType);
        giveTaskItemReward(p, idx);

        if (count >= plugin.getConfig().getInt("weekly-tasks.tasks-per-week", 3)) {
            int bonus = plugin.getConfig().getInt("weekly-tasks.bonus-rep", 500);
            rewardVkRep(p, bonus, "Бонус за все еженедельные задания");
            giveBonusItemReward(p);
            p.sendMessage(org.bukkit.ChatColor.GOLD + "🏆 Вы выполнили ВСЕ еженедельные задания! Бонус: +" + bonus + " репутации!");
        }

        p.sendMessage(org.bukkit.ChatColor.GREEN + "☑ Еженедельная награда получена: +" + rep + " репутации ВК!");
        save();
        return true;
    }

    public int getCompletedCount(UUID uuid) {
        ensureWeek(uuid);
        return weeklyCompletedCount.getOrDefault(uuid, 0);
    }

    public Map<String, Integer> getAllProgress(UUID uuid) {
        ensureWeek(uuid);
        return new HashMap<>(weeklyProgress.getOrDefault(uuid, new HashMap<>()));
    }

    private void rewardVkRep(Player p, int amount, String reason) {
        if (amount <= 0) return;
        try {
            org.bukkit.plugin.Plugin corePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null && corePlugin.isEnabled()) {
                Object api = corePlugin.getClass().getMethod("getApi").invoke(corePlugin);
                int vkId = (int) api.getClass().getMethod("getLinkedVkId", Player.class).invoke(api, p);
                if (vkId != -1) {
                    api.getClass().getMethod("addReputation", int.class, int.class).invoke(api, vkId, amount);
                }
            }
        } catch (Exception ignored) {}
    }

    private void giveTaskItemReward(Player p, int taskIdx) {
        if (taskIdx < 0 || taskIdx >= TASK_REWARD_ITEMS.length) return;
        String[] parts = TASK_REWARD_ITEMS[taskIdx].split(":");
        if (parts.length != 2) return;
        try {
            org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            org.bukkit.inventory.ItemStack reward = new org.bukkit.inventory.ItemStack(mat, amount);
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(reward);
            left.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
            p.sendMessage(org.bukkit.ChatColor.GRAY + "📦 Бонусный предмет: " + mat.name() + " x" + amount);
        } catch (Exception ignored) {}
    }

    private void giveBonusItemReward(Player p) {
        try {
            org.bukkit.inventory.ItemStack reward = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 3);
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(reward);
            left.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
            p.sendMessage(org.bukkit.ChatColor.AQUA + "💎 Бонус за все задания: DIAMOND x3");
        } catch (Exception ignored) {}
    }
}
