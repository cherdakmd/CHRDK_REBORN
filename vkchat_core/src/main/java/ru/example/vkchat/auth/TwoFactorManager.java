package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер двухфакторной аутентификации
 * Управляет кодами 2FA, попытками, блокировками и тоглом вкл/выкл
 */
public class TwoFactorManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, String> pendingCodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> codeExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockouts = new ConcurrentHashMap<>();

    /** Игроки, отключившие 2FA для себя */
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L;

    private final File disabledFile;

    public TwoFactorManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.disabledFile = new File(plugin.getDataFolder(), "disabled_2fa.txt");
        loadDisabledPlayers();
        startCleanupTask();
    }

    // ═══ TOGGLE 2FA ═══

    /**
     * Включить/выключить 2FA для игрока
     */
    public void set2faEnabled(UUID uuid, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(uuid);
        } else {
            disabledPlayers.add(uuid);
        }
        saveDisabledPlayers();
    }

    /**
     * Отключена ли 2FA для игрока
     */
    public boolean is2faDisabled(UUID uuid) {
        return disabledPlayers.contains(uuid);
    }

    /**
     * Разрешён ли тогл 2FA в конфиге
     */
    public boolean isToggleAllowed() {
        return plugin.getConfig().getBoolean("auth.2fa.allow-toggle", true);
    }

    private void loadDisabledPlayers() {
        if (!disabledFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(disabledFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        disabledPlayers.add(UUID.fromString(line));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось загрузить disabled_2fa.txt: " + e.getMessage());
        }
    }

    private void saveDisabledPlayers() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(disabledFile))) {
                for (UUID uuid : disabledPlayers) {
                    bw.write(uuid.toString());
                    bw.newLine();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сохранить disabled_2fa.txt: " + e.getMessage());
            }
        });
    }

    // ═══ TRIGGER / CONFIRM ═══

    /**
     * Сгенерировать и отправить 2FA код
     */
    public boolean trigger2fa(Player p, int vkId) {
        if (isLocked(p.getUniqueId())) {
            long remaining = getLockoutRemaining(p.getUniqueId());
            p.sendMessage("§c🔒 Слишком много попыток. Подожди " + (remaining / 1000) + " сек.");
            return false;
        }

        int codeLength = plugin.getConfig().getInt("auth.2fa.code-length", 4);
        int min = (int) Math.pow(10, codeLength - 1);
        int max = (int) Math.pow(10, codeLength) - 1;
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(min, max));

        pendingCodes.put(p.getUniqueId(), code);
        long expiryMs = plugin.getConfig().getLong("auth.2fa.expiry-minutes", 5) * 60 * 1000;
        codeExpiry.put(p.getUniqueId(), System.currentTimeMillis() + expiryMs);
        attempts.put(p.getUniqueId(), 0);

        try {
            String message = "🔐 Код для входа: " + code + "\n" +
                           "Введи его в чате игры командой /2fa " + code + "\n" +
                           "Код действует " + plugin.getConfig().getInt("auth.2fa.expiry-minutes", 5) + " мин.";
            plugin.getVkManager().sendMessage(vkId, message);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось отправить 2FA код в ВК: " + e.getMessage());
            return false;
        }

        p.sendMessage("§b📱 Код отправлен в ЛС ВК! Проверь личные сообщения.");
        p.sendMessage("§7Введи в чате: /2fa <код>");
        String groupLink = plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn");
        p.sendMessage("§7Нет кода? Открой §bЛС группы§7: " + groupLink);

        net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', " &a&l▶ НАЖМИ ЧТОБЫ ОТКРЫТЬ ЛС ГРУППЫ ◀"));
        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, groupLink));
        p.spigot().sendMessage(link);

        return true;
    }

    /**
     * Попробовать подтвердить 2FA код
     */
    public TwoFactorResult confirm2fa(UUID uuid, String code) {
        if (isLocked(uuid)) {
            return TwoFactorResult.LOCKED;
        }

        String expectedCode = pendingCodes.get(uuid);
        if (expectedCode == null) {
            return TwoFactorResult.NO_PENDING;
        }

        Long expiry = codeExpiry.get(uuid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            cleanup(uuid);
            return TwoFactorResult.EXPIRED;
        }

        if (!expectedCode.equals(code)) {
            int currentAttempts = attempts.getOrDefault(uuid, 0) + 1;
            attempts.put(uuid, currentAttempts);

            if (currentAttempts >= MAX_ATTEMPTS) {
                lockouts.put(uuid, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
                cleanup(uuid);
                return TwoFactorResult.LOCKED;
            }

            return TwoFactorResult.WRONG_CODE;
        }

        cleanup(uuid);
        return TwoFactorResult.SUCCESS;
    }

    // ═══ HELPERS ═══

    public boolean isWaiting2fa(UUID uuid) {
        return pendingCodes.containsKey(uuid);
    }

    /**
     * Получить записи ожидающих 2FA (для VK-боковой проверки)
     */
    public Set<Map.Entry<UUID, String>> getPendingCodeEntries() {
        return pendingCodes.entrySet();
    }

    public long getLockoutRemaining(UUID uuid) {
        Long lockoutEnd = lockouts.get(uuid);
        if (lockoutEnd == null) return 0;
        return Math.max(0, lockoutEnd - System.currentTimeMillis());
    }

    public boolean isLocked(UUID uuid) {
        Long lockoutEnd = lockouts.get(uuid);
        if (lockoutEnd == null) return false;
        if (System.currentTimeMillis() > lockoutEnd) {
            lockouts.remove(uuid);
            return false;
        }
        return true;
    }

    public int getRemainingAttempts(UUID uuid) {
        int used = attempts.getOrDefault(uuid, 0);
        return Math.max(0, MAX_ATTEMPTS - used);
    }

    public boolean isCodeExpired(UUID uuid) {
        Long expiry = codeExpiry.get(uuid);
        return expiry == null || System.currentTimeMillis() > expiry;
    }

    public String getPendingCode(UUID uuid) {
        return pendingCodes.get(uuid);
    }

    private void cleanup(UUID uuid) {
        pendingCodes.remove(uuid);
        codeExpiry.remove(uuid);
        attempts.remove(uuid);
    }

    public void onPlayerQuit(UUID uuid) {
        cleanup(uuid);
    }

    public int getPendingCount() {
        return pendingCodes.size();
    }

    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();

            codeExpiry.entrySet().removeIf(entry -> {
                if (now > entry.getValue()) {
                    pendingCodes.remove(entry.getKey());
                    attempts.remove(entry.getKey());
                    return true;
                }
                return false;
            });

            lockouts.entrySet().removeIf(entry -> now > entry.getValue());
        }, 6000L, 6000L);
    }

    public enum TwoFactorResult {
        SUCCESS,
        WRONG_CODE,
        EXPIRED,
        LOCKED,
        NO_PENDING
    }
}
