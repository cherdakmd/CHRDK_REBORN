package ru.example.vkchatgear.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatgear.VKChatGearPlugin;

public class CraftListener implements Listener {
    private final VKChatGearPlugin plugin;

    public CraftListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;

        if (plugin.getGearManager().isGear(result.getType())) {
            int cost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.craft-cost", 120);
            if (!plugin.getGearManager().takeVkReputation(p, cost, "ковка снаряжения")) {
                e.setCancelled(true);
                return;
            }

            int destroyChance = plugin.getConfig().getInt("hardcore-forging.destroy-chance.craft", 2);
            int bsLvl = plugin.getGearManager().getBlacksmithLevel(p);
            double reduction = bsLvl * plugin.getConfig().getDouble("hardcore-forging.blacksmith.failure-reduction-per-level", 0.01);
            destroyChance = Math.max(0, (int) Math.round(destroyChance * (1.0 - Math.min(0.8, reduction))));

            if (new java.util.Random().nextInt(100) < destroyChance) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.DARK_RED + "💥 Ковка сорвалась! Заготовка уничтожена, репутация потрачена.");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 0.7f);
                p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.08);
                return;
            }

            ItemStack modified = plugin.getGearManager().generateGear(result, p, false);
            if (new java.util.Random().nextInt(100) < plugin.getConfig().getInt("hardcore-forging.defects.chance-on-craft-fail", 70) && new java.util.Random().nextInt(100) < 10) {
                plugin.getGearManager().applyRandomDefect(modified);
                p.sendMessage(ChatColor.YELLOW + "⚠ Предмет сковался с дефектом. Его можно очистить через /forge cleanse за репутацию ВК.");
            }
            e.setCurrentItem(modified);
            p.sendMessage(ChatColor.GRAY + "Ковка MMO-снаряжения: списано " + cost + " репутации ВК.");
        }
    }
}
