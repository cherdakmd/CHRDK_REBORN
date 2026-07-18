package ru.example.vkchatmobs.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BestiaryManager {

    private final VKChatMobsPlugin plugin;
    private final Map<UUID, Map<String, Integer>> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Set<Integer>>> claimed = new ConcurrentHashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    private boolean enabled;
    private final SortedMap<Integer, MilestoneDef> milestones = new TreeMap<>();
    private static final String HP_KEY = "bestiary_hp_bonus";
    private static final String DMG_KEY = "bestiary_dmg_bonus";
    private static final String TOTAL_KEY = "bestiary_total_kills";

    public static class MilestoneDef {
        public final int kills;
        public final int hpBonus;
        public final double damageBonus;
        public final int repReward;
        public MilestoneDef(int kills, int hpBonus, double damageBonus, int repReward) {
            this.kills = kills; this.hpBonus = hpBonus; this.damageBonus = damageBonus;
            this.repReward = repReward;
        }
    }

    public BestiaryManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        milestones.clear();
        var cfg = plugin.getConfig().getConfigurationSection("bestiary");
        if (cfg == null) { enabled = false; return; }
        enabled = cfg.getBoolean("enabled", true);
        ConfigurationSection ms = cfg.getConfigurationSection("milestones");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                try {
                    int kills = Integer.parseInt(key);
                    ConfigurationSection s = ms.getConfigurationSection(key);
                    if (s == null) continue;
                    milestones.put(kills, new MilestoneDef(
                        kills,
                        s.getInt("hp-bonus", 0),
                        s.getDouble("damage-bonus", 0),
                        s.getInt("rep-reward", 0)
                    ));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public void load() {
        kills.clear();
        claimed.clear();
        dataFile = new File(plugin.getDataFolder(), "bestiary_data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать bestiary_data.yml: " + e.getMessage());
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = dataConfig.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { continue; }
            ConfigurationSection pSec = players.getConfigurationSection(uuidStr);
            if (pSec == null) continue;
            ConfigurationSection kSec = pSec.getConfigurationSection("kills");
            if (kSec != null) {
                Map<String, Integer> kmap = new ConcurrentHashMap<>();
                for (String type : kSec.getKeys(false)) {
                    kmap.put(type, kSec.getInt(type, 0));
                }
                kills.put(uuid, kmap);
            }
            ConfigurationSection cSec = pSec.getConfigurationSection("claimed");
            if (cSec != null) {
                Map<String, Set<Integer>> cmap = new ConcurrentHashMap<>();
                for (String type : cSec.getKeys(false)) {
                    Set<Integer> set = new HashSet<>();
                    for (String v : cSec.getStringList(type)) {
                        try { set.add(Integer.parseInt(v)); } catch (Exception ignored) {}
                    }
                    cmap.put(type, set);
                }
                claimed.put(uuid, cmap);
            }
        }
        plugin.getLogger().info("Загружен бестиарий: " + kills.size() + " игроков");
    }

    public void save() {
        if (dataConfig == null) return;
        dataConfig.set("players", null);
        ConfigurationSection players = dataConfig.createSection("players");
        for (Map.Entry<UUID, Map<String, Integer>> e : kills.entrySet()) {
            ConfigurationSection pSec = players.createSection(e.getKey().toString());
            ConfigurationSection kSec = pSec.createSection("kills");
            for (Map.Entry<String, Integer> ke : e.getValue().entrySet()) {
                kSec.set(ke.getKey(), ke.getValue());
            }
            Map<String, Set<Integer>> cMap = claimed.get(e.getKey());
            if (cMap != null && !cMap.isEmpty()) {
                ConfigurationSection cSec = pSec.createSection("claimed");
                for (Map.Entry<String, Set<Integer>> ce : cMap.entrySet()) {
                    cSec.set(ce.getKey(), new ArrayList<>(ce.getValue()));
                }
            }
        }
        try { dataConfig.save(dataFile); } catch (IOException ex) {
            plugin.getLogger().severe("Ошибка сохранения bestiary_data.yml: " + ex.getMessage());
        }
    }

    public boolean isMilestoneClaimed(Player player, String mobType, int threshold) {
        Map<String, Set<Integer>> cMap = claimed.get(player.getUniqueId());
        if (cMap == null) return false;
        Set<Integer> set = cMap.get(mobType);
        return set != null && set.contains(threshold);
    }

    public void recordKill(Player player, EntityType type) {
        if (!enabled) return;
        String key = type.name();
        Map<String, Integer> kmap = kills.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        kmap.merge(key, 1, Integer::sum);
        updateTotalKills(player);
    }

    public int getKills(Player player, String mobType) {
        Map<String, Integer> kmap = kills.get(player.getUniqueId());
        return kmap != null ? kmap.getOrDefault(mobType, 0) : 0;
    }

    public int getTotalKills(Player player) {
        int total = 0;
        Map<String, Integer> kmap = kills.get(player.getUniqueId());
        if (kmap != null) {
            for (int v : kmap.values()) total += v;
        }
        return total;
    }

    public Set<String> getDiscoveredTypes(UUID uuid) {
        Map<String, Integer> kmap = kills.get(uuid);
        return kmap != null ? kmap.keySet() : Collections.emptySet();
    }

    public void updateTotalKills(Player player) {
        int total = getTotalKills(player);
        player.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, TOTAL_KEY),
            PersistentDataType.INTEGER, total
        );
    }

    public List<Integer> getAvailableMilestones(Player player, String mobType) {
        int kc = getKills(player, mobType);
        Set<Integer> claimedSet = claimed.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(mobType, k -> new HashSet<>());
        List<Integer> available = new ArrayList<>();
        for (int threshold : milestones.keySet()) {
            if (kc >= threshold && !claimedSet.contains(threshold)) {
                available.add(threshold);
            }
        }
        return available;
    }

    public boolean claimMilestone(Player player, String mobType, int threshold) {
        MilestoneDef def = milestones.get(threshold);
        if (def == null) return false;
        Set<Integer> claimedSet = claimed.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(mobType, k -> new HashSet<>());
        if (claimedSet.contains(threshold)) return false;
        if (getKills(player, mobType) < threshold) return false;
        claimedSet.add(threshold);
        int totalHp = def.hpBonus;
        double totalDmg = def.damageBonus;
        int totalRep = def.repReward;
        for (int t : milestones.keySet()) {
            if (t < threshold) continue;
            MilestoneDef md = milestones.get(t);
            for (Map.Entry<String, Set<Integer>> e : claimed.getOrDefault(player.getUniqueId(), Collections.emptyMap()).entrySet()) {
                if (e.getValue().contains(t)) {
                    if (t != threshold) {
                        totalHp += md.hpBonus;
                        totalDmg += md.damageBonus;
                        totalRep += md.repReward;
                    }
                }
            }
        }
        applyHpBonus(player);
        if (totalRep > 0) VKChatBridge.addEffectiveRep(player, totalRep * 2);
        return true;
    }

    public void applyHpBonus(Player player) {
        int totalHp = 0;
        double totalDmg = 0;
        Map<String, Set<Integer>> cMap = claimed.get(player.getUniqueId());
        if (cMap != null) {
            for (Map.Entry<String, Set<Integer>> e : cMap.entrySet()) {
                for (int t : e.getValue()) {
                    MilestoneDef def = milestones.get(t);
                    if (def != null) {
                        totalHp += def.hpBonus;
                        totalDmg += def.damageBonus;
                    }
                }
            }
        }
        player.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, HP_KEY),
            PersistentDataType.INTEGER, totalHp
        );
        player.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, DMG_KEY),
            PersistentDataType.DOUBLE, totalDmg
        );
        player.setMaxHealth(20.0 + totalHp);
    }

    public int getTotalHpBonus(Player player) {
        return player.getPersistentDataContainer().getOrDefault(
            new org.bukkit.NamespacedKey(plugin, HP_KEY),
            PersistentDataType.INTEGER, 0
        );
    }

    public double getTotalDamageBonus(Player player) {
        return player.getPersistentDataContainer().getOrDefault(
            new org.bukkit.NamespacedKey(plugin, DMG_KEY),
            PersistentDataType.DOUBLE, 0.0
        );
    }

    public void applyOnJoin(Player player) {
        if (!enabled) return;
        applyHpBonus(player);
        updateTotalKills(player);
    }

    public boolean isEnabled() { return enabled; }
    public SortedMap<Integer, MilestoneDef> getMilestones() { return milestones; }
    public Map<String, Integer> getKillMap(UUID uuid) {
        return kills.getOrDefault(uuid, Collections.emptyMap());
    }
}
