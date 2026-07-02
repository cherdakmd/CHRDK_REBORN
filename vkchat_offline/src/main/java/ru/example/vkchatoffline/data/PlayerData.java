package ru.example.vkchatoffline.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.example.vkchatoffline.managers.ZoneData.ClassType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {
    private final File file;
    private FileConfiguration cfg;

    public PlayerData(File dataFolder) {
        file = new File(dataFolder, "players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (Exception e) {}
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { cfg.save(file); } catch (IOException ignored) {}
    }

    // ===== УРОВЕНЬ И КЛАСС =====
    public int getLevel(int vkId) {
        return cfg.getInt("players." + vkId + ".level", 1);
    }

    public void setLevel(int vkId, int level) {
        cfg.set("players." + vkId + ".level", level);
    }

    public void addXp(int vkId, int xp) {
        int cur = cfg.getInt("players." + vkId + ".xp", 0) + xp;
        int level = getLevel(vkId);
        int needed = level * 100;
        while (cur >= needed) {
            cur -= needed;
            level++;
            needed = level * 100;
        }
        cfg.set("players." + vkId + ".level", level);
        cfg.set("players." + vkId + ".xp", cur);
    }

    public int getXp(int vkId) {
        return cfg.getInt("players." + vkId + ".xp", 0);
    }

    public int getXpNeeded(int vkId) {
        return getLevel(vkId) * 100;
    }

    // ===== КЛАСС =====
    public String getClassName(int vkId) {
        return cfg.getString("players." + vkId + ".class", "WARRIOR");
    }

    public void setClass(int vkId, String className) {
        cfg.set("players." + vkId + ".class", className);
    }

    public boolean hasClass(int vkId) {
        return cfg.contains("players." + vkId + ".class");
    }

    // ===== СТАТИСТИКА =====
    public int getStat(int vkId, String stat) {
        return cfg.getInt("players." + vkId + "." + stat, 0);
    }

    public void addStat(int vkId, String stat, int amount) {
        cfg.set("players." + vkId + "." + stat, getStat(vkId, stat) + amount);
    }

    // adventuresCompleted, enemiesKilled, bossesKilled, totalRep, totalRes
    public int getAdventuresCompleted(int vkId) { return getStat(vkId, "adventures"); }
    public void addAdventure(int vkId) { addStat(vkId, "adventures", 1); }
    public int getEnemiesKilled(int vkId) { return getStat(vkId, "kills"); }
    public void addKill(int vkId) { addStat(vkId, "kills", 1); }
    public int getBossesKilled(int vkId) { return getStat(vkId, "bosses"); }
    public void addBoss(int vkId) { addStat(vkId, "bosses", 1); }

    // ===== СОБРАННЫЕ ЧАСТИ СЕТОВ =====
    public List<String> getCollectedPieces(int vkId) {
        return cfg.getStringList("players." + vkId + ".pieces");
    }

    public void addPiece(int vkId, String pieceName) {
        List<String> pieces = getCollectedPieces(vkId);
        if (!pieces.contains(pieceName)) {
            pieces.add(pieceName);
            cfg.set("players." + vkId + ".pieces", pieces);
        }
    }

    public boolean hasPiece(int vkId, String pieceName) {
        return getCollectedPieces(vkId).contains(pieceName);
    }

    // ===== РЕСУРСЫ =====
    public int getResource(int vkId, String resourceName) {
        return cfg.getInt("players." + vkId + ".resources." + resourceName, 0);
    }

    public void addResource(int vkId, String resourceName, int amount) {
        cfg.set("players." + vkId + ".resources." + resourceName, getResource(vkId, resourceName) + amount);
    }

    public void spendResource(int vkId, String resourceName, int amount) {
        addResource(vkId, resourceName, -amount);
    }

    // ===== РЕПУТАЦИЯ (локальный баланс приключений) =====
    public int getAdventureRep(int vkId) {
        return cfg.getInt("players." + vkId + ".rep", 0);
    }

    public void addAdventureRep(int vkId, int amount) {
        cfg.set("players." + vkId + ".rep", getAdventureRep(vkId) + amount);
    }

    // ===== КУЛДАУН =====
    public long getCooldown(int vkId) {
        return cfg.getLong("players." + vkId + ".cooldown", 0);
    }

    public void setCooldown(int vkId, long time) {
        cfg.set("players." + vkId + ".cooldown", time);
    }

    public boolean isOnCooldown(int vkId, int cooldownMinutes) {
        return System.currentTimeMillis() - getCooldown(vkId) < cooldownMinutes * 60000L;
    }

    // ===== ЕЖЕДНЕВНЫЙ ЛИМИТ =====
    public int getAdventuresToday(int vkId) {
        long today = System.currentTimeMillis() / 86400000;
        long stored = cfg.getLong("players." + vkId + ".day", 0);
        if (stored != today) {
            cfg.set("players." + vkId + ".day", today);
            cfg.set("players." + vkId + ".daily", 0);
        }
        return cfg.getInt("players." + vkId + ".daily", 0);
    }

    public void incrementDaily(int vkId) {
        int cur = getAdventuresToday(vkId) + 1;
        cfg.set("players." + vkId + ".daily", cur);
    }
}
