package ru.example.vkchat.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchat.VKChatPlugin;

import java.util.Random;

public class RandomSpawnListener implements Listener {
    private final VKChatPlugin plugin;
    private final Random random = new Random();

    public RandomSpawnListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("random-spawn.enabled", true)) return;
        
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            Location loc = getRandomLocation();
            if (loc != null) {
                p.teleport(loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.getConfig().getBoolean("random-spawn.enabled", true)) return;
        
        if (!e.isBedSpawn() && !e.isAnchorSpawn()) {
            Location loc = getRandomLocation();
            if (loc != null) {
                e.setRespawnLocation(loc);
            }
        }

        // Респавн-бафф: Regeneration I на 5 сек + Absorption I на 10 сек
        Player p = e.getPlayer();
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0)); // 5 сек
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0)); // 10 сек
    }

    private Location getRandomLocation() {
        String worldName = plugin.getConfig().getString("random-spawn.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        int minX = plugin.getConfig().getInt("random-spawn.min-x", -5000);
        int maxX = plugin.getConfig().getInt("random-spawn.max-x", 5000);
        int minZ = plugin.getConfig().getInt("random-spawn.min-z", -5000);
        int maxZ = plugin.getConfig().getInt("random-spawn.max-z", 5000);

        for (int i = 0; i < 10; i++) { // Пробуем найти безопасную точку 10 раз
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            int y = world.getHighestBlockYAt(x, z);

            Block b = world.getBlockAt(x, y - 1, z);
            if (b.getType().isSolid() && !b.isLiquid() && b.getType() != org.bukkit.Material.CACTUS && b.getType() != org.bukkit.Material.MAGMA_BLOCK) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }
}