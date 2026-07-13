package ru.example.vkchat.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class BloodMoonManager extends BukkitRunnable {
    private final VKChatPlugin plugin;
    private boolean isBloodMoonActive = false;
    private boolean nightHandled = false;

    public BloodMoonManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().getHardcoreConfig().getBoolean("hardcore.blood-moon.enabled", true)) return;

        World world = Bukkit.getWorld(plugin.getConfigManager().getHardcoreConfig().getString("hardcore.blood-moon.world", "world"));
        if (world == null) return;

        long time = world.getTime();
        
        // Ночь начинается примерно с 13000, заканчивается в 23000
        if (time > 13000 && time < 23000) {
            if (!nightHandled) {
                nightHandled = true;
                int chance = plugin.getConfigManager().getHardcoreConfig().getInt("hardcore.blood-moon.chance", 10);
                if (ThreadLocalRandom.current().nextInt(100) < chance) {
                    isBloodMoonActive = true;
                    String msg = "&4&lКРОВАВАЯ ЛУНА ВЗОШЛА! &cМонстры стали свирепее, но лут богаче!";
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        } else {
            if (nightHandled) {
                nightHandled = false;
                if (isBloodMoonActive) {
                    isBloodMoonActive = false;
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&e&lКровавая луна зашла. Мир снова в безопасности."));
                }
            }
        }
    }

    public boolean isActive() {
        return isBloodMoonActive;
    }
}