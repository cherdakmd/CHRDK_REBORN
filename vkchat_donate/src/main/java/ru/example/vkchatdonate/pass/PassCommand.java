package ru.example.vkchatdonate.pass;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatdonate.VKChatDonatePlugin;

import java.util.*;

/**
 * /pass — IMPROVE #4: Отдельная команда для проходки.
 *
 * /pass          — информация о проходке
 * /pass rep      — локальная репутация
 * /pass buy      — покупка за донат-средства (IMPROVE #10)
 * /pass list     — список владельцев (админ)
 * /pass give     — выдать вручную (админ)
 * /pass remove   — отозвать (админ)
 * /pass stats    — аналитика (админ)
 */
public class PassCommand implements CommandExecutor, TabCompleter {

    private final VKChatDonatePlugin plugin;

    public PassCommand(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                sender.sendMessage(plugin.getPassManager().getPassInfo(p));
            } else {
                sender.sendMessage(ChatColor.GOLD + "🎫 Система проходок");
                sender.sendMessage(ChatColor.GRAY + "/pass list | stats");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "rep" -> handleRep(sender);
            case "buy" -> handleBuy(sender);
            case "list" -> handleList(sender);
            case "give" -> handleGive(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "stats" -> handleStats(sender);
            default -> {
                sender.sendMessage(ChatColor.GOLD + "🎫 Проходка:");
                if (sender instanceof Player) {
                    sender.sendMessage(ChatColor.YELLOW + "/pass" + ChatColor.GRAY + " — информация");
                    sender.sendMessage(ChatColor.YELLOW + "/pass rep" + ChatColor.GRAY + " — локальная репутация");
                    sender.sendMessage(ChatColor.YELLOW + "/pass buy" + ChatColor.GRAY + " — купить за донат");
                }
                if (sender.hasPermission("vkchat.donate.admin")) {
                    sender.sendMessage(ChatColor.YELLOW + "/pass list" + ChatColor.GRAY + " — владельцы");
                    sender.sendMessage(ChatColor.YELLOW + "/pass give <ник>" + ChatColor.GRAY + " — выдать");
                    sender.sendMessage(ChatColor.YELLOW + "/pass remove <ник>" + ChatColor.GRAY + " — отозвать");
                    sender.sendMessage(ChatColor.YELLOW + "/pass stats" + ChatColor.GRAY + " — аналитика");
                }
            }
        }
        return true;
    }

    private void handleRep(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return;
        }
        PassManager pm = plugin.getPassManager();
        int localRep = pm.getLocalRep(p);
        int cap = pm.getLocalRepCap();

        sender.sendMessage(ChatColor.GOLD + "═══ 💰 Локальная репутация ═══");
        sender.sendMessage(ChatColor.YELLOW + "Баланс: " + ChatColor.WHITE + localRep
                + ChatColor.GRAY + " / " + cap);
        sender.sendMessage(ChatColor.GRAY + "Локальная репутация — для игроков без привязки ВК.");
        sender.sendMessage(ChatColor.GRAY + "Привяжите ВК (/vklink) для переноса в основной профиль!");

        if (pm.hasPass(p)) {
            PassManager.PassHolder h = pm.getPassHolder(p.getUniqueId());
            if (h != null) {
                sender.sendMessage(ChatColor.YELLOW + "Проходка: " + ChatColor.WHITE + h.getDaysLeft() + " дн.");
            }
        }
    }

    /**
     * IMPROVE #10: Покупка проходки за донат-средства.
     */
    private void handleBuy(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return;
        }

        PassManager pm = plugin.getPassManager();

        if (pm.hasPass(p)) {
            sender.sendMessage(ChatColor.YELLOW + "У вас уже есть проходка! Используйте /pass для информации.");
            return;
        }

        // Проверка привязки ВК
        int vkId = getLinkedVkId(p);
        if (vkId != -1) {
            sender.sendMessage(ChatColor.GREEN + "У вас уже привязан ВК — проходка не нужна!");
            return;
        }

        // Проверка донат-статуса
        if (pm.hasAnyDonateStatus(p)) {
            sender.sendMessage(ChatColor.GREEN + "У вас уже есть донат-статус — проходка не нужна!");
            return;
        }

        // Информация о покупке
        int price = pm.getPassPrice();
        sender.sendMessage(ChatColor.GOLD + "═══ 🎫 Покупка проходки ═══");
        sender.sendMessage(ChatColor.YELLOW + "Цена: " + ChatColor.WHITE + price + "₽");
        sender.sendMessage(ChatColor.YELLOW + "Длительность: " + ChatColor.WHITE
                + (pm.getPassDurationSeconds() / 86400) + " дней");
        sender.sendMessage(ChatColor.GRAY + "");
        sender.sendMessage(ChatColor.GRAY + "Для покупки поддержите сервер через DonatePay:");
        sender.sendMessage(ChatColor.WHITE + "  → Укажите свой ник в поле «Имя»");
        sender.sendMessage(ChatColor.WHITE + "  → Минимальная сумма: " + price + "₽");
        sender.sendMessage(ChatColor.GRAY + "");
        sender.sendMessage(ChatColor.GREEN + "Или привяжите ВК бесплатно: /vklink");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return;
        }
        var holders = plugin.getPassManager().getActivePassHolders();
        if (holders.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Нет активных проходок.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "🎫 Владельцы проходок (" + holders.size() + "):");
        for (PassManager.PassHolder h : holders) {
            String status = h.isExpired()
                    ? ChatColor.RED + "[ИСТЕКЛА]"
                    : h.isInGracePeriod(plugin.getPassManager().getPassGraceDays())
                    ? ChatColor.YELLOW + "[ГРЕЙС]"
                    : ChatColor.GREEN + "[АКТИВНА]";
            sender.sendMessage("  " + ChatColor.WHITE + h.getLastName()
                    + " " + status
                    + ChatColor.GRAY + " " + h.getDaysLeft() + "д"
                    + ChatColor.DARK_GRAY + " (" + h.getSource() + ")");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/pass give <ник>");
            return;
        }
        boolean ok = plugin.getPassManager().grantPassManually(args[1]);
        if (ok) {
            sender.sendMessage(ChatColor.GREEN + "✅ Проходка выдана: " + args[1]);
        } else {
            sender.sendMessage(ChatColor.RED + "❌ Не удалось выдать проходку. Игрок не найден или уже имеет ВК/статус.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/pass remove <ник>");
            return;
        }
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        plugin.getPassManager().removePass(off);
        sender.sendMessage(ChatColor.GREEN + "✅ Проходка отозвана: " + args[1]);
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return;
        }
        sender.sendMessage(plugin.getPassManager().getAnalyticsFormatted());
    }

    private int getLinkedVkId(Player p) {
        try {
            return ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        } catch (Exception e) { return -1; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            if (sender instanceof Player) {
                options.add("rep");
                options.add("buy");
            }
            if (sender.hasPermission("vkchat.donate.admin")) {
                options.add("list");
                options.add("give");
                options.add("remove");
                options.add("stats");
            }
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = args[1].toLowerCase();
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
