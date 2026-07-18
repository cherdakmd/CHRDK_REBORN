package ru.example.vkchatstarter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages YAML-based persistence for quest progress.
 * Data file: plugins/VKChatStarter/quest_progress.yml
 * Section per player UUID with keys: current_stage, progress, deaths, start_time, skipped, achievements
 */
public class QuestDataManager {
    private final VKChatStarterPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, PlayerQuestData> cache = new HashMap<>();

    public static class PlayerQuestData {
        public int currentStage;
        public int progress;
        public int deaths;
        public long startTime;
        public boolean skipped;
        public final Map<String, Boolean> achievements = new HashMap<>();
    }

    public QuestDataManager(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "quest_progress.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.log(Level.SEVERE, "Failed to create quest_progress.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        cache.clear();
        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerQuestData data = new PlayerQuestData();
                data.currentStage = dataConfig.getInt(uuidStr + ".current_stage", 0);
                data.progress = dataConfig.getInt(uuidStr + ".progress", 0);
                data.deaths = dataConfig.getInt(uuidStr + ".deaths", 0);
                data.startTime = dataConfig.getLong(uuidStr + ".start_time", 0L);
                data.skipped = dataConfig.getBoolean(uuidStr + ".skipped", false);
                if (dataConfig.contains(uuidStr + ".achievements")) {
                    for (String achKey : dataConfig.getConfigurationSection(uuidStr + ".achievements").getKeys(false)) {
                        data.achievements.put(achKey, dataConfig.getBoolean(uuidStr + ".achievements." + achKey));
                    }
                }
                cache.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.log(Level.INFO, "Loaded quest progress for " + cache.size() + " players.");
    }

    public void save() {
        if (dataConfig == null) return;
        for (Map.Entry<UUID, PlayerQuestData> entry : cache.entrySet()) {
            String path = entry.getKey().toString();
            PlayerQuestData data = entry.getValue();
            dataConfig.set(path + ".current_stage", data.currentStage);
            dataConfig.set(path + ".progress", data.progress);
            dataConfig.set(path + ".deaths", data.deaths);
            dataConfig.set(path + ".start_time", data.startTime);
            dataConfig.set(path + ".skipped", data.skipped);
            for (Map.Entry<String, Boolean> ach : data.achievements.entrySet()) {
                dataConfig.set(path + ".achievements." + ach.getKey(), ach.getValue());
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save quest_progress.yml", e);
        }
    }

    public PlayerQuestData getData(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new PlayerQuestData());
    }

    public void setData(UUID uuid, PlayerQuestData data) {
        cache.put(uuid, data);
    }

    public void clearData(UUID uuid) {
        cache.remove(uuid);
        if (dataConfig != null) {
            dataConfig.set(uuid.toString(), null);
        }
    }

    public void reload() {
        load();
    }
}
