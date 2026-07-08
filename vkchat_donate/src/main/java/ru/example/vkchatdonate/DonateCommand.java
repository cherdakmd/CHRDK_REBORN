package ru.example.vkchatdonate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DonateCommand implements CommandExecutor, TabCompleter {
    private final VKChatDonatePlugin plugin;

    public DonateCommand(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(plugin.getDonateManager().getSetupInfo());
            return true;
        }

        if (args[0].equalsIgnoreCase("setup")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Укажи API-токен: " + ChatColor.WHITE + "/donate setup <токен>");
                sender.sendMessage(ChatColor.GRAY + "Токен получить: donatepay.ru → Мои кассы → API");
                return true;
            }

            String token = args[1];
            plugin.getConfig().set("api-token", token);
            plugin.saveConfig();
            plugin.reloadConfig();

            sender.sendMessage(ChatColor.GREEN + "✅ Токен сохранён!");
            sender.sendMessage(ChatColor.YELLOW + "Настраиваю LuckPerms...");

            String[] groups = {"spark", "flame", "star", "legend", "overlord"};
            // Вес: spark=1, flame=2, star=3, legend=4, overlord=5
            for (int i = 0; i < groups.length; i++) {
                String group = groups[i];
                int weight = i + 1;
                String prefix = plugin.getConfig().getString("statuses." + group + ".prefix", "&7");
                String display = plugin.getConfig().getString("statuses." + group + ".display", group);

                dispatch("lp creategroup " + group);
                dispatch("lp group " + group + " setweight " + weight);
                dispatch("lp group " + group + " setprefix " + prefix + " ");
                dispatch("lp group " + group + " meta addprefix " + weight + " " + prefix + " ");
                dispatch("lp group " + group + " permission set vkchat.donate." + group + " true");
                dispatch("lp group " + group + " permission set vkchat.donate.fundraiser.toggle true");
                dispatch("lp group " + group + " meta setprefix " + weight + " " + prefix + " ");

                sender.sendMessage(ChatColor.GRAY + "  " + display + " — вес " + weight + " — OK");
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "✅ Готово! Группы, префиксы, вес и права настроены.");
            sender.sendMessage(ChatColor.GRAY + "Перезапусти сервер для старта опроса API.");

            // Проходка без ВК
            dispatch("lp creategroup pass");
            dispatch("lp group pass setweight 0");
            dispatch("lp group pass permission set vkchat.pass true");
            sender.sendMessage(ChatColor.GRAY + "  🎫 Проходка (vkchat.pass) — OK");

            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getDonateManager().shutdown();
            sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен. Перезапусти плагин для старта опроса API.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") && sender instanceof Player) {
            Player p = (Player) sender;
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
            return true;
        }

        if (args[0].equalsIgnoreCase("days") && sender instanceof Player) {
            Player p = (Player) sender;
            DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
            if (s == null) {
                p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
                p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
            } else {
                long days = plugin.getDonateManager().getDaysLeft(p);
                p.sendMessage(ChatColor.GREEN + "Статус: " + s.name);
                p.sendMessage(ChatColor.YELLOW + "Осталось дней: " + ChatColor.WHITE + days);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            List<Map.Entry<String, Double>> top = plugin.getDonateManager().getTopDonors(10);
            sender.sendMessage(ChatColor.GOLD + "═══ ТОП-ДОНАТЕРЫ ═══");
            if (top.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Пока нет донатов.");
            } else {
                int rank = 1;
                for (Map.Entry<String, Double> e : top) {
                    String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "  ";
                    sender.sendMessage(ChatColor.YELLOW + medal + " " + rank + ". " + ChatColor.WHITE + e.getKey() +
                            ChatColor.GRAY + " — " + ChatColor.GOLD + String.format("%.0f", e.getValue()) + "₽");
                    rank++;
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("fundraiser")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.getDonateManager().getFundraiserInfo());
                sender.sendMessage(ChatColor.GRAY + "/donate fundraiser start <цель_в_рублях>");
                sender.sendMessage(ChatColor.GRAY + "/donate fundraiser stop");
                return true;
            }
            if (args[1].equalsIgnoreCase("start")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Укажи цель: /donate fundraiser start 10000");
                    return true;
                }
                try {
                    double goal = Double.parseDouble(args[2]);
                    plugin.getDonateManager().startFundraiser(goal);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Цель должна быть числом!");
                }
            } else if (args[1].equalsIgnoreCase("stop")) {
                plugin.getDonateManager().stopFundraiser();
            } else if (args[1].equalsIgnoreCase("toggle") && sender instanceof Player) {
                Player p = (Player) sender;
                if (plugin.getDonateManager().getPlayerStatus(p) == null) {
                    p.sendMessage(ChatColor.RED + "Только донатеры могут скрывать BossBar сбора!");
                    return true;
                }
                boolean visible = plugin.getDonateManager().toggleFundraiserBar(p);
                sender.sendMessage(ChatColor.GREEN + (visible ? "✅ BossBar сбора показан!" : "❌ BossBar сбора скрыт."));
            } else {
                sender.sendMessage(ChatColor.RED + "/donate fundraiser start <цель> | stop");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("pass")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.GOLD + "🎫 Проходки: /donate pass list | give <ник> | remove <ник>");
                return true;
            }
            if (args[1].equalsIgnoreCase("list")) {
                var holders = plugin.getDonateManager().getPassHolders();
                if (holders.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Нет активных проходок.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "🎫 Владельцы проходок (" + holders.size() + "):");
                    for (String name : holders) {
                        sender.sendMessage(ChatColor.WHITE + "  • " + name);
                    }
                }
            } else if (args[1].equalsIgnoreCase("give") && args.length >= 3) {
                plugin.getDonateManager().grantPassManually(args[2]);
                sender.sendMessage(ChatColor.GREEN + "✅ Проходка выдана: " + args[2]);
            } else if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
                plugin.getDonateManager().removePass(args[2]);
                sender.sendMessage(ChatColor.GREEN + "✅ Проходка отозвана: " + args[2]);
            } else {
                sender.sendMessage(ChatColor.RED + "/donate pass list | give <ник> | remove <ник>");
            }
            return true;
        }

        sender.sendMessage(plugin.getDonateManager().getSetupInfo());
        return true;
    }

    private void dispatch(String cmd) {
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("info", "status", "days", "top"));
            if (sender.hasPermission("vkchat.donate.admin")) {
                subs.addAll(Arrays.asList("setup", "reload", "fundraiser"));
            }
            String prefix = args[0].toLowerCase();
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fundraiser") && sender.hasPermission("vkchat.donate.admin")) {
            String prefix = args[1].toLowerCase();
            List<String> subs = new ArrayList<>(Arrays.asList("start", "stop", "toggle"));
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }
        return new ArrayList<>();
    }
}
