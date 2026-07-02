package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Менеджер донатов — опрос DonatePay API, обработка платежей, выдача LuckPerms прав на 30 дней
 */
public class DonateManager {
    private final VKChatDonatePlugin plugin;
    private final HttpClient httpClient;
    private final File dataFile;
    private FileConfiguration dataCfg;
    private final Map<String, StatusDef> statuses = new LinkedHashMap<>();
    private int lastProcessedId = 0;
    private static final long MONTH_SECONDS = 2592000L; // 30 дней

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
                String sender = tx.optString("what", "").trim();

                processDonation(txId, amount, sender);
                lastProcessedId = Math.max(lastProcessedId, txId);
            }
            saveData();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка опроса DonatePay: " + e.getMessage());
        }
    }

    private void processDonation(int txId, double amountRub, String sender) {
        // Никнейм из ИМЕНИ отправителя (игрок указывает ник в имени донатера)
        String nick = extractNickFromSender(sender);
        if (nick == null) {
            plugin.getLogger().info("Донат #" + txId + " (" + amountRub + "₽) — ник не извлечён из '" + sender + "'");
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
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(nick);
        if (!offPlayer.hasPlayedBefore()) {
            plugin.getLogger().info("Донат #" + txId + " — игрок " + nick + " не найден");
            return;
        }

        // Проверить — продление или выдача нового
        boolean extending = hasAnyStatus(offPlayer);
        boolean higherExists = offPlayer.getPlayer() != null && hasHigherStatus(offPlayer.getPlayer(), bestStatus.id);

        if (higherExists) {
            // Уже есть статус выше — только продлеваем ТЕКУЩИЙ высший
            StatusDef current = getPlayerStatus(offPlayer.getPlayer());
            if (current != null) {
                extendStatus(offPlayer, current);
                announceDonation(nick, current, amountRub, true);
            }
            return;
        }

        // Выдача/продление нового статуса
        if (extending && bestStatus.price <= getCurrentStatusPrice(offPlayer)) {
            // Продление текущего статуса
            StatusDef current = getPlayerStatusByOffline(offPlayer);
            if (current != null) {
                extendStatus(offPlayer, current);
                announceDonation(nick, current, amountRub, true);
            }
        } else {
            // Новый статус или повышение
            grantStatus(offPlayer, bestStatus);
            announceDonation(nick, bestStatus, amountRub, false);
        }

        plugin.getLogger().info("ДОНАТ: " + nick + " → " + bestStatus.display + " (" + amountRub + "₽) #" + txId);
    }

    private String extractNickFromSender(String sender) {
        if (sender == null || sender.isEmpty()) return null;
        // Имя отправителя ДОЛЖНО содержать ник игрока
        // Очищаем от спецсимволов, оставляем буквы/цифры/подчёркивания
        String cleaned = sender.replaceAll("[^a-zA-Z0-9_а-яА-Я]", "");
        if (cleaned.length() >= 2 && cleaned.length() <= 16) return cleaned;
        return null;
    }

    private int getCurrentStatusPrice(OfflinePlayer player) {
        for (StatusDef s : statuses.values()) {
            if (player.getPlayer() != null && player.getPlayer().hasPermission("vkchat.donate." + s.id))
                return s.price;
        }
        return 0;
    }

    private StatusDef getPlayerStatusByOffline(OfflinePlayer player) {
        if (player.getPlayer() == null) return null;
        return getPlayerStatus(player.getPlayer());
    }

    private boolean hasAnyStatus(OfflinePlayer player) {
        if (player.getPlayer() == null) return false;
        for (String id : statuses.keySet()) {
            if (player.getPlayer().hasPermission("vkchat.donate." + id)) return true;
        }
        return false;
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
            runLuckPermsCommand("lp user " + player.getName() + " permission unset vkchat.donate." + id);
        }
        // Выдать на 30 дней
        runLuckPermsCommand("lp user " + player.getName()
                + " permission settemp vkchat.donate." + status.id + " true " + MONTH_SECONDS + "s");
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → vkchat.donate." + status.id + " (30д)");
    }

    private void extendStatus(OfflinePlayer player, StatusDef status) {
        // Продлить на 30 дней
        runLuckPermsCommand("lp user " + player.getName()
                + " permission settemp vkchat.donate." + status.id + " true " + MONTH_SECONDS + "s accumulate");
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → продление vkchat.donate." + status.id + " +30д");
    }

    private void runLuckPermsCommand(String cmd) {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    private void announceDonation(String nick, StatusDef status, double amount, boolean extending) {
        String action = extending ? "продлил" : "получил";
        String mcMsg = ChatColor.translateAlternateColorCodes('&',
                "&6💰 &e" + nick + " &6" + action + " статус " + status.name + " &6за донат!");
        String vkMsg = "💰 " + nick + " " + action + " статус " + status.display + " за донат!";

        // В Minecraft чат
        Bukkit.broadcastMessage(mcMsg);

        // В ВК беседу
        try {
            VKChatPlugin.getInstance().getApi().sendToMainChat(vkMsg);
        } catch (Exception ignored) {}

        // Игроку лично
        Player player = Bukkit.getPlayerExact(nick);
        if (player != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a✨ Спасибо за донат! Статус " + status.name + " &aактивен на 30 дней."));
        }
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
        sb.append("§7⚠ ВАЖНО: В ИМЕНИ отправителя укажите свой НИКНЕЙМ!\n");
        sb.append("§7Статусы действуют 30 дней, продлеваются при повторной покупке.\n\n");
        for (StatusDef s : statuses.values()) {
            sb.append(s.name).append(" §7— ").append(s.price).append("₽ / 30 дней\n");
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
