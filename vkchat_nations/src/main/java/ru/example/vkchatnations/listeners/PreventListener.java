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

    public PreventListener(VKChatNationsPlugin plugin) { this.plugin = plugin; }

    private boolean isAwaitingNationSelection(Player p) {
        return VKChatPlugin.getInstance().getApi().isFullyAuthorized(p) && !plugin.getNationManager().hasNation(p);
    }

    private boolean isUnauthorized(Player p) {
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) return false;
            if (p.hasPermission("vkchat.pass")) return false;
            return true;
        } catch (Exception e) { return true; }
    }

    private boolean block(Player p, boolean isMove) {
        if (isUnauthorized(p)) {
            if (!isMove) msgUnauthorized(p);
            return true;
        }
        if (isAwaitingNationSelection(p)) {
            if (!isMove) p.sendMessage(ChatColor.RED + "⚠ Выберите Нацию через /nation!");
            return true;
        }
        return false;
    }

    private void msgUnauthorized(Player p) {
        if (System.currentTimeMillis() % 5000 < 200) {
            p.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
            p.sendMessage(ChatColor.YELLOW + "⚠ Ты не авторизован!");
            p.sendMessage("");
            p.sendMessage(ChatColor.WHITE + "1) " + ChatColor.AQUA + "/vklink" + ChatColor.GRAY + " — привязать ВК");
            p.sendMessage(ChatColor.WHITE + "2) " + ChatColor.GOLD + "/donate info" + ChatColor.GRAY + " — купить проходку (500₽)");
            p.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        if (to == null) return;
        if (e.getFrom().getX() == to.getX() && e.getFrom().getY() == to.getY() && e.getFrom().getZ() == to.getZ()) return;
        if (isUnauthorized(p)) { e.setTo(e.getFrom()); msgUnauthorized(p); return; }
        if (isAwaitingNationSelection(p)) { e.setTo(e.getFrom()); return; }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (block(p, false)) { e.setCancelled(true); return; }
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
        if (isUnauthorized(p)) {
            String cmd = e.getMessage().toLowerCase().trim();
            if (cmd.startsWith("/vklink") || cmd.startsWith("/register") || cmd.startsWith("/login") || cmd.startsWith("/donate") || cmd.startsWith("/help") || cmd.startsWith("/?")) return;
            e.setCancelled(true);
            msgUnauthorized(p);
            return;
        }
        if (isAwaitingNationSelection(p)) {
            String cmd = e.getMessage().toLowerCase();
            if (cmd.startsWith("/nation") || cmd.startsWith("/нация")) return;
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "⚠️ Команды заблокированы до выбора Нации! Напиши /nation");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) { if (block(e.getPlayer(), false)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) { if (block(e.getPlayer(), false)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent e) { if (block(e.getPlayer(), false)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) { if (block(e.getPlayer(), false)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && block(p, false)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (isUnauthorized(p) || isAwaitingNationSelection(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && (isUnauthorized(p) || isAwaitingNationSelection(p))) e.setCancelled(true);
    }
}
