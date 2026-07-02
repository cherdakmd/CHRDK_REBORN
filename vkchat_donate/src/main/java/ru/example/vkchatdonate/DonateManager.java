package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Менеджер донатов — опрос DonatePay API, обработка платежей, выдача LuckPerms прав
 */
public class DonateManager {
    private final VKChatDonatePlugin plugin;
    private final HttpClient httpClient;
    private final File dataFile;
    private FileConfiguration dataCfg;
    private final Map<String, StatusDef> statuses = new LinkedHashMap<>();
    private int lastProcessedId = 0;

    public static class StatusDef {
        public final String id, name, display, description;
        public final int price;
        public final double repDiscount, tpCooldownMult, marketMult, jobsXpMult;
        public final int maxHomes;

        StatusDef(String id, ConfigurationSection cfg) {
            this.id = id;
            this.name = ChatColor.translateAlternateColorCodes('&', cfg.getString("name", id));
            this.display = cfg.getString("display", id);
            this.price = cfg.getInt("price", 0);
            this.description = cfg.getString("description", "");
            this.repDiscount = cfg.getDouble("rep-discount", 0);
            this.tpCooldownMult = cfg.getDouble("tp-cooldown-mult", 1.0);
            this.maxHomes = cfg.getInt("max-homes", 3);
            this.marketMult = cfg.getDouble("market-mult", 1.0);
            this.jobsXpMult = cfg.getDouble("jobs-xp-mult", 1.0);
        }
    }

    public DonateManager(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.dataFile = new File(plugin.getDataFolder(), "donations.yml");
        loadStatuses();
        loadData();
        startPolling();
    }

    private void loadStatuses() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("statuses");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            statuses.put(key, new StatusDef(key, sec.getConfigurationSection(key)));
        }
    }

    private void loadData() {
        if (!dataFile.exists()) try { dataFile.getParentFile().mkdirs(); dataFile.createNewFile(); } catch (Exception e) {}
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
        lastProcessedId = dataCfg.getInt("last_id", 0);
    }

    private void saveData() {
        dataCfg.set("last_id", lastProcessedId);
        try { dataCfg.save(dataFile); } catch (IOException ignored) {}
    }

    private void startPolling() {
        String token = plugin.getConfig().getString("api-token", "");
        if (token.isEmpty() || token.equals("YOUR_DONATEPAY_TOKEN")) {
            plugin.getLogger().warning("DonatePay токен не настроен! /donate setup");
            return;
        }
        int interval = plugin.getConfig().getInt("poll-interval", 30);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::pollDonations,
                100L, interval * 20L);
        plugin.getLogger().info("Опрос DonatePay запущен (интервал: " + interval + "с)");
    }

    private void pollDonations() {
        String token = plugin.getConfig().getString("api-token", "");
        if (token.isEmpty() || token.equals("YOUR_DONATEPAY_TOKEN")) return;

        try {
            String url = "https://donatepay.ru/api/v1/transactions?access_token=" + token
                    + "&limit=10&type=donation";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) return;

            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            if (!json.has("data")) return;

            org.json.JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject tx = data.getJSONObject(i);
                int txId = tx.getInt("id");
                if (txId <= lastProcessedId) continue;

                String status = tx.optString("status", "");
                if (!status.equals("success")) continue;

                double amount = tx.optDouble("amount", 0);
                String comment = tx.optString("comment", "").trim();
                String sender = tx.optString("what", "").trim();

                processDonation(txId, amount, comment, sender);
                lastProcessedId = Math.max(lastProcessedId, txId);
            }
            saveData();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка опроса DonatePay: " + e.getMessage());
        }
    }

    private void processDonation(int txId, double amountRub, String comment, String sender) {
        // Извлечь ник из комментария: "ник PlayerName" или "nick PlayerName"
        String nick = extractNick(comment, sender);
        if (nick == null) {
            plugin.getLogger().info("Донат #" + txId + " (" + amountRub + "₽) — ник не указан");
            return;
        }

        // Найти подходящий статус по сумме
        StatusDef bestStatus = null;
        for (StatusDef s : statuses.values()) {
            if (amountRub >= s.price) bestStatus = s;
        }
        if (bestStatus == null) {
            plugin.getLogger().info("Донат #" + txId + " от " + nick + " (" + amountRub + "₽) — сумма меньше минимального статуса");
            return;
        }

        // Найти игрока
        Player player = Bukkit.getPlayerExact(nick);
        OfflinePlayer offPlayer = player;
        if (offPlayer == null) {
            offPlayer = Bukkit.getOfflinePlayer(nick);
            if (offPlayer == null || !offPlayer.hasPlayedBefore()) {
                plugin.getLogger().info("Донат #" + txId + " — игрок " + nick + " не найден");
                return;
            }
        }

        // Проверить, не выше ли текущий статус
        String permNode = "vkchat.donate." + bestStatus.id;
        if (offPlayer.getPlayer() != null && hasHigherStatus(offPlayer.getPlayer(), bestStatus.id)) {
            plugin.getLogger().info("Донат #" + txId + " от " + nick + " — уже есть выше статус");
            return;
        }

        // Выдать права через LuckPerms
        grantStatus(offPlayer, bestStatus);

        // Сообщения
        String thanksMsg = plugin.getConfig().getString("messages.donate-thanks", "&aСпасибо за донат!")
                .replace("{player}", nick).replace("{status}", bestStatus.display);
        String broadMsg = plugin.getConfig().getString("messages.donate-broadcast", "")
                .replace("{player}", nick).replace("{status}", bestStatus.display);

        if (player != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', thanksMsg));
        }
        if (!broadMsg.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadMsg));
        }
        plugin.getLogger().info("ДОНАТ: " + nick + " → " + bestStatus.display + " (" + amountRub + "₽) #" + txId);
    }

    private String extractNick(String comment, String sender) {
        // Паттерн: "ник PlayerName" или "nick PlayerName" или если имя отправителя содержит ник
        if (comment != null) {
            Matcher m = Pattern.compile("(?i)(?:ник|nick)\\s+(\\w{2,16})").matcher(comment);
            if (m.find()) return m.group(1);
        }
        // Если нет комментария — попробовать извлечь из имени отправителя
        if (sender != null && !sender.isEmpty()) {
            String cleaned = sender.replaceAll("[^a-zA-Z0-9_а-яА-Я]", "");
            if (cleaned.length() >= 2 && cleaned.length() <= 16) return cleaned;
        }
        return null;
    }

    private boolean hasHigherStatus(Player player, String newStatusId) {
        List<String> order = new ArrayList<>(statuses.keySet());
        int newIdx = order.indexOf(newStatusId);
        for (int i = newIdx + 1; i < order.size(); i++) {
            if (player.hasPermission("vkchat.donate." + order.get(i))) return true;
        }
        return false;
    }

    private void grantStatus(OfflinePlayer player, StatusDef status) {
        // Удалить предыдущие донат-права
        for (String id : statuses.keySet()) {
            runLuckPermsCommand(player, "lp user " + player.getName() + " permission unset vkchat.donate." + id);
        }
        // Выдать новый статус
        runLuckPermsCommand(player, "lp user " + player.getName() + " permission set vkchat.donate." + status.id + " true");
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → vkchat.donate." + status.id);
    }

    private void runLuckPermsCommand(OfflinePlayer player, String cmd) {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    public StatusDef getPlayerStatus(Player player) {
        for (StatusDef s : statuses.values()) {
            if (player.hasPermission("vkchat.donate." + s.id)) return s;
        }
        return null;
    }

    public StatusDef getStatusById(String id) {
        return statuses.get(id);
    }

    public Collection<StatusDef> getStatuses() { return statuses.values(); }

    public String getSetupInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ ДОНАТ-СТАТУСЫ (DonatePay) ═══\n\n");
        sb.append("§7Ссылка: §ehttps://donatepay.ru/don/ВАШ_АККАУНТ\n\n");
        sb.append("§7В комментарии к донату укажите: §fник PlayerName\n\n");
        for (StatusDef s : statuses.values()) {
            sb.append(s.name).append(" §7— ").append(s.price).append("₽\n");
            sb.append("  §7Скидка: §f").append((int)(s.repDiscount * 100))
                    .append("% §7| КД ТП: §f×").append(s.tpCooldownMult)
                    .append(" §7| Домов: §f").append(s.maxHomes)
                    .append(" §7| Рынок: §f×").append(s.marketMult)
                    .append(" §7| Jobs: §f×").append(s.jobsXpMult).append("\n");
        }
        return sb.toString();
    }

    public void shutdown() {
        saveData();
    }

    // === ПУБЛИЧНЫЕ ГЕТТЕРЫ ДЛЯ ДРУГИХ МОДУЛЕЙ ===
    // Эти методы вызываются другими модулями через рефлексию

    public double getRepDiscount(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.repDiscount : 0;
    }

    public double getTpCooldownMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.tpCooldownMult : 1.0;
    }

    public int getMaxHomes(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.maxHomes : 3;
    }

    public double getMarketMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.marketMult : 1.0;
    }

    public double getJobsXpMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.jobsXpMult : 1.0;
    }
}
