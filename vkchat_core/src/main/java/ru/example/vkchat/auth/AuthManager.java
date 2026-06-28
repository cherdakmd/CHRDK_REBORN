package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;

public class AuthManager {
    private final VKChatPlugin plugin;

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
    private final Map<UUID, java.util.List<String>> loginHistory = new ConcurrentHashMap<>(); // История входов
    private final Map<UUID, Long> trustedDevices = new ConcurrentHashMap<>();       // Доверенные устройства (IP hash -> expiry)

    private static final long TWO_FA_EXPIRY_MS = 5 * 60 * 1000L; // 5 минут
    private static final int MAX_2FA_ATTEMPTS = 5;
    private static final int MAX_FAILED_LOGINS = 3;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 минут

    public AuthManager(VKChatPlugin plugin) {
        this.plugin = plugin;
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
                        String currentIp = player.getAddress().getAddress().getHostAddress();
                        
                        boolean isLocalIp = currentIp.equals("127.0.0.1") || currentIp.equals("0:0:0:0:0:0:0:1") || currentIp.equalsIgnoreCase("localhost");
                        boolean require2faAlways = plugin.getConfig().getBoolean("security.require-2fa-always", false);

                        // Проверка 2FA при входе (с нового IP, при локальном IP без форвардинга, или всегда если включено)
                        boolean trigger2fa = false;
                        if (plugin.getConfig().getBoolean("security.2fa-enabled", true)) {
                            if (require2faAlways) {
                                trigger2fa = true;
                            } else if (isLocalIp) {
                                // Безопасность: если IP локальный, мы не можем доверять ему, поэтому принудительно запрашиваем 2FA для защиты от взломов BungeeCord!
                                trigger2fa = true;
                            } else if (plugin.getConfig().getBoolean("security.require-on-new-ip", true)) {
                                if (savedIp != null && !savedIp.equals(currentIp)) {
                                    trigger2fa = true;
                                }
                            }
                        }

                        if (trigger2fa) {
                            // Проверка заморозки аккаунта
                            if (isAccountFrozen(player.getUniqueId())) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.kickPlayer(org.bukkit.ChatColor.RED + "🔒 Ваш аккаунт заморожен. Обратитесь к администрации.");
                                });
                                return;
                            }

                            // Проверка блокировки за неудачные попытки
                            if (isAccountLocked(player.getUniqueId())) {
                                long remaining = getLockoutRemaining(player.getUniqueId());
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.kickPlayer(org.bukkit.ChatColor.RED + "⏳ Аккаунт заблокирован на " + remaining + " сек. из-за неудачных попыток входа.");
                                });
                                return;
                            }

                            int codeLength = plugin.getConfig().getInt("security.code-length", 4);
                            StringBuilder codeBuilder = new StringBuilder();
                            for (int i = 0; i < codeLength; i++) {
                                codeBuilder.append(ThreadLocalRandom.current().nextInt(10));
                            }
                            String code = codeBuilder.toString();
                            await2fa.put(player.getUniqueId(), code);
                            await2faExpiry.put(player.getUniqueId(), System.currentTimeMillis() + TWO_FA_EXPIRY_MS);
                            await2faAttempts.put(player.getUniqueId(), 0);

                            int vkId = rs.getInt("vk_id");
                            String msg = "🛡️ Блокировка безопасности!\nМы заметили вход с нового или локального IP-адреса.\n\nТвой одноразовый код (2FA) для подтверждения в игре: " + code + "\n\n⏱ Код действителен 5 минут.\nНикому не сообщай этот код!";
                            
                            String kbJson = ru.example.vkchat.vk.VKKeyboardBuilder.twoFaKeyboard(code);
                            plugin.getVkManager().sendKeyboard(vkId, msg, kbJson);
                            
                            // Высылаем инструкцию игроку в чат
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                player.sendMessage("");
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &c&l🛡️ АКТИВИРОВАНА ДВУХФАКТОРНАЯ ЗА ЗАЩИТА (2FA)"));
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &fВам отправлен одноразовый код в ЛС ВКонтакте."));
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &fНапишите: &e/2fa <код> &fв игровой чат для входа!"));
                                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                                player.sendMessage("");
                            });
                            return;
                        }

                        // Стандартный авто-логин (работает только если IP не является локальным 127.0.0.1)
                        if (!isLocalIp && plugin.getConfig().getBoolean("auth.auto-login-ip", true)) {
                            if (savedIp != null && savedIp.equals(currentIp) && (System.currentTimeMillis() - ipTime) < 86400000L) {
                                loggedIn.put(player.getUniqueId(), true);
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_auto_login")));
                                    String msg = plugin.getConfigManager().getMessage("vk_player_join").replace("{player}", player.getName());
                                    plugin.getVkManager().sendToMainChat(msg);
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
        if (isFullyAuthorized(player)) {
            int vkId = getLinkedVkId(player);
            if (vkId != -1) {
                String msg = plugin.getConfigManager().getMessage("vk_player_quit").replace("{player}", player.getName());
                plugin.getVkManager().sendToMainChat(msg);
            }
        }
        
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
        
        // Anti-twink check via SQL
        if (plugin.getConfig().getBoolean("auth.twink-protection", true)) {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
                ps.setInt(1, vkId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    plugin.getVkManager().sendMessage(replyPeer, plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("vk_twink_blocked")));
                    return false;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        UUID uuid = linkCodes.entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
                
        if (uuid != null) {
            // Сохраняем в SQL
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    PreparedStatement ps;
                    if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                        ps = conn.prepareStatement("INSERT INTO vkchat_auth (uuid, vk_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE vk_id = ?");
                    } else {
                        ps = conn.prepareStatement("INSERT INTO vkchat_auth (uuid, vk_id) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET vk_id = ?");
                    }
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, vkId);
                    ps.setInt(3, vkId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("link_success")));
                p.sendMessage("");
                
                net.md_5.bungee.api.chat.TextComponent vkLink = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', " &a&l[ПЕРЕЙТИ В ГРУППУ] ")
                );
                vkLink.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, plugin.getConfig().getString("vk.group-link")));
                
                net.md_5.bungee.api.chat.TextComponent chatLink = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e&l[ПЕРЕЙТИ В БЕСЕДУ]\n")
                );
                chatLink.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, plugin.getConfig().getString("vk.chat-invite-link")));
                
                vkLink.addExtra(chatLink);
                p.spigot().sendMessage(vkLink);
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                
                // Начисляем 1000 стартовой репутации ВК при первой связке!
                plugin.getReputationManager().addPoints(vkId, 1000);
                p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Вам начислена стартовая репутация в размере 1000 очков ВК!");
                
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
                             " !рулетка - Сыграть в Русскую Рулетку\n" +
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
        cleanupExpired2fa();
        return await2fa.containsKey(p.getUniqueId()) && !is2faExpired(p.getUniqueId());
    }

    public boolean isWaiting2fa(UUID uuid) {
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
        if (expiry == null) return false;
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
        loginHistory.putIfAbsent(uuid, new ArrayList<>());
        java.util.List<String> history = loginHistory.get(uuid);
        String entry = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date()) + " | " + ip;
        history.add(0, entry);
        if (history.size() > 10) history.remove(history.size() - 1);
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
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT vk_id FROM vkchat_auth WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("vk_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
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
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
            ps.setInt(1, vkId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void unlink(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("UPDATE vkchat_auth SET vk_id = -1 WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
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
        if (!isLinked(p)) return false;
        if (plugin.getConfig().getBoolean("auth.require-auth", true)) {
            return isLoggedIn(p);
        }
        return true;
    }
}
