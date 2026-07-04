package ru.example.vkchatnations.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchat.VKChatPlugin;

import java.util.UUID;

public class PreventListener implements Listener {
    private final VKChatNationsPlugin plugin;

    public PreventListener(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAwaitingNationSelection(Player p) {
        // Игрок уже полностью зарегистрирован и вошел по ВК, но еще НЕ выбрал Нацию
        return VKChatPlugin.getInstance().getApi().isFullyAuthorized(p) && !plugin.getNationManager().hasNation(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to == null) return;

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                e.setTo(from);
                if (System.currentTimeMillis() % 3000 < 100) {
                    p.sendMessage(ChatColor.RED + "Выберите Нацию через /nation, чтобы начать движение!");
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "⚠️ Сначала выберите Нацию!");
            return;
        }
        // Переименование привата
        if (plugin.getNationManager().isAwaitingRename(p.getUniqueId())) {
            e.setCancelled(true);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("отмена")) {
                plugin.getNationManager().pollRenameClaim(p.getUniqueId());
                p.sendMessage(ChatColor.GRAY + "Переименование отменено.");
            } else {
                if (msg.length() > 32) msg = msg.substring(0, 32);
                String finalMsg = msg;
                ChunkClaim claim = plugin.getNationManager().pollRenameClaim(p.getUniqueId());
                if (claim != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        claim.setName(finalMsg);
                        plugin.getNationManager().saveAll();
                        p.sendMessage(ChatColor.GREEN + "✓ Приват переименован в: " + ChatColor.WHITE + finalMsg);
                    });
                }
            }
            return;
        }
        // Добавление доверенного
        if (plugin.getNationManager().isAwaitingTrustedAdd(p.getUniqueId())) {
            e.setCancelled(true);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("отмена")) {
                plugin.getNationManager().pollAddingTrusted(p.getUniqueId());
                p.sendMessage(ChatColor.GRAY + "Добавление отменено.");
            } else {
                Player target = Bukkit.getPlayer(msg);
                if (target == null) {
                    p.sendMessage(ChatColor.RED + "❌ Игрок '" + msg + "' не найден.");
                } else if (target.equals(p)) {
                    p.sendMessage(ChatColor.RED + "❌ Нельзя добавить себя.");
                } else {
                    ChunkClaim claim = plugin.getNationManager().pollAddingTrusted(p.getUniqueId());
                    if (claim != null) {
                        UUID targetId = target.getUniqueId();
                        String targetName = target.getName();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            claim.addTrusted(targetId);
                            plugin.getNationManager().saveAll();
                            p.sendMessage(ChatColor.GREEN + "✓ " + targetName + " добавлен в доверенные!");
                        });
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            // Разрешаем команду выбора нации
            String cmd = e.getMessage().toLowerCase();
            if (cmd.startsWith("/nation") || cmd.startsWith("/нация")) return;
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "⚠️ Команды заблокированы до выбора Нации! Напиши /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Взаимодействие заблокировано! Сначала выберите Нацию: /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Разрушение блоков заблокировано! Сначала выберите Нацию: /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Установка блоков заблокирована! Сначала выберите Нацию: /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (isAwaitingNationSelection(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Выбрасывание заблокировано! Сначала выберите Нацию: /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (isAwaitingNationSelection(p)) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Подбор предметов заблокирован! Сначала выберите Нацию: /nation");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (isAwaitingNationSelection(p)) {
                e.setCancelled(true); // Абсолютная неуязвимость во время выбора нации
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            if (isAwaitingNationSelection(p)) {
                e.setCancelled(true); // Не позволяет бить других до выбора нации
            }
        }
    }
}
