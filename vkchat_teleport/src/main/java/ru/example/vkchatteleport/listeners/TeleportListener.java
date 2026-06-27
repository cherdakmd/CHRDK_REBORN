package ru.example.vkchatteleport.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
            
            // Проверяем изменение координат блока (игнорируем поворот головы)
            if (from.getBlockX() != to.getBlockX() || 
                from.getBlockY() != to.getBlockY() || 
                from.getBlockZ() != to.getBlockZ()) {
                
                boolean cancelOnMove = plugin.getConfig().getBoolean("teleportation.warmup.cancel-on-move", true);
                if (cancelOnMove) {
                    plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), true);
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
                    plugin.getTeleportManager().cancelActiveWarmup(p.getUniqueId(), true);
                }
            }
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
