package ru.example.vkchatjobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RankingManager {
    private final VKChatJobsPlugin plugin;
    private File file;
    private FileConfiguration data;
    private long lastBroadcast = 0;

    private final Map<UUID, Integer> weeklyRepEarned = new HashMap<>();
    private String lastResetWeek = "";

    public RankingManager(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "ranking.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        lastBroadcast = data.getLong("last-broadcast", 0);
        lastResetWeek = data.getString("last-reset-week", getWeekKey());

        if (data.contains("weekly-rep")) {
            for (String uuidStr : data.getConfigurationSection("weekly-rep").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                weeklyRepEarned.put(uuid, data.getInt("weekly-rep." + uuidStr, 0));
            }
        }
    }

    public void save() {
        data.set("last-broadcast", lastBroadcast);
        data.set("last-reset-week", lastResetWeek);
        data.set("weekly-rep", null);
        for (Map.Entry<UUID, Integer> e : weeklyRepEarned.entrySet()) {
            data.set("weekly-rep." + e.getKey().toString(), e.getValue());
        }
        try { data.save(file); } catch (IOException ignored) {}
    }

    private String getWeekKey() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    public void checkWeeklyReset() {
        String current = getWeekKey();
        if (!current.equals(lastResetWeek)) {
            if (!weeklyRepEarned.isEmpty()) {
                broadcastTop();
                save();
            }
            lastResetWeek = current;
            weeklyRepEarned.clear();
            plugin.getLogger().info("Еженедельный рейтинг сброшен.");
        }
    }

    public void addWeeklyRep(UUID uuid, int amount) {
        if (amount <= 0) return;
        double donorMult = 1.0;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            if (p.hasPermission("vkchat.donate.overlord")) donorMult = 1.70;
            else if (p.hasPermission("vkchat.donate.legend")) donorMult = 1.50;
            else if (p.hasPermission("vkchat.donate.star")) donorMult = 1.35;
            else if (p.hasPermission("vkchat.donate.flame")) donorMult = 1.20;
            else if (p.hasPermission("vkchat.donate.spark")) donorMult = 1.10;
            else if (p.hasPermission("vkchat.donate.vip")) donorMult = 1.05;
        }
        int finalAmount = Math.max(1, (int) Math.round(amount * donorMult));
        weeklyRepEarned.merge(uuid, finalAmount, Integer::sum);
        if (plugin.getConfig().getBoolean("ranking.debug-log", false)) {
            plugin.getLogger().info("[Ranking] +" + finalAmount + " rep for " + uuid + " (base=" + amount + ", donor=" + String.format("%.2f", donorMult) + ")");
        }
    }

    public void tryBroadcast() {
        if (!plugin.getConfig().getBoolean("ranking.enabled", true)) return;
        long interval = plugin.getConfig().getLong("ranking.broadcast-interval", 86400) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastBroadcast < interval) return;
        lastBroadcast = now;
        broadcastTop();
        save();
    }

    public void broadcastTop() {
        int topCount = plugin.getConfig().getInt("ranking.top-count", 10);
        List<UUID> sorted = new ArrayList<>(weeklyRepEarned.keySet());
        sorted.sort((a, b) -> Integer.compare(weeklyRepEarned.getOrDefault(b, 0), weeklyRepEarned.getOrDefault(a, 0)));

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GOLD + "  🏆 Рейтинг Профессий (неделя)");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");

        int n = 0;
        for (UUID uuid : sorted) {
            if (n >= topCount) break;
            int rep = weeklyRepEarned.getOrDefault(uuid, 0);
            if (rep <= 0) continue;
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            ChatColor color = n == 0 ? ChatColor.GOLD : n == 1 ? ChatColor.WHITE : n == 2 ? ChatColor.YELLOW : ChatColor.GRAY;
            String medal = n == 0 ? "🥇" : n == 1 ? "🥈" : n == 2 ? "🥉" : "  ";
            Bukkit.broadcastMessage(color + medal + " #" + (n + 1) + " " + name + ChatColor.GRAY + " — " + ChatColor.AQUA + rep + " реп.");
            n++;
        }

        if (n == 0) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "  Пока нет данных за эту неделю.");
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
        Bukkit.broadcastMessage("");

        if (n >= 3) {
            giveRankRewards(sorted);
        }
    }

    private void giveRankRewards(List<UUID> sorted) {
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            UUID uuid = sorted.get(i);
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            String rankKey = String.valueOf(i + 1);

            int repReward = plugin.getConfig().getInt("rank-rewards.weekly." + rankKey + ".reputation", -1);
            if (repReward < 0) {
                repReward = plugin.getConfig().getInt("ranking.rewards." + rankKey + "th",
                        i == 0 ? 300 : i == 1 ? 200 : 100);
            }
            if (repReward > 0) {
                rewardVkRep(p, repReward, "Топ-" + rankKey + " рейтинга профессий");
            }

            if (!plugin.getConfig().getBoolean("rank-rewards.enabled", true)) continue;

            String matName = plugin.getConfig().getString("rank-rewards.weekly." + rankKey + ".material", null);
            int amount = plugin.getConfig().getInt("rank-rewards.weekly." + rankKey + ".amount", 1);
            if (matName == null || matName.isEmpty()) continue;

            try {
                Material mat = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
                java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, Math.max(1, amount)));
                left.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                p.sendMessage(ChatColor.GOLD + "🎁 Ранговая награда за топ-" + rankKey + ": " + mat.name() + " x" + amount);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            } catch (Exception ignored) {}
        }
    }

    public List<UUID> getTopPlayers(int count) {
        List<UUID> sorted = new ArrayList<>(weeklyRepEarned.keySet());
        sorted.sort((a, b) -> Integer.compare(weeklyRepEarned.getOrDefault(b, 0), weeklyRepEarned.getOrDefault(a, 0)));
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    public int getWeeklyRep(UUID uuid) {
        return weeklyRepEarned.getOrDefault(uuid, 0);
    }

    public int getPlayerRank(UUID uuid) {
        int myRep = weeklyRepEarned.getOrDefault(uuid, 0);
        if (myRep <= 0) return -1;
        List<UUID> sorted = new ArrayList<>(weeklyRepEarned.keySet());
        sorted.sort((a, b) -> Integer.compare(weeklyRepEarned.getOrDefault(b, 0), weeklyRepEarned.getOrDefault(a, 0)));
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).equals(uuid)) return i + 1;
        }
        return -1;
    }

    private void rewardVkRep(Player p, int amount, String reason) {
        if (amount <= 0) return;
        try {
            org.bukkit.plugin.Plugin corePlugin = Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null && corePlugin.isEnabled()) {
                Object api = corePlugin.getClass().getMethod("getApi").invoke(corePlugin);
                int vkId = (int) api.getClass().getMethod("getLinkedVkId", Player.class).invoke(api, p);
                if (vkId != -1) {
                    api.getClass().getMethod("addReputation", int.class, int.class).invoke(api, vkId, amount);
                    p.sendMessage(ChatColor.AQUA + "✨ " + reason + ": +" + amount + " репутации ВК!");
                }
            }
        } catch (Exception ignored) {}
    }
}
