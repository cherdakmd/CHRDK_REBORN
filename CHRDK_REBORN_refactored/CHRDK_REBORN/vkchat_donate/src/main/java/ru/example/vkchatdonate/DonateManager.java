package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер донатов — опрос DonatePay API, обработка платежей, выдача LuckPerms прав на 30 дней
 *
 * РЕФАКТОРИНГ v2.1.0:
 * - FIX: Save-ahead для lastProcessedId (идемпотентность при рестарте)
 * - FIX: Использование LuckPerms API вместо reflection в getDaysLeft()
 * - FIX: Синхронизация обработок донатов (предотвращение дублирования)
 * - IMPROVE: fundraiserCollected сохраняется между рестартами
 */
public class DonateManager {
    private final VKChatDonatePlugin plugin;
    private final HttpClient httpClient;
    private final File dataFile;
    private FileConfiguration dataCfg;
    private final Map<String, StatusDef> statuses = new LinkedHashMap<>();
    private final Map<String, Double> totalDonated = new LinkedHashMap<>();
    private final Set<String> passHolders = new HashSet<>();
    private int lastProcessedId = 0;
    private boolean vkAnnounceWarningLogged = false;
    private BossBar fundraiserBar;
    private double fundraiserCollected = 0;
    private final Set<UUID> fundraiserHidden = new HashSet<>();
    private static final long MONTH_SECONDS = 2592000L; // 30 дней
    // FIX: Синхронизация для предотвращения дублирующей обработки донатов
    private final Set<Integer> processingTxIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static class StatusDef {
        public final String id, name, display, description, prefix;
        public final int price, weight;
        public final double repDiscount, tpCooldownMult, marketMult, jobsXpMult;
        public final int maxHomes;

        StatusDef(String id, ConfigurationSection cfg) {
            this.id = id;
            this.name = ChatColor.translateAlternateColorCodes('&', cfg.getString("name", id));
            this.display = cfg.getString("display", id);
            this.prefix = ChatColor.translateAlternateColorCodes('&', cfg.getString("prefix", "&7"));
            this.price = cfg.getInt("price", 0);
            this.weight = cfg.getInt("weight", 0);
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
        loadFundraiser();
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
        for (String key : dataCfg.getConfigurationSection("donated") != null ? dataCfg.getConfigurationSection("donated").getKeys(false) : Collections.<String>emptySet()) {
            totalDonated.put(key, dataCfg.getDouble("donated." + key, 0));
        }
        for (String name : dataCfg.getStringList("pass_holders")) {
            passHolders.add(name.toLowerCase());
        }
    }

    private void saveData() {
        dataCfg.set("last_id", lastProcessedId);
        for (Map.Entry<String, Double> e : totalDonated.entrySet()) {
            dataCfg.set("donated." + e.getKey(), e.getValue());
        }
        dataCfg.set("pass_holders", new ArrayList<>(passHolders));
        try { dataCfg.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("Ошибка сохранения donations.yml: " + e.getMessage());
        }
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
            List<Integer> processedIds = new ArrayList<>();

            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject tx = data.getJSONObject(i);
                int txId = tx.getInt("id");
                if (txId <= lastProcessedId) continue;

                // FIX: Предотвращение дублирующей обработки в параллельном потоке
                if (!processingTxIds.add(txId)) continue;

                String status = tx.optString("status", "");
                if (!status.equals("success")) {
                    processingTxIds.remove(txId);
                    continue;
                }

                double amount = tx.optDouble("amount", 0);
                String sender = tx.optString("what", "").trim();

                // FIX: Save-ahead — сохраняем lastProcessedId ДО обработки
                lastProcessedId = Math.max(lastProcessedId, txId);
                saveData();

                processDonation(txId, amount, sender);
                processedIds.add(txId);
                processingTxIds.remove(txId);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка опроса DonatePay: " + e.getMessage());
        }
    }

    private void processDonation(int txId, double amountRub, String sender) {
        String nick = extractNickFromSender(sender);
        if (nick == null) {
            plugin.getLogger().info("Донат #" + txId + " (" + amountRub + "₽) — ник не извлечён из '" + sender + "'");
            return;
        }

        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(nick);
        if (!offPlayer.hasPlayedBefore()) {
            plugin.getLogger().info("Донат #" + txId + " — игрок " + nick + " не найден");
            return;
        }

        // ═══ ПРОХОДКА (без ВК) ═══
        int passPrice = plugin.getConfig().getInt("pass.price", 500);
        boolean hasVk = false;
        Player online = offPlayer.getPlayer();
        if (online != null) {
            try { hasVk = VKChatPlugin.getInstance().getApi().getLinkedVkId(online) != -1; } catch (Exception ignored) {}
        }

        if (!hasVk && amountRub >= passPrice) {
            grantPass(offPlayer, amountRub);
            String key = offPlayer.getName().toLowerCase();
            totalDonated.put(key, totalDonated.getOrDefault(key, 0.0) + amountRub);
            updateFundraiser(amountRub);
            plugin.getLogger().info("ПРОХОДКА: " + nick + " (" + amountRub + "₽) #" + txId);
            return;
        }

        // Найти подходящий статус по сумме (максимальный по цене)
        StatusDef bestStatus = null;
        for (StatusDef s : statuses.values()) {
            if (amountRub >= s.price && (bestStatus == null || s.price > bestStatus.price))
                bestStatus = s;
        }

        // Нет статуса → репутация
        if (bestStatus == null) {
            if (plugin.getConfig().getBoolean("rep-purchase.enabled", true)) {
                int maxWithoutStatus = plugin.getConfig().getInt("rep-purchase.max-without-status", 0);
                if (maxWithoutStatus == 0 || amountRub <= maxWithoutStatus) {
                    processRepPurchase(txId, amountRub, nick);
                    return;
                }
            }
            plugin.getLogger().info("Донат #" + txId + " от " + nick + " (" + amountRub + "₽) — сумма меньше минимального статуса");
            return;
        }

        // Найти игрока
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

        // Track total donated
        String key = offPlayer.getName().toLowerCase();
        totalDonated.put(key, totalDonated.getOrDefault(key, 0.0) + amountRub);

        updateFundraiser(amountRub);

        plugin.getLogger().info("ДОНАТ: " + nick + " → " + bestStatus.display + " (" + amountRub + "₽) #" + txId);
    }

    private void processRepPurchase(int txId, double amountRub, String nick) {
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(nick);
        if (!offPlayer.hasPlayedBefore()) {
            plugin.getLogger().info("Донат #" + txId + " — игрок " + nick + " не найден");
            return;
        }

        int rate = plugin.getConfig().getInt("rep-purchase.rate", 100);
        int rep = (int) (amountRub * rate);

        Player p = offPlayer.getPlayer();
        int vkId = p != null ? VKChatPlugin.getInstance().getApi().getLinkedVkId(p) : -1;
        if (vkId == -1 && p != null && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink) чтобы получить репутацию за донат!");
        } else if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
        }

        String key = offPlayer.getName().toLowerCase();
        totalDonated.put(key, totalDonated.getOrDefault(key, 0.0) + amountRub);

        updateFundraiser(amountRub);

        // Broadcast
        if (plugin.getConfig().getBoolean("broadcasts.enabled", true)) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6💰 &e" + nick + " &6пополнил баланс на &e" + rep + " реп. &6за &e" + (int)amountRub + "₽"));
        }

        plugin.getLogger().info("РЕП-ДОНАТ: " + nick + " → " + rep + " реп (" + amountRub + "₽) #" + txId);
    }

    // ═══════════════════════════════
    // FUNDRAISER BOSS BAR
    // ═══════════════════════════════

    private void loadFundraiser() {
        if (!plugin.getConfig().getBoolean("fundraiser.enabled", false)) return;
        fundraiserCollected = dataCfg.getDouble("fundraiser_collected", 0);
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        if (goal <= 0) return;

        String color = plugin.getConfig().getString("fundraiser.bar-color", "PURPLE");
        BarColor barColor;
        try { barColor = BarColor.valueOf(color); } catch (IllegalArgumentException e) { barColor = BarColor.PURPLE; }

        fundraiserBar = Bukkit.createBossBar(
                formatFundraiserTitle(goal), barColor, BarStyle.SOLID);
        fundraiserBar.setVisible(true);
        fundraiserBar.setProgress(Math.min(1.0, fundraiserCollected / goal));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!fundraiserHidden.contains(p.getUniqueId())) fundraiserBar.addPlayer(p);
        }
    }

    private String formatFundraiserTitle(double goal) {
        String template = plugin.getConfig().getString("fundraiser.bar-text", "&d💰 Сбор: &f{collected}₽ &7/ &f{goal}₽ &a({percent}%)");
        double pct = goal > 0 ? Math.min(100, (fundraiserCollected / goal) * 100) : 0;
        return ChatColor.translateAlternateColorCodes('&', template
                .replace("{collected}", String.format("%.0f", fundraiserCollected))
                .replace("{goal}", String.format("%.0f", goal))
                .replace("{percent}", String.format("%.0f", pct)));
    }

    private void updateFundraiser(double amount) {
        if (fundraiserBar == null) return;
        fundraiserCollected += amount;
        dataCfg.set("fundraiser_collected", fundraiserCollected);
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        fundraiserBar.setTitle(formatFundraiserTitle(goal));
        fundraiserBar.setProgress(Math.min(1.0, fundraiserCollected / goal));
        try { dataCfg.save(dataFile); } catch (IOException ignored) {}
    }

    public void startFundraiser(double goal) {
        plugin.getConfig().set("fundraiser.enabled", true);
        plugin.getConfig().set("fundraiser.goal", goal);
        plugin.saveConfig();
        fundraiserCollected = 0;
        dataCfg.set("fundraiser_collected", 0.0);
        try { dataCfg.save(dataFile); } catch (IOException ignored) {}

        String color = plugin.getConfig().getString("fundraiser.bar-color", "PURPLE");
        BarColor barColor;
        try { barColor = BarColor.valueOf(color); } catch (IllegalArgumentException e) { barColor = BarColor.PURPLE; }

        if (fundraiserBar != null) fundraiserBar.removeAll();
        fundraiserBar = Bukkit.createBossBar(formatFundraiserTitle(goal), barColor, BarStyle.SOLID);
        fundraiserBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) fundraiserBar.addPlayer(p);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "💰 Старт сбора средств! Цель: " + (int)goal + "₽");
    }

    public void stopFundraiser() {
        plugin.getConfig().set("fundraiser.enabled", false);
        plugin.saveConfig();
        if (fundraiserBar != null) {
            fundraiserBar.removeAll();
            fundraiserBar = null;
        }
        fundraiserCollected = 0;
        dataCfg.set("fundraiser_collected", 0.0);
        try { dataCfg.save(dataFile); } catch (IOException ignored) {}
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "💰 Сбор средств завершён!");
    }

    public String getFundraiserInfo() {
        if (fundraiserBar == null) return ChatColor.RED + "Сбор средств не активен.";
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        return ChatColor.LIGHT_PURPLE + "💰 Сбор средств: " +
                ChatColor.WHITE + String.format("%.0f", fundraiserCollected) + "₽" +
                ChatColor.GRAY + " / " + ChatColor.WHITE + String.format("%.0f", goal) + "₽" +
                ChatColor.GREEN + " (" + String.format("%.0f", (goal > 0 ? fundraiserCollected / goal * 100 : 0)) + "%)";
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

    private void grantPass(OfflinePlayer player, double amount) {
        runLuckPermsCommand("lp user " + player.getName()
                + " permission settemp vkchat.pass true " + MONTH_SECONDS + "s");
        passHolders.add(player.getName().toLowerCase());
        saveData();
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → проходка vkchat.pass (30д)");

        if (plugin.getConfig().getBoolean("broadcasts.enabled", true)) {
            String mcMsg = ChatColor.translateAlternateColorCodes('&',
                    "&6🎫 &e" + player.getName() + " &6приобрёл проходку на сервер! &aДобро пожаловать!");
            Bukkit.broadcastMessage(mcMsg);
        }
    }

    private void grantStatus(OfflinePlayer player, StatusDef status) {
        // Удалить из старых LP групп и прав
        for (String id : statuses.keySet()) {
            runLuckPermsCommand("lp user " + player.getName() + " parent remove " + id);
            runLuckPermsCommand("lp user " + player.getName() + " permission unset vkchat.donate." + id);
        }
        // Добавить в LP группу (для TAB) и выдать право на 30 дней
        runLuckPermsCommand("lp user " + player.getName() + " parent add " + status.id);
        runLuckPermsCommand("lp user " + player.getName()
                + " permission settemp vkchat.donate." + status.id + " true " + MONTH_SECONDS + "s");
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → группа " + status.id + " (30д)");
    }

    private void extendStatus(OfflinePlayer player, StatusDef status) {
        // Продлить право на 30 дней (группа уже есть)
        runLuckPermsCommand("lp user " + player.getName()
                + " permission settemp vkchat.donate." + status.id + " true " + MONTH_SECONDS + "s accumulate");
        plugin.getLogger().info("LuckPerms: " + player.getName() + " → продление " + status.id + " +30д");
    }

    private void runLuckPermsCommand(String cmd) {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    private void announceDonation(String nick, StatusDef status, double amount, boolean extending) {
        String action = extending ? "продлил" : "получил";
        boolean broadcast = plugin.getConfig().getBoolean("broadcasts.enabled", true);

        // В Minecraft чат
        if (broadcast) {
            String mcMsg = ChatColor.translateAlternateColorCodes('&',
                    "&6💰 &e" + nick + " &6" + action + " статус " + status.name + " &6за донат!");
            Bukkit.broadcastMessage(mcMsg);
        }

        // В ВК беседу
        if (broadcast) {
            String vkMsg = "💰 " + nick + " " + action + " статус " + status.display + " за донат!";
            try {
                VKChatPlugin.getInstance().getApi().sendToMainChat(vkMsg);
            } catch (Exception e) {
                if (!vkAnnounceWarningLogged) {
                    plugin.getLogger().warning("Ошибка отправки анонса доната в ВК: " + e.getMessage());
                    vkAnnounceWarningLogged = true;
                }
            }
        }

        // Игроку лично
        Player player = Bukkit.getPlayerExact(nick);
        if (player != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a✨ Спасибо за донат! Статус " + status.name + " &aактивен на 30 дней."));
        }
    }

    public StatusDef getPlayerStatus(Player player) {
        // Проверяем от высшего к низшему по weight
        return statuses.values().stream()
                .filter(s -> player.hasPermission("vkchat.donate." + s.id))
                .max(Comparator.comparingInt(s -> s.weight))
                .orElse(null);
    }

    public StatusDef getStatusById(String id) {
        return statuses.get(id);
    }

    public Collection<StatusDef> getStatuses() { return statuses.values(); }

    public String getSetupInfo() {
        String token = plugin.getConfig().getString("api-token", "");
        boolean configured = !token.isEmpty() && !token.equals("YOUR_DONATEPAY_TOKEN");
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ ДОНАТ-СТАТУСЫ (DonatePay) ═══\n\n");
        sb.append("§7Статус: ").append(configured ? "§a✅ Настроен" : "§c❌ Не настроен").append("\n");
        sb.append("§7Команда настройки: §e/donate setup <API-токен>\n\n");
        sb.append("§7⚠ В ИМЕНИ отправителя укажите свой НИКНЕЙМ!\n");
        sb.append("§7Статусы действуют 30 дней, продлеваются.\n\n");
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

    public Collection<String> getPassHolders() { return passHolders; }

    public void grantPassManually(String name) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        passHolders.add(name.toLowerCase());
        runLuckPermsCommand("lp user " + off.getName()
                + " permission settemp vkchat.pass true " + MONTH_SECONDS + "s");
        saveData();
        plugin.getLogger().info("Ручная выдача проходки: " + off.getName());
    }

    public void removePass(String name) {
        passHolders.remove(name.toLowerCase());
        runLuckPermsCommand("lp user " + name + " permission unset vkchat.pass");
        saveData();
        plugin.getLogger().info("Проходка отозвана: " + name);
    }

    public void shutdown() {
        saveData();
        if (fundraiserBar != null) fundraiserBar.removeAll();
    }

    public void addPlayerToFundraiser(Player p) {
        if (fundraiserBar != null && !fundraiserHidden.contains(p.getUniqueId()))
            fundraiserBar.addPlayer(p);
    }

    public boolean toggleFundraiserBar(Player p) {
        if (fundraiserBar == null) return false;
        if (getPlayerStatus(p) == null) return false; // только донатеры
        if (fundraiserHidden.contains(p.getUniqueId())) {
            fundraiserHidden.remove(p.getUniqueId());
            fundraiserBar.addPlayer(p);
            return true;
        } else {
            fundraiserHidden.add(p.getUniqueId());
            fundraiserBar.removePlayer(p);
            return false;
        }
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

    public long getDaysLeft(Player player) {
        StatusDef s = getPlayerStatus(player);
        if (s == null) return 0;

        String permission = "vkchat.donate." + s.id;
        try {
            // FIX: Используем LuckPerms API вместо reflection
            org.bukkit.plugin.Plugin lpPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (lpPlugin == null || !lpPlugin.isEnabled()) return 30;

            // Пробуем LuckPerms API (без reflection)
            try {
                net.luckperms.api.LuckPerms lpApi = Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
                if (lpApi != null) {
                    net.luckperms.api.model.user.UserManager um = lpApi.getUserManager();
                    net.luckperms.api.model.user.User user = um.getUser(player.getUniqueId());
                    if (user == null) return 30;
                    for (net.luckperms.api.node.Node node : user.getNodes()) {
                        if (node.getKey().equals(permission) && node.hasExpiry()) {
                            long expiryMs = node.getExpiry().toEpochMilli();
                            long secLeft = (expiryMs - System.currentTimeMillis()) / 1000;
                            return Math.max(0, secLeft / 86400);
                        }
                    }
                    return 30;
                }
            } catch (NoClassDefFoundError ignored) {
                // LuckPerms API не в classpath — fallback
            }

            // Fallback: reflection для старых версий
            Object api = lpPlugin.getClass().getMethod("getProvider").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user != null) {
                Object nodes = user.getClass().getMethod("getNodes").invoke(user);
                for (Object node : (Iterable<?>) nodes) {
                    String key = (String) node.getClass().getMethod("getKey").invoke(node);
                    if (permission.equals(key)) {
                        boolean hasExpiry = (boolean) node.getClass().getMethod("hasExpiry").invoke(node);
                        if (hasExpiry) {
                            Object expiry = node.getClass().getMethod("getExpiry").invoke(node);
                            if (expiry != null) {
                                long epoch = (long) expiry.getClass().getMethod("toEpochMilli").invoke(expiry);
                                long secLeft = (epoch - System.currentTimeMillis()) / 1000;
                                return Math.max(0, secLeft / 86400);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return 30;
        }
        return 30;
    }

    public double getTotalDonated(String playerName) {
        return totalDonated.getOrDefault(playerName.toLowerCase(), 0.0);
    }

    public List<Map.Entry<String, Double>> getTopDonors(int limit) {
        return totalDonated.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }
}
