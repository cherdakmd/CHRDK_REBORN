package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.example.vkchat.VKChatPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Улучшенный VK LongPoll менеджер.
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Exponential backoff при ошибках</li>
 *   <li>Rate limiting (ограничение запросов)</li>
 *   <li>Автоматический reconnect</li>
 *   <li>Кэширование информации о пользователях</li>
 *   <li>Отправка сообщений и клавиатур</li>
 * </ul>
 * 
 * <p>Пример использования:</p>
 * <pre>{@code
 * VKLongPollManager vk = plugin.getVkLongPollManager();
 * vk.sendToMainChat("Привет, мир!");
 * }</pre>
 * 
 * @author cherdakmd
 * @version 2.0.7
 */
public class VKLongPollManager {

    private final VKChatPlugin plugin;
    private final String token;
    private final int groupId;
    private final int peerId;

    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

    private String server;
    private String key;
    private String ts;

    // Rate limiting
    private volatile long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 350; // ~3 запроса в секунду

    // Reconnect
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    private static final long BASE_BACKOFF_MS = 1000;

    // Кэширование пользователей
    private final java.util.Map<Integer, JSONObject> userCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Long> userCacheTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long USER_CACHE_TTL = 300000; // 5 минут

    /**
     * Создаёт новый экземпляр VKLongPollManager.
     * 
     * @param plugin экземпляр главного плагина
     */
    public VKLongPollManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.token = plugin.getConfig().getString("vk.token");
        this.groupId = plugin.getConfig().getInt("vk.group-id");
        this.peerId = plugin.getConfig().getInt("vk.peer-id");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Запускает LongPoll соединение.
     * 
     * <p>Метод проверяет наличие токена и запускает фоновый поток
     * для обработки входящих сообщений.</p>
     */
    public void start() {
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
            plugin.getLogger().warning("[VK] Token не настроен. VK-функции отключены.");
            return;
        }

        running.set(true);
        plugin.getLogger().info("[VK] LongPoll запущен.");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::longPollLoop);
    }

    /**
     * Останавливает LongPoll соединение.
     */
    public void stop() {
        running.set(false);
        plugin.getLogger().info("[VK] LongPoll остановлен.");
    }

    /**
     * Основной цикл LongPoll.
     * 
     * <p>Постоянно опрашивает VK API на наличие новых сообщений
     * и обрабатывает их.</p>
     */
    private void longPollLoop() {
        int backoff = 0;

        while (running.get()) {
            try {
                if (server == null || key == null || ts == null) {
                    updateLongPollServer();
                    backoff = 0;
                }

                String url = String.format("%s?act=a_check&key=%s&ts=%s&wait=25", server, key, ts);

                rateLimit();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    handleError("HTTP " + response.statusCode());
                    continue;
                }

                JSONObject json = new JSONObject(response.body());

                if (json.has("failed")) {
                    int code = json.getInt("failed");
                    handleLongPollFailure(code);
                    continue;
                }

                ts = json.getString("ts");
                JSONArray updates = json.getJSONArray("updates");

                for (int i = 0; i < updates.length(); i++) {
                    handleUpdate(updates.getJSONObject(i));
                }

                consecutiveErrors.set(0);
                backoff = 0;

            } catch (Exception e) {
                handleError(e.getMessage());
                backoff = Math.min((int) (BASE_BACKOFF_MS * Math.pow(1.5, consecutiveErrors.get())), 30000);
            }

            if (backoff > 0) {
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Ограничивает частоту запросов к VK API.
     */
    private void rateLimit() {
        long now = System.currentTimeMillis();
        long wait = MIN_REQUEST_INTERVAL_MS - (now - lastRequestTime);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {}
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * Обрабатывает ошибки LongPoll.
     */
    private void handleLongPollFailure(int code) {
        switch (code) {
            case 1:
                plugin.getLogger().warning("[VK] LongPoll ts устарел. Обновляем сервер...");
                updateLongPollServer();
                break;
            case 2:
            case 3:
                plugin.getLogger().warning("[VK] LongPoll ключ устарел. Переподключение...");
                updateLongPollServer();
                break;
            default:
                plugin.getLogger().warning("[VK] Неизвестная ошибка LongPoll: " + code);
                updateLongPollServer();
        }
    }

    /**
     * Обрабатывает ошибки.
     */
    private void handleError(String message) {
        int errors = consecutiveErrors.incrementAndGet();
        plugin.getLogger().warning("[VK] Ошибка LongPoll (" + errors + "): " + message);

        if (errors >= MAX_CONSECUTIVE_ERRORS) {
            plugin.getLogger().severe("[VK] Слишком много ошибок. Переподключаемся...");
            server = null;
            key = null;
            ts = null;
            consecutiveErrors.set(0);
        }
    }

    /**
     * Обновляет LongPoll сервер.
     */
    private void updateLongPollServer() {
        try {
            rateLimit();
            String url = String.format(
                    "https://api.vk.com/method/groups.getLongPollServer?group_id=%d&access_token=%s&v=5.131",
                    groupId, token
            );

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());

            if (json.has("error")) {
                plugin.getLogger().severe("[VK] Ошибка получения LongPoll сервера: " +
                        json.getJSONObject("error").optString("error_msg"));
                return;
            }

            JSONObject resp = json.getJSONObject("response");
            server = resp.getString("server");
            key = resp.getString("key");
            ts = resp.getString("ts");

            plugin.getLogger().info("[VK] LongPoll сервер обновлён.");

        } catch (Exception e) {
            plugin.getLogger().severe("[VK] Не удалось обновить LongPoll сервер: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает входящее обновление.
     */
    private void handleUpdate(JSONObject update) {
        try {
            String type = update.getString("type");
            if ("message_new".equals(type)) {
                JSONObject object = update.getJSONObject("object");
                JSONObject message = object.optJSONObject("message");
                if (message == null) message = object;
                int peer = message.optInt("peer_id", 0);
                int fromId = message.optInt("from_id", 0);
                if (peer == 0 || fromId == 0) return;

                String text = message.optString("text", "").trim();

                // Обработка payload от кнопок (inline / callback / keyboard)
                String payloadRaw = "";
                if (message.has("payload")) {
                    Object payloadObj = message.opt("payload");
                    if (payloadObj instanceof String) {
                        payloadRaw = (String) payloadObj;
                    } else if (payloadObj instanceof JSONObject) {
                        payloadRaw = payloadObj.toString();
                    }
                }

                if (!payloadRaw.isEmpty()) {
                    try {
                        JSONObject payload = new JSONObject(payloadRaw);
                        if (payload.has("cmd")) {
                            text = payload.getString("cmd");
                        } else if (payload.has("2fa_code")) {
                            text = payload.getString("2fa_code");
                        } else if (payload.has("2fa_block")) {
                            text = "❌ БЛОКИРОВКА " + payload.getString("2fa_block");
                        }
                    } catch (Exception ignored) {}
                }

                if (!text.isEmpty()) {
                    final String finalText = text;
                    final int finalPeer = peer;
                    final int finalFromId = fromId;

                    // VKCommandEvent и VKMessageEvent асинхронные — fire должен быть НЕ с main thread.
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            // 1. Сначала VKMessageEvent (кнопки-лейблы, старая экспедиция)
                            ru.example.vkchat.api.VKMessageEvent messageEvent =
                                    new ru.example.vkchat.api.VKMessageEvent(finalPeer, finalFromId, finalText);
                            Bukkit.getPluginManager().callEvent(messageEvent);

                            // 2. Если сообщение не обработано — запускаем командный обработчик
                            if (!messageEvent.isCancelled()) {
                                VKCommandHandler.handle(plugin, finalText, finalFromId, finalPeer);
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().warning("[VK] Ошибка обработки сообщения: " + ex.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Ошибка обработки обновления: " + e.getMessage());
        }
    }

    // ==================== МЕТОДЫ ОТПРАВКИ СООБЩЕНИЙ ====================

    /**
     * Отправить сообщение в основной чат.
     * 
     * @param message текст сообщения
     */
    public void sendToMainChat(String message) {
        sendMessage(peerId, message);
    }

    /**
     * Отправить сообщение конкретному пользователю.
     * 
     * @param peerId ID пользователя или беседы
     * @param message текст сообщения
     */
    public void sendMessage(int peerId, String message) {
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());

                String url = String.format(
                    "https://api.vk.com/method/messages.send?peer_id=%d&random_id=%d&message=%s&access_token=%s&v=5.131",
                    peerId,
                    System.currentTimeMillis() % 1000000000,
                    encodedMsg,
                    token
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JSONObject json = new JSONObject(response.body());
                if (json.has("error")) {
                    plugin.getLogger().warning("[VK] Ошибка отправки сообщения: " + 
                        json.getJSONObject("error").optString("error_msg"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[VK] Не удалось отправить сообщение: " + e.getMessage());
            }
        });
    }

    /**
     * Отправить сообщение с клавиатурой.
     * 
     * @param targetPeer ID получателя
     * @param message текст сообщения
     * @param keyboardJson JSON клавиатуры
     */
    public void sendKeyboard(int targetPeer, String message, String keyboardJson) {
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
                String encodedKb = URLEncoder.encode(keyboardJson, StandardCharsets.UTF_8.toString());

                String url = String.format(
                    "https://api.vk.com/method/messages.send?peer_id=%d&random_id=%d&message=%s&keyboard=%s&access_token=%s&v=5.131",
                    targetPeer,
                    System.currentTimeMillis() % 1000000000,
                    encodedMsg,
                    encodedKb,
                    token
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JSONObject json = new JSONObject(response.body());
                if (json.has("error")) {
                    plugin.getLogger().warning("[VK] Ошибка отправки клавиатуры: " + 
                        json.getJSONObject("error").optString("error_msg"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[VK] Не удалось отправить клавиатуру: " + e.getMessage());
            }
        });
    }

    /**
     * Отправить сообщение с вложением (фото, документ и т.д.).
     * 
     * @param peerId ID получателя
     * @param message текст сообщения
     * @param attachment вложение в формате "type{owner_id}_{media_id}"
     */
    public void sendMessageWithAttachment(int peerId, String message, String attachment) {
        if (token == null || token.isEmpty() || token.equals("YOUR_VK_GROUP_TOKEN")) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
                String encodedAttachment = URLEncoder.encode(attachment, StandardCharsets.UTF_8.toString());

                String url = String.format(
                    "https://api.vk.com/method/messages.send?peer_id=%d&random_id=%d&message=%s&attachment=%s&access_token=%s&v=5.131",
                    peerId,
                    System.currentTimeMillis() % 1000000000,
                    encodedMsg,
                    encodedAttachment,
                    token
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JSONObject json = new JSONObject(response.body());
                if (json.has("error")) {
                    plugin.getLogger().warning("[VK] Ошибка отправки сообщения с вложением: " + 
                        json.getJSONObject("error").optString("error_msg"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[VK] Не удалось отправить сообщение с вложением: " + e.getMessage());
            }
        });
    }

    // ==================== МЕТОДЫ ПОЛУЧЕНИЯ ИНФОРМАЦИИ ====================

    /**
     * Получить информацию о пользователе.
     * 
     * @param userId ID пользователя
     * @return JSONObject с информацией о пользователе или null
     */
    public JSONObject getUserInfo(int userId) {
        try {
            String url = String.format(
                "https://api.vk.com/method/users.get?user_ids=%d&access_token=%s&v=5.131",
                userId,
                token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONArray arr = new JSONObject(response.body()).getJSONArray("response");
            if (arr.length() > 0) {
                return arr.getJSONObject(0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось получить информацию о пользователе: " + e.getMessage());
        }
        return null;
    }

    /**
     * Получить информацию о пользователе (с кэшированием).
     * 
     * <p>Информация хранится в кэше 5 минут.</p>
     * 
     * @param userId ID пользователя
     * @return JSONObject с информацией о пользователе
     */
    public JSONObject getUserInfoCached(int userId) {
        if (userCache.containsKey(userId)) {
            long cacheTime = userCacheTime.getOrDefault(userId, 0L);
            if (System.currentTimeMillis() - cacheTime < USER_CACHE_TTL) {
                return userCache.get(userId);
            }
        }

        JSONObject userInfo = getUserInfo(userId);
        if (userInfo != null) {
            userCache.put(userId, userInfo);
            userCacheTime.put(userId, System.currentTimeMillis());
        }
        return userInfo;
    }

    /**
     * Получить информацию о беседе.
     * 
     * @param chatId ID беседы
     * @return JSONObject с информацией о беседе
     */
    public JSONObject getChatInfo(int chatId) {
        try {
            String url = String.format(
                "https://api.vk.com/method/messages.getConversationsById?peer_ids=%d&access_token=%s&v=5.131",
                chatId,
                token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            if (json.has("response")) {
                JSONArray items = json.getJSONObject("response").getJSONArray("items");
                if (items.length() > 0) {
                    return items.getJSONObject(0);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось получить информацию о беседе: " + e.getMessage());
        }
        return null;
    }

    /**
     * Получить количество участников беседы.
     * 
     * @param chatId ID беседы
     * @return количество участников
     */
    public int getChatMembersCount(int chatId) {
        try {
            String url = String.format(
                "https://api.vk.com/method/messages.getConversationMembers?peer_id=%d&access_token=%s&v=5.131",
                chatId,
                token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            if (json.has("response")) {
                return json.getJSONObject("response").getInt("count");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось получить количество участников: " + e.getMessage());
        }
        return 0;
    }

    // ==================== МЕТОДЫ ПРОВЕРКИ ПРАВ ====================

    /**
     * Проверить, является ли пользователь администратором бота.
     * 
     * @param userId ID пользователя
     * @return true если пользователь администратор
     */
    public boolean isBotAdmin(int userId) {
        try {
            String url = String.format(
                "https://api.vk.com/method/groups.getMembers?group_id=%d&filter=managers&access_token=%s&v=5.131",
                groupId,
                token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            if (json.has("response")) {
                JSONArray items = json.getJSONObject("response").getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    if (items.getJSONObject(i).getInt("id") == userId) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось проверить права администратора: " + e.getMessage());
        }
        return false;
    }

    public boolean isMemberOfGroupAndChat(int userId) {
        // Проверка участия в группе
        boolean inGroup = isGroupMember(userId);
        if (!inGroup) {
            plugin.getLogger().info("[VK] Пользователь " + userId + " не является участником группы " + groupId);
            return false;
        }

        // Проверка участия в беседе (опционально)
        boolean requireChat = plugin.getConfig().getBoolean("vk.require-chat-membership", false);
        if (requireChat) {
            int peerId = plugin.getConfig().getInt("vk.peer-id", 0);
            if (peerId > 0) {
                boolean inChat = isChatMember(userId, peerId);
                if (!inChat) {
                    plugin.getLogger().info("[VK] Пользователь " + userId + " не является участником беседы " + peerId);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Проверить, является ли пользователь участником группы
     */
    public boolean isGroupMember(int userId) {
        try {
            String url = String.format(
                "https://api.vk.com/method/groups.getMembers?group_id=%d&user_ids=%d&access_token=%s&v=5.131",
                groupId, userId, token
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

            if (json.has("error")) {
                plugin.getLogger().warning("[VK] Ошибка проверки группы: " + json.getJSONObject("error").getString("error_msg"));
                // При ошибке API НЕ кикаем игрока
                return true;
            }

            if (json.has("response")) {
                JSONObject resp = json.getJSONObject("response");
                if (resp.has("items")) {
                    return resp.getJSONArray("items").length() > 0;
                }
                return resp.getInt("count") > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось проверить участника группы: " + e.getMessage());
            // При ошибке сети НЕ кикаем игрока
            return true;
        }
        return true; // По умолчанию пропускаем
    }

    /**
     * Проверить, является ли пользователь участником беседы
     */
    public boolean isChatMember(int userId, int peerId) {
        try {
            int chatId = peerId - 2000000000;
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
                plugin.getLogger().warning("[VK] Ошибка проверки беседы: " + json.getJSONObject("error").getString("error_msg"));
                // При ошибке API НЕ кикаем игрока
                return true;
            }

            if (json.has("response")) {
                JSONArray profiles = json.getJSONObject("response").getJSONArray("profiles");
                for (int i = 0; i < profiles.length(); i++) {
                    if (profiles.getJSONObject(i).getInt("id") == userId) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[VK] Не удалось проверить участника беседы: " + e.getMessage());
            // При ошибке сети НЕ кикаем игрока
            return true;
        }
        return false;
    }

    public void sendMessage(int peerId, int senderId, String message) {
        sendMessage(peerId, message);
    }

    // ==================== МЕТОДЫ УПРАВЛЕНИЯ КЭШЕМ БЕСЕД ====================

    // === Кэширование бесед ===

    private final java.util.Map<Integer, JSONObject> chatCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Long> chatCacheTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHAT_CACHE_TTL = 600000; // 10 минут

    public JSONObject getChatInfoCached(int chatId) {
        if (chatCache.containsKey(chatId)) {
            long cacheTime = chatCacheTime.getOrDefault(chatId, 0L);
            if (System.currentTimeMillis() - cacheTime < CHAT_CACHE_TTL) {
                return chatCache.get(chatId);
            }
        }

        JSONObject chatInfo = getChatInfo(chatId);
        if (chatInfo != null) {
            chatCache.put(chatId, chatInfo);
            chatCacheTime.put(chatId, System.currentTimeMillis());
        }
        return chatInfo;
    }

    public void clearChatCache() {
        chatCache.clear();
        chatCacheTime.clear();
        plugin.getLogger().info("[VK] Кэш бесед очищен");
    }

    public int getChatCacheSize() {
        return chatCache.size();
    }

    public void clearAllCache() {
        clearUserCache();
        clearChatCache();
        plugin.getLogger().info("[VK] Весь кэш очищен");
    }

    /**
     * Отправить уведомление.
     * 
     * @param peerId ID получателя
     * @param message текст уведомления
     */
    public void sendNotification(int peerId, String message) {
        sendMessage(peerId, "🔔 " + message);
    }

    // ==================== МЕТОДЫ УПРАВЛЕНИЯ КЭШЕМ ====================

    /**
     * Очистить кэш пользователей.
     */
    public void clearUserCache() {
        userCache.clear();
        userCacheTime.clear();
        plugin.getLogger().info("[VK] Кэш пользователей очищен");
    }

    /**
     * Получить размер кэша пользователей.
     * 
     * @return количество записей в кэше
     */
    public int getUserCacheSize() {
        return userCache.size();
    }
}
