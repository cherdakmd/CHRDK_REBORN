package ru.example.vkchatoffline.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.managers.ShiftManager;
import ru.example.vkchatoffline.managers.ShiftManager.ShiftData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShiftCommand implements CommandExecutor, TabCompleter {
    private final VKChatOfflinePlugin plugin;
    private static final List<String> SHIFT_DURATIONS = Arrays.asList("1h", "3h", "8h", "12h");

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
            vkId = VKChatBridge.getLinkedVkId(p);
        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            return true;
        }

        if (!VKChatBridge.hasVkOrPass(p)) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            handleStatus(p, vkId);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getShiftManager().getShiftsInfo()));
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            handleStart(p, vkId, args);
            return true;
        }

        p.sendMessage(ChatColor.GRAY + "Используйте: /shift start <1h|3h|8h|12h>, /shift status, /shift info");
        return true;
    }

    private void handleStatus(Player p, int vkId) {
        ShiftManager sm = plugin.getShiftManager();
        String status = sm.getShiftStatus(vkId);
        int history = sm.getShiftHistory(vkId);
        long vkCd = sm.getCooldownRemaining(vkId);
        long inGameCd = sm.getInGameCooldownRemaining(vkId);
        p.sendMessage(ChatColor.GOLD + "⛏ Шахтёрские смены");
        p.sendMessage(ChatColor.YELLOW + "Статус: " + ChatColor.WHITE + status);
        if (history > 0) {
            p.sendMessage(ChatColor.YELLOW + "Выполнено смен: " + ChatColor.WHITE + history);
            if (history >= 5) p.sendMessage(ChatColor.GREEN + "Бонус: +50% к наградам за 5+ смен подряд!");
            else if (history >= 3) p.sendMessage(ChatColor.GREEN + "Бонус: +25% к наградам за 3+ смены подряд!");
        }
        if (vkCd > 0) {
            long mins = vkCd / 60000;
            p.sendMessage(ChatColor.RED + "Кулдаун (ВК): " + mins + " мин.");
        }
        if (inGameCd > 0) {
            p.sendMessage(ChatColor.RED + "Кулдаун (игра): " + sm.formatCooldown(inGameCd));
        }
        int repCost = plugin.getConfig().getInt("shift.rep-cost", 100);
        p.sendMessage(ChatColor.GRAY + "Стоимость запуска: " + repCost + " репутации");
        p.sendMessage(ChatColor.GRAY + "Используйте /shift start <1h|3h|8h|12h>");
    }

    private void handleStart(Player p, int vkId, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Используйте: /shift start <1h|3h|8h|12h>");
            p.sendMessage(ChatColor.GRAY + "Доступные смены: " + String.join(", ", SHIFT_DURATIONS));
            return;
        }

        String shiftKey = args[1].toLowerCase();
        if (!SHIFT_DURATIONS.contains(shiftKey)) {
            p.sendMessage(ChatColor.RED + "Неизвестная смена: " + shiftKey);
            p.sendMessage(ChatColor.GRAY + "Доступные: " + String.join(", ", SHIFT_DURATIONS));
            return;
        }

        ShiftManager sm = plugin.getShiftManager();

        if (sm.hasActiveShift(vkId)) {
            p.sendMessage(ChatColor.RED + "У вас уже активная смена! " + sm.getShiftStatus(vkId));
            return;
        }

        if (sm.hasCompletedShift(vkId)) {
            p.sendMessage(ChatColor.RED + "Сначала заберите награды за прошлую смену через ВК бота (!шахта)!");
            return;
        }

        if (!sm.canStartInGameShift(vkId)) {
            long remaining = sm.getInGameCooldownRemaining(vkId);
            p.sendMessage(ChatColor.RED + "Подождите ещё " + sm.formatCooldown(remaining) + " перед следующей сменой.");
            return;
        }

        if (!sm.hasEnoughRep(vkId)) {
            int cost = plugin.getConfig().getInt("shift.rep-cost", 100);
            int current;
            try {
                current = VKChatBridge.getReputation(vkId);
            } catch (Exception e) {
                current = 0;
            }
            p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + cost + ", у вас: " + current);
            return;
        }

        if (!sm.deductRepCost(vkId)) {
            p.sendMessage(ChatColor.RED + "Ошибка списания репутации.");
            return;
        }

        if (!sm.startShift(vkId, shiftKey, true)) {
            p.sendMessage(ChatColor.RED + "Не удалось начать смену.");
            return;
        }

        ShiftData sd = sm.getShift(vkId);
        long hrs = (sd.endTime - sd.startTime) / 3600000;
        long mins = ((sd.endTime - sd.startTime) % 3600000) / 60000;
        String duration = hrs > 0 ? hrs + "ч " + mins + "мин" : mins + "мин";
        int repCost = plugin.getConfig().getInt("shift.rep-cost", 100);

        p.sendMessage(ChatColor.GREEN + "⛏ Смена '" + sm.getShiftName(shiftKey) + "' начата!");
        p.sendMessage(ChatColor.YELLOW + "⏳ Длительность: " + ChatColor.WHITE + duration);
        p.sendMessage(ChatColor.RED + "💰 Списано: " + repCost + " репутации");
        p.sendMessage(ChatColor.GRAY + "Награды будут в /stash после завершения.");

        startActionBarTask(p, vkId, sd);
    }

    private void startActionBarTask(Player player, int vkId, ShiftData sd) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = plugin.getServer().getPlayer(player.getUniqueId());
            if (online == null || !online.isOnline()) return;
            ShiftData current = plugin.getShiftManager().getShift(vkId);
            if (current == null || current.completed) return;
            long left = current.endTime - System.currentTimeMillis();
            if (left <= 0) {
                String msg = "§a⛏ Смена '" + plugin.getShiftManager().getShiftName(current.shiftKey) + "' завершена! Заберите награды в /stash";
                online.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                return;
            }
            long hrs = left / 3600000;
            long mins = (left % 3600000) / 60000;
            String time = hrs > 0 ? hrs + "ч " + mins + "мин" : mins + "мин";
            String msg = "§e⛏ В шахте | " + plugin.getShiftManager().getShiftName(current.shiftKey) + " | §fОсталось: " + time;
            online.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
        }, 20L, 20L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("start", "status", "info")) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> completions = new ArrayList<>();
            for (String s : SHIFT_DURATIONS) {
                if (s.startsWith(args[1].toLowerCase())) completions.add(s);
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
