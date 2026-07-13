package ru.example.vkchatdonate.pass;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatdonate.DonateManager;
import ru.example.vkchatdonate.VKChatDonatePlugin;
import ru.example.vkchatdonate.luckperms.LuckPermsHelper;
import ru.example.vkchatdonate.pass.event.PassConvertEvent;
import ru.example.vkchatdonate.pass.event.PassExpireEvent;
import ru.example.vkchatdonate.pass.event.PassGrantEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PassManager — выделенный менеджер системы проходок.
 *
 * FIX #1:  Хранение по UUID вместо имён (были String → теперь UUID)
 * FIX #2:  Проверка истечения проходки при входе (было только для статусов)
 * FIX #3:  Проходка автоматически удаляется при получении донат-статуса
 * FIX #4:  Валидация passHolders при загрузке (синхронизация с LP)
 * FIX #5:  Отдельная длительность проходки (pass.duration-days, не donation-duration)
 * FIX #6:  Пропуск выдачи проходки если уже привязан ВК
 * FIX #7:  Локальная репутация для проходочников имеет настраиваемый лимит
 * FIX #8:  Очистка PDC local_rep при истечении проходки
 * FIX #9:  Save-ahead при выдаче/удалении (идемпотентность)
 * FIX #10: Логика проходки вынесена из DonateManager (SRP)
 *
 * IMPROVE #1:  Выделенный PassManager (Single Responsibility)
 * IMPROVE #2:  PassHolder record с метаданными (UUID, grantDate, expiry, source, amount)
 * IMPROVE #3:  Миграция проходка → ВК (автоматический перенос локальной репутации)
 * IMPROVE #4:  /pass команда для игроков (статус, остаток дней)
 * IMPROVE #5:  Grace-период после истечения (1-3 дня, настраиваемо)
 * IMPROVE #6:  Аналитика: куплено / активно / истекло / конвертировано в ВК
 * IMPROVE #7:  Настраиваемые сообщения проходки из config.yml
 * IMPROVE #8:  События Bukkit: PassGrantEvent, PassExpireEvent, PassConvertEvent
 * IMPROVE #9:  Автоочистка устаревших записей при загрузке
 * IMPROVE #10: /pass buy — покупка проходки за донат-средства (если уже есть донат)
 */
public class PassManager {

    private final VKChatDonatePlugin plugin;
    private final File passDataFile;
    private FileConfiguration passData;

    /** UUID → PassHolder */
    private final Map<UUID, PassHolder> passHolders = new ConcurrentHashMap<>();

    // Конфигурация
    private int passPrice;
    private long passDurationSeconds;
    private int passGraceDays;
    private int localRepCap;
    private boolean autoConvertOnVkLink;

    // Статистика
    private int totalPurchased = 0;
    private int totalConverted = 0;
    private int totalExpired = 0;

    // ═══════════════════════════════════════
    // PASSHOLDER RECORD — IMPROVE #2
    // ═══════════════════════════════════════

    public static final class PassHolder {
        private final UUID uuid;
        private final String lastName;          // для отображения
        private final long grantDate;           // millis
        private final long expiryDate;          // millis
        private final String source;            // "donate", "manual", "upgrade"
        private final double amountPaid;        // ₽

        public PassHolder(UUID uuid, String lastName, long grantDate, long expiryDate,
                          String source, double amountPaid) {
            this.uuid = uuid;
            this.lastName = lastName;
            this.grantDate = grantDate;
            this.expiryDate = expiryDate;
            this.source = source;
            this.amountPaid = amountPaid;
        }

        public UUID getUuid() { return uuid; }
        public String getLastName() { return lastName; }
        public long getGrantDate() { return grantDate; }
        public long getExpiryDate() { return expiryDate; }
        public String getSource() { return source; }
        public double getAmountPaid() { return amountPaid; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryDate;
        }

        public boolean isInGracePeriod(int graceDays) {
            if (!isExpired()) return false;
            long graceEnd = expiryDate + (graceDays * 86400_000L);
            return System.currentTimeMillis() <= graceEnd;
        }

        public long getDaysLeft() {
            long msLeft = expiryDate - System.currentTimeMillis();
            return Math.max(0, msLeft / 86400_000L);
        }

        public long getHoursLeft() {
            long msLeft = expiryDate - System.currentTimeMillis();
            return Math.max(0, msLeft / 3600_000L);
        }
    }

    // ═══════════════════════════════════════
    // КОНСТРУКТОР
    // ═══════════════════════════════════════

    public PassManager(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
        this.passDataFile = new File(plugin.getDataFolder(), "pass_data.yml");
        loadConfig();
        loadPassData();
        validatePassHolders();  // FIX #4
        startExpiryCheckTask();
    }

    // ═══════════════════════════════════════
    // КОНФИГУРАЦИЯ
    // ═══════════════════════════════════════

    private void loadConfig() {
        this.passPrice = plugin.getConfig().getInt("pass.price", 500);
        this.passDurationSeconds = plugin.getConfig().getLong("pass.duration-days", 30) * 86400L;
        this.passGraceDays = plugin.getConfig().getInt("pass.grace-days", 3);
        this.localRepCap = plugin.getConfig().getInt("pass.local-rep-cap", 50000);
        this.autoConvertOnVkLink = plugin.getConfig().getBoolean("pass.auto-convert-on-vk-link", true);
    }

    public int getPassPrice() { return passPrice; }
    public long getPassDurationSeconds() { return passDurationSeconds; }
    public int getPassGraceDays() { return passGraceDays; }
    public int getLocalRepCap() { return localRepCap; }

    // ═══════════════════════════════════════
    // ЗАГРУЗКА / СОХРАНЕНИЕ (FIX #1: UUID-based)
    // ═══════════════════════════════════════

    private void loadPassData() {
        if (!passDataFile.exists()) {
            // Миграция из старого donations.yml → pass_data.yml
            migrateFromDonations();
            return;
        }
        passData = YamlConfiguration.loadConfiguration(passDataFile);

        totalPurchased = passData.getInt("stats.total-purchased", 0);
        totalConverted = passData.getInt("stats.total-converted", 0);
        totalExpired = passData.getInt("stats.total-expired", 0);

        ConfigurationSection holders = passData.getConfigurationSection("holders");
        if (holders == null) return;

        for (String uuidStr : holders.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection sec = holders.getConfigurationSection(uuidStr);
                if (sec == null) continue;

                PassHolder holder = new PassHolder(
                        uuid,
                        sec.getString("last-name", "?"),
                        sec.getLong("grant-date", 0),
                        sec.getLong("expiry-date", 0),
                        sec.getString("source", "donate"),
                        sec.getDouble("amount-paid", 0)
                );
                passHolders.put(uuid, holder);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Pass] Некорректный UUID в pass_data.yml: " + uuidStr);
            }
        }

        // IMPROVE #9: Автоочистка устаревших + grace period
        int cleaned = 0;
        Iterator<Map.Entry<UUID, PassHolder>> it = passHolders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PassHolder> entry = it.next();
            PassHolder h = entry.getValue();
            if (h.isExpired() && !h.isInGracePeriod(passGraceDays)) {
                it.remove();
                cleaned++;
            }
        }
        if (cleaned > 0) {
            plugin.getLogger().info("[Pass] Очищено " + cleaned + " истёкших проходок при загрузке");
            savePassData();
        }

        plugin.getLogger().info("[Pass] Загружено " + passHolders.size() + " активных проходок");
    }

    /**
     * Миграция из старого формата donations.yml (pass_holders: [name1, name2])
     * в новый UUID-based pass_data.yml
     */
    private void migrateFromDonations() {
        File oldFile = new File(plugin.getDataFolder(), "donations.yml");
        if (!oldFile.exists()) {
            createEmptyPassData();
            return;
        }

        FileConfiguration oldData = YamlConfiguration.loadConfiguration(oldFile);
        List<String> oldNames = oldData.getStringList("pass_holders");

        if (oldNames.isEmpty()) {
            createEmptyPassData();
            return;
        }

        plugin.getLogger().info("[Pass] Миграция " + oldNames.size() + " проходок из donations.yml...");
        int migrated = 0;
        long now = System.currentTimeMillis();
        long expiryDefault = now + passDurationSeconds;

        passData = new YamlConfiguration();

        for (String name : oldNames) {
            OfflinePlayer off = ru.example.vkchat.util.UUIDResolver.resolve(name);
            if (off == null) {
                plugin.getLogger().warning("[Pass] Пропуск неизвестного игрока: " + name);
                continue;
            }

            UUID uuid = off.getUniqueId();
            PassHolder holder = new PassHolder(uuid, name, now - passDurationSeconds * 1000,
                    expiryDefault, "donate-legacy", 0);
            passHolders.put(uuid, holder);
            migrated++;
        }

        totalPurchased = migrated;
        savePassData();
        plugin.getLogger().info("[Pass] Мигрировано " + migrated + "/" + oldNames.size() + " проходок");
    }

    private void createEmptyPassData() {
        passData = new YamlConfiguration();
        savePassData();
    }

    private final Object saveLock = new Object();

    public void savePassData() {
        synchronized (saveLock) {
            passData.set("stats.total-purchased", totalPurchased);
            passData.set("stats.total-converted", totalConverted);
            passData.set("stats.total-expired", totalExpired);

            for (Map.Entry<UUID, PassHolder> entry : passHolders.entrySet()) {
                String path = "holders." + entry.getKey().toString();
                PassHolder h = entry.getValue();
                passData.set(path + ".last-name", h.getLastName());
                passData.set(path + ".grant-date", h.getGrantDate());
                passData.set(path + ".expiry-date", h.getExpiryDate());
                passData.set(path + ".source", h.getSource());
                passData.set(path + ".amount-paid", h.getAmountPaid());
            }

            try {
                passData.save(passDataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[Pass] Ошибка сохранения pass_data.yml: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════
    // FIX #4: Валидация против LuckPerms
    // ═══════════════════════════════════════

    private void validatePassHolders() {
        if (!LuckPermsHelper.isAvailable()) return;

        int removed = 0;
        Iterator<Map.Entry<UUID, PassHolder>> it = passHolders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PassHolder> entry = it.next();
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) {
                // Онлайн-игрок — проверяем LP право
                if (!online.hasPermission("vkchat.pass")) {
                    it.remove();
                    removed++;
                }
            }
            // Оффлайн-игроков не трогаем — LP право может быть временно недоступно
        }
        if (removed > 0) {
            plugin.getLogger().info("[Pass] Удалено " + removed + " проходок без LP-права (валидация)");
            savePassData();
        }
    }

    // ═══════════════════════════════════════
    // ВЫДАЧА ПРОХОДКИ
    // ═══════════════════════════════════════

    /**
     * Выдать проходку игроку через донат.
     * FIX #6: Пропускаем если уже привязан ВК
     * FIX #9: Save-ahead паттерн
     */
    public boolean grantPass(OfflinePlayer player, double amount, String source) {
        if (player == null) return false;

        // FIX #6: Пропуск если есть ВК
        Player online = player.getPlayer();
        if (online != null) {
            int vkId = getLinkedVkId(online);
            if (vkId != -1) {
                plugin.getLogger().info("[Pass] " + player.getName()
                        + " уже привязал ВК — проходка не нужна");
                return false;
            }
        }

        // FIX #3: Если уже есть донат-статус — проходка не нужна
        if (online != null && hasAnyDonateStatus(online)) {
            plugin.getLogger().info("[Pass] " + player.getName()
                    + " уже имеет донат-статус — проходка не нужна");
            return false;
        }

        // Если уже есть проходка — продлеваем
        if (hasPass(player)) {
            return extendPass(player, amount);
        }

        // FIX #9: Save-ahead — сохраняем ДО выдачи права
        long now = System.currentTimeMillis();
        long expiry = now + passDurationSeconds * 1000;
        PassHolder holder = new PassHolder(
                player.getUniqueId(),
                player.getName() != null ? player.getName() : "?",
                now, expiry, source, amount
        );
        passHolders.put(player.getUniqueId(), holder);
        totalPurchased++;
        savePassData(); // Save-ahead

        // Выдаём LP право
        String perm = "vkchat.pass";
        if (online != null) {
            LuckPermsHelper.setTempPermission(player.getUniqueId(), perm, true, passDurationSeconds);
        } else {
            LuckPermsHelper.dispatchCommand("lp user " + player.getName()
                    + " permission settemp " + perm + " true " + passDurationSeconds + "s");
        }

        // IMPROVE #8: Событие
        if (online != null) {
            Bukkit.getPluginManager().callEvent(new PassGrantEvent(online, holder));
        }

        plugin.getLogger().info("[Pass] " + player.getName() + " получил проходку ("
                + (passDurationSeconds / 86400) + "д, " + source + ", " + (int) amount + "₽)");
        return true;
    }

    /**
     * Продление существующей проходки.
     */
    private boolean extendPass(OfflinePlayer player, double amount) {
        PassHolder existing = passHolders.get(player.getUniqueId());
        if (existing == null) return false;

        long newExpiry = existing.getExpiryDate() + passDurationSeconds * 1000;
        PassHolder extended = new PassHolder(
                existing.getUuid(), player.getName() != null ? player.getName() : existing.getLastName(),
                existing.getGrantDate(), newExpiry, existing.getSource(), existing.getAmountPaid() + amount
        );
        passHolders.put(player.getUniqueId(), extended);
        savePassData();

        // Продлеваем LP право
        String perm = "vkchat.pass";
        if (player.getPlayer() != null) {
            LuckPermsHelper.extendTempPermission(player.getUniqueId(), perm, passDurationSeconds);
        } else {
            LuckPermsHelper.dispatchCommand("lp user " + player.getName()
                    + " permission settemp " + perm + " true " + passDurationSeconds + "s accumulate");
        }

        plugin.getLogger().info("[Pass] " + player.getName() + " продлил проходку (+" + (passDurationSeconds / 86400) + "д)");
        return true;
    }

    /**
     * Выдать проходку вручную (админ).
     */
    public boolean grantPassManually(String playerName) {
        org.bukkit.OfflinePlayer off = ru.example.vkchat.util.UUIDResolver.resolve(playerName);
        if (off == null) {
            return false;
        }
        return grantPass(off, 0, "manual");
    }

    // ═══════════════════════════════════════
    // УДАЛЕНИЕ ПРОХОДКИ
    // ═══════════════════════════════════════

    /**
     * Отозвать проходку.
     */
    public void removePass(OfflinePlayer player) {
        if (player == null) return;
        passHolders.remove(player.getUniqueId());

        // Удаляем LP право
        if (player.getPlayer() != null) {
            LuckPermsHelper.unsetPermission(player.getUniqueId(), "vkchat.pass");
        } else {
            LuckPermsHelper.dispatchCommand("lp user " + player.getName() + " permission unset vkchat.pass");
        }

        // FIX #8: Очистка локальной репутации
        cleanupLocalRep(player);

        savePassData();
        plugin.getLogger().info("[Pass] Проходка отозвана: " + player.getName());
    }

    public void removePass(UUID uuid) {
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        removePass(off);
    }

    /**
     * FIX #3: Удалить проходку при получении донат-статуса.
     */
    public void removePassOnStatusGrant(Player player) {
        if (!hasPass(player)) return;

        passHolders.remove(player.getUniqueId());
        LuckPermsHelper.unsetPermission(player.getUniqueId(), "vkchat.pass");

        // Локальную репутацию НЕ чистим — она может быть нужна
        savePassData();

        player.sendMessage(ChatColor.GREEN + "✨ Проходка заменена донат-статусом! "
                + "Локальная репутация сохранена.");
        plugin.getLogger().info("[Pass] " + player.getName() + " — проходка заменена донат-статусом");
    }

    // ═══════════════════════════════════════
    // ИСТЕЧЕНИЕ И ПРОВЕРКИ (FIX #2)
    // ═══════════════════════════════════════

    private void startExpiryCheckTask() {
        // Каждые 5 минут проверяем истёкшие проходки
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int expired = 0;
            Iterator<Map.Entry<UUID, PassHolder>> it = passHolders.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, PassHolder> entry = it.next();
                PassHolder h = entry.getValue();
                if (h.isExpired() && !h.isInGracePeriod(passGraceDays)) {
                    it.remove();
                    expired++;
                }
            }
            if (expired > 0) {
                totalExpired += expired;
                savePassData();
                plugin.getLogger().info("[Pass] Истекло " + expired + " проходок (фоновая проверка)");
            }
        }, 6000L, 6000L); // 5 минут
    }

    /**
     * FIX #2: Проверка истечения проходки при входе игрока.
     * IMPROVE #5: Grace-период
     */
    public void checkPassExpiry(Player player) {
        PassHolder holder = passHolders.get(player.getUniqueId());
        if (holder == null) {
            // Нет записи, но есть право → синхронизируем
            if (player.hasPermission("vkchat.pass")) {
                // LP право есть, но данных нет — создаём запись
                long now = System.currentTimeMillis();
                long expiry = LuckPermsHelper.getDaysLeft(player, "vkchat.pass") * 86400_000L + now;
                holder = new PassHolder(player.getUniqueId(), player.getName(),
                        now, expiry, "legacy-sync", 0);
                passHolders.put(player.getUniqueId(), holder);
                savePassData();
            }
            return;
        }

        if (holder.isExpired()) {
            if (holder.isInGracePeriod(passGraceDays)) {
                // IMPROVE #5: Grace-период
                long graceEnd = holder.getExpiryDate() + (passGraceDays * 86400_000L);
                long hoursLeft = (graceEnd - System.currentTimeMillis()) / 3600_000L;
                player.sendMessage(ChatColor.RED + "⚠ Ваша проходка ИСТЕКЛА!");
                player.sendMessage(ChatColor.YELLOW + "⏰ Льготный период: ещё " + hoursLeft + " ч.");
                player.sendMessage(ChatColor.GRAY + "Продлите через /donate info или привяжите ВК: /vklink");
            } else {
                // Полностью истекла — уведомление и очистка
                player.sendMessage(ChatColor.RED + "⚠ Ваша проходка истекла! Привяжите ВК: /vklink");
                player.sendMessage(ChatColor.GRAY + "Или продлите проходку через /donate info");

                // IMPROVE #8: Событие
                Bukkit.getPluginManager().callEvent(new PassExpireEvent(player, holder));

                // Удаляем LP право на главном потоке
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    LuckPermsHelper.unsetPermission(player.getUniqueId(), "vkchat.pass");
                    // FIX #8: Очистка PDC локальной репутации
                    cleanupLocalRep(player);
                });

                passHolders.remove(player.getUniqueId());
                totalExpired++;
                savePassData();
            }
        } else {
            // Активная проходка
            long daysLeft = holder.getDaysLeft();
            if (daysLeft <= 3) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Ваша проходка истекает через "
                        + daysLeft + " дн.!");
                player.sendMessage(ChatColor.GRAY + "Привяжите ВК (/vklink) или продлите через донат");
            } else {
                player.sendMessage(ChatColor.GREEN + "🎫 Проходка активна — осталось "
                        + daysLeft + " дн.");
            }
        }
    }

    // ═══════════════════════════════════════
    // IMPROVE #3: МИГРАЦИЯ ПРОХОДКА → ВК
    // ═══════════════════════════════════════

    /**
     * Вызывается когда игрок с проходкой привязывает ВК.
     * Переносит локальную репутацию в ВК-репутацию.
     */
    public void convertPassToVk(Player player, int vkId) {
        if (!hasPass(player)) return;
        if (vkId <= 0) return;

        PassHolder holder = passHolders.get(player.getUniqueId());
        if (holder == null) return;

        // Переносим локальную репутацию → ВК
        int localRep = getLocalRep(player);
        if (localRep > 0) {
            try {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, localRep);
                player.sendMessage(ChatColor.GREEN + "✨ " + localRep
                        + " локальной репутации перенесено в ВК-профиль!");
                plugin.getLogger().info("[Pass] " + player.getName() + ": перенесено "
                        + localRep + " лок.реп → ВК (id" + vkId + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("[Pass] Ошибка переноса репутации: " + e.getMessage());
            }
        }

        // Очищаем локальную репутацию
        cleanupLocalRep(player);

        // IMPROVE #8: Событие
        Bukkit.getPluginManager().callEvent(new PassConvertEvent(player, holder, vkId, localRep));

        // Удаляем проходку — ВК-привязка заменяет её
        passHolders.remove(player.getUniqueId());
        LuckPermsHelper.unsetPermission(player.getUniqueId(), "vkchat.pass");
        totalConverted++;
        savePassData();

        player.sendMessage(ChatColor.GREEN + "🎫→🔗 Проходка заменена привязкой ВК!");
        player.sendMessage(ChatColor.GRAY + "Теперь вам доступна полная функциональность ВК-интеграции.");
        plugin.getLogger().info("[Pass] " + player.getName() + " конвертирован: проходка → ВК");
    }

    // ═══════════════════════════════════════
    // ЛОКАЛЬНАЯ РЕПУТАЦИЯ (FIX #7: cap)
    // ═══════════════════════════════════════

    public int getLocalRep(Player player) {
        if (player == null) return 0;
        try {
            var pdc = player.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            return pdc.getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        } catch (Exception e) { return 0; }
    }

    public void addLocalRep(Player player, int amount) {
        if (player == null || amount <= 0) return;
        try {
            var pdc = player.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            int cur = pdc.getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            int newVal = Math.min(localRepCap, cur + amount); // FIX #7: cap
            pdc.set(key, org.bukkit.persistence.PersistentDataType.INTEGER, newVal);
        } catch (Exception ignored) {}
    }

    public boolean takeLocalRep(Player player, int amount) {
        if (player == null || amount <= 0) return false;
        try {
            var pdc = player.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            int cur = pdc.getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            if (cur < amount) return false;
            pdc.set(key, org.bukkit.persistence.PersistentDataType.INTEGER, cur - amount);
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * FIX #8: Очистка локальной репутации при истечении/отзыве проходки.
     */
    private void cleanupLocalRep(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online == null) return;
        try {
            var pdc = online.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            pdc.remove(key);
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════
    // ПРОВЕРКИ
    // ═══════════════════════════════════════

    public boolean hasPass(OfflinePlayer player) {
        if (player == null) return false;
        PassHolder holder = passHolders.get(player.getUniqueId());
        if (holder != null && !holder.isExpired()) return true;
        // Fallback: проверяем LP право
        return player.getPlayer() != null && player.getPlayer().hasPermission("vkchat.pass");
    }

    public boolean hasPass(UUID uuid) {
        PassHolder holder = passHolders.get(uuid);
        if (holder != null && !holder.isExpired()) return true;
        Player online = Bukkit.getPlayer(uuid);
        return online != null && online.hasPermission("vkchat.pass");
    }

    public PassHolder getPassHolder(UUID uuid) {
        return passHolders.get(uuid);
    }

    public boolean hasAnyDonateStatus(Player player) {
        if (player == null) return false;
        DonateManager dm = plugin.getDonateManager();
        if (dm == null) return false;
        return dm.getPlayerStatus(player) != null;
    }

    // ═══════════════════════════════════════
    // ИНФОРМАЦИЯ И АНОНСЫ
    // ═══════════════════════════════════════

    public String getPassInfo(Player player) {
        PassHolder holder = passHolders.get(player.getUniqueId());
        if (holder == null) {
            return ChatColor.GRAY + "У вас нет проходки.\n"
                    + ChatColor.YELLOW + "Купить: /donate info (" + passPrice + "₽/30д)\n"
                    + ChatColor.GRAY + "Или привяжите ВК бесплатно: /vklink";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("═══ 🎫 Ваша проходка ═══\n");
        sb.append(ChatColor.YELLOW).append("Статус: ");
        if (holder.isExpired()) {
            if (holder.isInGracePeriod(passGraceDays)) {
                long hoursLeft = (holder.getExpiryDate() + passGraceDays * 86400_000L
                        - System.currentTimeMillis()) / 3600_000L;
                sb.append(ChatColor.RED).append("ИСТЕКЛА").append(ChatColor.GOLD)
                        .append(" (льготный период: ").append(hoursLeft).append(" ч)\n");
            } else {
                sb.append(ChatColor.RED).append("ИСТЕКЛА\n");
            }
        } else {
            sb.append(ChatColor.GREEN).append("Активна\n");
        }
        sb.append(ChatColor.YELLOW).append("Осталось: ").append(ChatColor.WHITE)
                .append(holder.getDaysLeft()).append(" дн.\n");
        sb.append(ChatColor.YELLOW).append("Источник: ").append(ChatColor.WHITE)
                .append(holder.getSource()).append("\n");
        sb.append(ChatColor.YELLOW).append("Лок. репутация: ").append(ChatColor.WHITE)
                .append(getLocalRep(player)).append("/").append(localRepCap).append("\n");
        sb.append(ChatColor.GRAY).append("Привяжите ВК (/vklink) для полной функциональности!");
        return sb.toString();
    }

    /**
     * Анонс о покупке проходки.
     */
    public void announcePassGrant(String playerName, double amount) {
        if (!plugin.getConfig().getBoolean("broadcasts.enabled", true)) return;

        String template = plugin.getConfig().getString("messages.pass-broadcast-mc",
                "&6🎫 &e{player} &6приобрёл проходку! &aДобро пожаловать!");
        String msg = template.replace("{player}", playerName)
                .replace("{amount}", String.format("%.0f", amount))
                .replace("{rub}", String.format("%.0f", amount));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Сообщение игроку о выдаче проходки.
     */
    public void sendPassGrantMessage(Player player, double amount) {
        String template = plugin.getConfig().getString("messages.pass-grant",
                "&a🎫 Проходка активирована на {days} дней! Добро пожаловать на сервер!");
        String msg = template
                .replace("{days}", String.valueOf(passDurationSeconds / 86400))
                .replace("{amount}", String.format("%.0f", amount))
                .replace("{rub}", String.format("%.0f", amount));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        String infoTemplate = plugin.getConfig().getString("messages.pass-info",
                "&7Локальная репутация: /pass rep | Привяжите ВК: /vklink");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', infoTemplate));
    }

    // ═══════════════════════════════════════
    // IMPROVE #6: АНАЛИТИКА
    // ═══════════════════════════════════════

    public int getActivePassCount() {
        return (int) passHolders.values().stream()
                .filter(h -> !h.isExpired())
                .count();
    }

    public int getTotalPurchased() { return totalPurchased; }
    public int getTotalConverted() { return totalConverted; }
    public int getTotalExpired() { return totalExpired; }

    public Map<String, Object> getAnalytics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("active", getActivePassCount());
        stats.put("total_purchased", totalPurchased);
        stats.put("total_converted", totalConverted);
        stats.put("total_expired", totalExpired);
        stats.put("conversion_rate", totalPurchased > 0
                ? String.format("%.1f%%", (double) totalConverted / totalPurchased * 100) : "N/A");
        return stats;
    }

    public String getAnalyticsFormatted() {
        var s = getAnalytics();
        return ChatColor.GOLD + "═══ 🎫 Аналитика проходок ═══\n"
                + ChatColor.YELLOW + "Активных: " + ChatColor.WHITE + s.get("active") + "\n"
                + ChatColor.YELLOW + "Всего куплено: " + ChatColor.WHITE + s.get("total_purchased") + "\n"
                + ChatColor.YELLOW + "Конвертировано в ВК: " + ChatColor.WHITE + s.get("total_converted") + "\n"
                + ChatColor.YELLOW + "Истекло: " + ChatColor.WHITE + s.get("total_expired") + "\n"
                + ChatColor.YELLOW + "Конверсия ВК: " + ChatColor.WHITE + s.get("conversion_rate");
    }

    // ═══════════════════════════════════════
    // СПИСОК ВЛАДЕЛЬЦЕВ (для /donate pass list)
    // ═══════════════════════════════════════

    public List<PassHolder> getActivePassHolders() {
        return passHolders.values().stream()
                .filter(h -> !h.isExpired())
                .sorted((a, b) -> Long.compare(a.getExpiryDate(), b.getExpiryDate()))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ
    // ═══════════════════════════════════════

    private int getLinkedVkId(Player p) {
        try {
            return VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        } catch (Exception e) { return -1; }
    }

    public void shutdown() {
        savePassData();
    }

    public void reloadConfig() {
        loadConfig();
    }
}
