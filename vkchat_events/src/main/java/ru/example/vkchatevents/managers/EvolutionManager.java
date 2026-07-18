package ru.example.vkchatevents.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [31-35] Слияние, Трансцендентность, Апокалипсис, Перерождение, Просветление
 */
public class EvolutionManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final File dataFile;

    // Слияние
    private final Map<UUID, Integer> fusionLevel = new ConcurrentHashMap<>();
    // Трансцендентность
    private final Map<UUID, Integer> transcendence = new ConcurrentHashMap<>();
    // Апокалипсис
    private final Map<UUID, Integer> apocalypseSurvived = new ConcurrentHashMap<>();
    // Перерождение
    private final Map<UUID, Integer> rebirthCount = new ConcurrentHashMap<>();
    // Просветление
    private final Map<UUID, Integer> enlightenment = new ConcurrentHashMap<>();

    public EvolutionManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "evolution_data.yml");
        load();
        startAutoSave();
    }

    // Слияние
    public void fuse(UUID uuid) {
        fusionLevel.merge(uuid, 1, Integer::sum);
    }

    public int getFusionLevel(UUID uuid) {
        return fusionLevel.getOrDefault(uuid, 0);
    }

    // Трансцендентность
    public void transcend(UUID uuid) {
        transcendence.merge(uuid, 1, Integer::sum);
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) {
            VKChatBridge.addPoints(
                    VKChatBridge.getLinkedVkId(p), 500);
        }
    }

    // Апокалипсис
    public void surviveApocalypse(UUID uuid) {
        apocalypseSurvived.merge(uuid, 1, Integer::sum);
    }

    // Перерождение
    public void rebirth(UUID uuid) {
        rebirthCount.merge(uuid, 1, Integer::sum);
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) {
            VKChatBridge.addPoints(
                    VKChatBridge.getLinkedVkId(p), 1000);
        }
    }

    // Просветление
    public void enlighten(UUID uuid) {
        enlightenment.merge(uuid, 1, Integer::sum);
    }

    public String getEvolutionStats(UUID uuid) {
        return "🧬 Эволюция:\n" +
                "• Слияние: ур. " + getFusionLevel(uuid) + "\n" +
                "• Трансцендентность: " + transcendence.getOrDefault(uuid, 0) + "\n" +
                "• Апокалипсисов пережито: " + apocalypseSurvived.getOrDefault(uuid, 0) + "\n" +
                "• Перерождений: " + rebirthCount.getOrDefault(uuid, 0) + "\n" +
                "• Просветление: " + enlightenment.getOrDefault(uuid, 0);
    }

    // ═══ ПЕРСИСТЕНТНОСТЬ ═══

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(fusionLevel.keySet());
        allPlayers.addAll(transcendence.keySet());
        allPlayers.addAll(apocalypseSurvived.keySet());
        allPlayers.addAll(rebirthCount.keySet());
        allPlayers.addAll(enlightenment.keySet());

        for (UUID uuid : allPlayers) {
            String path = uuid.toString();
            config.set(path + ".fusion_count", fusionLevel.getOrDefault(uuid, 0));
            config.set(path + ".transcendence_count", transcendence.getOrDefault(uuid, 0));
            config.set(path + ".apocalypse_count", apocalypseSurvived.getOrDefault(uuid, 0));
            config.set(path + ".rebirth_count", rebirthCount.getOrDefault(uuid, 0));
            config.set(path + ".enlightenment_count", enlightenment.getOrDefault(uuid, 0));
        }

        try {
            plugin.getDataFolder().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка сохранения evolution_data.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (!dataFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                fusionLevel.put(uuid, config.getInt(uuidStr + ".fusion_count", 0));
                transcendence.put(uuid, config.getInt(uuidStr + ".transcendence_count", 0));
                apocalypseSurvived.put(uuid, config.getInt(uuidStr + ".apocalypse_count", 0));
                rebirthCount.put(uuid, config.getInt(uuidStr + ".rebirth_count", 0));
                enlightenment.put(uuid, config.getInt(uuidStr + ".enlightenment_count", 0));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Невалидный UUID в evolution_data.yml: " + uuidStr);
            }
        }
        plugin.getLogger().info("EvolutionManager: загружены данные для " + fusionLevel.size() + " игроков.");
    }

    private void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }
}
