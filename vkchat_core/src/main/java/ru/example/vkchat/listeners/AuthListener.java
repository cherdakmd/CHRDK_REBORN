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
 * Интегрирован с новыми менеджерами: SessionManager, PassManager, MembershipManager, TwoFactorManager
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
            // ВК НЕ ПРИВЯЗАН — проверяем проходку
            if (plugin.getPassManager().hasPass(p.getUniqueId())) {
                // ЕСТЬ ПРОХОДКА — разрешаем вход
                session.hasPass = true;
                session.state = SessionManager.SessionState.PASS_HOLDER;
                long remaining = plugin.getPassManager().getPassRemainingDays(p.getUniqueId());
                p.sendMessage("§a✅ Добро пожаловать! У тебя есть проходка.");
                p.sendMessage("§7Осталось: §e" + remaining + " дней");

                if (remaining <= 3) {
                    p.sendMessage("§e⚠️ Проходка скоро истекает! Привяжи ВК или продли.");
                }
            } else {
                // НЕТ ВК, НЕТ ПРОХОДКИ — КИК
                String kickMsg = "§c❌ Для игры необходимо привязать ВКонтакте!\n\n" +
                               "§eПривязка ВК:\n" +
                               "§71. Вступи в группу: " + plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn") + "\n" +
                               "§72. Зайди на сервер и введи /vklink\n" +
                               "§73. Отправь код в беседу ВК\n\n" +
                               "§eПокупка проходки:\n" +
                               "§7Донат 500р на DonatePay с указанием никнейма\n" +
                               "§7Ссылка: https://donatepay.ru/don/dedworkshop\n\n" +
                               "§7После покупки перезайди на сервер.";
                p.kickPlayer(kickMsg);
                return;
            }
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
                // Не удалось отправить 2FA — пропускаем
                session.state = SessionManager.SessionState.LOGGED_IN;
            }
        } else {
            // 2FA отключен — сразу авторизуем
            session.state = SessionManager.SessionState.LOGGED_IN;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeSafetyPlatform(e.getPlayer());
        plugin.getAuthManager().onQuit(e.getPlayer());
    }

    // ═══ БЛОКИРОВКА ДЕЙСТВИЙ ДО АВТОРИЗАЦИИ ═══

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getAuthManager().isFullyAuthorized(e.getPlayer())) {
            if (e.getTo() == null) return;
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.getPlayer().teleport(e.getFrom());
            }
        } else {
            plugin.getAuthManager().updateLastActivity(e.getPlayer().getUniqueId());
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
        // Разрешаем команды авторизации
        if (cmd.equals("/vklink") || cmd.equals("/register") || cmd.equals("/login") || cmd.equals("/2fa")) return;

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
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e&m================================================="));
            p.sendMessage("§e🔐 Код подтверждения отправлен в твои личные сообщения ВК!");
            p.sendMessage("§7Введи код в чат для подтверждения входа.");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e&m================================================="));
            p.sendMessage("");
            return;
        }

        p.sendMessage("");
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
        if (!plugin.getAuthManager().isLinked(p)) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &f &lПриветствуем на сервере!"));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &fДля игры необходимо привязать аккаунт к &bВКонтакте&f."));
            p.sendMessage("");
            TextComponent msg = new TextComponent(ChatColor.translateAlternateColorCodes('&', " &a&l▶ НАЖМИ СЮДА, ЧТОБЫ ПОЛУЧИТЬ КОД ◀"));
            msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vklink"));
            p.spigot().sendMessage(msg);
        } else if (!plugin.getAuthManager().isRegistered(p)) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &f✅ &lВК успешно привязан!"));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &fОстался последний шаг: &aпридумай надежный пароль&f."));
            p.sendMessage("");
            TextComponent msg = new TextComponent(ChatColor.translateAlternateColorCodes('&', " &a&l▶ НАЖМИ СЮДА ДЛЯ РЕГИСТРАЦИИ ◀"));
            msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/register "));
            p.spigot().sendMessage(msg);
        } else if (!plugin.getAuthManager().isLoggedIn(p)) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &f &lС возвращением!"));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', " &fПожалуйста, введи свой пароль для входа в игру."));
            p.sendMessage("");
            TextComponent msg = new TextComponent(ChatColor.translateAlternateColorCodes('&', " &e&l▶ НАЖМИ СЮДА ДЛЯ ВХОДА ◀"));
            msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/login "));
            p.spigot().sendMessage(msg);
        }
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
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
