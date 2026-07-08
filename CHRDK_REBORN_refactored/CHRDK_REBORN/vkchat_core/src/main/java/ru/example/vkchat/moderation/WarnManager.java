package ru.example.vkchat.moderation;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class WarnManager {
    private final VKChatPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public WarnManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warns.yml");
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (Exception e) { plugin.getLogger().warning("Не удалось сохранить warns.yml: " + e.getMessage()); }
    }

    public int warn(String targetName, String admin, String reason) {
        String key = targetName.toLowerCase(Locale.ROOT);
        int count = data.getInt("players." + key + ".count", 0) + 1;
        data.set("players." + key + ".name", targetName);
        data.set("players." + key + ".count", count);
        List<String> history = data.getStringList("players." + key + ".history");
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        history.add(stamp + " | " + admin + " | " + reason);
        data.set("players." + key + ".history", history);
        save();

        Player online = Bukkit.getPlayerExact(targetName);
        String msg = ChatColor.RED + "⚠ Вам выдан варн " + ChatColor.YELLOW + count + ChatColor.RED + "/3. Причина: " + ChatColor.WHITE + reason;
        if (online != null) online.sendMessage(msg);

        Bukkit.broadcastMessage(ChatColor.RED + "⚠ Игрок " + ChatColor.YELLOW + targetName + ChatColor.RED + " получил предупреждение " + count + ". Причина: " + ChatColor.WHITE + reason);
        applyPunishmentIfNeeded(targetName, count, reason);
        return count;
    }

    public int removeWarn(String targetName, int amount) {
        String key = targetName.toLowerCase(Locale.ROOT);
        int count = Math.max(0, data.getInt("players." + key + ".count", 0) - Math.max(1, amount));
        data.set("players." + key + ".count", count);
        save();
        return count;
    }

    public void clearWarns(String targetName) {
        String key = targetName.toLowerCase(Locale.ROOT);
        data.set("players." + key + ".count", 0);
        data.set("players." + key + ".history", new ArrayList<String>());
        save();
    }

    public int getWarns(String targetName) {
        return data.getInt("players." + targetName.toLowerCase(Locale.ROOT) + ".count", 0);
    }

    public List<String> getHistory(String targetName) {
        return data.getStringList("players." + targetName.toLowerCase(Locale.ROOT) + ".history");
    }

    private void applyPunishmentIfNeeded(String targetName, int count, String reason) {
        if (count < 3 || count % 3 != 0) return;
        long durationMillis = getBanDurationMillis(count);
        if (durationMillis <= 0) return;
        Date expires = new Date(System.currentTimeMillis() + durationMillis);
        String banReason = "Автобан за " + count + " предупреждений. Последняя причина: " + reason;

        // [FIX] Бан по UUID + IP вместо имени
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            String uuid = online.getUniqueId().toString();
            String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : "unknown";

            // Бан по UUID
            Bukkit.getBanList(BanList.Type.NAME).addBan(uuid, banReason, expires, "VKChatWarns");
            // Бан по IP
            if (!ip.equals("unknown")) {
                Bukkit.getBanList(BanList.Type.IP).addBan(ip, banReason, expires, "VKChatWarns");
            }

            online.kickPlayer(ChatColor.RED + banReason + "\n" + ChatColor.YELLOW + "Бан до: " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(expires));
        } else {
            // Если игрок оффлайн, бан по имени (fallback)
            Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, banReason, expires, "VKChatWarns");
        }

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "⛔ " + targetName + " забанен за " + count + " варнов до " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(expires));
    }

    private long getBanDurationMillis(int warns) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("moderation.warns.ban-thresholds");
        int days;
        if (sec != null && sec.contains(String.valueOf(warns))) {
            days = sec.getInt(String.valueOf(warns), 1);
        } else {
            // 3 варна = 1 день, 6 = 3 дня, 9 = 7 дней, 12+ = 30 дней по умолчанию.
            if (warns >= 12) days = plugin.getConfig().getInt("moderation.warns.default-repeat-ban-days", 30);
            else if (warns >= 9) days = 7;
            else if (warns >= 6) days = 3;
            else days = 1;
        }
        return days * 24L * 60L * 60L * 1000L;
    }
}
