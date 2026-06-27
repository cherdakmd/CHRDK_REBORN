package ru.example.vkchat.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;

public class WallCheckerTask extends BukkitRunnable {
    private final VKChatPlugin plugin;
    private final HttpClient httpClient;
    private int lastPostId = -1;

    public WallCheckerTask(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void run() {
        String token = plugin.getConfig().getString("vk.token");
        int groupId = plugin.getConfig().getInt("vk.group-id");
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) return;

        try {
            String url = String.format("https://api.vk.com/method/wall.get?owner_id=-%d&count=2&access_token=%s&v=5.131", groupId, token);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            JSONObject json = new JSONObject(response.body()).getJSONObject("response");
            JSONArray items = json.getJSONArray("items");
            
            for (int i = 0; i < items.length(); i++) {
                JSONObject post = items.getJSONObject(i);
                int id = post.getInt("id");
                
                // Skip pinned post to always get the real latest post if it's new
                if (post.has("is_pinned") && post.getInt("is_pinned") == 1 && items.length() > 1) {
                    continue; 
                }
                
                if (lastPostId == -1) {
                    lastPostId = id;
                    break;
                }
                
                if (id > lastPostId) {
                    lastPostId = id;
                    String link = "https://vk.com/wall-" + groupId + "_" + id;
                    
                    String mcMsg = plugin.getConfigManager().getMessage("vk_wall_announce_mc").replace("{link}", link);
                    plugin.getServer().broadcastMessage(mcMsg);
                    
                    String vkMsg = plugin.getConfigManager().getMessage("vk_wall_announce_vk").replace("{link}", link);
                    plugin.getVkManager().sendToMainChat(org.bukkit.ChatColor.stripColor(vkMsg));
                }
                break;
            }
        } catch (Exception e) {
            // Ignore for background task
        }
    }
}