package ru.example.vkchat.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

public class BleedingTask extends BukkitRunnable {
    private final VKChatPlugin plugin;

    public BleedingTask(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().getHardcoreConfig().getBoolean("hardcore.bleeding.enabled", true)) return;
        
        double threshold = plugin.getConfigManager().getHardcoreConfig().getDouble("hardcore.bleeding.threshold-hp", 6.0);
        String effectName = plugin.getConfigManager().getHardcoreConfig().getString("hardcore.bleeding.effect", "SLOW");
        int effectLevel = plugin.getConfigManager().getHardcoreConfig().getInt("hardcore.bleeding.effect-level", 1);
        
        PotionEffectType type = PotionEffectType.getByName(effectName);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getHealth() <= threshold && !p.isDead()) {
                // Выдаем эффект замедления/слабости
                if (type != null) {
                    p.addPotionEffect(new PotionEffect(type, 60, effectLevel - 1, false, false, true));
                }
                
                // Спавним частицы крови (редстоун) под ногами
                Location loc = p.getLocation().add(0, 0.1, 0);
                Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.RED, 1.5F);
                p.getWorld().spawnParticle(Particle.REDSTONE, loc, 3, 0.2, 0.1, 0.2, 0, dustOptions);
            }
        }
    }
}