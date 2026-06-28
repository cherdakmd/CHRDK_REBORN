package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;
import ru.example.vkchatevents.util.ClaimProtection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MeteorManager implements Listener {
    private final VKChatEventsPlugin plugin;
    
    private boolean meteorActive = false;
    private Location meteorCore = null;
    private List<Location> meteorBlocks = new ArrayList<>();

    public MeteorManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        
        int interval = plugin.getConfig().getInt("meteor.interval", 10800); // Раз в 3 часа
        new BukkitRunnable() {
            @Override
            public void run() {
                tryStartMeteor();
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);
    }
    
    public boolean isActive() {
        return meteorActive && meteorCore != null && meteorCore.getBlock().getType() == Material.ANCIENT_DEBRIS;
    }
    
    public Location getActiveLocation() {
        return meteorCore;
    }

    public void tryStartMeteor() {
        if (isActive()) return;

        World world = Bukkit.getWorlds().get(0);
        int radius = plugin.getConfig().getInt("meteor.spawn-radius", 2500);
        Location center = ClaimProtection.findSafeWildernessLocation(world, radius, plugin.getConfig().getInt("meteor.protected-radius", 12), 80);
        if (center == null) return;
        int x = center.getBlockX();
        int z = center.getBlockZ();
        
        // Spawn meteor crater
        meteorBlocks.clear();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx*dx + dy*dy + dz*dz <= 9) {
                        Location bLoc = center.clone().add(dx, dy, dz);
                        if (ClaimProtection.isLocationClaimed(bLoc)) continue;
                        Block b = bLoc.getBlock();
                        if (ThreadLocalRandom.current().nextInt(100) < 70) {
                            b.setType(Material.MAGMA_BLOCK);
                            meteorBlocks.add(b.getLocation());
                        } else if (ThreadLocalRandom.current().nextInt(100) < 50) {
                            b.setType(Material.OBSIDIAN);
                            meteorBlocks.add(b.getLocation());
                        }
                    }
                }
            }
        }
        
        meteorCore = center.clone().add(0, 1, 0);
        if (ClaimProtection.isLocationClaimed(meteorCore)) return;
        meteorCore.getBlock().setType(Material.ANCIENT_DEBRIS);
        meteorActive = true;

        String msg = "Meteor crashed at X: " + x + ", Z: " + z + "! Mine the core!";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.GOLD + "[Метеорит] " + ChatColor.YELLOW + msg);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        }

        if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatPlugin.getInstance().getApi().sendToMainChat("\ud83c\udf20 Метеоритный Дождь!\n" + msg);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (isActive() && e.getBlock().getLocation().equals(meteorCore)) {
            meteorActive = false;
            Player miner = e.getPlayer();
            
            String msg = miner.getName() + " mined the meteor core!";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.GREEN + "[Метеорит] " + msg);
            }
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("\ud83d\udca5 " + msg);
            }
            
            // Turn remaining magma into basalt
            for (Location loc : meteorBlocks) {
                if (loc.getBlock().getType() == Material.MAGMA_BLOCK) {
                    loc.getBlock().setType(Material.BASALT);
                }
            }
            meteorBlocks.clear();
        }
    }
}
