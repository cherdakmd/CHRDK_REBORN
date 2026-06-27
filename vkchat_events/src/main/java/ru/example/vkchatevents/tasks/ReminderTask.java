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
        StringBuilder vkMessage = new StringBuilder(new String(new byte[]{(byte)226, (byte)143, (byte)179, 32, (byte)208, (byte)157, (byte)208, (byte)176, (byte)208, (byte)191, (byte)208, (byte)190, (byte)208, (byte)188, (byte)208, (byte)184, (byte)208, (byte)189, (byte)208, (byte)176, (byte)208, (byte)189, (byte)208, (byte)184, (byte)208, (byte)181, 32, (byte)208, (byte)190, (byte)208, (byte)177, 32, (byte)208, (byte)176, (byte)208, (byte)186, (byte)209, (byte)130, (byte)208, (byte)184, (byte)208, (byte)178, (byte)208, (byte)189, (byte)209, (byte)139, (byte)209, (byte)133, 32, (byte)209, (byte)129, (byte)208, (byte)190, (byte)208, (byte)177, (byte)209, (byte)139, (byte)209, (byte)130, (byte)208, (byte)184, (byte)209, (byte)143, (byte)209, (byte)133, 58, 10, 10}, java.nio.charset.StandardCharsets.UTF_8));

        if (plugin.getAirdropManager().isActive()) {
            hasReminders = true;
            String msg = new String(new byte[]{(byte)208, (byte)144, (byte)208, (byte)184, (byte)209, (byte)128, (byte)208, (byte)180, (byte)209, (byte)128, (byte)208, (byte)190, (byte)208, (byte)191, 32, 40}, java.nio.charset.StandardCharsets.UTF_8) + plugin.getAirdropManager().getActiveTierName() + ChatColor.YELLOW + ") is still active! Buy coords via !airdrop";
            broadcastInGame(ChatColor.YELLOW + "[Напоминание] " + msg);
            vkMessage.append(new String(new byte[]{(byte)240, (byte)159, (byte)147, (byte)166, 32, (byte)208, (byte)144, (byte)208, (byte)184, (byte)209, (byte)128, (byte)208, (byte)180, (byte)208, (byte)190, (byte)208, (byte)191, 32, (byte)208, (byte)178, (byte)209, (byte)129, (byte)209, (byte)145, 32, (byte)208, (byte)181, (byte)209, (byte)137, (byte)209, (byte)145, 32, (byte)208, (byte)182, (byte)208, (byte)180, (byte)209, (byte)145, (byte)209, (byte)130, 32, (byte)209, (byte)129, (byte)208, (byte)178, (byte)208, (byte)190, (byte)208, (byte)184, (byte)209, (byte)133, 32, (byte)208, (byte)179, (byte)208, (byte)181, (byte)209, (byte)128, (byte)208, (byte)190, (byte)208, (byte)181, (byte)208, (byte)178, 33, 32, (byte)208, (byte)163, (byte)208, (byte)183, (byte)208, (byte)189, (byte)208, (byte)176, (byte)209, (byte)130, (byte)209, (byte)140, 32, (byte)208, (byte)186, (byte)208, (byte)190, (byte)208, (byte)190, (byte)209, (byte)128, (byte)208, (byte)180, (byte)208, (byte)184, (byte)208, (byte)189, (byte)208, (byte)176, (byte)209, (byte)130, (byte)209, (byte)139, 58, 32, 33, (byte)208, (byte)176, (byte)208, (byte)184, (byte)209, (byte)128, (byte)208, (byte)180, (byte)208, (byte)190, (byte)208, (byte)191, 10}, java.nio.charset.StandardCharsets.UTF_8));
        }

        if (plugin.getInvasionManager().isActive()) {
            hasReminders = true;
            String msg = "Разлом Бездны всё ещё открыт на координатах X: " + plugin.getInvasionManager().getActiveLocation().getBlockX() + " Z: " + plugin.getInvasionManager().getActiveLocation().getBlockZ();
            broadcastInGame(ChatColor.DARK_PURPLE + "[Напоминание] " + ChatColor.LIGHT_PURPLE + msg);
            vkMessage.append(new String(new byte[]{(byte)240, (byte)159, (byte)140, (byte)128, 32, (byte)208, (byte)160, (byte)208, (byte)176, (byte)208, (byte)183, (byte)208, (byte)187, (byte)208, (byte)190, (byte)208, (byte)188, 32, (byte)208, (byte)145, (byte)208, (byte)181, (byte)208, (byte)183, (byte)208, (byte)180, (byte)208, (byte)189, (byte)209, (byte)139, 32, (byte)208, (byte)191, (byte)209, (byte)128, (byte)208, (byte)190, (byte)208, (byte)180, (byte)208, (byte)190, (byte)208, (byte)187, (byte)208, (byte)182, (byte)208, (byte)176, (byte)208, (byte)181, (byte)209, (byte)130, 32, (byte)208, (byte)184, (byte)208, (byte)183, (byte)208, (byte)178, (byte)208, (byte)181, (byte)209, (byte)128, (byte)208, (byte)179, (byte)208, (byte)176, (byte)209, (byte)130, (byte)209, (byte)140, 32, (byte)208, (byte)188, (byte)208, (byte)190, (byte)208, (byte)189, (byte)209, (byte)129, (byte)209, (byte)130, (byte)209, (byte)128, (byte)208, (byte)190, (byte)208, (byte)178, 32, 40, 88, 58, 32}, java.nio.charset.StandardCharsets.UTF_8)).append(plugin.getInvasionManager().getActiveLocation().getBlockX()).append(", Z: ").append(plugin.getInvasionManager().getActiveLocation().getBlockZ()).append(")!");
        }
        
        if (plugin.getWrathManager().isActive()) {
            hasReminders = true;
            String msg = "Мировой Босс Аватар Гнева всё ещё жив на координатах X: " + plugin.getWrathManager().getActiveLocation().getBlockX() + " Z: " + plugin.getWrathManager().getActiveLocation().getBlockZ() + "!";
            broadcastInGame(ChatColor.DARK_RED + "[Напоминание] " + ChatColor.RED + msg);
            vkMessage.append(new String(new byte[]{(byte)226, (byte)155, (byte)136, (byte)239, (byte)184, (byte)143, 32, (byte)208, (byte)144, (byte)208, (byte)178, (byte)208, (byte)176, (byte)209, (byte)130, (byte)208, (byte)176, (byte)209, (byte)128, 32, (byte)208, (byte)147, (byte)208, (byte)189, (byte)208, (byte)181, (byte)208, (byte)178, (byte)208, (byte)176, 32, (byte)208, (byte)178, (byte)209, (byte)129, (byte)209, (byte)145, 32, (byte)208, (byte)181, (byte)209, (byte)137, (byte)209, (byte)145, 32, (byte)209, (byte)130, (byte)208, (byte)181, (byte)209, (byte)128, (byte)209, (byte)128, (byte)208, (byte)190, (byte)209, (byte)128, (byte)208, (byte)184, (byte)208, (byte)183, (byte)208, (byte)184, (byte)209, (byte)128, (byte)209, (byte)131, (byte)208, (byte)181, (byte)209, (byte)130, 32, (byte)208, (byte)188, (byte)208, (byte)184, (byte)209, (byte)128, 32, 40, 88, 58, 32}, java.nio.charset.StandardCharsets.UTF_8)).append(plugin.getWrathManager().getActiveLocation().getBlockX()).append(", Z: ").append(plugin.getWrathManager().getActiveLocation().getBlockZ()).append(")!");
        }
        
        if (plugin.getMeteorManager().isActive()) {
            hasReminders = true;
            String msg = "Ядро Метеорита всё ещё не расколото на координатах X: " + plugin.getMeteorManager().getActiveLocation().getBlockX() + " Z: " + plugin.getMeteorManager().getActiveLocation().getBlockZ() + "!";
            broadcastInGame(ChatColor.GOLD + "[Напоминание] " + ChatColor.YELLOW + msg);
            vkMessage.append(new String(new byte[]{(byte)240, (byte)159, (byte)140, (byte)160, 32, (byte)208, (byte)154, (byte)208, (byte)190, (byte)209, (byte)129, (byte)208, (byte)188, (byte)208, (byte)184, (byte)209, (byte)135, (byte)208, (byte)181, (byte)209, (byte)129, (byte)208, (byte)186, (byte)208, (byte)184, (byte)208, (byte)185, 32, (byte)208, (byte)188, (byte)208, (byte)181, (byte)209, (byte)130, (byte)208, (byte)181, (byte)208, (byte)190, (byte)209, (byte)128, (byte)208, (byte)184, (byte)209, (byte)130, 32, (byte)208, (byte)190, (byte)209, (byte)129, (byte)209, (byte)130, (byte)209, (byte)139, (byte)208, (byte)178, (byte)208, (byte)176, (byte)208, (byte)181, (byte)209, (byte)130, 32, 40, 88, 58, 32}, java.nio.charset.StandardCharsets.UTF_8)).append(plugin.getMeteorManager().getActiveLocation().getBlockX()).append(", Z: ").append(plugin.getMeteorManager().getActiveLocation().getBlockZ()).append(")!");
        }

        if (hasReminders && Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatPlugin.getInstance().getApi().sendToMainChat(vkMessage.toString());
        }
    }

    private void broadcastInGame(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
