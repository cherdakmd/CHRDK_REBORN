package ru.example.vkchatjobs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class VKChatJobsPlugin extends JavaPlugin {
    private static VKChatJobsPlugin instance;
    private JobsDataManager jobsDataManager;
    private SkillManager skillManager;
    private PlacedBlockTracker placedBlockTracker;
    private WeeklyTaskManager weeklyTaskManager;
    private RankingManager rankingManager;

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

        // Пассивные эффекты профессий (раз в 2 секунды)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Material hand = p.getInventory().getItemInMainHand().getType();
                String name = hand.name();
                Location loc = p.getLocation();
                World world = loc.getWorld();
                boolean dark = world != null && loc.getBlock().getLightLevel() <= 7;
                Biome biome = loc.getBlock().getBiome();
                boolean forest = biome.name().contains("FOREST") || biome.name().contains("TAIGA") || biome.name().contains("JUNGLE");

                // Шахтер
                if (name.endsWith("_PICKAXE")) {
                    if (jobsDataManager.hasSkill(p.getUniqueId(), "miner", "miner_haste")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, true, false, false));
                    }
                    if (dark && jobsDataManager.hasSkill(p.getUniqueId(), "miner", "miner_night")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, false));
                    }
                    if (dark && jobsDataManager.hasSkill(p.getUniqueId(), "miner", "miner_seism")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, false));
                    }
                }

                // Лесоруб
                if (name.endsWith("_AXE")) {
                    if (jobsDataManager.hasSkill(p.getUniqueId(), "woodcutter", "wood_haste")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 0, true, false, false));
                    }
                    if (forest && jobsDataManager.hasSkill(p.getUniqueId(), "woodcutter", "wood_bird")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 1, true, false, false));
                    }
                    if (forest && jobsDataManager.hasSkill(p.getUniqueId(), "woodcutter", "wood_forest")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2, true, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0, true, false, false));
                    }
                }

                // Фермер
                if (name.endsWith("_HOE")) {
                    if (jobsDataManager.hasSkill(p.getUniqueId(), "farmer", "farm_speed")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, false));
                    }
                    if (jobsDataManager.hasSkill(p.getUniqueId(), "farmer", "farm_harvest")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                    if (jobsDataManager.hasSkill(p.getUniqueId(), "farmer", "farm_nature")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                }

                // Рыбак
                if (hand == Material.FISHING_ROD && jobsDataManager.hasSkill(p.getUniqueId(), "fisherman", "fish_luck")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 60, 0, true, false, false));
                }
                if (hand == Material.FISHING_ROD && jobsDataManager.hasSkill(p.getUniqueId(), "fisherman", "fish_neptune")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, false));
                }
            }
        }, 40L, 40L);

        // Еженедельный сброс рейтинга и проверка бродкаста (раз в 10 минут)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (rankingManager != null) {
                rankingManager.checkWeeklyReset();
                rankingManager.tryBroadcast();
            }
        }, 12000L, 12000L);
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
        if (jobsDataManager != null) jobsDataManager.saveAll();
        if (placedBlockTracker != null) placedBlockTracker.save();
        if (weeklyTaskManager != null) weeklyTaskManager.save();
        if (rankingManager != null) rankingManager.save();
    }

    public static VKChatJobsPlugin getInstance() { return instance; }
    public JobsDataManager getJobsDataManager() { return jobsDataManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public PlacedBlockTracker getPlacedBlockTracker() { return placedBlockTracker; }
    public WeeklyTaskManager getWeeklyTaskManager() { return weeklyTaskManager; }
    public RankingManager getRankingManager() { return rankingManager; }
}
