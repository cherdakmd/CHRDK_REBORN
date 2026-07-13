package ru.example.vkchatdonate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * DonateCommand v3.1 — делегирование /donate pass в PassCommand.
 */
public class DonateCommand implements CommandExecutor, TabCompleter {
    private final VKChatDonatePlugin plugin;

    private record SubCommand(String name, boolean adminOnly, boolean playerOnly,
                               String usage, BiConsumer<CommandSender, String[]> handler) {}

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public DonateCommand(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        subCommands.put("info", new SubCommand("info", false, false, "",
                (s, a) -> s.sendMessage(plugin.getDonateManager().getSetupInfo())));

        subCommands.put("status", new SubCommand("status", false, true, "",
                (s, a) -> showStatus((Player) s)));

        subCommands.put("days", new SubCommand("days", false, true, "",
                (s, a) -> showDaysLeft((Player) s)));

        subCommands.put("top", new SubCommand("top", false, false, "",
                (s, a) -> showTopDonors(s)));

        subCommands.put("upgrade", new SubCommand("upgrade", false, true, "",
                (s, a) -> showUpgradeInfo((Player) s)));

        subCommands.put("log", new SubCommand("log", true, false, "",
                (s, a) -> showDonateLog(s)));

        subCommands.put("stats", new SubCommand("stats", true, false, "",
                (s, a) -> showStats(s)));

        subCommands.put("setup", new SubCommand("setup", true, false, "<API-токен>",
                (s, a) -> handleSetup(s, a)));

        subCommands.put("reload", new SubCommand("reload", true, false, "",
                (s, a) -> handleReload(s)));

        subCommands.put("fundraiser", new SubCommand("fundraiser", true, false, "start <цель> | stop | toggle",
                (s, a) -> handleFundraiser(s, a)));

        // /donate pass делегирует в PassCommand
        subCommands.put("pass", new SubCommand("pass", true, false,
                "list | give <ник> | remove <ник> | stats",
                (s, a) -> handlePass(s, a)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getDonateManager().getSetupInfo());
            return true;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(plugin.getDonateManager().getSetupInfo());
            return true;
        }

        if (sub.adminOnly() && !sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков.");
            return true;
        }

        sub.handler().accept(sender, args);
        return true;
    }

    // ═══════════════════════════════════════
    // ПОДКОМАНДЫ
    // ═══════════════════════════════════════

    private void showStatus(Player p) {
        DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
        if (s == null) {
            // Проверяем проходку
            if (plugin.getPassManager().hasPass(p)) {
                p.sendMessage(plugin.getPassManager().getPassInfo(p));
            } else {
                p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
                p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
            }
        } else {
            p.sendMessage(ChatColor.GREEN + "Ваш статус: " + s.getName());
            long days = plugin.getDonateManager().getDaysLeft(p);
            p.sendMessage(ChatColor.GRAY + "Скидка: " + (int)(s.getRepDiscount() * 100)
                    + "% | КД ТП: ×" + s.getTpCooldownMult()
                    + " | Домов: " + s.getMaxHomes()
                    + " | Осталось: " + days + " дн.");
        }
    }

    private void showDaysLeft(Player p) {
        DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
        if (s == null) {
            if (plugin.getPassManager().hasPass(p)) {
                var holder = plugin.getPassManager().getPassHolder(p.getUniqueId());
                if (holder != null) {
                    long days = holder.getDaysLeft();
                    if (days <= 3) {
                        p.sendMessage(ChatColor.RED + "⚠ Проходка истекает через: " + days + " дн.!");
                    } else {
                        p.sendMessage(ChatColor.YELLOW + "Проходка: " + ChatColor.WHITE + days + " дн.");
                    }
                }
            } else {
                p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
                p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
            }
        } else {
            long days = plugin.getDonateManager().getDaysLeft(p);
            p.sendMessage(ChatColor.GREEN + "Статус: " + s.getName());
            if (days <= 3) {
                p.sendMessage(ChatColor.RED + "⚠ Истекает через: " + days + " дн.!");
            } else {
                p.sendMessage(ChatColor.YELLOW + "Осталось дней: " + ChatColor.WHITE + days);
            }
        }
    }

    private void showUpgradeInfo(Player p) {
        String info = plugin.getDonateManager().getUpgradeInfo(p);
        p.sendMessage(info);
    }

    private void showDonateLog(CommandSender sender) {
        var log = plugin.getDonateManager().getRecentLog(15);
        sender.sendMessage(ChatColor.GOLD + "═══ 📋 Лог донатов (последние 15) ═══");
        if (log.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Записей нет.");
        } else {
            for (var entry : log) {
                String color = switch (entry.type()) {
                    case "GRANT" -> "§a";
                    case "EXTEND" -> "§e";
                    case "PASS" -> "§b";
                    case "REP" -> "§7";
                    default -> "§f";
                };
                sender.sendMessage(ChatColor.GRAY + entry.formatDate() + " "
                        + color + entry.type() + " §f" + entry.player()
                        + " §7" + entry.detail()
                        + " §e" + String.format("%.0f", entry.amount()) + "₽");
            }
        }
    }

    private void showStats(CommandSender sender) {
        var dm = plugin.getDonateManager();
        var pm = plugin.getPassManager();
        sender.sendMessage(ChatColor.GOLD + "═══ 📊 Статистика донатов ═══");
        sender.sendMessage(ChatColor.YELLOW + "Всего донатеров: " + ChatColor.WHITE + dm.getDonorCount());
        sender.sendMessage(ChatColor.YELLOW + "Общая сумма: " + ChatColor.WHITE
                + String.format("%.0f", dm.getTotalDonatedAll()) + "₽");
        sender.sendMessage(ChatColor.YELLOW + "Проходок активно: " + ChatColor.WHITE + pm.getActivePassCount());
        sender.sendMessage(ChatColor.YELLOW + "Проходок конвертировано: " + ChatColor.WHITE + pm.getTotalConverted());

        var top = dm.getTopDonors(5);
        if (!top.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "Топ-5 донатеров:");
            int rank = 1;
            for (var e : top) {
                sender.sendMessage("  " + rank + ". " + ChatColor.WHITE + e.getKey()
                        + ChatColor.GRAY + " — " + ChatColor.GOLD + String.format("%.0f", e.getValue()) + "₽");
                rank++;
            }
        }
    }

    private void showTopDonors(CommandSender sender) {
        List<Map.Entry<String, Double>> top = plugin.getDonateManager().getTopDonors(10);
        sender.sendMessage(ChatColor.GOLD + "═══ ТОП-ДОНАТЕРЫ ═══");
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Пока нет донатов.");
        } else {
            int rank = 1;
            for (Map.Entry<String, Double> e : top) {
                String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "  ";
                sender.sendMessage(ChatColor.YELLOW + medal + " " + rank + ". " + ChatColor.WHITE + e.getKey()
                        + ChatColor.GRAY + " — " + ChatColor.GOLD + String.format("%.0f", e.getValue()) + "₽");
                rank++;
            }
        }
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Укажи API-токен: " + ChatColor.WHITE + "/donate setup <токен>");
            sender.sendMessage(ChatColor.GRAY + "Токен получить: donatepay.ru → Мои кассы → API");
            return;
        }

        String token = args[1];
        plugin.getConfig().set("api-token", token);
        plugin.saveConfig();
        plugin.reloadConfig();

        sender.sendMessage(ChatColor.GREEN + "✅ Токен сохранён!");
        sender.sendMessage(ChatColor.YELLOW + "Настраиваю LuckPerms...");

        var statusSec = plugin.getConfig().getConfigurationSection("statuses");
        if (statusSec != null) {
            int weight = 1;
            for (String groupId : statusSec.getKeys(false)) {
                String prefix = plugin.getConfig().getString("statuses." + groupId + ".prefix", "&7");
                String display = plugin.getConfig().getString("statuses." + groupId + ".display", groupId);

                dispatch("lp creategroup " + groupId);
                dispatch("lp group " + groupId + " setweight " + weight);
                dispatch("lp group " + groupId + " setprefix " + prefix + " ");
                dispatch("lp group " + groupId + " meta addprefix " + weight + " " + prefix + " ");
                dispatch("lp group " + groupId + " permission set vkchat.donate." + groupId + " true");
                dispatch("lp group " + groupId + " permission set vkchat.donate.fundraiser.toggle true");
                dispatch("lp group " + groupId + " meta setprefix " + weight + " " + prefix + " ");

                sender.sendMessage(ChatColor.GRAY + "  " + display + " — вес " + weight + " — OK");
                weight++;
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "✅ Готово! Группы, префиксы, вес и права настроены.");
        sender.sendMessage(ChatColor.GRAY + "Перезапусти сервер для старта опроса API.");

        dispatch("lp creategroup pass");
        dispatch("lp group pass setweight 0");
        dispatch("lp group pass permission set vkchat.pass true");
        sender.sendMessage(ChatColor.GRAY + "  🎫 Проходка (vkchat.pass) — OK");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getPassManager().reloadConfig();
        plugin.getDonateManager().shutdown();
        sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен. Перезапусти плагин для полного применения.");
    }

    private void handleFundraiser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getDonateManager().getFundraiserInfo());
            sender.sendMessage(ChatColor.GRAY + "/donate fundraiser start <цель> | stop | toggle");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Укажи цель: /donate fundraiser start 10000");
                    return;
                }
                try {
                    double goal = Double.parseDouble(args[2]);
                    plugin.getDonateManager().startFundraiser(goal);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Цель должна быть числом!");
                }
            }
            case "stop" -> plugin.getDonateManager().stopFundraiser();
            case "toggle" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Только для игроков!");
                    return;
                }
                if (plugin.getDonateManager().getPlayerStatus(p) == null) {
                    p.sendMessage(ChatColor.RED + "Только донатеры могут скрывать BossBar!");
                    return;
                }
                boolean visible = plugin.getDonateManager().toggleFundraiserBar(p);
                sender.sendMessage(ChatColor.GREEN + (visible ? "✅ BossBar показан!" : "❌ BossBar скрыт."));
            }
            default -> sender.sendMessage(ChatColor.RED + "/donate fundraiser start <цель> | stop | toggle");
        }
    }

    /**
     * /donate pass — делегирует в PassManager.
     */
    private void handlePass(CommandSender sender, String[] args) {
        var pm = plugin.getPassManager();
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "🎫 Проходки: /donate pass list | give <ник> | remove <ник> | stats");
            sender.sendMessage(ChatColor.GRAY + "Или используй /pass для отдельной команды");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                var holders = pm.getActivePassHolders();
                if (holders.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Нет активных проходок.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "🎫 Владельцы (" + holders.size() + "):");
                    for (var h : holders) {
                        sender.sendMessage(ChatColor.WHITE + "  • " + h.getLastName()
                                + ChatColor.GRAY + " — " + h.getDaysLeft() + "д"
                                + ChatColor.DARK_GRAY + " (" + h.getSource() + ")");
                    }
                }
            }
            case "give" -> {
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "/donate pass give <ник>"); return; }
                boolean ok = pm.grantPassManually(args[2]);
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✅ Проходка выдана: " + args[2]);
                } else {
                    sender.sendMessage(ChatColor.RED + "❌ Не удалось. Игрок не найден или уже имеет ВК/статус.");
                }
            }
            case "remove" -> {
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "/donate pass remove <ник>"); return; }
                pm.removePass(ru.example.vkchat.util.UUIDResolver.resolve(args[2]));
                sender.sendMessage(ChatColor.GREEN + "✅ Проходка отозвана: " + args[2]);
            }
            case "stats" -> sender.sendMessage(pm.getAnalyticsFormatted());
            default -> sender.sendMessage(ChatColor.RED + "/donate pass list | give <ник> | remove <ник> | stats");
        }
    }

    private void dispatch(String cmd) {
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return subCommands.values().stream()
                    .filter(sub -> !sub.adminOnly() || sender.hasPermission("vkchat.donate.admin"))
                    .map(SubCommand::name)
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String subName = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            return switch (subName) {
                case "fundraiser" -> sender.hasPermission("vkchat.donate.admin")
                        ? List.of("start", "stop", "toggle").stream().filter(s -> s.startsWith(prefix)).toList()
                        : List.of();
                case "pass" -> sender.hasPermission("vkchat.donate.admin")
                        ? List.of("list", "give", "remove", "stats").stream().filter(s -> s.startsWith(prefix)).toList()
                        : List.of();
                default -> List.of();
            };
        }
        return List.of();
    }
}
