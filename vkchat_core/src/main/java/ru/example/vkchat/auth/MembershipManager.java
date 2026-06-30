package ru.example.vkchat.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

/**
 * Менеджер проверки членства в группе ВК
 * Проверяет, является ли пользователь участником группы
 */
public class MembershipManager {
    private final VKChatPlugin plugin;
    private final HttpClient httpClient;

    public MembershipManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Проверить, является ли пользователь участником группы
     */
    public boolean isGroupMember(int vkId) {
        try {
            int groupId = plugin.getConfig().getInt("vk.group-id");
            String token = plugin.getConfig().getString("vk.token");

            if (groupId == 0 || token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
                plugin.getLogger().warning("VK group-id или token не настроены!");
                return true; // Пропускаем если не настроено
            }

            String url = String.format(
                "https://api.vk.com/method/groups.isMember?group_id=%d&user_id=%d&access_token=%s&v=5.131",
                groupId, vkId, token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

            if (json.has("error")) {
                plugin.getLogger().warning("VK API ошибка проверки группы: " + json.getJSONObject("error").getString("error_msg"));
                return true; // При ошибке НЕ кикаем
            }

            if (json.has("response")) {
                return json.getInt("response") == 1;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка проверки членства в группе: " + e.getMessage());
        }

        return true; // При ошибке НЕ кикаем
    }

    /**
     * Проверить, является ли пользователь участником беседы
     */
    public boolean isChatMember(int vkId) {
        try {
            int peerId = plugin.getConfig().getInt("vk.peer-id");
            String token = plugin.getConfig().getString("vk.token");

            if (peerId == 0 || token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
                return true; // Пропускаем если не настроено
            }

            String url = String.format(
                "https://api.vk.com/method/messages.getConversationMembers?peer_id=%d&access_token=%s&v=5.131",
                peerId, token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

            if (json.has("error")) {
                plugin.getLogger().warning("VK API ошибка проверки беседы: " + json.getJSONObject("error").getString("error_msg"));
                return true; // При ошибке НЕ кикаем
            }

            if (json.has("response")) {
                JSONObject resp = json.getJSONObject("response");
                if (resp.has("profiles")) {
                    var profiles = resp.getJSONArray("profiles");
                    for (int i = 0; i < profiles.length(); i++) {
                        if (profiles.getJSONObject(i).getInt("id") == vkId) {
                            return true;
                        }
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка проверки членства в беседе: " + e.getMessage());
        }

        return false;
    }

    /**
     * Проверить полное членство (группа + беседа)
     */
    public boolean isFullMember(int vkId) {
        // Проверка группы (обязательно)
        if (!isGroupMember(vkId)) {
            return false;
        }

        // Проверка беседы (опционально)
        if (plugin.getConfig().getBoolean("auth.link.require-chat-membership", false)) {
            if (!isChatMember(vkId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Получить сообщение об ошибке членства
     */
    public String getMembershipErrorMessage(int vkId) {
        if (!isGroupMember(vkId)) {
            return "§c❌ Для игры необходимо быть участником группы ВК!\n" +
                   "§eГруппа: " + plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn") + "\n" +
                   "§7После вступления перезайди на сервер.";
        }

        if (plugin.getConfig().getBoolean("auth.link.require-chat-membership", false) && !isChatMember(vkId)) {
            return "§c❌ Для игры необходимо быть участником беседы ВК!\n" +
                   "§eБеседа: " + plugin.getConfig().getString("vk.chat-invite-link", "") + "\n" +
                   "§7После вступления перезайди на сервер.";
        }

        return "";
    }
}
