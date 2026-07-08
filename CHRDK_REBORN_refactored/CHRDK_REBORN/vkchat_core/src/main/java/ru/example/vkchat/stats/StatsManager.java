package ru.example.vkchat.stats;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class StatsManager {
    private static final java.util.Set<String> VALID_STATS = java.util.Set.of("kills", "deaths", "blocks", "achievements");
    private final VKChatPlugin plugin;
    
    // Временные счетчики для онлайна за сутки
    private volatile int todayJoins = 0;
    private volatile int totalJoins = 0;
    private Economy econ = null;

    public StatsManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        
        // Восстановление totalJoins
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total FROM vkchat_auth");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                totalJoins = rs.getInt("total"); // Примерно равно кол-ву уникальных игроков
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addJoin() {
        todayJoins++;
    }

    public int getTodayJoins() { return todayJoins; }
    public int getTotalJoins() { return totalJoins; }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public Economy getEconomy() { return econ; }

    public int getKills(UUID uuid) { return getStat(uuid, "kills"); }
    public int getDeaths(UUID uuid) { return getStat(uuid, "deaths"); }
    public int getBlocks(UUID uuid) { return getStat(uuid, "blocks"); }
    public int getAchievements(UUID uuid) { return getStat(uuid, "achievements"); }

    private int getStat(UUID uuid, String stat) {
        if (!VALID_STATS.contains(stat)) throw new IllegalArgumentException("Invalid stat: " + stat);
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT " + stat + " FROM vkchat_stats WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(stat);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private void incrementStat(UUID uuid, String stat) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps;
                if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                    ps = conn.prepareStatement("INSERT INTO vkchat_stats (uuid, " + stat + ") VALUES (?, 1) ON DUPLICATE KEY UPDATE " + stat + " = " + stat + " + 1");
                } else {
                    ps = conn.prepareStatement("INSERT INTO vkchat_stats (uuid, " + stat + ") VALUES (?, 1) ON CONFLICT(uuid) DO UPDATE SET " + stat + " = " + stat + " + 1");
                }
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void addKill(Player p) { incrementStat(p.getUniqueId(), "kills"); }
    public void addDeath(Player p) { incrementStat(p.getUniqueId(), "deaths"); }
    public void addBlockBreak(Player p) { incrementStat(p.getUniqueId(), "blocks"); }
    public void addAchievement(Player p) { incrementStat(p.getUniqueId(), "achievements"); }
    
    public String getTopPlayersString() {
        Map<String, Double> scores = new HashMap<>();
        double moneyWeight = plugin.getConfig().getDouble("stats.money-weight", 0.1);
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM vkchat_stats");
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                int kills = rs.getInt("kills");
                int blocks = rs.getInt("blocks");
                int deaths = rs.getInt("deaths");
                
                double score = (kills * 10) + blocks - (deaths * 5);
                
                if (econ != null) {
                    org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(UUID.fromString(uuidStr));
                    if (op != null) {
                        score += econ.getBalance(op) * moneyWeight;
                    }
                }
                scores.put(uuidStr, score);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        List<Map.Entry<String, Double>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Double> entry : list) {
            if (count >= 10) break;
            org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(UUID.fromString(entry.getKey()));
            String name = op != null && op.getName() != null ? op.getName() : "OfflinePlayer";
            sb.append(count + 1).append(". ").append(name).append(" - ").append(Math.round(entry.getValue())).append("\n");
            count++;
        }
        if (sb.length() > 0) {
            sb.append("\n Всего игроков в рейтинге: ").append(scores.size());
        }
        return sb.length() == 0 ? "Нет данных" : sb.toString().trim();
    }

    public int getRank(UUID targetUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            int myKills = getKills(targetUuid);
            int myBlocks = getBlocks(targetUuid);
            int myTotal = myKills + myBlocks;
            PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) as rank FROM vkchat_stats WHERE (kills + blocks) > ?");
            ps.setInt(1, myTotal);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("rank") + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public int getServerTotalPlayers() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total FROM vkchat_stats");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("total");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public void save() { }
}
