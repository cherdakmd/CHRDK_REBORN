package ru.example.vkchat.listeners;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.auth.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Обновлённый слушатель авторизации
 * Интегрирован с менеджерами: SessionManager, MembershipManager, TwoFactorManager
 */
public class AuthListener implements Listener {
    private final VKChatPlugin plugin;
    private final Map<UUID, List<Location>> safetyPlatforms = new HashMap<>();

    public AuthListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // ═══ 0. СИНХРОНИЗАЦИЯ СО СТАРЫМ AUTHMANAGER ═══
        plugin.getAuthManager().onJoin(p);

        // ═══ 1. СОЗДАЁМ СЕССИЮ ═══
        SessionManager.PlayerSession session = plugin.getSessionManager().createSession(p);

        // ═══ 2. ПРОВЕРЯЕМ ПРИВЯЗКУ ВК ═══
        int vkId = plugin.getAuthManager().getLinkedVkId(p);

        if (vkId != -1) {
            // ВК ПРИВЯЗАН
            session.vkId = vkId;

            // Проверяем членство в группе ВК
            if (plugin.getConfig().getBoolean("auth.link.require-membership", true)) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean isMember = plugin.getMembershipManager().isFullMember(vkId);
                    if (!isMember) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String kickMsg = plugin.getMembershipManager().getMembershipErrorMessage(vkId);
                            p.kickPlayer(kickMsg);
                        });
                        return;
                    }

                    // Членство подтверждено — запускаем 2FA
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        start2faFlow(p, vkId, session);
                    });
                });
            } else {
                // Без проверки членства — сразу 2FA
                start2faFlow(p, vkId, session);
            }
        } else {
            // ВК НЕ ПРИВЯЗАН — пускаем и показываем инструкцию
            p.sendMessage("§c⚠ Для игры нужна привязка ВК!");
            p.sendMessage("§7➊ Вступи в группу: §b" + plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn"));
            p.sendMessage("§7➋ Введи §a/vklink §7— получишь код");
            p.sendMessage("§7➌ Отправь код в беседу ВК боту");
            p.sendMessage("§7➍ Подтверди вход через /2fa <код>");
            p.sendMessage("§7➎ Зарегистрируйся: §a/register <пароль>");
            p.sendTitle("§c⚠ Привяжи ВК!", "§7/vklink — получить код", 10, 70, 10);
            placeSafetyPlatform(p);
            p.setVelocity(new Vector(0, 0, 0));
        }

        // ═══ 3. БЕЗОПАСНАЯ ПЛАТФОРМА ═══
        if (plugin.getConfig().getBoolean("auth.safety-platform", true)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (!plugin.getAuthManager().isFullyAuthorized(p)) {
                    placeSafetyPlatform(p);
                    p.setVelocity(new Vector(0, 0, 0));
                    p.setFallDistance(0);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!p.isOnline() || plugin.getAuthManager().isFullyAuthorized(p)) {
                                removeSafetyPlatform(p);
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 10L, 10L);
                }
            }, 1L);
        }

        // ═══ 4. ПОКАЗЫВАЕМ ИНСТРУКЦИИ ═══
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !plugin.getAuthManager().isFullyAuthorized(p)) {
                sendJoinInstructions(p);
            }
        }, 15L);
    }

    /**
     * Запуск 2FA потока
     */
    private void start2faFlow(Player p, int vkId, SessionManager.PlayerSession session) {
        // Проверяем, нужно ли 2FA
        if (plugin.getConfig().getBoolean("auth.2fa.enabled", true)) {
            boolean sent = plugin.getTwoFactorManager().trigger2fa(p, vkId);
            if (sent) {
                session.state = SessionManager.SessionState.WAITING_2FA;
            } else {
                // Не удалось отправить 2FA — авторизуем напрямую
                authorizePlayer(p, session);
            }
        } else {
            // 2FA отключен — сразу авторизуем
            authorizePlayer(p, session);
        }
    }

    /**
     * Авторизация игрока (синхронизация обоих систем)
     */
    private void authorizePlayer(Player p, SessionManager.PlayerSession session) {
        session.state = SessionManager.SessionState.LOGGED_IN;
        // Синхронизация со старым AuthManager
        plugin.getAuthManager().setLoggedIn(p.getUniqueId(), true);
        plugin.getAuthManager().updateLastActivity(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        removeSafetyPlatform(p);
        plugin.getAuthManager().onQuit(p);
        // Очистка новых менеджеров
        plugin.getSessionManager().destroySession(p.getUniqueId());
        plugin.getTwoFactorManager().onPlayerQuit(p.getUniqueId());
    }

    // ═══ БЛОКИРОВКА ДЕЙСТВИЙ ДО АВТОРИЗАЦИИ ═══

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        // Разрешаем только если полностью авторизован
        if (!plugin.getAuthManager().isFullyAuthorized(p)) {
            if (e.getTo() == null) return;
            // Блокируем движение (разрешаем поворот головы)
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
                p.teleport(e.getFrom());
            }
        } else {
            plugin.getAuthManager().updateLastActivity(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) {
            e.setCancelled(true);
        } else {
            plugin.getAuthManager().updateLastActivity(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (!plugin.getAuthManager().isFullyAuthorized((Player) e.getEntity())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            if (!plugin.getAuthManager().isFullyAuthorized((Player) e.getDamager())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) {
            e.setCancelled(true);
            sendJoinInstructions(e.getPlayer());
            return;
        }
        plugin.getAuthManager().updateLastActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().split(" ")[0].toLowerCase();
        // Разрешаем команды авторизации и привязки
        if (cmd.equals("/vklink") || cmd.equals("/register") || cmd.equals("/login") || cmd.equals("/2fa") ||
            cmd.equals("/menu") || cmd.equals("/help") || cmd.equals("/vk")) return;

        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) {
            e.setCancelled(true);
            sendJoinInstructions(e.getPlayer());
        }
    }

    /**
     * Показать инструкции при входе
     */
    private void sendJoinInstructions(Player p) {
        // Проверяем, ожидает ли 2FA
        if (plugin.getTwoFactorManager() != null && plugin.getTwoFactorManager().isWaiting2fa(p.getUniqueId())) {
            p.sendMessage("");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7⌛ Код отправлен в &bЛС ВК&7. Введи &e/2fa <код>&7."));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Нет кода? Открой &bЛС группы&7:"));
            TextComponent link = new TextComponent(ChatColor.translateAlternateColorCodes('&',
                    " &a&l▶ НАЖМИ ЧТОБЫ ОТКРЫТЬ ЛС ГРУППЫ ◀"));
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                    plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn")));
            p.spigot().sendMessage(link);
            p.sendMessage("");
            return;
        }

        if (!plugin.getAuthManager().isLinked(p)) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &fПривет! Для игры нужна &bпривязка ВК&f."));
            TextComponent msg = new TextComponent(ChatColor.translateAlternateColorCodes('&', " &a&l▶ НАЖМИ СЮДА ДЛЯ ПРИВЯЗКИ ◀"));
            msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vklink"));
            p.spigot().sendMessage(msg);
        } else {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &a✅ ВК привязан! Проверь ЛС ВК."));
        }
        p.sendMessage("");
    }

    // ═══ БЕЗОПАСНАЯ ПЛАТФОРМА ═══

    private void placeSafetyPlatform(Player p) {
        removeSafetyPlatform(p);

        Location loc = p.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        int cx = loc.getBlockX();
        int y = loc.getBlockY() - 1;
        int cz = loc.getBlockZ();
        if (y < 0) y = 0;

        List<Location> placed = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = world.getBlockAt(cx + dx, y, cz + dz);
                if (isAir(b.getType())) {
                    b.setType(Material.BARRIER);
                    placed.add(b.getLocation());
                }
            }
        }

        if (!placed.isEmpty()) {
            safetyPlatforms.put(p.getUniqueId(), placed);
        }
    }

    private void removeSafetyPlatform(Player p) {
        List<Location> placed = safetyPlatforms.remove(p.getUniqueId());
        if (placed == null || placed.isEmpty()) return;

        for (Location loc : placed) {
            if (loc.getWorld() == null) continue;
            Block b = loc.getBlock();
            if (b.getType() == Material.BARRIER) {
                b.setType(Material.AIR);
            }
        }
    }

    private boolean isAir(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }
}
