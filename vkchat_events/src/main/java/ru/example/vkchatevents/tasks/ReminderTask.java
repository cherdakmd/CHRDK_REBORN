package ru.example.vkchatevents.tasks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

public class ReminderTask extends BukkitRunnable {
    private final VKChatEventsPlugin plugin;

    public ReminderTask(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean hasReminders = false;
        StringBuilder vkMessage = new StringBuilder("⏰ Напоминания об активных событиях:\n\n");

        if (plugin.getInvasionManager().isActive()) {
            Location loc = plugin.getInvasionManager().getActiveLocation();
            if (loc != null) {
                hasReminders = true;
                String msg = "Разлом Бездны всё ещё открыт на координатах X: " + loc.getBlockX() + " Z: " + loc.getBlockZ();
                broadcastInGame(ChatColor.DARK_PURPLE + "[Напоминание] " + ChatColor.LIGHT_PURPLE + msg);
                vkMessage.append("🌀 Разлом Бездны открыт! Координаты: X: ").append(loc.getBlockX()).append(", Z: ").append(loc.getBlockZ()).append("!\n");
            }
        }
        
        if (plugin.getWrathManager().isActive()) {
            Location loc = plugin.getWrathManager().getActiveLocation();
            if (loc != null) {
                hasReminders = true;
                String msg = "Мировой Босс Аватар Гнева всё ещё жив на координатах X: " + loc.getBlockX() + " Z: " + loc.getBlockZ() + "!";
                broadcastInGame(ChatColor.DARK_RED + "[Напоминание] " + ChatColor.RED + msg);
                vkMessage.append("💀 Аватар Гнева жив! Координаты: X: ").append(loc.getBlockX()).append(", Z: ").append(loc.getBlockZ()).append("!\n");
            }
        }

        if (hasReminders && Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatBridge.sendToMainChat(vkMessage.toString().trim());
        }
    }

    private void broadcastInGame(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
