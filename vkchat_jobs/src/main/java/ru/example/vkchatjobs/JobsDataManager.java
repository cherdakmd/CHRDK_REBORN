package ru.example.vkchatjobs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class JobsDataManager {
    private final VKChatJobsPlugin plugin;
    private File file;
    private FileConfiguration data;
    
    private final Map<UUID, Map<String, Integer>> expData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> lvlData = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fatigueData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> skillPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, java.util.List<String>>> unlockedSkills = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> specializations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> dailyProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> dailyClaimed = new ConcurrentHashMap<>();
    private final Map<UUID, String> dailyDate = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> repEarned = new ConcurrentHashMap<>();
    private final Map<UUID, Double> repFractions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingRepNotify = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRepNotify = new ConcurrentHashMap<>();

    public JobsDataManager(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "jobs_data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                expData.put(uuid, new ConcurrentHashMap<>());
                lvlData.put(uuid, new ConcurrentHashMap<>());
                fatigueData.put(uuid, data.getInt("players." + uuidStr + ".fatigue", 0));
                skillPoints.put(uuid, new ConcurrentHashMap<>());
                unlockedSkills.put(uuid, new ConcurrentHashMap<>());
                specializations.put(uuid, new ConcurrentHashMap<>());
                dailyProgress.put(uuid, new ConcurrentHashMap<>());
                dailyClaimed.put(uuid, new ConcurrentHashMap<>());
                repEarned.put(uuid, new ConcurrentHashMap<>());
                dailyDate.put(uuid, data.getString("players." + uuidStr + ".daily.date", today()));
                if (data.contains("players." + uuidStr + ".daily.progress")) {
                    for (String job : data.getConfigurationSection("players." + uuidStr + ".daily.progress").getKeys(false)) {
                        dailyProgress.get(uuid).put(job, data.getInt("players." + uuidStr + ".daily.progress." + job, 0));
                        dailyClaimed.get(uuid).put(job, data.getBoolean("players." + uuidStr + ".daily.claimed." + job, false));
                    }
                }
                
                for (String job : data.getConfigurationSection("players." + uuidStr + ".jobs").getKeys(false)) {
                    expData.get(uuid).put(job, data.getInt("players." + uuidStr + ".jobs." + job + ".exp"));
                    lvlData.get(uuid).put(job, data.getInt("players." + uuidStr + ".jobs." + job + ".lvl"));
                    skillPoints.get(uuid).put(job, data.getInt("players." + uuidStr + ".jobs." + job + ".skill_points", 0));
                    unlockedSkills.get(uuid).put(job, data.getStringList("players." + uuidStr + ".jobs." + job + ".skills"));
                    specializations.get(uuid).put(job, data.getString("players." + uuidStr + ".jobs." + job + ".specialization", ""));
                    repEarned.get(uuid).put(job, data.getInt("players." + uuidStr + ".jobs." + job + ".rep_earned", 0));
                }
            }
        }
    }

    public void saveAll() {
        data.set("players", null);
        for (UUID uuid : expData.keySet()) {
            String path = "players." + uuid.toString();
            data.set(path + ".fatigue", fatigueData.getOrDefault(uuid, 0));
            data.set(path + ".daily.date", dailyDate.getOrDefault(uuid, today()));
            for (String job : dailyProgress.getOrDefault(uuid, new HashMap<>()).keySet()) {
                data.set(path + ".daily.progress." + job, dailyProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0));
                data.set(path + ".daily.claimed." + job, dailyClaimed.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, false));
            }
            java.util.HashSet<String> allJobs = new java.util.HashSet<>(expData.get(uuid).keySet());
            allJobs.addAll(specializations.getOrDefault(uuid, new HashMap<>()).keySet());
            allJobs.addAll(repEarned.getOrDefault(uuid, new HashMap<>()).keySet());
            for (String job : allJobs) {
                data.set(path + ".jobs." + job + ".exp", expData.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0));
                data.set(path + ".jobs." + job + ".lvl", lvlData.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 1));
                data.set(path + ".jobs." + job + ".skill_points", skillPoints.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0));
                data.set(path + ".jobs." + job + ".skills", unlockedSkills.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, new java.util.ArrayList<>()));
                data.set(path + ".jobs." + job + ".specialization", specializations.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, ""));
                data.set(path + ".jobs." + job + ".rep_earned", repEarned.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0));
            }
        }
        try { data.save(file); } catch (IOException ignored) {}
    }

    public int getLevel(UUID uuid, String job) {
        return lvlData.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 1);
    }
    
    public int getExp(UUID uuid, String job) {
        return expData.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0);
    }

    public int getSkillPoints(UUID uuid, String job) {
        return skillPoints.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0);
    }

    public void removeSkillPoint(UUID uuid, String job) {
        skillPoints.putIfAbsent(uuid, new ConcurrentHashMap<>());
        int pts = getSkillPoints(uuid, job);
        if (pts > 0) {
            skillPoints.get(uuid).put(job, pts - 1);
        }
    }

    public java.util.List<String> getUnlockedSkills(UUID uuid, String job) {
        return unlockedSkills.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, new java.util.ArrayList<>());
    }

    public boolean hasSkill(UUID uuid, String job, String skill) {
        return getUnlockedSkills(uuid, job).contains(skill);
    }

    public void unlockSkill(UUID uuid, String job, String skill) {
        unlockedSkills.putIfAbsent(uuid, new ConcurrentHashMap<>());
        unlockedSkills.get(uuid).putIfAbsent(job, new java.util.ArrayList<>());
        if (!unlockedSkills.get(uuid).get(job).contains(skill)) {
            unlockedSkills.get(uuid).get(job).add(skill);
        }
    }

    public int getFatigue(UUID uuid) {
        return fatigueData.getOrDefault(uuid, 0);
    }

    public void addFatigue(UUID uuid, int amount) {
        fatigueData.put(uuid, getFatigue(uuid) + amount);
    }

    public void removeFatigue(UUID uuid, int amount) {
        fatigueData.put(uuid, Math.max(0, getFatigue(uuid) - amount));
    }


    private String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }

    private void ensureDaily(UUID uuid) {
        String t = today();
        if (!t.equals(dailyDate.get(uuid))) {
            dailyDate.put(uuid, t);
            dailyProgress.put(uuid, new ConcurrentHashMap<>());
            dailyClaimed.put(uuid, new ConcurrentHashMap<>());
        }
    }

    public int getDailyTarget(String job) {
        return plugin.getConfig().getInt("daily.targets." + job, plugin.getConfig().getInt("daily.default-target", 100));
    }

    public int getDailyProgress(UUID uuid, String job) {
        ensureDaily(uuid);
        return dailyProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0);
    }

    public boolean isDailyClaimed(UUID uuid, String job) {
        ensureDaily(uuid);
        return dailyClaimed.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, false);
    }

    public void addDailyProgress(org.bukkit.entity.Player p, String job, int amount) {
        if (!plugin.getConfig().getBoolean("daily.enabled", true)) return;
        UUID uuid = p.getUniqueId();
        ensureDaily(uuid);
        dailyProgress.putIfAbsent(uuid, new ConcurrentHashMap<>());
        int target = getDailyTarget(job);
        int old = getDailyProgress(uuid, job);
        if (old >= target) return;
        int now = Math.min(target, old + amount);
        dailyProgress.get(uuid).put(job, now);
        if ((now == target) || (now % Math.max(1, target / 5) == 0 && old != now)) {
            p.sendMessage(org.bukkit.ChatColor.GREEN + "☑ Ежедневка " + job + ": " + now + "/" + target + (now >= target ? " — забери награду: /jobs claim " + job : ""));
        }
    }

    public int getDailyRewardRep(UUID uuid, String job) {
        int rep = plugin.getConfig().getInt("daily.reward-reputation", 150);
        if ("reward".equals(getSpecialization(uuid, job))) rep = (int)Math.round(rep * 1.20);
        return rep;
    }

    public boolean claimDaily(org.bukkit.entity.Player p, String job) {
        UUID uuid = p.getUniqueId();
        ensureDaily(uuid);
        if (isDailyClaimed(uuid, job) || getDailyProgress(uuid, job) < getDailyTarget(job)) return false;
        dailyClaimed.putIfAbsent(uuid, new ConcurrentHashMap<>());
        dailyClaimed.get(uuid).put(job, true);
        int rep = getDailyRewardRep(uuid, job);
        try {
            org.bukkit.plugin.Plugin corePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null && corePlugin.isEnabled()) {
                Object api = corePlugin.getClass().getMethod("getApi").invoke(corePlugin);
                int vkId = (int) api.getClass().getMethod("getLinkedVkId", org.bukkit.entity.Player.class).invoke(api, p);
                if (vkId != -1) api.getClass().getMethod("addReputation", int.class, int.class).invoke(api, vkId, rep);
            }
        } catch (Exception ignored) {}
        Material mat = Material.valueOf(plugin.getConfig().getString("daily.reward-item", "EXPERIENCE_BOTTLE"));
        int amount = plugin.getConfig().getInt("daily.reward-item-amount", 8);
        p.getInventory().addItem(new ItemStack(mat, Math.max(1, amount)));
        p.sendMessage(org.bukkit.ChatColor.GOLD + "☑ Ежедневка профессии выполнена: +" + rep + " репутации и ванильная награда.");
        saveAll();
        return true;
    }

    public String getSpecialization(UUID uuid, String job) {
        return specializations.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, "");
    }

    public boolean setSpecialization(org.bukkit.entity.Player p, String job, String spec) {
        if (!(spec.equals("xp") || spec.equals("stamina") || spec.equals("reward"))) return false;
        int req = plugin.getConfig().getInt("specializations.required-level", 20);
        if (getLevel(p.getUniqueId(), job) < req) return false;
        specializations.putIfAbsent(p.getUniqueId(), new ConcurrentHashMap<>());
        if (!getSpecialization(p.getUniqueId(), job).isEmpty()) return false;
        specializations.get(p.getUniqueId()).put(job, spec);
        saveAll();
        return true;
    }

    public int applyFatigueModifier(UUID uuid, String job, int amount) {
        if ("stamina".equals(getSpecialization(uuid, job))) amount = (int)Math.max(1, Math.round(amount * 0.75));
        return amount;
    }


    public java.util.Set<UUID> getKnownPlayers() {
        java.util.HashSet<UUID> set = new java.util.HashSet<>();
        set.addAll(expData.keySet());
        set.addAll(lvlData.keySet());
        return set;
    }

    public int getTotalLevel(UUID uuid) {
        int total = 0;
        for (String job : java.util.Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman")) {
            total += getLevel(uuid, job);
        }
        return total;
    }

    private void rewardVkRep(org.bukkit.entity.Player p, int amount, String reason) {
        if (amount <= 0) return;
        try {
            org.bukkit.plugin.Plugin corePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null && corePlugin.isEnabled()) {
                Object api = corePlugin.getClass().getMethod("getApi").invoke(corePlugin);
                int vkId = (int) api.getClass().getMethod("getLinkedVkId", org.bukkit.entity.Player.class).invoke(api, p);
                if (vkId != -1) {
                    api.getClass().getMethod("addReputation", int.class, int.class).invoke(api, vkId, amount);
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ " + reason + ": +" + amount + " репутации ВК!");
                }
            }
        } catch (Exception ignored) {}
    }

    private void giveVanillaRankReward(org.bukkit.entity.Player p, String job, int level) {
        if (!plugin.getConfig().getBoolean("rank-rewards.enabled", true)) return;
        int milestone = level >= 50 ? 50 : level >= 40 ? 40 : level >= 30 ? 30 : level >= 20 ? 20 : level >= 10 ? 10 : 0;
        if (milestone <= 0 || level != milestone) return;
        String matName = plugin.getConfig().getString("rank-rewards.items." + job + "." + milestone + ".material", defaultRankMaterial(job, milestone).name());
        int amount = plugin.getConfig().getInt("rank-rewards.items." + job + "." + milestone + ".amount", defaultRankAmount(milestone));
        try {
            Material mat = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
            java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, Math.max(1, amount)));
            left.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
            p.sendMessage(org.bukkit.ChatColor.GOLD + "🎁 Ранговая награда профессии " + job + " за уровень " + milestone + ": " + mat.name() + " x" + amount);
        } catch (Exception ignored) {}
    }

    private Material defaultRankMaterial(String job, int milestone) {
        if (milestone >= 50) return Material.DIAMOND;
        if (milestone >= 40) return Material.EMERALD;
        if (job.equals("miner")) return milestone >= 30 ? Material.DIAMOND : Material.IRON_INGOT;
        if (job.equals("woodcutter")) return Material.OAK_LOG;
        if (job.equals("farmer")) return milestone >= 30 ? Material.GOLDEN_CARROT : Material.BREAD;
        if (job.equals("alchemist")) return milestone >= 30 ? Material.BLAZE_ROD : Material.EXPERIENCE_BOTTLE;
        if (job.equals("blacksmith")) return milestone >= 30 ? Material.DIAMOND : Material.IRON_INGOT;
        if (job.equals("hunter")) return milestone >= 30 ? Material.GOLDEN_APPLE : Material.BONE;
        if (job.equals("fisherman")) return milestone >= 30 ? Material.NAUTILUS_SHELL : Material.COD;
        return Material.EMERALD;
    }

    private int defaultRankAmount(int milestone) {
        if (milestone >= 50) return 8;
        if (milestone >= 40) return 12;
        if (milestone >= 30) return 8;
        if (milestone >= 20) return 16;
        return 8;
    }


    public int getRepEarned(UUID uuid, String job) {
        return repEarned.getOrDefault(uuid, new HashMap<>()).getOrDefault(job, 0);
    }

    public void addActionReputation(org.bukkit.entity.Player p, String job, String actionKey, int amount) {
        if (!plugin.getConfig().getBoolean("jobs-reputation.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("jobs-reputation.jobs." + job + ".enabled", isDefaultRepJob(job))) return;
        if (amount <= 0) return;

        UUID uuid = p.getUniqueId();
        double base = plugin.getConfig().getDouble("jobs-reputation.jobs." + job + ".base", defaultRepBase(job));
        double actionMult = plugin.getConfig().getDouble("jobs-reputation.action-multipliers." + actionKey, defaultActionMultiplier(actionKey));
        double levelMult = getJobLevelRepMultiplier(getLevel(uuid, job));
        double donorMult = getDonateStatusMultiplier(p);
        double raw = Math.max(0.0, base * actionMult * amount * levelMult * donorMult);
        if (raw <= 0) return;

        double accumulated = repFractions.getOrDefault(uuid, 0.0) + raw;
        int payout = (int)Math.floor(accumulated);
        repFractions.put(uuid, accumulated - payout);
        if (payout <= 0) return;

        rewardVkRepSilent(p, payout);
        repEarned.putIfAbsent(uuid, new ConcurrentHashMap<>());
        repEarned.get(uuid).put(job, getRepEarned(uuid, job) + payout);
        pendingRepNotify.put(uuid, pendingRepNotify.getOrDefault(uuid, 0) + payout);
        maybeNotifyRepBatch(p, job);
    }

    private boolean isDefaultRepJob(String job) {
        return job.equals("miner") || job.equals("woodcutter") || job.equals("farmer") || job.equals("hunter") || job.equals("fisherman");
    }

    private double defaultRepBase(String job) {
        if (job.equals("miner")) return 0.18;
        if (job.equals("woodcutter")) return 0.10;
        if (job.equals("farmer")) return 0.08;
        if (job.equals("hunter")) return 0.35;
        if (job.equals("fisherman")) return 0.22;
        return 0.05;
    }

    private double defaultActionMultiplier(String key) {
        String k = key == null ? "" : key.toUpperCase(java.util.Locale.ROOT);
        if (k.contains("ANCIENT_DEBRIS")) return 14.0;
        if (k.contains("DIAMOND") || k.contains("EMERALD")) return 8.0;
        if (k.contains("GOLD") || k.contains("LAPIS") || k.contains("REDSTONE")) return 3.0;
        if (k.contains("IRON") || k.contains("COPPER") || k.contains("QUARTZ")) return 2.0;
        if (k.contains("COAL")) return 1.3;
        if (k.contains("BOSS") || k.contains("WITHER") || k.contains("ENDER_DRAGON")) return 30.0;
        if (k.contains("ELITE") || k.contains("MINI")) return 8.0;
        if (k.contains("ZOMBIE") || k.contains("SKELETON") || k.contains("CREEPER") || k.contains("SPIDER")) return 1.6;
        if (k.contains("COD") || k.contains("SALMON") || k.contains("PUFFERFISH") || k.contains("TROPICAL")) return 1.2;
        return 1.0;
    }

    private double getJobLevelRepMultiplier(int lvl) {
        double rank = 1.0;
        if (lvl >= 50) rank = 2.25;
        else if (lvl >= 40) rank = 1.90;
        else if (lvl >= 30) rank = 1.60;
        else if (lvl >= 20) rank = 1.35;
        else if (lvl >= 10) rank = 1.15;
        double perLevel = plugin.getConfig().getDouble("jobs-reputation.level-bonus-per-level", 0.01);
        double maxLevelBonus = plugin.getConfig().getDouble("jobs-reputation.max-level-bonus", 0.50);
        return rank + Math.min(maxLevelBonus, Math.max(0, lvl * perLevel));
    }

    private double getDonateStatusMultiplier(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("jobs-reputation.donate-multipliers.legend", 11.00);
        if (p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("jobs-reputation.donate-multipliers.star", 7.00);
        if (p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("jobs-reputation.donate-multipliers.flame", 4.50);
        if (p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("jobs-reputation.donate-multipliers.spark", 2.50);
        return 1.0;
    }

    private void rewardVkRepSilent(org.bukkit.entity.Player p, int amount) {
        if (amount <= 0) return;
        try {
            org.bukkit.plugin.Plugin corePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null && corePlugin.isEnabled()) {
                Object api = corePlugin.getClass().getMethod("getApi").invoke(corePlugin);
                int vkId = (int) api.getClass().getMethod("getLinkedVkId", org.bukkit.entity.Player.class).invoke(api, p);
                if (vkId != -1) api.getClass().getMethod("addReputation", int.class, int.class).invoke(api, vkId, amount);
            }
        } catch (Exception ignored) {}
    }

    private void maybeNotifyRepBatch(org.bukkit.entity.Player p, String job) {
        String mode = plugin.getConfig().getString("jobs-reputation.message-mode", "chat_batch");
        if (mode.equalsIgnoreCase("silent")) return;
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long interval = plugin.getConfig().getLong("jobs-reputation.message-interval-seconds", 20) * 1000L;
        int pending = pendingRepNotify.getOrDefault(uuid, 0);
        if (pending <= 0 || now - lastRepNotify.getOrDefault(uuid, 0L) < interval) return;
        pendingRepNotify.put(uuid, 0);
        lastRepNotify.put(uuid, now);
        String msg = org.bukkit.ChatColor.GREEN + "💼 Jobs: +" + pending + " репутации ВК за работу" + org.bukkit.ChatColor.GRAY + " (" + job + ")";
        if (mode.equalsIgnoreCase("actionbar")) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg));
        } else {
            p.sendMessage(msg);
        }
    }

    public void addExp(org.bukkit.entity.Player p, String job, int amount) {
        UUID uuid = p.getUniqueId();
        double cfgMult = plugin.getConfig().getDouble("jobs." + job + ".exp-multiplier", 1.0);
        amount = (int)Math.max(1, Math.round(amount * cfgMult));
        if ("xp".equals(getSpecialization(uuid, job))) amount = (int)Math.max(1, Math.round(amount * 1.15));
        expData.putIfAbsent(uuid, new ConcurrentHashMap<>());
        lvlData.putIfAbsent(uuid, new ConcurrentHashMap<>());
        skillPoints.putIfAbsent(uuid, new ConcurrentHashMap<>());
        
        int currentExp = getExp(uuid, job);
        int currentLvl = getLevel(uuid, job);
        int maxLvl = plugin.getConfig().getInt("settings.max-level", 50);
        if (currentLvl >= maxLvl) return;

        currentExp += Math.max(0, amount);
        int levelsGained = 0;
        int pointEvery = plugin.getConfig().getInt("settings.skill-point-every-levels", 10);
        int repReward = plugin.getConfig().getInt("settings.reputation-per-level", 20);

        while (currentLvl < maxLvl && currentExp >= currentLvl * 1000) {
            int reqExp = currentLvl * 1000;
            currentExp -= reqExp;
            currentLvl++;
            levelsGained++;
            p.sendMessage(org.bukkit.ChatColor.GREEN + "✨ Уровень профессии " + job + " повышен до " + currentLvl + "!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            if (pointEvery > 0 && currentLvl % pointEvery == 0) {
                skillPoints.get(uuid).put(job, getSkillPoints(uuid, job) + 1);
                p.sendMessage(org.bukkit.ChatColor.GOLD + "Вы получили 1 Очко Навыков для профессии " + job + "! Откройте /jobs.");
            }
            rewardVkRep(p, repReward, "Новый уровень профессии " + job);
            giveVanillaRankReward(p, job, currentLvl);
        }

        expData.get(uuid).put(job, currentExp);
        lvlData.get(uuid).put(job, currentLvl);
        if (levelsGained > 0) saveAll();
    }

}
