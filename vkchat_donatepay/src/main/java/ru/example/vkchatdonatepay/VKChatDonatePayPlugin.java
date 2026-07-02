package ru.example.vkchatdonatepay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VKChatDonatePayPlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {
    private File dataFile;
    private FileConfiguration data;
    private int taskId = -1;
    private long lastPoll = 0L;
    private String lastError = "";
    private int processedThisSession = 0;
    private final String GUI_TITLE = ChatColor.DARK_PURPLE + "💳 DonatePay-статусы";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfigDefaults();
        loadData();
        getCommand("donatepay").setExecutor(this);
        getCommand("donatepay").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        startPolling();
        startDonorStatusTask();
        getLogger().info("VKChatDonatePay загружен. enabled=" + getConfig().getBoolean("enabled", false));
    }

    @Override
    public void onDisable() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        saveData();
    }

    private void migrateConfigDefaults() {
        try {
            reloadConfig();
            java.io.InputStream defStream = getResource("config.yml");
            if (defStream == null) return;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            getConfig().setDefaults(def);
            boolean missing = false;
            for (String key : def.getKeys(true)) if (!getConfig().isSet(key)) { missing = true; break; }
            if (missing) {
                File configFile = new File(getDataFolder(), "config.yml");
                if (configFile.exists()) {
                    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                    java.nio.file.Files.copy(configFile.toPath(), new File(getDataFolder(), "config.yml.bak-before-migration-" + stamp).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                getConfig().options().copyDefaults(true);
                saveConfig();
                reloadConfig();
            }
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить миграцию config.yml: " + e.getMessage());
        }
    }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "donatepay_data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private synchronized void saveData() {
        try { data.save(dataFile); } catch (Exception e) { getLogger().warning("Не удалось сохранить donatepay_data.yml: " + e.getMessage()); }
    }

    private void startPolling() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        long interval = Math.max(20L, getConfig().getLong("api.poll-interval-seconds", 30L)) * 20L;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::pollSafe, 100L, interval).getTaskId();
    }

    private void pollSafe() {
        if (!getConfig().getBoolean("enabled", false)) return;
        String token = getConfig().getString("api.access-token", "");
        if (token == null || token.trim().isEmpty() || token.contains("PUT_DONATEPAY_TOKEN")) return;
        try {
            pollOnce(false);
            lastError = "";
        } catch (Exception e) {
            lastError = e.getMessage();
            getLogger().warning("DonatePay poll error: " + e.getMessage());
            if (getConfig().getBoolean("logging.debug", false)) e.printStackTrace();
        }
    }

    private synchronized void pollOnce(boolean manual) throws Exception {
        lastPoll = System.currentTimeMillis();
        String json = httpGet(buildTransactionsUrl());
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        String status = str(root, "status", "");
        if (!status.equalsIgnoreCase("success")) throw new IllegalStateException("API status=" + status + " message=" + str(root, "message", ""));
        JsonArray arr = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
        List<JsonObject> txs = new ArrayList<>();
        int maxId = data.getInt("last-id", 0);
        for (JsonElement el : arr) {
            if (el != null && el.isJsonObject()) {
                JsonObject tx = el.getAsJsonObject();
                int id = integer(tx, "id", 0);
                if (id > maxId) maxId = id;
                txs.add(tx);
            }
        }
        int lastId = data.getInt("last-id", 0);
        if (lastId <= 0 && getConfig().getBoolean("processing.ignore-existing-on-first-run", true) && !manual) {
            data.set("last-id", maxId);
            saveData();
            log("FIRST_RUN_SKIP last-id=" + maxId + " count=" + txs.size());
            return;
        }
        txs.sort(Comparator.comparingInt(o -> integer(o, "id", 0)));
        int processed = 0;
        for (JsonObject tx : txs) {
            int id = integer(tx, "id", 0);
            if (id <= lastId) continue;
            if (isProcessed(id)) continue;
            if (!"donation".equalsIgnoreCase(str(tx, "type", "donation"))) continue;
            String txStatus = str(tx, "status", "success");
            if (!txStatus.equalsIgnoreCase("success") && !txStatus.equalsIgnoreCase("paid")) continue;
            processDonation(tx);
            markProcessed(id);
            data.set("last-id", Math.max(data.getInt("last-id", 0), id));
            processed++;
        }
        if (processed > 0) {
            processedThisSession += processed;
            saveData();
        }
        if (manual) log("MANUAL_CHECK processed=" + processed + " loaded=" + txs.size());
    }

    private String buildTransactionsUrl() throws Exception {
        String base = trimSlash(getConfig().getString("api.base-url", "https://donatepay.ru/api/v1"));
        String token = enc(getConfig().getString("api.access-token", ""));
        int limit = getConfig().getInt("api.limit", 25);
        String order = enc(getConfig().getString("api.order", "DESC"));
        String type = enc(getConfig().getString("api.type", "donation"));
        String status = enc(getConfig().getString("api.status", "success"));
        return base + "/transactions?access_token=" + token + "&limit=" + limit + "&order=" + order + "&type=" + type + "&status=" + status;
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod("GET");
            con.setConnectTimeout(getConfig().getInt("api.request-timeout-ms", 10000));
            con.setReadTimeout(getConfig().getInt("api.request-timeout-ms", 10000));
            con.setRequestProperty("User-Agent", "VKChatDonatePay/2.0.0");
            int code = con.getResponseCode();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + sb);
                return sb.toString();
            }
        } finally {
            con.disconnect();
        }
    }

    private void processDonation(JsonObject tx) {
        int id = integer(tx, "id", 0);
        double amount = dbl(tx, "sum", 0.0);
        JsonObject vars = tx.has("vars") && tx.get("vars").isJsonObject() ? tx.getAsJsonObject("vars") : new JsonObject();
        String donator = firstNonEmpty(str(vars, "name", ""), str(tx, "what", ""), "Аноним");
        String comment = firstNonEmpty(str(vars, "comment", ""), str(tx, "comment", ""));
        String playerName = findPlayerName(donator, comment);
        OfflinePlayer offline = playerName == null ? null : Bukkit.getOfflinePlayer(playerName);
        Player online = playerName == null ? null : Bukkit.getPlayerExact(playerName);
        boolean playerKnown = playerName != null && (!getConfig().getBoolean("processing.require-player", false) || offline != null);

        if (getConfig().getBoolean("processing.require-player", false) && !playerKnown) {
            log("SKIP id=" + id + " amount=" + amount + " no-player donator=" + donator + " comment=" + comment);
            return;
        }

        recordStats(id, amount, donator, comment, playerName);
        if (playerName != null) grantMonthlyStatusIfQualified(playerName, amount, donator, comment, id);

        // Автоматическая выдача проходки при донате 500р+
        if (playerName != null && amount >= getConfig().getDouble("passes.min-amount", 500.0)) {
            grantPass(playerName, amount);
        }

        double statusMultiplier = playerName == null ? 1.0 : getDonorStatusRepMultiplier(playerName);
        int rep = (int) Math.round(amount * getConfig().getDouble("rewards.reputation-per-rub", 10.0) * statusMultiplier);
        if (!getConfig().getBoolean("rewards.round-reputation", true)) rep = (int) (amount * getConfig().getDouble("rewards.reputation-per-rub", 10.0) * statusMultiplier);
        rep = Math.max(getConfig().getInt("rewards.min-reputation", 0), rep);
        if (playerName != null && rep > 0) {
            try {
                UUID uuid = offline != null ? offline.getUniqueId() : null;
                int vk = uuid != null ? VKChatPlugin.getInstance().getApi().getLinkedVkId(uuid) : (online != null ? VKChatPlugin.getInstance().getApi().getLinkedVkId(online) : -1);
                if (vk != -1) VKChatPlugin.getInstance().getApi().addReputation(vk, rep);
            } catch (Throwable ignored) {}
        }

        executeActions(id, amount, donator, comment, playerName, online);
        announce(id, amount, donator, comment, playerName);
        log("PROCESSED id=" + id + " amount=" + amount + " donator=" + donator + " player=" + playerName + " comment=" + comment);
    }

    private void executeActions(int id, double amount, String donator, String comment, String playerName, Player online) {
        ConfigurationSection sec = getConfig().getConfigurationSection("rewards.actions");
        if (sec == null) return;
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort(Comparator.comparingDouble(k -> getConfig().getDouble("rewards.actions." + k + ".min-amount", 0.0)));
        for (String key : keys) {
            double min = getConfig().getDouble("rewards.actions." + key + ".min-amount", 0.0);
            if (amount < min) continue;
            for (String cmd : getConfig().getStringList("rewards.actions." + key + ".commands")) {
                String prepared = apply(cmd, id, amount, donator, comment, playerName);
                if (prepared.contains("{player}")) continue;
                if (online == null && playerName != null && getConfig().getBoolean("processing.queue-offline-commands", true) && commandNeedsOnlinePlayer(prepared, playerName)) {
                    queueCommand(playerName, prepared);
                } else {
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared));
                }
            }
            if (online != null) {
                for (String msg : getConfig().getStringList("rewards.actions." + key + ".message-player")) online.sendMessage(color(apply(msg, id, amount, donator, comment, playerName)));
            } else if (playerName != null && getConfig().getBoolean("processing.queue-offline-messages", true)) {
                for (String msg : getConfig().getStringList("rewards.actions." + key + ".message-player")) queueMessage(playerName, color(apply(msg, id, amount, donator, comment, playerName)));
            }
            String bc = getConfig().getString("rewards.actions." + key + ".broadcast", "");
            if (bc != null && !bc.isEmpty()) Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(color(apply(bc, id, amount, donator, comment, playerName))));
        }
    }

    private void announce(int id, double amount, String donator, String comment, String playerName) {
        String playerPart = playerName == null ? "" : apply(getConfig().getString("rewards.player-part-format", "Игрок: {player}"), id, amount, donator, comment, playerName);
        String msg = apply(getConfig().getString("rewards.broadcast-format", "{donator} donated {amount}"), id, amount, donator, comment, playerName).replace("{player_part}", playerPart);
        if (getConfig().getBoolean("rewards.broadcast", true)) Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(color(msg)));
        if (getConfig().getBoolean("rewards.vk-announce", true)) {
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(color(msg))); } catch (Throwable ignored) {}
        }
    }



    private java.util.List<String> statusKeysByThreshold() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection sec = getConfig().getConfigurationSection("donor-statuses.levels");
        if (sec != null) keys.addAll(sec.getKeys(false));
        keys.sort(java.util.Comparator.comparingDouble(k -> getConfig().getDouble("donor-statuses.levels." + k + ".price", getConfig().getDouble("donor-statuses.levels." + k + ".threshold", 0.0))));
        return keys;
    }

    private String calculateDonorStatus(double amount) {
        String best = "none";
        for (String key : statusKeysByThreshold()) {
            double price = getConfig().getDouble("donor-statuses.levels." + key + ".price", getConfig().getDouble("donor-statuses.levels." + key + ".threshold", 0.0));
            if (amount >= price) best = key;
        }
        return best;
    }

    private int statusRank(String status) {
        if (status == null || status.equals("none")) return 0;
        int rank = 0;
        for (String key : statusKeysByThreshold()) {
            rank++;
            if (key.equals(status)) return rank;
        }
        return 0;
    }

    private String getDonorStatus(String playerName) {
        if (playerName == null) return "none";
        String key = playerName.toLowerCase(Locale.ROOT);
        long expires = data.getLong("players." + key + ".status-expires", 0L);
        if (expires > 0 && expires < System.currentTimeMillis()) return "none";
        return data.getString("players." + key + ".status", "none");
    }

    private String getDonorStatusName(String status) {
        if (status == null || status.equals("none")) return getConfig().getString("donor-statuses.none-name", "без статуса");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', getConfig().getString("donor-statuses.levels." + status + ".name", status));
    }

    private double getDonorStatusRepMultiplier(String playerName) {
        if (!getConfig().getBoolean("donor-statuses.enabled", true) || playerName == null) return 1.0;
        String status = getDonorStatus(playerName);
        if (status.equals("none")) return 1.0;
        return Math.max(1.0, getConfig().getDouble("donor-statuses.levels." + status + ".reputation-multiplier", 1.0));
    }

    private void grantMonthlyStatusIfQualified(String playerName, double amount, String donator, String comment, int id) {
        if (!getConfig().getBoolean("donor-statuses.enabled", true) || playerName == null) return;
        String newStatus = calculateDonorStatus(amount);
        if (newStatus.equals("none")) return;

        String key = playerName.toLowerCase(Locale.ROOT);
        Player online = Bukkit.getPlayerExact(playerName);
        String currentStatus = online != null ? getHighestPermissionStatus(online) : getDonorStatus(playerName);
        int currentRank = statusRank(currentStatus);
        int newRank = statusRank(newStatus);
        double price = getConfig().getDouble("donor-statuses.levels." + newStatus + ".price", 0.0);
        int days = Math.max(1, getConfig().getInt("donor-statuses.levels." + newStatus + ".duration-days", 30));
        String duration = days + "d";

        // Защита от понижения: меньший донат не трогает текущую более высокую категорию,
        // но выдаётся бонусная группа младшего статуса (страховка / дополнительные дни).
        if (currentRank > newRank) {
            if (getConfig().getBoolean("donor-statuses.lower-tier-bonuses.enabled", true)) {
                addBonusTier(playerName, newStatus, days, id, amount, donator, comment);
            } else {
                String keepMsg = getConfig().getString("donor-statuses.keep-higher-message", "&e❤ У {player} уже активен более высокий статус {status}. Малый донат засчитан, статус не понижен.")
                        .replace("{player}", playerName)
                        .replace("{status}", getDonorStatusName(currentStatus))
                        .replace("{new_status}", getDonorStatusName(newStatus))
                        .replace("{duration}", duration)
                        .replace("{price}", String.format(Locale.US, "%.2f", price));
                if (online != null) online.sendMessage(color(keepMsg));
                else if (getConfig().getBoolean("processing.queue-offline-messages", true)) queueMessage(playerName, color(keepMsg));
                log("DONOR_STATUS_KEEP_HIGHER player=" + playerName + " current=" + currentStatus + " attempted=" + newStatus + " amount=" + amount);
            }
            return;
        }

        boolean sameStatus = currentRank == newRank && currentRank > 0;
        long oldExpires = data.getLong("players." + key + ".status-expires", 0L);
        long base = sameStatus ? Math.max(System.currentTimeMillis(), oldExpires) : System.currentTimeMillis();
        long expires = base + days * 86400000L;
        data.set("players." + key + ".status", newStatus);
        data.set("players." + key + ".status-updated", System.currentTimeMillis());
        data.set("players." + key + ".status-expires", expires);

        String display = getDonorStatusName(newStatus);
        if (sameStatus) {
            // Повторная покупка той же категории = продление на duration-days.
            String lp = "lp user " + playerName + " parent addtemp donate_" + newStatus + " " + duration + " accumulate";
            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lp));
            String msg = getConfig().getString("donor-statuses.extend-message", "&d❤ {player} продлил статус {status}&d ещё на {duration}!")
                    .replace("{player}", playerName)
                    .replace("{status}", display)
                    .replace("{duration}", duration)
                    .replace("{price}", String.format(Locale.US, "%.2f", price))
                    .replace("{expires}", new Date(expires).toString());
            if (online != null) online.sendMessage(color(msg));
            else if (getConfig().getBoolean("processing.queue-offline-messages", true)) queueMessage(playerName, color(msg));
            if (getConfig().getBoolean("donor-statuses.vk-announce-extensions", false)) {
                try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(color(msg))); } catch (Throwable ignored) {}
            }
            log("DONOR_STATUS_EXTEND player=" + playerName + " status=" + newStatus + " amount=" + amount + " expires=" + expires);
            return;
        }

        // Новый или более высокий статус: удаляем старые donate-группы и выдаём высшую подходящую категорию.
        for (String command : getConfig().getStringList("donor-statuses.levels." + newStatus + ".commands-on-purchase")) {
            String prepared = apply(command, id, amount, donator, comment, playerName)
                    .replace("{status}", ChatColor.stripColor(display))
                    .replace("{duration}", duration)
                    .replace("{price}", String.format(Locale.US, "%.2f", price));
            if (online == null && getConfig().getBoolean("processing.queue-offline-commands", true) && commandNeedsOnlinePlayer(prepared, playerName)) queueCommand(playerName, prepared);
            else Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared));
        }
        String msg = getConfig().getString("donor-statuses.upgrade-message", "&d❤ &f{player} получил DonatePay-статус {status}&f на {duration}! Цена статуса: &e{price}₽")
                .replace("{player}", playerName)
                .replace("{status}", display)
                .replace("{total}", String.format(Locale.US, "%.2f", data.getDouble("players." + key + ".total", 0.0)))
                .replace("{duration}", duration)
                .replace("{price}", String.format(Locale.US, "%.2f", price))
                .replace("{expires}", new Date(expires).toString());
        Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(color(msg)));
        if (getConfig().getBoolean("donor-statuses.vk-announce", true)) {
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(color(msg))); } catch (Throwable ignored) {}
        }
        if (online != null) {
            online.sendMessage(color("&d❤ Ваш DonatePay-статус активирован: " + display + "&d на " + duration + ". Бонус к репутации за донаты: x" + getConfig().getDouble("donor-statuses.levels." + newStatus + ".reputation-multiplier", 1.0)));
        } else if (getConfig().getBoolean("processing.queue-offline-messages", true)) {
            queueMessage(playerName, color("&d❤ Ваш DonatePay-статус активирован: " + display + "&d на " + duration + "."));
        }
        log("DONOR_STATUS_MONTH player=" + playerName + " old=" + currentStatus + " new=" + newStatus + " amount=" + amount + " expires=" + expires);
    }

    /**
     * Автоматическая выдача проходки при донате
     */
    private void grantPass(String playerName, double amount) {
        try {
            // Найти игрока
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            if (offline == null) return;

            UUID uuid = offline.getUniqueId();
            int days = getConfig().getInt("passes.default-days", 30);

            // Проверить, есть ли уже проходка
            ru.example.vkchat.auth.PassManager passManager = VKChatPlugin.getInstance().getPassManager();
            if (passManager == null) return;

            boolean extended = passManager.hasPass(uuid);
            passManager.grantPass(uuid, playerName, days, "donatepay");

            // Уведомление
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage("§a§l🎉 Проходка выдана! Срок: " + days + " дней");
            }

            // Объявление
            String msg = "§a§l[ПРОХОДКА] §e" + playerName + " §7получил проходку на §a" + days + " §7дней!";
            Bukkit.broadcastMessage(msg);

            log("PASS_GRANTED player=" + playerName + " days=" + days + " extended=" + extended);
        } catch (Exception e) {
            log("PASS_ERROR player=" + playerName + " error=" + e.getMessage());
        }
    }

    private void recordStats(int id, double amount, String donator, String comment, String playerName) {
        String month = new SimpleDateFormat("yyyyMM").format(new Date());
        data.set("stats.total.amount", data.getDouble("stats.total.amount", 0.0) + amount);
        data.set("stats.total.count", data.getInt("stats.total.count", 0) + 1);
        data.set("stats.month." + month + ".amount", data.getDouble("stats.month." + month + ".amount", 0.0) + amount);
        data.set("stats.month." + month + ".count", data.getInt("stats.month." + month + ".count", 0) + 1);
        if (playerName != null && !playerName.trim().isEmpty()) {
            String key = playerName.toLowerCase(Locale.ROOT);
            data.set("players." + key + ".name", playerName);
            data.set("players." + key + ".total", data.getDouble("players." + key + ".total", 0.0) + amount);
            data.set("players." + key + ".count", data.getInt("players." + key + ".count", 0) + 1);
            data.set("players." + key + ".month." + month, data.getDouble("players." + key + ".month." + month, 0.0) + amount);
        }
        String historyPath = "history";
        List<String> h = data.getStringList(historyPath);
        h.add(new SimpleDateFormat("dd.MM HH:mm").format(new Date()) + " #" + id + " " + amount + " RUB " + donator + (playerName != null ? " -> " + playerName : "") + (comment == null || comment.isEmpty() ? "" : " | " + comment));
        while (h.size() > getConfig().getInt("stats.history-limit", 80)) h.remove(0);
        data.set(historyPath, h);
        checkGoals(amount, playerName, donator);
    }

    private void checkGoals(double amount, String playerName, String donator) {
        if (!getConfig().getBoolean("goals.enabled", false)) return;
        String month = new SimpleDateFormat("yyyyMM").format(new Date());
        double current = data.getDouble("stats.month." + month + ".amount", 0.0);
        double target = getConfig().getDouble("goals.monthly-target", 0.0);
        if (target <= 0) return;
        int oldPct = data.getInt("goals." + month + ".last-percent", 0);
        int newPct = (int)Math.floor((current / target) * 100.0);
        int step = Math.max(1, getConfig().getInt("goals.announce-every-percent", 25));
        if (newPct >= 100 && !data.getBoolean("goals." + month + ".completed", false)) {
            data.set("goals." + month + ".completed", true);
            announceRaw(getConfig().getString("goals.complete-message", "&6❤ Месячная цель донатов выполнена: {current}/{target}₽!"), current, target, playerName, donator);
        } else if (newPct / step > oldPct / step) {
            data.set("goals." + month + ".last-percent", newPct);
            announceRaw(getConfig().getString("goals.progress-message", "&d❤ Цель донатов: {current}/{target}₽ ({percent}%)"), current, target, playerName, donator);
        }
    }

    private void announceRaw(String raw, double current, double target, String playerName, String donator) {
        String msg = raw.replace("{current}", String.format(Locale.US, "%.2f", current))
                .replace("{target}", String.format(Locale.US, "%.2f", target))
                .replace("{percent}", target <= 0 ? "0" : String.valueOf((int)Math.floor(current / target * 100.0)))
                .replace("{player}", playerName == null ? "" : playerName)
                .replace("{donator}", donator == null ? "" : donator);
        Bukkit.getScheduler().runTask(this, () -> Bukkit.broadcastMessage(color(msg)));
        if (getConfig().getBoolean("rewards.vk-announce", true)) try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(color(msg))); } catch (Throwable ignored) {}
    }

    private boolean commandNeedsOnlinePlayer(String command, String playerName) {
        String lower = command.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("give " + playerName.toLowerCase(Locale.ROOT) + " ") || lower.startsWith("xp ") || lower.startsWith("effect ") || lower.startsWith("title ");
    }

    private void queueCommand(String playerName, String command) {
        String key = playerName.toLowerCase(Locale.ROOT);
        List<String> list = data.getStringList("pending." + key + ".commands");
        list.add(command);
        data.set("pending." + key + ".name", playerName);
        data.set("pending." + key + ".commands", list);
        log("QUEUE_COMMAND player=" + playerName + " command=" + command);
    }

    private void queueMessage(String playerName, String message) {
        String key = playerName.toLowerCase(Locale.ROOT);
        List<String> list = data.getStringList("pending." + key + ".messages");
        list.add(message);
        data.set("pending." + key + ".name", playerName);
        data.set("pending." + key + ".messages", list);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        cleanupExpiredBonusTiers(e.getPlayer().getName());
        deliverPending(e.getPlayer());
    }

    private void deliverPending(Player p) {
        String key = p.getName().toLowerCase(Locale.ROOT);
        List<String> commands = data.getStringList("pending." + key + ".commands");
        List<String> messages = data.getStringList("pending." + key + ".messages");
        if (commands.isEmpty() && messages.isEmpty()) return;
        for (String cmd : commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        for (String msg : messages) p.sendMessage(color(msg));
        p.sendMessage(ChatColor.GREEN + "❤ Вы получили отложенные DonatePay-награды: команд " + commands.size() + ", сообщений " + messages.size() + ".");
        data.set("pending." + key, null);
        saveData();
        log("DELIVER_PENDING player=" + p.getName() + " commands=" + commands.size() + " messages=" + messages.size());
    }

    private String findPlayerName(String name, String comment) {
        String source = getConfig().getString("processing.player-source", "both");
        String text;
        if ("name".equalsIgnoreCase(source)) text = name;
        else if ("comment".equalsIgnoreCase(source)) text = comment;
        else text = name + " " + comment;
        Pattern pattern = Pattern.compile(getConfig().getString("processing.player-regex", "([A-Za-z0-9_]{3,16})"));
        Matcher m = pattern.matcher(text == null ? "" : text);
        while (m.find()) {
            String candidate = m.group(1);
            Player online = Bukkit.getPlayerExact(candidate);
            if (online != null) return online.getName();
            if (getConfig().getBoolean("processing.allow-offline-player", true)) return candidate;
        }
        return null;
    }

    private boolean isProcessed(int id) { return data.getBoolean("processed." + id, false); }
    private void markProcessed(int id) {
        data.set("processed." + id, true);
        List<Integer> ids = data.getIntegerList("processed-list");
        ids.add(id);
        while (ids.size() > 300) {
            int old = ids.remove(0);
            data.set("processed." + old, null);
        }
        data.set("processed-list", ids);
    }

    private String apply(String s, int id, double amount, String donator, String comment, String playerName) {
        if (s == null) return "";
        return s.replace("{id}", String.valueOf(id))
                .replace("{amount}", String.format(Locale.US, "%.2f", amount))
                .replace("{amount_int}", String.valueOf((int)Math.round(amount)))
                .replace("{donator}", donator == null ? "" : donator)
                .replace("{comment}", comment == null ? "" : comment)
                .replace("{player}", playerName == null ? "{player}" : playerName)
                .replace("{player_part}", "");
    }

    private void log(String line) {
        if (!getConfig().getBoolean("logging.file", true)) return;
        try {
            File file = new File(getDataFolder(), getConfig().getString("logging.file-name", "donatepay.log"));
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            try (FileWriter fw = new FileWriter(file, true)) { fw.write(stamp + " | " + line + "\n"); }
        } catch (Exception e) { getLogger().warning("Не удалось записать donatepay.log: " + e.getMessage()); }
    }

    private void openStatusGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        fill(inv);
        String status = getHighestPermissionStatus(p);
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Ваш DonatePay-статус",
                ChatColor.GRAY + "Текущий статус: " + getDonorStatusName(status),
                ChatColor.GRAY + "Ник для доната: " + ChatColor.YELLOW + p.getName(),
                ChatColor.DARK_GRAY + "Ник нужно указать в имени донатера DonatePay."));
        addStatusCard(inv, 19, "spark", Material.EMERALD);
        addStatusCard(inv, 21, "flame", Material.BLAZE_POWDER);
        addStatusCard(inv, 23, "star", Material.NETHER_STAR);
        addStatusCard(inv, 25, "legend", Material.ENCHANTED_GOLDEN_APPLE);
        inv.setItem(40, item(Material.WRITABLE_BOOK, ChatColor.AQUA + "Как купить статус?",
                ChatColor.GRAY + "1) Перейдите на DonatePay-страницу сервера.",
                ChatColor.GRAY + "2) В поле имени донатера укажите ник: " + ChatColor.YELLOW + p.getName(),
                ChatColor.GRAY + "3) Донат на цену статуса выдаст группу на месяц.",
                ChatColor.GRAY + "4) Повтор той же категории продлит срок.",
                ChatColor.GRAY + "5) Меньший донат не понизит высокий статус."));
        inv.setItem(49, item(Material.PAPER, ChatColor.YELLOW + "Команды",
                ChatColor.GRAY + "/donatepay — это меню",
                ChatColor.GRAY + "/donatepay player <ник> — админ-проверка",
                ChatColor.GRAY + "/donatepay lpsetup — админ-настройка LP"));
        p.openInventory(inv);
    }

    private void addStatusCard(Inventory inv, int slot, String key, Material mat) {
        String name = getDonorStatusName(key);
        int price = getConfig().getInt("donor-statuses.levels." + key + ".price", 0);
        int days = getConfig().getInt("donor-statuses.levels." + key + ".duration-days", 30);
        double rep = getConfig().getDouble("donor-statuses.levels." + key + ".reputation-multiplier", 1.0);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "Цена: " + ChatColor.YELLOW + price + "₽");
        lore.add(ChatColor.GRAY + "Срок: " + ChatColor.AQUA + days + " дней");
        lore.add(ChatColor.GRAY + "Репутация за донаты: " + ChatColor.GREEN + "x" + rep);
        lore.add("");
        for (String line : getConfig().getStringList("donor-statuses.levels." + key + ".description")) lore.add(color(line));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Клик — показать инструкцию в чат");
        ItemStack item = item(mat, name, lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "donate_status_key"), PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        Player p = (Player) e.getWhoClicked();
        String key = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this, "donate_status_key"), PersistentDataType.STRING);
        if (key == null) return;
        int price = getConfig().getInt("donor-statuses.levels." + key + ".price", 0);
        int days = getConfig().getInt("donor-statuses.levels." + key + ".duration-days", 30);
        p.closeInventory();
        p.sendMessage(ChatColor.LIGHT_PURPLE + "💳 " + getDonorStatusName(key) + ChatColor.GRAY + ": " + ChatColor.YELLOW + price + "₽" + ChatColor.GRAY + " на " + days + " дней.");
        p.sendMessage(ChatColor.GRAY + "В DonatePay укажи ник в имени донатера: " + ChatColor.YELLOW + p.getName());
        String url = getConfig().getString("donate-url", "");
        if (url != null && !url.isEmpty()) p.sendMessage(ChatColor.AQUA + "Ссылка: " + ChatColor.WHITE + url);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player && (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("статусы") || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("статус"))) {
            openStatusGui((Player) sender);
            return true;
        }
        if (!sender.hasPermission("vkchat.donatepay.admin")) { sender.sendMessage(ChatColor.RED + "Нет прав."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "VKChatDonatePay status:");
            sender.sendMessage(ChatColor.GRAY + "enabled: " + getConfig().getBoolean("enabled", false));
            sender.sendMessage(ChatColor.GRAY + "last-id: " + data.getInt("last-id", 0));
            sender.sendMessage(ChatColor.GRAY + "last-poll: " + (lastPoll == 0 ? "never" : new Date(lastPoll)));
            sender.sendMessage(ChatColor.GRAY + "processed session: " + processedThisSession);
            sender.sendMessage(ChatColor.GRAY + "last-error: " + (lastError == null || lastError.isEmpty() ? "none" : lastError));
            sender.sendMessage(ChatColor.GRAY + "player-source: " + getConfig().getString("processing.player-source", "name"));
            return true;
        }
        if (args[0].equalsIgnoreCase("player") && args.length >= 2) {
            String key = args[1].toLowerCase(java.util.Locale.ROOT);
            double total = data.getDouble("players." + key + ".total", 0.0);
            int count = data.getInt("players." + key + ".count", 0);
            String status = data.getString("players." + key + ".status", "none");
            sender.sendMessage(ChatColor.GOLD + "DonatePay player: " + data.getString("players." + key + ".name", args[1]));
            sender.sendMessage(ChatColor.GRAY + "total: " + String.format(java.util.Locale.US, "%.2f", total) + " RUB, count: " + count);
            long exp = data.getLong("players." + key + ".status-expires", 0L);
            sender.sendMessage(ChatColor.GRAY + "status: " + getDonorStatusName(status) + ChatColor.GRAY + " (rep x" + getConfig().getDouble("donor-statuses.levels." + status + ".reputation-multiplier", 1.0) + ")" + (exp > 0 ? " до " + new java.util.Date(exp) : ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            sendTop(sender, args.length >= 2 && args[1].equalsIgnoreCase("month"));
            return true;
        }
        if (args[0].equalsIgnoreCase("goal")) {
            String month = new SimpleDateFormat("yyyyMM").format(new Date());
            double current = data.getDouble("stats.month." + month + ".amount", 0.0);
            double target = getConfig().getDouble("goals.monthly-target", 0.0);
            sender.sendMessage(ChatColor.GOLD + "DonatePay goal: " + String.format(Locale.US, "%.2f", current) + " / " + String.format(Locale.US, "%.2f", target) + " RUB" + (target > 0 ? " (" + (int)Math.floor(current / target * 100.0) + "%)" : ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("history")) {
            List<String> h = data.getStringList("history");
            int from = Math.max(0, h.size() - 10);
            sender.sendMessage(ChatColor.GOLD + "DonatePay history:");
            for (int i = from; i < h.size(); i++) sender.sendMessage(ChatColor.GRAY + "• " + h.get(i));
            return true;
        }
        if (args[0].equalsIgnoreCase("pending")) {
            if (data.getConfigurationSection("pending") == null) { sender.sendMessage(ChatColor.GREEN + "Pending rewards empty."); return true; }
            sender.sendMessage(ChatColor.GOLD + "Pending DonatePay rewards:");
            for (String key : data.getConfigurationSection("pending").getKeys(false)) {
                sender.sendMessage(ChatColor.GRAY + "• " + data.getString("pending." + key + ".name", key) + ": commands=" + data.getStringList("pending." + key + ".commands").size() + ", messages=" + data.getStringList("pending." + key + ".messages").size());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("lpsetup")) {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
                sender.sendMessage(ChatColor.RED + "LuckPerms не найден на сервере.");
                return true;
            }
            java.util.List<String> commands = getConfig().getStringList("luckperms.setup-commands");
            if (commands.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "luckperms.setup-commands пустой в config.yml");
                return true;
            }
            int count = 0;
            for (String c : commands) {
                if (c == null || c.trim().isEmpty()) continue;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "LuckPerms-группы DonatePay созданы/обновлены. Команд выполнено: " + count);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); migrateConfigDefaults(); startPolling(); sender.sendMessage(ChatColor.GREEN + "DonatePay config перезагружен."); return true;
        }
        if (args[0].equalsIgnoreCase("check")) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try { pollOnce(true); sender.sendMessage(ChatColor.GREEN + "DonatePay check completed."); }
                catch (Exception e) { sender.sendMessage(ChatColor.RED + "DonatePay error: " + e.getMessage()); }
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("setlast") && args.length >= 2) {
            try { data.set("last-id", Integer.parseInt(args[1])); saveData(); sender.sendMessage(ChatColor.GREEN + "last-id установлен."); } catch (Exception e) { sender.sendMessage(ChatColor.RED + "Число некорректно."); }
            return true;
        }
        if (args[0].equalsIgnoreCase("test")) {
            double amount = args.length >= 2 ? parseDouble(args[1], 100.0) : 100.0;
            String player = args.length >= 3 ? args[2] : (sender instanceof Player ? sender.getName() : null);
            JsonObject tx = new JsonObject();
            tx.addProperty("id", -new Random().nextInt(999999));
            tx.addProperty("sum", String.valueOf(amount));
            tx.addProperty("status", "success");
            tx.addProperty("type", "donation");
            JsonObject vars = new JsonObject(); vars.addProperty("name", player == null ? "Test" : player); vars.addProperty("comment", player == null ? "" : player); tx.add("vars", vars);
            processDonation(tx);
            sender.sendMessage(ChatColor.GREEN + "Тестовый донат обработан.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/donatepay status|player <nick>|lpsetup|reload|check|setlast <id>|test [amount] [player]|top [month]|goal|history|pending");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("menu", "статусы", "status", "статус", "player", "top", "goal", "history", "pending", "lpsetup", "reload", "check", "setlast", "test"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("player") || sub.equals("test")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    completions.add(online.getName());
                }
            }
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }



    private void startDonorStatusTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("donor-statuses.enabled", true)) return;
            for (Player p : Bukkit.getOnlinePlayers()) applyStatusPotions(p);
        }, 200L, 400L);
    }

    private String getHighestPermissionStatus(Player p) {
        String permissionStatus = "none";
        if (p.hasPermission("vkchat.donate.status.legend")) permissionStatus = "legend";
        else if (p.hasPermission("vkchat.donate.status.star")) permissionStatus = "star";
        else if (p.hasPermission("vkchat.donate.status.flame")) permissionStatus = "flame";
        else if (p.hasPermission("vkchat.donate.status.spark")) permissionStatus = "spark";
        String fileStatus = getDonorStatus(p.getName());
        if (statusRank(permissionStatus) >= statusRank(fileStatus)) return permissionStatus;
        return fileStatus;
    }

    private String getEffectiveStatus(Player p) {
        String main = getHighestPermissionStatus(p);
        int mainRank = statusRank(main);
        long now = System.currentTimeMillis();
        String bestBonus = "none";
        int bestBonusRank = 0;
        if (data.contains("players." + p.getName().toLowerCase(Locale.ROOT) + ".bonus-tiers")) {
            for (String key : data.getConfigurationSection("players." + p.getName().toLowerCase(Locale.ROOT) + ".bonus-tiers").getKeys(false)) {
                long expires = data.getLong("players." + p.getName().toLowerCase(Locale.ROOT) + ".bonus-tiers." + key + ".expires", 0L);
                if (expires > now) {
                    int r = statusRank(key);
                    if (r > bestBonusRank) {
                        bestBonusRank = r;
                        bestBonus = key;
                    }
                }
            }
        }
        if (bestBonusRank > mainRank) return bestBonus;
        return main;
    }

    private void cleanupExpiredBonusTiers(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String path = "players." + key + ".bonus-tiers";
        if (!data.contains(path)) return;
        long now = System.currentTimeMillis();
        for (String tier : new ArrayList<>(data.getConfigurationSection(path).getKeys(false))) {
            long expires = data.getLong(path + "." + tier + ".expires", 0L);
            if (expires <= 0 || expires < now) data.set(path + "." + tier, null);
        }
    }

    private void addBonusTier(String playerName, String tier, int days, int id, double amount, String donator, String comment) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String path = "players." + key + ".bonus-tiers." + tier;
        long now = System.currentTimeMillis();
        long oldExpires = data.getLong(path + ".expires", 0L);
        long base = Math.max(now, oldExpires);
        long expires = base + days * 86400000L;
        data.set(path + ".expires", expires);
        data.set(path + ".granted", now);
        data.set(path + ".amount", amount);

        // Выдаём группу LuckPerms с accumulate — она останется даже если основной статус истечёт.
        String lp = "lp user " + playerName + " parent addtemp donate_" + tier + " " + days + "d accumulate";
        Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lp));

        String msg = getConfig().getString("donor-statuses.lower-tier-bonus-message", "&e❤ {player} получил бонусы статуса {status}&e на {duration} (страховочная группа).")
                .replace("{player}", playerName)
                .replace("{status}", getDonorStatusName(tier))
                .replace("{duration}", days + "d")
                .replace("{amount}", String.format(Locale.US, "%.2f", amount))
                .replace("{expires}", new Date(expires).toString());
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) online.sendMessage(color(msg));
        else if (getConfig().getBoolean("processing.queue-offline-messages", true)) queueMessage(playerName, color(msg));
        log("DONOR_STATUS_LOWER_BONUS player=" + playerName + " tier=" + tier + " amount=" + amount + " expires=" + expires);
    }

    private void applyStatusPotions(Player p) {
        cleanupExpiredBonusTiers(p.getName());
        String status = getEffectiveStatus(p);
        if (status == null || status.equals("none")) return;
        for (String line : getConfig().getStringList("donor-statuses.levels." + status + ".effects.potion-effects")) {
            try {
                String[] parts = line.split(";");
                PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
                int amp = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (type != null) p.addPotionEffect(new PotionEffect(type, 500, amp, true, false, false));
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) return;
        cleanupExpiredBonusTiers(p.getName());
        String status = getEffectiveStatus(p);
        if (status == null || status.equals("none")) return;
        int chance = getConfig().getInt("donor-statuses.levels." + status + ".effects.mob-extra-drop-chance", 0);
        int copies = bonusCopiesFromPercent(chance);
        if (copies > 0 && !e.getDrops().isEmpty()) {
            ItemStack extra = e.getDrops().get(0).clone();
            for (int i = 0; i < copies; i++) e.getDrops().add(extra.clone());
        }
        int rep = getConfig().getInt("donor-statuses.levels." + status + ".effects.mob-rep-bonus", 0);
        if (rep > 0) {
            try { int vk = VKChatPlugin.getInstance().getApi().getLinkedVkId(p); if (vk != -1) VKChatPlugin.getInstance().getApi().addReputation(vk, rep); } catch (Throwable ignored) {}
        }
        double expMult = getConfig().getDouble("donor-statuses.levels." + status + ".effects.exp-multiplier", 1.0);
        if (expMult > 1.0) e.setDroppedExp((int)Math.round(e.getDroppedExp() * expMult));
    }

    @EventHandler
    public void onPlayerExp(PlayerExpChangeEvent e) {
        cleanupExpiredBonusTiers(e.getPlayer().getName());
        String status = getEffectiveStatus(e.getPlayer());
        if (status == null || status.equals("none")) return;
        double expMult = getConfig().getDouble("donor-statuses.levels." + status + ".effects.exp-multiplier", 1.0);
        if (expMult > 1.0) e.setAmount((int)Math.round(e.getAmount() * expMult));
    }

    /**
     * Chance can be higher than 100 after x10 donate buff:
     * 100 = one guaranteed copy,
     * 150 = one guaranteed copy + 50% chance for the second one.
     */
    private int bonusCopiesFromPercent(int percent) {
        if (percent <= 0) return 0;
        int guaranteed = percent / 100;
        int remainder = percent % 100;
        return guaranteed + (remainder > 0 && new Random().nextInt(100) < remainder ? 1 : 0);
    }

    private void sendTop(CommandSender sender, boolean monthOnly) {
        String month = new SimpleDateFormat("yyyyMM").format(new Date());
        if (data.getConfigurationSection("players") == null) { sender.sendMessage(ChatColor.YELLOW + "Донатов по игрокам пока нет."); return; }
        List<String> keys = new ArrayList<>(data.getConfigurationSection("players").getKeys(false));
        keys.sort((a, b) -> Double.compare(
                monthOnly ? data.getDouble("players." + b + ".month." + month, 0.0) : data.getDouble("players." + b + ".total", 0.0),
                monthOnly ? data.getDouble("players." + a + ".month." + month, 0.0) : data.getDouble("players." + a + ".total", 0.0)));
        sender.sendMessage(ChatColor.GOLD + "DonatePay top " + (monthOnly ? "month" : "all") + ":");
        int n = 0;
        for (String key : keys) {
            double val = monthOnly ? data.getDouble("players." + key + ".month." + month, 0.0) : data.getDouble("players." + key + ".total", 0.0);
            if (val <= 0) continue;
            sender.sendMessage(ChatColor.YELLOW + "#" + (++n) + " " + data.getString("players." + key + ".name", key) + " — " + String.format(Locale.US, "%.2f", val) + "₽");
            if (n >= 10) break;
        }
    }

    private double parseDouble(String s, double def) { try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception e) { return def; } }
    private String trimSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String firstNonEmpty(String... vals) { for (String v : vals) if (v != null && !v.trim().isEmpty()) return v; return ""; }
    private String str(JsonObject o, String k, String def) { try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; } catch (Exception e) { return def; } }
    private int integer(JsonObject o, String k, int def) { try { return o.has(k) ? o.get(k).getAsInt() : def; } catch (Exception e) { return def; } }
    private double dbl(JsonObject o, String k, double def) { try { return o.has(k) ? Double.parseDouble(o.get(k).getAsString().replace(',', '.')) : def; } catch (Exception e) { return def; } }
}
