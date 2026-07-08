package ru.example.vkchatoffline.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.managers.ShiftManager.ShiftData;

public class ShiftCommand implements CommandExecutor {
    private final VKChatOfflinePlugin plugin;

    public ShiftCommand(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }
        Player p = (Player) sender;

        int vkId;
        try {
            vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            return true;
        }

        if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            String status = plugin.getShiftManager().getShiftStatus(vkId);
            int history = plugin.getShiftManager().getShiftHistory(vkId);
            long cd = plugin.getShiftManager().getCooldownRemaining(vkId);
            p.sendMessage(ChatColor.GOLD + "⛏ Шахтёрские смены");
            p.sendMessage(ChatColor.YELLOW + "Статус: " + ChatColor.WHITE + status);
            if (history > 0) {
                p.sendMessage(ChatColor.YELLOW + "Выполнено смен: " + ChatColor.WHITE + history);
                if (history >= 5) p.sendMessage(ChatColor.GREEN + "Бонус: +50% к наградам за 5+ смен подряд!");
                else if (history >= 3) p.sendMessage(ChatColor.GREEN + "Бонус: +25% к наградам за 3+ смены подряд!");
            }
            if (cd > 0) {
                long mins = cd / 60000;
                p.sendMessage(ChatColor.RED + "Кулдаун до новой смены: " + mins + " мин.");
            }
            p.sendMessage(ChatColor.GRAY + "Смены запускаются через ВК бота командой !шахта");
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getShiftManager().getShiftsInfo()));
            return true;
        }

        p.sendMessage(ChatColor.GRAY + "Используйте !шахта в ВК боте для запуска смены.");
        return true;
    }
}
