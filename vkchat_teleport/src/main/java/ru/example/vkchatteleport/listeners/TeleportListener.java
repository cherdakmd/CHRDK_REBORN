package ru.example.vkchatteleport.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.example.vkchatteleport.VKChatTeleportPlugin;

public class TeleportListener implements Listener {
    private final VKChatTeleportPlugin plugin;

    public TeleportListener(VKChatTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (plugin.getTeleportManager().isTeleporting(p.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            
            Location startLoc = plugin.getTeleportManager().getActiveWarmupStartLocation(p.getUniqueId());

            if (from.getBlockX() != to.getBlockX() || 
                from.getBlockY() != to.getBlockY() || 
                from.getBlockZ() != to.getBlockZ()) {
                
                boolean cancelOnMove = plugin.getConfig().getBoolean("teleportation.warmup.cancel-on-move", true);
                if (cancelOnMove) {
                    double distance = startLoc != null ? startLoc.distance(to) : 0;
                    String reason = startLoc != null && distance > 2.0
                            ? "Вы отошли слишком далеко от точки старта (" + String.format("%.1f", distance) + " блоков)!"
                            : "Вы сдвинулись с места!";
                    plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), true, reason);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (plugin.getTeleportManager().isTeleporting(p.getUniqueId())) {
                boolean cancelOnDamage = plugin.getConfig().getBoolean("teleportation.warmup.cancel-on-damage", true);
                if (cancelOnDamage) {
                    plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), true, "Вы получили урон!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), false);
        plugin.getTeleportManager().saveDeathLocation(p.getUniqueId(), p.getLocation());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (plugin.getTeleportManager().hasDeathLocation(p.getUniqueId())) {
            p.sendMessage(ChatColor.YELLOW + "⚰ Используйте " + ChatColor.GOLD + "/back" + ChatColor.YELLOW + " чтобы вернуться к месту смерти. Стоимость: " + ChatColor.GOLD + plugin.getConfig().getInt("teleportation.back.cost", 50) + " реп.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), false);
        plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        // Проверяем, что игрок успешно лег в кровать
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            Player p = event.getPlayer();
            
            // Получаем центр над кроватью (чтобы игрок не застревал в блоке при телепортации)
            Location bedLoc = event.getBed().getLocation().add(0.5, 1.0, 0.5);
            bedLoc.setYaw(p.getLocation().getYaw());
            bedLoc.setPitch(p.getLocation().getPitch());

            // Устанавливаем точку дома "default"
            plugin.getTeleportManager().setHome(p.getUniqueId(), "default", bedLoc);
            
            p.sendMessage(ChatColor.GREEN + "✓ Ваша кровать автоматически установлена как главная точка дома '" + ChatColor.YELLOW + "default" + ChatColor.GREEN + "'!");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }
}
