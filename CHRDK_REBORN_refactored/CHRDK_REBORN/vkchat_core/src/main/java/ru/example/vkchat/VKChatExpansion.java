package ru.example.vkchat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import java.util.UUID;

public class VKChatExpansion extends PlaceholderExpansion {

    private final VKChatPlugin plugin;

    public VKChatExpansion(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vkchat";
    }

    @Override
    public String getAuthor() {
        return "CHRDK";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        
        UUID uuid = player.getUniqueId();

        
        if (params.equalsIgnoreCase("nation")) {
            org.bukkit.plugin.Plugin nationsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                try {
                    Object nationMgr = nationsPlugin.getClass().getMethod("getNationManager").invoke(nationsPlugin);
                    String n = (String) nationMgr.getClass().getMethod("getPlayerNation", org.bukkit.entity.Player.class).invoke(nationMgr, player.getPlayer());
                    return n != null ? n : "Нет";
                } catch (Exception e) {}
            }
            return "Нет";
        }
        
        if (params.equalsIgnoreCase("nation_prefix")) {
            org.bukkit.plugin.Plugin nationsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                try {
                    Object nationMgr = nationsPlugin.getClass().getMethod("getNationManager").invoke(nationsPlugin);
                    String n = (String) nationMgr.getClass().getMethod("getPlayerNation", org.bukkit.entity.Player.class).invoke(nationMgr, player.getPlayer());
                    if (n != null) {
                        return nationsPlugin.getConfig().getString("nations." + n + ".prefix", "[" + n + "]");
                    }
                } catch (Exception e) {}
            }
            return "";
        }

        if (params.equalsIgnoreCase("reputation")) {
            int vkId = plugin.getAuthManager().getLinkedVkId(uuid);
            if (vkId != -1) {
                return String.valueOf(plugin.getReputationManager().getPoints(vkId));
            }
            return "";
        }
        
        if (params.equalsIgnoreCase("linked")) {
            return plugin.getAuthManager().isLinked(uuid) ? "Да" : "Нет";
        }
        
        if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(plugin.getStatsManager().getKills(uuid));
        }

        if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(plugin.getStatsManager().getDeaths(uuid));
        }

        if (params.equalsIgnoreCase("blocks")) {
            return String.valueOf(plugin.getStatsManager().getBlocks(uuid));
        }

        if (params.equalsIgnoreCase("achievements")) {
            return String.valueOf(plugin.getStatsManager().getAchievements(uuid));
        }

        return null;
    }
}