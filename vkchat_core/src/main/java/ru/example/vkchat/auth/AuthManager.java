package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;

public class AuthManager {
    private final VKChatPlugin plugin;

    // Файловое хранилище привязок UUID↔VK
    private final LinkStorage linkStorage;

    // Новые менеджеры
    private final SessionManager sessionManager;
    private final TwoFactorManager twoFactorManager;

    private final Map<UUID, String> linkCodes = new ConcurrentHashMap<>();
    private final Map<String, Integer> codeToVk = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loggedIn = new ConcurrentHashMap<>();
    private final Map<UUID, String> await2fa = new ConcurrentHashMap<>();
    private final Map<UUID, Long> await2faExpiry = new ConcurrentHashMap<>();      // 2FA код истекает через 5 минут
    private final Map<UUID, Integer> await2faAttempts = new ConcurrentHashMap<>();  // Попытки ввода 2FA
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockouts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> frozenAccounts = new ConcurrentHashMap<>();    // Замороженные аккаунты
    private final Map<UUID, java.util.List<String>> loginHistory = new ConcurrentHashMap<>(); // История входов (потокобезопасная)

    private static final long TWO_FA_EXPIRY_MS = 5 * 60 * 1000L; // 5 минут
    private static final int MAX_2FA_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 минут

    public AuthManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.linkStorage = new LinkStorage(plugin);
        this.sessionManager = new SessionManager(plugin);
        this.twoFactorManager = new TwoFactorManager(plugin);
        startCleanupTask();
    }

    // ═══ ГЕТТЕРЫ НОВЫХ МЕНЕДЖЕРОВ ═══

    public SessionManager getSessionManager() { return sessionManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }
    public LinkStorage getLinkStorage() { return linkStorage; }

    // ═══ МЕТОДЫ СИНХРОНИЗАЦИИ С SESSIONMANAGER ═══

    /**
     * Установить состояние loggedIn (для синхронизации с SessionManager)
     */
    public void setLoggedIn(UUID uuid, boolean value) {
        loggedIn.put(uuid, value);
    }

    /**
     * Установить 2FA ожидание (для синхронизации с SessionManager)
     */
    public void setAwait2fa(UUID uuid, String code) {
        await2fa.put(uuid, code);
        await2faExpiry.put(uuid, System.currentTimeMillis() + TWO_FA_EXPIRY_MS);
        await2faAttempts.put(uuid, 0);
    }

    /**
     * [FIX] Периодическая очистка неактивных данных для предотвращения утечки памяти
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupInactiveData();
        }, 12000L, 12000L); // Каждые 10 минут
    }

    private void cleanupInactiveData() {
        long now = System.currentTimeMillis();
        long maxInactive = 3600000; // 1 час

        // Очищаем lockouts которые истекли
        lockouts.entrySet().removeIf(e -> now - e.getValue() > LOCKOUT_DURATION_MS);

        // Очищаем await2faExpiry которые истекли
        await2faExpiry.entrySet().removeIf(e -> now - e.getValue() > TWO_FA_EXPIRY_MS);

        // Очищаем joinTimes для оффлайн игроков
        joinTimes.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем loggedIn для оффлайн игроков
        loggedIn.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем lastActivity для оффлайн игроков
        lastActivity.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем await2fa для оффлайн игроков
        await2fa.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем await2faAttempts для оффлайн игроков
        await2faAttempts.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем failedAttempts для оффлайн игроков
        failedAttempts.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // frozenAccounts не очищаем автоматически — только через unfreezeAccount()

        // Очищаем loginHistory для оффлайн игроков
        loginHistory.entrySet().removeIf(e -> {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });
    }

    public void save() {
        // SQL сохраняет данные в реальном времени, этот метод оставлен для совместимости с интерфейсом ядра
    }

    public void updateLastActivity(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
    }

    public void startSessionTimeoutTask() {
        int timeoutMinutes = plugin.getConfig().getInt("auth.session-timeout-minutes", 30);
        if (timeoutMinutes <= 0) return;
        long checkInterval = 60 * 20L; // every 60 seconds
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long timeoutMillis = timeoutMinutes * 60L * 1000L;
            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (isLoggedIn(p) && lastActivity.containsKey(uuid)) {
                    if (now - lastActivity.get(uuid) > timeoutMillis) {
                        logout(p);
                        lastActivity.remove(uuid);
                        p.sendMessage(org.bukkit.ChatColor.RED + "❌ Вы были автоматически отключены из-за неактивности (" + timeoutMinutes + " мин.).");
                        plugin.getLogger().info("[Session] Player " + p.getName() + " auto-logged out due to inactivity.");
                    }
                }
            }
        }, checkInterval, checkInterval);
    }

    public void onJoin(Player player) {
        joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
        loggedIn.put(player.getUniqueId(), false);
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        await2fa.remove(player.getUniqueId());

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM vkchat_auth WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    boolean isRegistered = rs.getString("password") != null;
                    boolean isLinked = rs.getInt("vk_id") != -1;

                    if (isRegistered && isLinked) {
                        String savedIp = rs.getString("last_ip");
                        long ipTime = rs.getLong("reg_date");
                        String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
                        
                        boolean isLocalIp = currentIp.equals("127.0.0.1") || currentIp.equals("0:0:0:0:0:0:0:1") || currentIp.equalsIgnoreCase("localhost");

                        // Стандартный авто-логин (работает только если IP не является локальным 127.0.0.1)
                        if (!isLocalIp && plugin.getConfig().getBoolean("auth.auto-login-ip", false)) {
                            if (savedIp != null && savedIp.equals(currentIp) && (System.currentTimeMillis() - ipTime) < 86400000L) {
                                loggedIn.put(player.getUniqueId(), true);
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_auto_login")));
                                });
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void onQuit(Player player) {
        joinTimes.remove(player.getUniqueId());
        loggedIn.remove(player.getUniqueId());
        linkCodes.remove(player.getUniqueId());
        lastActivity.remove(player.getUniqueId());
    }

    public String generateLinkCode(Player player) {
        if (isLinked(player)) return null;
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(999999));
        linkCodes.put(player.getUniqueId(), code);
        codeToVk.put(code, -1);
        return code;
    }

    public boolean tryLink(int vkId, String code, int replyPeer) {
        if (!codeToVk.containsKey(code)) {
            // Проверка на 2FA
            for (Map.Entry<UUID, String> entry : await2fa.entrySet()) {
                if (entry.getValue().equals(code)) {
                    // Проверка истечения кода
                    if (is2faExpired(entry.getKey())) {
                        await2fa.remove(entry.getKey());
                        await2faExpiry.remove(entry.getKey());
                        plugin.getVkManager().sendMessage(replyPeer, "❌ Код 2FA истёк (действителен 5 минут). Зайдите на сервер заново для получения нового кода.");
                        return false;
                    }

                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    if (p != null) {
                        int linkedVk = getLinkedVkId(p);
                        if (linkedVk == vkId) {
                            confirm2fa(entry.getKey());
                            plugin.getVkManager().sendMessage(replyPeer, plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_2fa_success")));
                            p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_2fa_success")));
                            return true;
                        } else {
                            // Неверный VK ID —.increment attempts
                            boolean locked = increment2faAttempts(entry.getKey());
                            if (locked) {
                                plugin.getVkManager().sendMessage(replyPeer, "🔒 Слишком много неудачных попыток! Аккаунт заблокирован на 5 минут.");
                            } else {
                                int remaining = MAX_2FA_ATTEMPTS - await2faAttempts.getOrDefault(entry.getKey(), 0);
                                plugin.getVkManager().sendMessage(replyPeer, "❌ Неверный код. Осталось попыток: " + remaining);
                            }
                        }
                    }
                }
            }
            return false;
        }
        
        // Anti-twink check via file
        if (plugin.getConfig().getBoolean("auth.twink-protection", true)) {
            if (linkStorage.isVkLinked(vkId)) {
                plugin.getVkManager().sendMessage(replyPeer, plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("vk_twink_blocked")));
                return false;
            }
        }
        
        UUID uuid = linkCodes.entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
                
        if (uuid != null) {
            // Сохраняем в файл
            linkStorage.link(uuid, vkId);

            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("link_success")));
                p.sendMessage("");
                
                net.md_5.bungee.api.chat.TextComponent vkLink = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', " &a&l[ПЕРЕЙТИ В ГРУППУ] ")
                );
                vkLink.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL,
                        plugin.getConfig().getString("vk.group-link", "https://vk.com/")));
                
                net.md_5.bungee.api.chat.TextComponent chatLink = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e&l[ПЕРЕЙТИ В БЕСЕДУ]\n")
                );
                chatLink.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL,
                        plugin.getConfig().getString("vk.chat-invite-link", "https://vk.com/")));
                
                vkLink.addExtra(chatLink);
                p.spigot().sendMessage(vkLink);
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                
                // Начисляем 1000 стартовой репутации ВК при первой связке!
                plugin.getReputationManager().addPoints(vkId, 1000);
                p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Вам начислена стартовая репутация в размере 1000 очков ВК!");

                // Автоматически добавляем в вайтлист
                if (plugin.getConfig().getBoolean("auth.auto-whitelist", true)) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                            offlinePlayer.setWhitelisted(true);
                            p.sendMessage(org.bukkit.ChatColor.GREEN + "✅ Вы добавлены в белый список сервера!");
                            plugin.getLogger().info("[Whitelist] " + p.getName() + " автоматически добавлен в вайтлист после привязки ВК.");
                        } catch (Exception e) {
                            plugin.getLogger().warning("[Whitelist] Не удалось добавить " + p.getName() + " в вайтлист: " + e.getMessage());
                        }
                    });
                }
                
                if (plugin.getConfig().getBoolean("bonus.first-link-enabled", true)) {
                    p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("bonus_received")));
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        for (String cmd : plugin.getConfig().getStringList("bonus.commands")) {
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("{player}", p.getName()));
                        }
                    });
                }
            }

            String helpMsg = "✅ Аккаунт Minecraft успешно привязан!\n\n" +
                             " Доступные команды в беседе:\n" +
                             " !online - Кто сейчас играет на сервере\n" +
                             " !stats - Статистика заходов\n" +
                             " !status - Статус сервера\n" +
                             " !топ - Топ игроков сервера\n" +
                             " !топреп - Топ богачей чата\n" +
                             " !профиль - Твоя игровая статистика\n" +
                             " !рейтинг - Узнать свою репутацию\n" +
                             " !бонус - Ежедневный бонус\n" +
                             " !анекдот - Случайная шутка\n" +
                             " !сейф - Взлом сейфа\n" +
                             " !код <число> - Подобрать код к сейфу\n" +
                             " !казино <ставка> - Рулетка на очки\n" +
                             " !промо <код> - Активировать код\n" +
                             " !помощь - Это меню";
            plugin.getVkManager().sendMessage(replyPeer, helpMsg);

            Player linkedPlayer = plugin.getServer().getPlayer(uuid);
            if (linkedPlayer != null) {
                try {
                    ru.example.vkchat.api.events.VKPlayerLinkEvent event = new ru.example.vkchat.api.events.VKPlayerLinkEvent(linkedPlayer, vkId);
                    org.bukkit.Bukkit.getPluginManager().callEvent(event);
                } catch (Exception ignored) {}
            }

            linkCodes.remove(uuid);
            codeToVk.remove(code);
            return true;
        }
        return false;
    }

    public boolean isWaiting2fa(Player p) {
        // Проверяем новую TwoFactorManager систему
        if (plugin.getTwoFactorManager() != null && plugin.getTwoFactorManager().isWaiting2fa(p.getUniqueId())) {
            return true;
        }
        // Fallback на старую систему
        cleanupExpired2fa();
        return await2fa.containsKey(p.getUniqueId()) && !is2faExpired(p.getUniqueId());
    }

    public boolean isWaiting2fa(UUID uuid) {
        // Проверяем новую TwoFactorManager систему
        if (plugin.getTwoFactorManager() != null && plugin.getTwoFactorManager().isWaiting2fa(uuid)) {
            return true;
        }
        // Fallback на старую систему
        cleanupExpired2fa();
        return await2fa.containsKey(uuid) && !is2faExpired(uuid);
    }

    public String getPending2faCode(UUID uuid) {
        if (is2faExpired(uuid)) {
            await2fa.remove(uuid);
            await2faExpiry.remove(uuid);
            return null;
        }
        return await2fa.get(uuid);
    }

    public Iterable<Map.Entry<UUID, String>> getAwait2faEntries() {
        cleanupExpired2fa();
        return new ArrayList<>(await2fa.entrySet());
    }

    public void confirm2fa(UUID uuid) {
        await2fa.remove(uuid);
        await2faExpiry.remove(uuid);
        await2faAttempts.remove(uuid);
        loggedIn.put(uuid, true);
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            saveIp(p);
            addLoginHistory(uuid, p.getAddress().getAddress().getHostAddress());
        }
    }

    // ==================== 2FA ИСТЕЧЕНИЕ И ОДНОРАЗОВОСТЬ ====================

    /**
     * Проверяет, истёк ли код 2FA (5 минут).
     */
    public boolean is2faExpired(UUID uuid) {
        Long expiry = await2faExpiry.get(uuid);
        if (expiry == null) return true; // Нет записи = истёк/не существует
        return System.currentTimeMillis() > expiry;
    }

    /**
     * Очищает истёкшие коды 2FA.
     */
    public void cleanupExpired2fa() {
        long now = System.currentTimeMillis();
        await2faExpiry.entrySet().removeIf(e -> now > e.getValue());
        await2fa.entrySet().removeIf(e -> !await2faExpiry.containsKey(e.getKey()));
    }

    /**
     * Увеличивает счётчик неудачных попыток 2FA.
     * @return true если заблокирован (превышен лимит)
     */
    public boolean increment2faAttempts(UUID uuid) {
        int attempts = await2faAttempts.getOrDefault(uuid, 0) + 1;
        await2faAttempts.put(uuid, attempts);
        if (attempts >= MAX_2FA_ATTEMPTS) {
            await2fa.remove(uuid);
            await2faExpiry.remove(uuid);
            await2faAttempts.remove(uuid);
            lockouts.put(uuid, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
            return true;
        }
        return false;
    }

    /**
     * Проверяет, заблокирован ли аккаунт из-за неудачных попыток.
     */
    public boolean isAccountLocked(UUID uuid) {
        Long lockoutExpiry = lockouts.get(uuid);
        if (lockoutExpiry == null) return false;
        if (System.currentTimeMillis() > lockoutExpiry) {
            lockouts.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Получает оставшееся время блокировки в секундах.
     */
    public long getLockoutRemaining(UUID uuid) {
        Long lockoutExpiry = lockouts.get(uuid);
        if (lockoutExpiry == null) return 0;
        long remaining = (lockoutExpiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // ==================== ЗАМОРОЗКА АККАУНТА ====================

    /**
     * Замораживает аккаунт (нельзя войти).
     */
    public void freezeAccount(UUID uuid) {
        frozenAccounts.put(uuid, true);
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.kickPlayer(org.bukkit.ChatColor.RED + "🔒 Ваш аккаунт заморожен администратором!");
        }
    }

    /**
     * Размораживает аккаунт.
     */
    public void unfreezeAccount(UUID uuid) {
        frozenAccounts.remove(uuid);
    }

    /**
     * Проверяет, заморожен ли аккаунт.
     */
    public boolean isAccountFrozen(UUID uuid) {
        return frozenAccounts.getOrDefault(uuid, false);
    }

    // ==================== ИСТОРИЯ ВХОДОВ ====================

    /**
     * Добавляет запись в историю входов.
     */
    public void addLoginHistory(UUID uuid, String ip) {
        loginHistory.putIfAbsent(uuid, new java.util.concurrent.CopyOnWriteArrayList<>());
        java.util.List<String> history = loginHistory.get(uuid);
        String entry = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date()) + " | " + ip;
        history.add(0, entry);
        while (history.size() > 10) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * Получает историю входов.
     */
    public java.util.List<String> getLoginHistory(UUID uuid) {
        return loginHistory.getOrDefault(uuid, new ArrayList<>());
    }

    // ==================== БЛОКИРОВКА ВХОДА ====================

    public boolean blockLoginByCode(String code) {
        UUID victimUuid = null;
        for (Map.Entry<UUID, String> entry : await2fa.entrySet()) {
            if (entry.getValue().equals(code)) {
                victimUuid = entry.getKey();
                break;
            }
        }
        if (victimUuid != null) {
            final UUID fUuid = victimUuid;
            await2fa.remove(victimUuid);
            await2faExpiry.remove(victimUuid);
            await2faAttempts.remove(victimUuid);
            
            // Кикаем игрока на главном потоке
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(fUuid);
                if (player != null && player.isOnline()) {
                    player.kickPlayer(org.bukkit.ChatColor.RED + "❌ Вход заблокирован владельцем аккаунта через ВКонтакте!");
                }
            });
            return true;
        }
        return false;
    }

    public boolean isValidCode(String code) {
        return codeToVk.containsKey(code);
    }
    
    public boolean is2faCode(String code) {
        return await2fa.containsValue(code);
    }
    
    public int getLinkedVkId(UUID uuid) {
        return linkStorage.getVkId(uuid);
    }
    
    public int getLinkedVkId(Player player) {
        return getLinkedVkId(player.getUniqueId());
    }

    public boolean isLinked(UUID uuid) {
        return getLinkedVkId(uuid) != -1;
    }

    public boolean isLinked(Player player) {
        return isLinked(player.getUniqueId());
    }

    public boolean isLinkedByVkId(int vkId) {
        return linkStorage.isVkLinked(vkId);
    }

    public void unlink(Player player) {
        linkStorage.unlink(player.getUniqueId());
        loggedIn.put(player.getUniqueId(), false);
    }

    public boolean isLoggedIn(Player player) {
        return loggedIn.getOrDefault(player.getUniqueId(), false);
    }

    public boolean isRegistered(Player player) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT password FROM vkchat_auth WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("password") != null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void register(Player player, String password) {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String ip = player.getAddress().getAddress().getHostAddress();
        long time = System.currentTimeMillis();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("UPDATE vkchat_auth SET password = ?, last_ip = ?, reg_date = ? WHERE uuid = ?");
                ps.setString(1, hash);
                ps.setString(2, ip);
                ps.setLong(3, time);
                ps.setString(4, player.getUniqueId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        
        loggedIn.put(player.getUniqueId(), true);
        
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
        player.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_register_success")));
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
        
        int vkId = getLinkedVkId(player);
        if (vkId != -1) {
            String msg = plugin.getConfigManager().getMessage("vk_player_join").replace("{player}", player.getName());
            plugin.getVkManager().sendToMainChat(msg);
        }
    }

    public boolean login(Player player, String password) {
        UUID uuid = player.getUniqueId();
        
        // Проверка локаута
        if (lockouts.containsKey(uuid)) {
            long left = (lockouts.get(uuid) - System.currentTimeMillis()) / 1000L;
            if (left > 0) {
                player.sendMessage(org.bukkit.ChatColor.RED + "❌ Ваш аккаунт заблокирован из-за подбора пароля! Попробуйте через " + left + " сек.");
                return false;
            } else {
                lockouts.remove(uuid);
                failedAttempts.remove(uuid);
            }
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT password FROM vkchat_auth WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next() && rs.getString("password") != null) {
                String hash = rs.getString("password");
                if (BCrypt.checkpw(password, hash)) {
                    loggedIn.put(uuid, true);
                    failedAttempts.remove(uuid); // Сбрасываем попытки при успехе
                    saveIp(player);
                    
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                    player.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_login_success")));
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                    
                    int vkId = getLinkedVkId(player);
                    if (vkId != -1) {
                        String ip = player.getAddress().getAddress().getHostAddress();
                        plugin.getVkManager().sendMessage(vkId, "🔔 Безопасность: Выполнен успешный вход в ваш аккаунт '" + player.getName() + "' с IP: " + ip + ".\nЕсли это были не вы, немедленно смените пароль!");

                        String msg = plugin.getConfigManager().getMessage("vk_player_join").replace("{player}", player.getName());
                        plugin.getVkManager().sendToMainChat(msg);
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Неверный пароль
        int attempts = failedAttempts.getOrDefault(uuid, 0) + 1;
        failedAttempts.put(uuid, attempts);

        int vkId = getLinkedVkId(player);
        String ip = player.getAddress().getAddress().getHostAddress();

        if (attempts >= 3) {
            lockouts.put(uuid, System.currentTimeMillis() + 300000L); // 5 минут локаут
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.kickPlayer(org.bukkit.ChatColor.RED + "❌ Превышено число попыток входа!\nВаш аккаунт заблокирован на 5 минут.");
            });

            if (vkId != -1) {
                plugin.getVkManager().sendMessage(vkId, "🚨 ПРЕДУПРЕЖДЕНИЕ: Обнаружен подбор пароля к вашему аккаунту '" + player.getName() + "'!\nБыло введено 3 неверных пароля подряд с IP: " + ip + ".\n\nВход временно заблокирован на 5 минут.");
            }
        } else {
            player.sendMessage(org.bukkit.ChatColor.RED + "❌ Неверный пароль! Попыток осталось: " + (3 - attempts));
            if (vkId != -1) {
                plugin.getVkManager().sendMessage(vkId, "⚠️ Безопасность: Неудачная попытка входа в ваш аккаунт '" + player.getName() + "' с IP: " + ip + " (введен неверный пароль).");
            }
        }

        return false;
    }

    private void saveIp(Player player) {
        if (plugin.getConfig().getBoolean("auth.auto-login-ip", true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("UPDATE vkchat_auth SET last_ip = ?, reg_date = ? WHERE uuid = ?");
                    ps.setString(1, player.getAddress().getAddress().getHostAddress());
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, player.getUniqueId().toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void logout(Player player) {
        loggedIn.put(player.getUniqueId(), false);
    }

    public void changePassword(Player player, String newPass) {
        String hash = BCrypt.hashpw(newPass, BCrypt.gensalt(12));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("UPDATE vkchat_auth SET password = ? WHERE uuid = ?");
                ps.setString(1, hash);
                ps.setString(2, player.getUniqueId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public long getJoinTime(Player player) {
        return joinTimes.getOrDefault(player.getUniqueId(), 0L);
    }

    public boolean isFullyAuthorized(Player p) {
        if (isLinked(p) && !isWaiting2fa(p)) return true;
        if (p.hasPermission("vkchat.pass")) return true;
        return false;
    }
}
