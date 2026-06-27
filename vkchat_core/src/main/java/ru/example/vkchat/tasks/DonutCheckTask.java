package ru.example.vkchat.tasks;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class DonutCheckTask extends BukkitRunnable {
    private final VKChatPlugin plugin;
    private final HttpClient httpClient;

    public DonutCheckTask(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void run() {
        FileConfiguration donutConfig = plugin.getConfigManager().getDonutConfig();
        if (!donutConfig.getBoolean("enabled", false)) return;
        
        String token = plugin.getConfig().getString("vk.token");
        int groupId = plugin.getConfig().getInt("vk.group-id");
        
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) return;

        try {
            // Запрос на получение донов группы
            String url = String.format("https://api.vk.com/method/groups.getMembers?group_id=%d&filter=donut&access_token=%s&v=5.131", groupId, token);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            JSONObject json = new JSONObject(response.body());
            if (!json.has("response")) return;

            JSONArray items = json.getJSONObject("response").getJSONArray("items");
            Set<Integer> donuts = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                donuts.add(items.getInt(i));
            }

            File authFile = new File(plugin.getDataFolder(), "auth.yml");
            FileConfiguration authConfig = YamlConfiguration.loadConfiguration(authFile);
            boolean changed = false;

            for (String uuidStr : authConfig.getKeys(false)) {
                int vkId = authConfig.getInt(uuidStr + ".vk_id", -1);
                if (vkId != -1) {
                    boolean wasDonut = authConfig.getBoolean(uuidStr + ".is_donut", false);
                    boolean isDonut = donuts.contains(vkId);

                    if (isDonut && !wasDonut) {
                        // Игрок стал доном
                        String playerName = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                        if (playerName == null) playerName = "Unknown";
                        final String fPlayerName = playerName;
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (String cmd : donutConfig.getStringList("on-subscribe")) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{uuid}", uuidStr).replace("{player}", fPlayerName));
                            }
                            plugin.getLogger().info("Игрок " + fPlayerName + " получил подписку VK Donut.");
                        });
                        authConfig.set(uuidStr + ".is_donut", true);
                        changed = true;
                    } else if (!isDonut && wasDonut) {
                        // Игрок перестал быть доном
                        String playerName = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                        if (playerName == null) playerName = "Unknown";
                        final String fPlayerName = playerName;
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (String cmd : donutConfig.getStringList("on-unsubscribe")) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{uuid}", uuidStr).replace("{player}", fPlayerName));
                            }
                            plugin.getLogger().info("Игрок " + fPlayerName + " потерял подписку VK Donut.");
                        });
                        authConfig.set(uuidStr + ".is_donut", false);
                        changed = true;
                    }
                }
            }
            
            if (changed) {
                authConfig.save(authFile);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при проверке VK Donut: " + e.getMessage());
        }
    }
}