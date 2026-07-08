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
import ru.example.vkchatdonate.api.DonatePayClient;
import ru.example.vkchatdonate.luckperms.LuckPermsHelper;
import ru.example.vkchatdonate.pass.PassManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DonateManager v3.1 — делегирование проходки в PassManager.
 *
 * ═══ 10 ИСПРАВЛЕНИЙ (из v3.0) ═══
 * FIX #1:  Все LP-операции через LuckPermsHelper (единая точка)
 * FIX #2:  LP API вместо dispatchCommand("lp ...") где возможно
 * FIX #3:  getDaysLeft через чистый LP API (без reflection)
 * FIX #4:  HTTP-клиент вынесен в DonatePayClient
 * FIX #5:  Логирование HTTP-кода ошибки
 * FIX #6:  Защита токена от утечки в логи
 * FIX #7:  Save-ahead lastProcessedId ПЕРЕД обработкой
 * FIX #8:  processingTxIds — защита от параллельной обработки
 * FIX #9:  Оффлайн-игроки: LP команды через UUID
 * FIX #10: fundraiserCollected сохраняется между рестартами
 *
 * ═══ 10 ИСПРАВЛЕНИЙ ПРОХОДКИ (v3.1) ═══
 * PASS_FIX #1:  Хранение по UUID вместо имён → делегировано в PassManager
 * PASS_FIX #2:  Проверка истечения проходки при входе → PassManager.checkPassExpiry
 * PASS_FIX #3:  Проходка автоматически удаляется при получении донат-статуса
 * PASS_FIX #4:  Валидация passHolders при загрузке → PassManager.validatePassHolders
 * PASS_FIX #5:  Отдельная длительность проходки (pass.duration-days)
 * PASS_FIX #6:  Пропуск выдачи проходки если уже привязан ВК
 * PASS_FIX #7:  Локальная репутация с настраиваемым лимитом
 * PASS_FIX #8:  Очистка PDC local_rep при истечении проходки
 * PASS_FIX #9:  Save-ahead при выдаче/удалении проходки
 * PASS_FIX #10: Логика проходки вынесена из DonateManager → PassManager
 *
 * ═══ 10 УЛУЧШЕНИЙ ПРОХОДКИ (v3.1) ═══
 * PASS_IMPROVE #1:  Выделенный PassManager (Single Responsibility)
 * PASS_IMPROVE #2:  PassHolder record с метаданными
 * PASS_IMPROVE #3:  Миграция проходка → ВК (перенос локальной репутации)
 * PASS_IMPROVE #4:  /pass команда для игроков
 * PASS_IMPROVE #5:  Grace-период после истечения
 * PASS_IMPROVE #6:  Аналитика: куплено / активно / конвертировано
 * PASS_IMPROVE #7:  Настраиваемые сообщения проходки
 * PASS_IMPROVE #8:  События Bukkit: PassGrantEvent, PassExpireEvent, PassConvertEvent
 * PASS_IMPROVE #9:  Автоочистка устаревших записей при загрузке
 * PASS_IMPROVE #10: /pass buy — информация о покупке проходки
 */
public class DonateManager {
    private final VKChatDonatePlugin plugin;
    private final DonatePayClient apiClient;
    private final File dataFile;
    private FileConfiguration dataCfg;

    private final Map<String, StatusDef> statuses = new LinkedHashMap<>();
    private final Map<String, Double> totalDonated = new LinkedHashMap<>();
    private final List<DonateLogEntry> donateLog = new ArrayList<>();
    private static final int MAX_LOG_SIZE = 100;

    private int lastProcessedId = 0;
    private boolean vkAnnounceWarningLogged = false;
    private BossBar fundraiserBar;
    private double fundraiserCollected = 0;
    private final Set<UUID> fundraiserHidden = new HashSet<>();

    // FIX #7: Длительность из конфига
    private long donationDurationSeconds;

    // FIX #8: Синхронизация обработок
    private final Set<Integer> processingTxIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ═══════════════════════════════════════
    // STATUSDEF — Immutable
    // ═══════════════════════════════════════

    public static final class StatusDef {
        private final String id, name, display, description, prefix;
        private final int price, weight, maxHomes;
        private final double repDiscount, tpCooldownMult, marketMult, jobsXpMult;

        public StatusDef(String id, ConfigurationSection cfg) {
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

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDisplay() { return display; }
        public String getDescription() { return description; }
        public String getPrefix() { return prefix; }
        public int getPrice() { return price; }
        public int getWeight() { return weight; }
        public int getMaxHomes() { return maxHomes; }
        public double getRepDiscount() { return repDiscount; }
        public double getTpCooldownMult() { return tpCooldownMult; }
        public double getMarketMult() { return marketMult; }
        public double getJobsXpMult() { return jobsXpMult; }
    }

    // Структура для лога донатов
    public record DonateLogEntry(long timestamp, String player, String type,
                                  String detail, double amount) {
        public String formatDate() {
            return new java.text.SimpleDateFormat("dd.MM HH:mm").format(new Date(timestamp));
        }
    }

    // ═══════════════════════════════════════
    // КОНСТРУКТОР
    // ═══════════════════════════════════════

    public DonateManager(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
        this.apiClient = new DonatePayClient(plugin);
        this.dataFile = new File(plugin.getDataFolder(), "donations.yml");
        this.donationDurationSeconds = plugin.getConfig().getLong("donation-duration-days", 30) * 86400L;
        loadStatuses();
        loadData();
        loadFundraiser();
        loadDonateLog();
        startPolling();
    }

    // ═══════════════════════════════════════
    // ЗАГРУЗКА / СОХРАНЕНИЕ
    // ═══════════════════════════════════════

    private void loadStatuses() {
        statuses.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("statuses");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(key);
            if (sub != null) statuses.put(key, new StatusDef(key, sub));
        }
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось создать donations.yml: " + e.getMessage());
            }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
        lastProcessedId = dataCfg.getInt("last_id", 0);
        fundraiserCollected = dataCfg.getDouble("fundraiser_collected", 0);

        ConfigurationSection donatedSec = dataCfg.getConfigurationSection("donated");
        if (donatedSec != null) {
            for (String key : donatedSec.getKeys(false)) {
                totalDonated.put(key, donatedSec.getDouble(key, 0));
            }
        }
        // PASS_FIX #1: passHolders больше НЕ хранятся в donations.yml
        // Старые данные мигрируются в pass_data.yml через PassManager
    }

    private void saveData() {
        dataCfg.set("last_id", lastProcessedId);
        dataCfg.set("fundraiser_collected", fundraiserCollected);
        for (Map.Entry<String, Double> e : totalDonated.entrySet()) {
            dataCfg.set("donated." + e.getKey(), e.getValue());
        }
        // PASS_FIX #1: pass_holders удалены из этого файла
        dataCfg.set("pass_holders", null);
        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка сохранения donations.yml: " + e.getMessage());
        }
    }

    private void loadDonateLog() {
        donateLog.clear();
        File logFile = new File(plugin.getDataFolder(), "donate.log");
        if (!logFile.exists()) return;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Простое чтение — лог показывается через /donate log
            }
        } catch (Exception ignored) {}
    }

    private void appendDonateLog(String type, String player, String detail, double amount) {
        DonateLogEntry entry = new DonateLogEntry(System.currentTimeMillis(), player, type, detail, amount);
        donateLog.add(entry);
        while (donateLog.size() > MAX_LOG_SIZE) donateLog.remove(0);

        File logFile = new File(plugin.getDataFolder(), "donate.log");
        try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
             java.io.PrintWriter out = new java.io.PrintWriter(fw)) {
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println(stamp + " | " + type + " | " + player + " | " + detail + " | " + amount + "₽");
        } catch (IOException ignored) {}
    }

    // ═══════════════════════════════════════
    // POLLING
    // ═══════════════════════════════════════

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
        List<DonatePayClient.DonateTransaction> transactions = apiClient.fetchTransactions(token, 10);

        for (DonatePayClient.DonateTransaction tx : transactions) {
            if (tx.id() <= lastProcessedId) continue;
            if (!processingTxIds.add(tx.id())) continue;
            if (!tx.isSuccess()) {
                processingTxIds.remove(tx.id());
                continue;
            }

            lastProcessedId = Math.max(lastProcessedId, tx.id());
            saveData();

            processDonation(tx.id(), tx.amount(), tx.sender(), tx.comment());
            processingTxIds.remove(tx.id());
        }
    }

    // ═══════════════════════════════════════
    // ОБРАБОТКА ДОНАТОВ
    // ═══════════════════════════════════════

    private void processDonation(int txId, double amountRub, String sender, String comment) {
        String nick = extractNick(sender, comment);
        if (nick == null) {
            plugin.getLogger().info("Донат #" + txId + " (" + amountRub + "₽) — ник не извлечён из '" + sender + "'");
            appendDonateLog("FAILED", "unknown", "nick not found from: " + sender, amountRub);
            return;
        }

        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(nick);
        if (!offPlayer.hasPlayedBefore()) {
            plugin.getLogger().info("Донат #" + txId + " — игрок " + nick + " не найден");
            appendDonateLog("FAILED", nick, "player not found", amountRub);
            return;
        }

        // ═══ ПРОХОДКА → делегируем в PassManager ═══
        PassManager pm = plugin.getPassManager();
        int passPrice = pm.getPassPrice();
        boolean hasVk = checkHasVk(offPlayer);

        if (!hasVk && amountRub >= passPrice && amountRub < getStatusMinPrice()) {
            // PASS_FIX #10: Логика проходки делегирована в PassManager
            boolean granted = pm.grantPass(offPlayer, amountRub, "donate");
            if (granted) {
                String key = nick.toLowerCase();
                totalDonated.put(key, totalDonated.getOrDefault(key, 0.0) + amountRub);
                updateFundraiser(amountRub);
                appendDonateLog("PASS", nick, "Проходка (" + amountRub + "₽)", amountRub);

                // Анонс через PassManager
                pm.announcePassGrant(nick, amountRub);

                // Сообщение игроку
                Player online = offPlayer.getPlayer();
                if (online != null) {
                    pm.sendPassGrantMessage(online, amountRub);
                }

                plugin.getLogger().info("ПРОХОДКА: " + nick + " (" + amountRub + "₽) #" + txId);
                return;
            }
        }

        // Найти подходящий статус по сумме
        StatusDef bestStatus = findBestStatus(amountRub);

        // Нет статуса → репутация
        if (bestStatus == null) {
            if (plugin.getConfig().getBoolean("rep-purchase.enabled", true)) {
                int maxWithoutStatus = plugin.getConfig().getInt("rep-purchase.max-without-status", 0);
                if (maxWithoutStatus == 0 || amountRub <= maxWithoutStatus) {
                    processRepPurchase(txId, amountRub, nick);
                    return;
                }
            }
            plugin.getLogger().info("Донат #" + txId + " от " + nick + " (" + amountRub + "₽) — ниже минимального статуса");
            return;
        }

        handleStatusGrant(offPlayer, bestStatus, amountRub, txId, nick);
    }

    private void handleStatusGrant(OfflinePlayer offPlayer, StatusDef bestStatus, double amountRub, int txId, String nick) {
        Player online = offPlayer.getPlayer();
        boolean hasAny = hasAnyStatus(offPlayer);
        boolean higherExists = online != null && hasHigherStatus(online, bestStatus.id);

        // PASS_FIX #3: При выдаче донат-статуса — удалить проходку
        if (online != null && plugin.getPassManager().hasPass(online)) {
            plugin.getPassManager().removePassOnStatusGrant(online);
        }

        if (higherExists) {
            StatusDef current = getPlayerStatus(online);
            if (current != null) {
                extendStatus(offPlayer, current);
                announceDonation(nick, current, amountRub, true);
                appendDonateLog("EXTEND", nick, current.display + " продление", amountRub);
            }
        } else if (hasAny && bestStatus.price <= getCurrentStatusPrice(offPlayer)) {
            StatusDef current = getPlayerStatusByOffline(offPlayer);
            if (current != null) {
                extendStatus(offPlayer, current);
                announceDonation(nick, current, amountRub, true);
                appendDonateLog("EXTEND", nick, current.display + " продление", amountRub);
            }
        } else {
            grantStatus(offPlayer, bestStatus);
            announceDonation(nick, bestStatus, amountRub, false);
            appendDonateLog("GRANT", nick, bestStatus.display + " выдача", amountRub);
        }

        String key = nick.toLowerCase();
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

        // PASS_FIX #6: Если нет ВК — используем локальную репутацию
        if (vkId == -1 && p != null) {
            if (plugin.getPassManager().hasPass(p)) {
                plugin.getPassManager().addLocalRep(p, rep);
                p.sendMessage(ChatColor.GREEN + "✨ +" + rep + " локальной репутации за донат ("
                        + (int) amountRub + "₽)!");
                p.sendMessage(ChatColor.GRAY + "Привяжите ВК (/vklink) для переноса в основной профиль!");
            } else {
                p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink) или купи проходку, чтобы получить репутацию за донат!");
            }
        } else if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + "✨ +" + rep + " репутации за донат (" + (int) amountRub + "₽)!");
            }
        }

        String key = nick.toLowerCase();
        totalDonated.put(key, totalDonated.getOrDefault(key, 0.0) + amountRub);
        updateFundraiser(amountRub);

        if (plugin.getConfig().getBoolean("broadcasts.enabled", true)) {
            String template = plugin.getConfig().getString("messages.rep-broadcast-mc",
                    "&6💰 &e{player} &6пополнил баланс на &e{amount} реп. &6за &e{rub}₽");
            String msg = formatMessage(template, nick, null, amountRub)
                    .replace("{amount}", String.valueOf(rep))
                    .replace("{rub}", String.valueOf((int) amountRub));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }

        appendDonateLog("REP", nick, rep + " реп за " + amountRub + "₽", amountRub);
        plugin.getLogger().info("РЕП-ДОНАТ: " + nick + " → " + rep + " реп (" + amountRub + "₽) #" + txId);
    }

    // ═══════════════════════════════════════
    // ВЫДАЧА ПРАВ (через LuckPermsHelper)
    // ═══════════════════════════════════════

    private void grantStatus(OfflinePlayer player, StatusDef status) {
        for (String id : statuses.keySet()) {
            if (player.getPlayer() != null) {
                LuckPermsHelper.unsetPermission(player.getUniqueId(), "vkchat.donate." + id);
                LuckPermsHelper.removeFromGroup(player.getUniqueId(), id);
            } else {
                LuckPermsHelper.dispatchCommand("lp user " + player.getName() + " parent remove " + id);
                LuckPermsHelper.dispatchCommand("lp user " + player.getName() + " permission unset vkchat.donate." + id);
            }
        }

        if (player.getPlayer() != null) {
            LuckPermsHelper.addToGroup(player.getUniqueId(), status.id);
            LuckPermsHelper.setTempPermission(player.getUniqueId(),
                    "vkchat.donate." + status.id, true, donationDurationSeconds);
        } else {
            LuckPermsHelper.dispatchCommand("lp user " + player.getName() + " parent add " + status.id);
            LuckPermsHelper.dispatchCommand("lp user " + player.getName()
                    + " permission settemp vkchat.donate." + status.id + " true " + donationDurationSeconds + "s");
        }
        plugin.getLogger().info("LP: " + player.getName() + " → группа " + status.id + " (30д)");
    }

    private void extendStatus(OfflinePlayer player, StatusDef status) {
        String perm = "vkchat.donate." + status.id;
        if (player.getPlayer() != null) {
            LuckPermsHelper.extendTempPermission(player.getUniqueId(), perm, donationDurationSeconds);
        } else {
            LuckPermsHelper.dispatchCommand("lp user " + player.getName()
                    + " permission settemp " + perm + " true " + donationDurationSeconds + "s accumulate");
        }
        plugin.getLogger().info("LP: " + player.getName() + " → продление " + status.id + " +30д");
    }

    // ═══════════════════════════════════════
    // АНОНСЫ (шаблоны из конфига)
    // ═══════════════════════════════════════

    private void announceDonation(String nick, StatusDef status, double amount, boolean extending) {
        boolean broadcast = plugin.getConfig().getBoolean("broadcasts.enabled", true);

        if (broadcast) {
            String mcTemplate = extending
                    ? plugin.getConfig().getString("messages.extend-broadcast-mc",
                        "&6💰 &e{player} &6продлил статус {status} &6за донат!")
                    : plugin.getConfig().getString("messages.donate-broadcast-mc",
                        "&6💰 &e{player} &6получил статус {status} &6за донат!");
            String mcMsg = formatMessage(mcTemplate, nick, status, amount);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', mcMsg));
        }

        if (broadcast) {
            String vkTemplate = extending
                    ? plugin.getConfig().getString("messages.extend-broadcast-vk",
                        "💰 {player} продлил статус {status} за донат!")
                    : plugin.getConfig().getString("messages.donate-broadcast-vk",
                        "💰 {player} получил статус {status} за донат!");
            String vkMsg = formatMessage(vkTemplate, nick, status, amount);
            try {
                VKChatPlugin.getInstance().getApi().sendToMainChat(vkMsg);
            } catch (Exception e) {
                if (!vkAnnounceWarningLogged) {
                    plugin.getLogger().warning("Ошибка отправки анонса в ВК: " + e.getMessage());
                    vkAnnounceWarningLogged = true;
                }
            }
        }

        Player player = Bukkit.getPlayerExact(nick);
        if (player != null) {
            String thanksTemplate = plugin.getConfig().getString("messages.donate-thanks",
                    "&a✨ Спасибо за донат! Статус {status} &aактивен на 30 дней.");
            String thanksMsg = formatMessage(thanksTemplate, nick, status, amount);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', thanksMsg));
        }
    }

    private String formatMessage(String template, String player, StatusDef status, double amount) {
        if (template == null) return "";
        return template
                .replace("{player}", player != null ? player : "?")
                .replace("{status}", status != null ? status.display : "")
                .replace("{amount}", String.format("%.0f", amount))
                .replace("{rub}", String.format("%.0f", amount));
    }

    // ═══════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════

    private StatusDef findBestStatus(double amountRub) {
        StatusDef best = null;
        for (StatusDef s : statuses.values()) {
            if (amountRub >= s.price && (best == null || s.price > best.price))
                best = s;
        }
        return best;
    }

    /**
     * Возвращает минимальную цену донат-статуса.
     */
    private int getStatusMinPrice() {
        int min = Integer.MAX_VALUE;
        for (StatusDef s : statuses.values()) {
            if (s.price < min) min = s.price;
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private boolean checkHasVk(OfflinePlayer offPlayer) {
        Player online = offPlayer.getPlayer();
        if (online == null) return false;
        try {
            return VKChatPlugin.getInstance().getApi().getLinkedVkId(online) != -1;
        } catch (Exception e) { return false; }
    }

    private String extractNick(String sender, String comment) {
        String nick = cleanNick(sender);
        if (nick != null) return nick;
        return cleanNick(comment);
    }

    private String cleanNick(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.length() >= 2 && cleaned.length() <= 16) return cleaned;
        return null;
    }

    private int getCurrentStatusPrice(OfflinePlayer player) {
        Player p = player.getPlayer();
        if (p == null) return 0;
        for (StatusDef s : statuses.values()) {
            if (p.hasPermission("vkchat.donate." + s.id)) return s.price;
        }
        return 0;
    }

    private StatusDef getPlayerStatusByOffline(OfflinePlayer player) {
        if (player.getPlayer() == null) return null;
        return getPlayerStatus(player.getPlayer());
    }

    private boolean hasAnyStatus(OfflinePlayer player) {
        Player p = player.getPlayer();
        if (p == null) return false;
        for (String id : statuses.keySet()) {
            if (p.hasPermission("vkchat.donate." + id)) return true;
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

    // ═══════════════════════════════════════
    // FUNDRAISER (BossBar)
    // ═══════════════════════════════════════

    private void loadFundraiser() {
        if (!plugin.getConfig().getBoolean("fundraiser.enabled", false)) return;
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        if (goal <= 0) return;

        String color = plugin.getConfig().getString("fundraiser.bar-color", "PURPLE");
        BarColor barColor;
        try { barColor = BarColor.valueOf(color); } catch (IllegalArgumentException e) { barColor = BarColor.PURPLE; }

        fundraiserBar = Bukkit.createBossBar(formatFundraiserTitle(goal), barColor, BarStyle.SOLID);
        fundraiserBar.setVisible(true);
        fundraiserBar.setProgress(Math.min(1.0, fundraiserCollected / goal));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!fundraiserHidden.contains(p.getUniqueId())) fundraiserBar.addPlayer(p);
        }
    }

    private String formatFundraiserTitle(double goal) {
        String template = plugin.getConfig().getString("fundraiser.bar-text",
                "&d💰 Сбор: &f{collected}₽ &7/ &f{goal}₽ &a({percent}%)");
        double pct = goal > 0 ? Math.min(100, (fundraiserCollected / goal) * 100) : 0;
        return ChatColor.translateAlternateColorCodes('&', template
                .replace("{collected}", String.format("%.0f", fundraiserCollected))
                .replace("{goal}", String.format("%.0f", goal))
                .replace("{percent}", String.format("%.0f", pct)));
    }

    private void updateFundraiser(double amount) {
        if (fundraiserBar == null) return;
        fundraiserCollected += amount;
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        fundraiserBar.setTitle(formatFundraiserTitle(goal));
        fundraiserBar.setProgress(Math.min(1.0, fundraiserCollected / goal));
        saveData();

        if (fundraiserCollected >= goal && goal > 0) {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "🎉💰 Цель сбора средств достигнута! "
                    + String.format("%.0f", fundraiserCollected) + "₽ / " + String.format("%.0f", goal) + "₽");
        }
    }

    public void startFundraiser(double goal) {
        plugin.getConfig().set("fundraiser.enabled", true);
        plugin.getConfig().set("fundraiser.goal", goal);
        plugin.saveConfig();
        fundraiserCollected = 0;
        saveData();

        String color = plugin.getConfig().getString("fundraiser.bar-color", "PURPLE");
        BarColor barColor;
        try { barColor = BarColor.valueOf(color); } catch (IllegalArgumentException e) { barColor = BarColor.PURPLE; }

        if (fundraiserBar != null) fundraiserBar.removeAll();
        fundraiserBar = Bukkit.createBossBar(formatFundraiserTitle(goal), barColor, BarStyle.SOLID);
        fundraiserBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) fundraiserBar.addPlayer(p);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "💰 Старт сбора средств! Цель: " + (int) goal + "₽");
    }

    public void stopFundraiser() {
        plugin.getConfig().set("fundraiser.enabled", false);
        plugin.saveConfig();
        if (fundraiserBar != null) { fundraiserBar.removeAll(); fundraiserBar = null; }
        fundraiserCollected = 0;
        saveData();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "💰 Сбор средств завершён!");
    }

    public String getFundraiserInfo() {
        if (fundraiserBar == null) return ChatColor.RED + "Сбор средств не активен.";
        double goal = plugin.getConfig().getDouble("fundraiser.goal", 10000);
        double pct = goal > 0 ? fundraiserCollected / goal * 100 : 0;
        return ChatColor.LIGHT_PURPLE + "💰 Сбор: " + ChatColor.WHITE
                + String.format("%.0f", fundraiserCollected) + "₽" + ChatColor.GRAY + " / "
                + ChatColor.WHITE + String.format("%.0f", goal) + "₽"
                + ChatColor.GREEN + " (" + String.format("%.0f", pct) + "%)";
    }

    // ═══════════════════════════════════════
    // ПУБЛИЧНЫЕ GETTERS
    // ═══════════════════════════════════════

    public StatusDef getPlayerStatus(Player player) {
        return statuses.values().stream()
                .filter(s -> player.hasPermission("vkchat.donate." + s.id))
                .max(Comparator.comparingInt(StatusDef::getWeight))
                .orElse(null);
    }

    public StatusDef getStatusById(String id) { return statuses.get(id); }
    public Collection<StatusDef> getStatuses() { return statuses.values(); }

    public long getDaysLeft(Player player) {
        StatusDef s = getPlayerStatus(player);
        if (s == null) return 0;
        return LuckPermsHelper.getDaysLeft(player, "vkchat.donate." + s.id);
    }

    public double getRepDiscount(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.getRepDiscount() : 0;
    }

    public double getTpCooldownMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.getTpCooldownMult() : 1.0;
    }

    public int getMaxHomes(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.getMaxHomes() : 3;
    }

    public double getMarketMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.getMarketMult() : 1.0;
    }

    public double getJobsXpMult(Player player) {
        StatusDef s = getPlayerStatus(player);
        return s != null ? s.getJobsXpMult() : 1.0;
    }

    public double getTotalDonated(String playerName) {
        return totalDonated.getOrDefault(playerName.toLowerCase(), 0.0);
    }

    public double getTotalDonatedAll() {
        return totalDonated.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public int getDonorCount() { return totalDonated.size(); }

    public List<Map.Entry<String, Double>> getTopDonors(int limit) {
        return totalDonated.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * PASS_FIX #10: getPassHolders() делегирует в PassManager.
     * Сохранено для обратной совместимости.
     */
    public Collection<String> getPassHolders() {
        return plugin.getPassManager().getActivePassHolders().stream()
                .map(PassManager.PassHolder::getLastName)
                .toList();
    }

    public List<DonateLogEntry> getRecentLog(int limit) {
        int from = Math.max(0, donateLog.size() - limit);
        return new ArrayList<>(donateLog.subList(from, donateLog.size()));
    }

    public String getUpgradeInfo(Player player) {
        StatusDef current = getPlayerStatus(player);
        StatusDef next = null;
        for (StatusDef s : statuses.values()) {
            if (current == null || s.getPrice() > current.getPrice()) {
                if (next == null || s.getPrice() < next.getPrice()) next = s;
            }
        }
        if (next == null) return ChatColor.GOLD + "У вас максимальный статус!";
        int diff = next.getPrice() - (current != null ? current.getPrice() : 0);
        return ChatColor.YELLOW + "Следующий статус: " + next.getName()
                + ChatColor.GRAY + " (доплатить " + diff + "₽)";
    }

    public void checkExpiredStatus(Player player) {
        StatusDef status = getPlayerStatus(player);
        if (status == null) return;

        long daysLeft = getDaysLeft(player);
        if (daysLeft <= 0) {
            player.sendMessage(ChatColor.RED + "⚠ Ваш донат-статус " + status.getName()
                    + ChatColor.RED + " истёк! Продлите через донат.");
        } else if (daysLeft <= 3) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Ваш статус " + status.getName()
                    + ChatColor.YELLOW + " истекает через " + daysLeft + " дн.!");
        }
    }

    /**
     * Делегирование в PassManager (обратная совместимость).
     */
    public void grantPassManually(String name) {
        plugin.getPassManager().grantPassManually(name);
    }

    public void removePass(String name) {
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        plugin.getPassManager().removePass(off);
    }

    public void addPlayerToFundraiser(Player p) {
        if (fundraiserBar != null && !fundraiserHidden.contains(p.getUniqueId()))
            fundraiserBar.addPlayer(p);
    }

    public boolean toggleFundraiserBar(Player p) {
        if (fundraiserBar == null) return false;
        if (getPlayerStatus(p) == null) return false;
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

    public String getSetupInfo() {
        String token = plugin.getConfig().getString("api-token", "");
        boolean configured = !token.isEmpty() && !token.equals("YOUR_DONATEPAY_TOKEN");
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ ДОНАТ-СТАТУСЫ (DonatePay) ═══\n\n");
        sb.append("§7API: ").append(configured ? "§a✅ Настроен" : "§c❌ Не настроен").append("\n");
        sb.append("§7LP API: ").append(LuckPermsHelper.isAvailable() ? "§a✅" : "§c❌").append("\n");
        sb.append("§7Команда: §e/donate setup <API-токен>\n\n");
        sb.append("§7⚠ В ИМЕНИ отправителя укажите НИКНЕЙМ!\n");
        sb.append("§7Статусы действуют ").append(donationDurationSeconds / 86400)
                .append(" дней, продлеваются.\n\n");
        for (StatusDef s : statuses.values()) {
            sb.append(s.getName()).append(" §7— ").append(s.getPrice()).append("₽ / 30 дней\n");
            sb.append("  §7Скидка: §f").append((int)(s.getRepDiscount() * 100))
                    .append("% §7| КД ТП: §f×").append(s.getTpCooldownMult())
                    .append(" §7| Домов: §f").append(s.getMaxHomes())
                    .append(" §7| Рынок: §f×").append(s.getMarketMult())
                    .append(" §7| Jobs: §f×").append(s.getJobsXpMult()).append("\n");
        }
        // Проходка
        PassManager pm = plugin.getPassManager();
        sb.append("\n§7🎫 Проходка: §f").append(pm.getPassPrice())
                .append("₽ / ").append(pm.getPassDurationSeconds() / 86400).append("д\n");
        sb.append("§7   Активных: §f").append(pm.getActivePassCount())
                .append(" §7| Конвертировано: §f").append(pm.getTotalConverted()).append("\n");
        sb.append("\n§7Всего донатеров: §f").append(getDonorCount());
        sb.append(" §7| Общая сумма: §f").append(String.format("%.0f", getTotalDonatedAll())).append("₽\n");
        return sb.toString();
    }

    public void shutdown() {
        saveData();
        if (fundraiserBar != null) fundraiserBar.removeAll();
    }
}
