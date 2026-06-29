package ru.example.vkchatevents.tasks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
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
            hasReminders = true;
            String msg = "Разлом Бездны всё ещё открыт на координатах X: " + plugin.getInvasionManager().getActiveLocation().getBlockX() + " Z: " + plugin.getInvasionManager().getActiveLocation().getBlockZ();
            broadcastInGame(ChatColor.DARK_PURPLE + "[Напоминание] " + ChatColor.LIGHT_PURPLE + msg);
            vkMessage.append("🌀 Разлом Бездны открыт! Координаты: X: ").append(plugin.getInvasionManager().getActiveLocation().getBlockX()).append(", Z: ").append(plugin.getInvasionManager().getActiveLocation().getBlockZ()).append("!\n");
        }
        
        if (plugin.getWrathManager().isActive()) {
            hasReminders = true;
            String msg = "Мировой Босс Аватар Гнева всё ещё жив на координатах X: " + plugin.getWrathManager().getActiveLocation().getBlockX() + " Z: " + plugin.getWrathManager().getActiveLocation().getBlockZ() + "!";
            broadcastInGame(ChatColor.DARK_RED + "[Напоминание] " + ChatColor.RED + msg);
            vkMessage.append("💀 Аватар Гнева жив! Координаты: X: ").append(plugin.getWrathManager().getActiveLocation().getBlockX()).append(", Z: ").append(plugin.getWrathManager().getActiveLocation().getBlockZ()).append("!\n");
        }

        if (hasReminders && Bukkit.getPluginManager().getPlugin("VKChat") != null) {
        }
    }

    private void broadcastInGame(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
