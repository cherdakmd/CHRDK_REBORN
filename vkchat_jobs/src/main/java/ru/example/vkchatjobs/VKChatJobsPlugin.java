package ru.example.vkchatjobs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VKChatJobsPlugin extends JavaPlugin {
    private static VKChatJobsPlugin instance;
    private JobsDataManager jobsDataManager;
    private SkillManager skillManager;
    private PlacedBlockTracker placedBlockTracker;
    private WeeklyTaskManager weeklyTaskManager;
    private RankingManager rankingManager;
    private final Map<UUID, Set<String>> jobSkills = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerProfessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        updateConfigWithDefaults();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        jobsDataManager = new JobsDataManager(this);
        skillManager = new SkillManager(this);
        placedBlockTracker = new PlacedBlockTracker(this);
        weeklyTaskManager = new WeeklyTaskManager(this);
        rankingManager = new RankingManager(this);
        getServer().getPluginManager().registerEvents(placedBlockTracker, this);
        getServer().getPluginManager().registerEvents(new JobsListener(this), this);
        JobsCommand jobsCmd = new JobsCommand(this);
        getCommand("jobs").setExecutor(jobsCmd);
        getCommand("jobs").setTabCompleter(jobsCmd);

        getLogger().info("VKChatJobs успешно запущен!");


        // Автосейв прогресса Jobs: ежедневки, специализации, усталость, уровни, неделя, рейтинг.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (jobsDataManager != null) jobsDataManager.saveAll();
        if (placedBlockTracker != null) placedBlockTracker.save();
        if (weeklyTaskManager != null) weeklyTaskManager.save();
        if (rankingManager != null) rankingManager.save();
        }, 6000L, 6000L);
        // Восстановление усталости раз в минуту
        int restPerMinute = getConfig().getInt("fatigue.rest-per-minute", 10);
        if (getConfig().getBoolean("fatigue.enabled", true) && restPerMinute > 0) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    jobsDataManager.removeFatigue(p.getUniqueId(), restPerMinute);
                }
            }, 1200L, 1200L); // 1200 тиков = 60 секунд
        }

        // Пассивные эффекты профессий (раз в 2 секунды) — использует кеш навыков
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uid = p.getUniqueId();
                Set<String> cached = jobSkills.get(uid);
                if (cached == null) continue;

                Material hand = p.getInventory().getItemInMainHand().getType();
                String name = hand.name();
                Location loc = p.getLocation();
                World world = loc.getWorld();
                boolean dark = world != null && loc.getBlock().getLightLevel() <= 7;
                Biome biome = loc.getBlock().getBiome();
                boolean forest = biome.name().contains("FOREST") || biome.name().contains("TAIGA") || biome.name().contains("JUNGLE");

                // Шахтер
                if (name.endsWith("_PICKAXE")) {
                    if (cached.contains("miner:miner_haste")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, true, false, false));
                    }
                    if (dark && cached.contains("miner:miner_night")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, false));
                    }
                    if (dark && cached.contains("miner:miner_seism")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, false));
                    }
                }

                // Лесоруб
                if (name.endsWith("_AXE")) {
                    if (cached.contains("woodcutter:wood_haste")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, true, false, false));
                    }
                    if (forest && cached.contains("woodcutter:wood_bird")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 1, true, false, false));
                    }
                    if (forest && cached.contains("woodcutter:wood_forest")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2, true, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, true, false, false));
                    }
                }

                // Фермер
                if (name.endsWith("_HOE")) {
                    if (cached.contains("farmer:farm_speed")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, false));
                    }
                    if (cached.contains("farmer:farm_harvest")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                    if (cached.contains("farmer:farm_nature")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                }

                // Рыбак
                if (hand == Material.FISHING_ROD && cached.contains("fisherman:fish_luck")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 60, 0, true, false, false));
                }
                if (hand == Material.FISHING_ROD && cached.contains("fisherman:fish_neptune")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, false));
                }
            }
        }, 40L, 40L);

        // Еженедельный сброс рейтинга и проверка бродкаста (раз в 10 минут)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (rankingManager != null) {
                rankingManager.tryBroadcast();
                rankingManager.checkWeeklyReset();
            }
        }, 12000L, 12000L);

        // Заполняем кеш навыков для уже онлайн игроков (на случай перезагрузки плагина)
        for (Player p : Bukkit.getOnlinePlayers()) {
            rebuildJobSkills(p.getUniqueId());
        }
    }

    private void updateConfigWithDefaults() {
        reloadConfig();
        FileConfiguration config = getConfig();
        InputStream defStream = getResource("config.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
            config.options().copyDefaults(true);
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (jobsDataManager != null) jobsDataManager.saveAll();
        if (placedBlockTracker != null) placedBlockTracker.save();
        if (weeklyTaskManager != null) weeklyTaskManager.save();
        if (rankingManager != null) rankingManager.save();
        instance = null;
    }

    public static VKChatJobsPlugin getInstance() { return instance; }
    public JobsDataManager getJobsDataManager() { return jobsDataManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public PlacedBlockTracker getPlacedBlockTracker() { return placedBlockTracker; }
    public WeeklyTaskManager getWeeklyTaskManager() { return weeklyTaskManager; }
    public RankingManager getRankingManager() { return rankingManager; }

    public void rebuildJobSkills(UUID uuid) {
        Set<String> skills = new HashSet<>();
        String[] jobs = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        for (String job : jobs) {
            for (String skill : jobsDataManager.getUnlockedSkills(uuid, job)) {
                skills.add(job + ":" + skill);
            }
        }
        jobSkills.put(uuid, skills);
        playerProfessions.put(uuid, jobsDataManager.getTopJob(uuid));
    }

    public void clearJobSkills(UUID uuid) {
        jobSkills.remove(uuid);
        playerProfessions.remove(uuid);
    }

    public Set<String> getCachedSkills(UUID uuid) {
        return jobSkills.get(uuid);
    }

    public String getCachedProfession(UUID uuid) {
        return playerProfessions.get(uuid);
    }
}
