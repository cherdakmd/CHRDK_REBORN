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
 * DonateCommand v2.1.0 — рефакторинг с таблицей подкоманд.
 * Убирает лестницу if-else, добавляет структурированную обработку.
 */
public class DonateCommand implements CommandExecutor, TabCompleter {
    private final VKChatDonatePlugin plugin;

    /** Описание подкоманды */
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

        subCommands.put("setup", new SubCommand("setup", true, false, "<API-токен>",
                (s, a) -> handleSetup(s, a)));

        subCommands.put("reload", new SubCommand("reload", true, false, "",
                (s, a) -> handleReload(s)));

        subCommands.put("fundraiser", new SubCommand("fundraiser", true, false, "start <цель> | stop | toggle",
                (s, a) -> handleFundraiser(s, a)));

        subCommands.put("pass", new SubCommand("pass", true, false, "list | give <ник> | remove <ник>",
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

        // Проверки прав и типа отправителя
        if (sub.adminOnly() && !sender.hasPermission("vkchat.donate.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков.");
            return true;
        }

        // Вызов обработчика
        sub.handler().accept(sender, args);
        return true;
    }

    // ═══════════════════════════════════════
    // ОБРАБОТЧИКИ ПОДКОМАНД
    // ═══════════════════════════════════════

    private void showStatus(Player p) {
        DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
        if (s == null) {
            p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
            p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
        } else {
            p.sendMessage(ChatColor.GREEN + "Ваш статус: " + s.name);
            p.sendMessage(ChatColor.GRAY + "Скидка: " + (int)(s.repDiscount * 100)
                    + "% | КД ТП: ×" + s.tpCooldownMult
                    + " | Домов: " + s.maxHomes);
        }
    }

    private void showDaysLeft(Player p) {
        DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
        if (s == null) {
            p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
            p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
        } else {
            long days = plugin.getDonateManager().getDaysLeft(p);
            p.sendMessage(ChatColor.GREEN + "Статус: " + s.name);
            p.sendMessage(ChatColor.YELLOW + "Осталось дней: " + ChatColor.WHITE + days);
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

        // Создание групп из конфигурации (динамически, не хардкод)
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

        // Проходка без ВК
        dispatch("lp creategroup pass");
        dispatch("lp group pass setweight 0");
        dispatch("lp group pass permission set vkchat.pass true");
        sender.sendMessage(ChatColor.GRAY + "  🎫 Проходка (vkchat.pass) — OK");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getDonateManager().shutdown();
        sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен. Перезапусти плагин для старта опроса API.");
    }

    private void handleFundraiser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getDonateManager().getFundraiserInfo());
            sender.sendMessage(ChatColor.GRAY + "/donate fundraiser start <цель_в_рублях>");
            sender.sendMessage(ChatColor.GRAY + "/donate fundraiser stop");
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
                    p.sendMessage(ChatColor.RED + "Только донатеры могут скрывать BossBar сбора!");
                    return;
                }
                boolean visible = plugin.getDonateManager().toggleFundraiserBar(p);
                sender.sendMessage(ChatColor.GREEN + (visible ? "✅ BossBar сбора показан!" : "❌ BossBar сбора скрыт."));
            }
            default -> sender.sendMessage(ChatColor.RED + "/donate fundraiser start <цель> | stop | toggle");
        }
    }

    private void handlePass(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "🎫 Проходки: /donate pass list | give <ник> | remove <ник>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                var holders = plugin.getDonateManager().getPassHolders();
                if (holders.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Нет активных проходок.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "🎫 Владельцы проходок (" + holders.size() + "):");
                    for (String name : holders) {
                        sender.sendMessage(ChatColor.WHITE + "  • " + name);
                    }
                }
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "/donate pass give <ник>");
                    return;
                }
                plugin.getDonateManager().grantPassManually(args[2]);
                sender.sendMessage(ChatColor.GREEN + "✅ Проходка выдана: " + args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "/donate pass remove <ник>");
                    return;
                }
                plugin.getDonateManager().removePass(args[2]);
                sender.sendMessage(ChatColor.GREEN + "✅ Проходка отозвана: " + args[2]);
            }
            default -> sender.sendMessage(ChatColor.RED + "/donate pass list | give <ник> | remove <ник>");
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
                        ? List.of("list", "give", "remove").stream().filter(s -> s.startsWith(prefix)).toList()
                        : List.of();
                default -> List.of();
            };
        }
        return List.of();
    }
}
