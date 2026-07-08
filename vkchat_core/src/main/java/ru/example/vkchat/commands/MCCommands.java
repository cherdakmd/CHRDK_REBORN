package ru.example.vkchat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.auth.TwoFactorManager;
import ru.example.vkchat.auth.SessionManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MCCommands implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final VKChatPlugin plugin;

    public MCCommands(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        
        if (name.equals("warn")) {
            if (!sender.hasPermission("vkchat.moderation.warn")) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Использование: /warn <игрок> <причина>");
                return true;
            }
            String target = args[0];
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) reasonBuilder.append(' ');
                reasonBuilder.append(args[i]);
            }
            String reason = reasonBuilder.toString();
            int count = plugin.getWarnManager().warn(target, sender.getName(), reason);
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Игрок " + target + " получил варн " + count + ".");
            return true;
        }

        if (name.equals("unwarn")) {
            if (!sender.hasPermission("vkchat.moderation.warn")) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Использование: /unwarn <игрок> [кол-во]");
                return true;
            }
            int amount = 1;
            if (args.length >= 2) {
                try { amount = Integer.parseInt(args[1]); } catch (Exception ignored) {}
            }
            int left = plugin.getWarnManager().removeWarn(args[0], amount);
            sender.sendMessage(org.bukkit.ChatColor.GREEN + "Снято варнов: " + amount + ". Осталось у " + args[0] + ": " + left);
            return true;
        }

        if (name.equals("clearwarns")) {
            if (!sender.hasPermission("vkchat.moderation.warn")) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Использование: /clearwarns <игрок>");
                return true;
            }
            plugin.getWarnManager().clearWarns(args[0]);
            sender.sendMessage(org.bukkit.ChatColor.GREEN + "Варны игрока " + args[0] + " очищены.");
            return true;
        }

        if (name.equals("warns")) {
            if (!sender.hasPermission("vkchat.moderation.warn")) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Использование: /warns <игрок>");
                return true;
            }
            int count = plugin.getWarnManager().getWarns(args[0]);
            java.util.List<String> history = plugin.getWarnManager().getHistory(args[0]);
            sender.sendMessage(org.bukkit.ChatColor.GOLD + "Варны " + args[0] + ": " + count);
            int from = Math.max(0, history.size() - 5);
            for (int i = from; i < history.size(); i++) sender.sendMessage(org.bukkit.ChatColor.GRAY + "- " + history.get(i));
            return true;
        }

        if (name.equals("vklink")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (plugin.getAuthManager().isLinked(p)) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("link_already")));
                return true;
            }
            String code = plugin.getAuthManager().generateLinkCode(p);
            if (code != null) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &f Твой уникальный код для привязки:"));
                
                net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', " &b&l" + code + " &7(нажми, чтобы скопировать)\n")
                );
                base.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, code));
                p.spigot().sendMessage(base);
                
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &fОтправь этот код прямо в нашу беседу ВК!"));
                
                net.md_5.bungee.api.chat.TextComponent chatLink = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', "\n &a&l[ПЕРЕЙТИ В БЕСЕДУ ВК]")
                );
                chatLink.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, plugin.getConfig().getString("vk.chat-invite-link", "https://vk.com/")));
                
                p.spigot().sendMessage(chatLink);
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&m================================================="));
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', " &6💡 Нет ВК? Купи проходку — &e/donate info &6(500₽/30д)"));
                p.sendMessage("");
            }
            return true;
        }
        
        if (name.equals("vkunlink")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            plugin.getAuthManager().unlink(p);
            p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("unlink_success")));
            return true;
        }
        
        if (name.equals("register")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (plugin.getAuthManager().isRegistered(p)) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_already_registered")));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_register_help")));
                return true;
            }
            
            String password = args[0];
            if (password.length() < 6) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Пароль слишком короткий! Минимальная длина — 6 символов.");
                return true;
            }
            if (password.equalsIgnoreCase(p.getName())) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Слишком простой пароль! Он не должен совпадать с вашим ником.");
                return true;
            }

            plugin.getAuthManager().register(p, password);
            
            String msg = plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_broadcast_welcome"))
                    .replace("{player}", p.getName())
                    .replace("{name}", "Игрок");
            plugin.getServer().broadcastMessage(msg);
            return true;
        }

        if (name.equals("2fa")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!plugin.getAuthManager().isWaiting2fa(p)) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Подтверждение не требуется. Войди: /login <пароль>");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Введи код из ЛС ВК: /2fa <код>");
                return true;
            }
            
            String code = args[0].trim();
            TwoFactorManager.TwoFactorResult result = plugin.getTwoFactorManager().confirm2fa(p.getUniqueId(), code);
            if (result == TwoFactorManager.TwoFactorResult.SUCCESS) {
                plugin.getSessionManager().setState(p.getUniqueId(), SessionManager.SessionState.LOGGED_IN);
                plugin.getAuthManager().setLoggedIn(p.getUniqueId(), true);
                plugin.getAuthManager().updateLastActivity(p.getUniqueId());
                p.sendMessage("");
                p.sendMessage(org.bukkit.ChatColor.GREEN + "✅ Подтверждено! Приятной игры.");
                p.sendMessage("");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } else if (result == TwoFactorManager.TwoFactorResult.WRONG_CODE) {
                int remaining = plugin.getTwoFactorManager().getRemainingAttempts(p.getUniqueId());
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Неверный код. Попыток: " + remaining);
            } else if (result == TwoFactorManager.TwoFactorResult.LOCKED) {
                p.sendMessage(org.bukkit.ChatColor.RED + "🔒 Слишком много попыток. Подожди 5 мин.");
            } else if (result == TwoFactorManager.TwoFactorResult.EXPIRED) {
                p.sendMessage(org.bukkit.ChatColor.RED + "⏰ Код просрочен. Перезайди на сервер.");
            } else {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Нет активного кода. Перезайди на сервер.");
            }
            return true;
        }
        
        if (name.equals("login")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;

            // Шорткат: если игрок ожидает 2FA, разрешаем подтвердить код и через /login <code>
            if (plugin.getTwoFactorManager().isWaiting2fa(p.getUniqueId())) {
                if (args.length < 1) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "❌ Введи код из ЛС ВК: /login <код>");
                    return true;
                }
                String code = args[0].trim();
                TwoFactorManager.TwoFactorResult result = plugin.getTwoFactorManager().confirm2fa(p.getUniqueId(), code);
                if (result == TwoFactorManager.TwoFactorResult.SUCCESS) {
                    plugin.getSessionManager().setState(p.getUniqueId(), SessionManager.SessionState.LOGGED_IN);
                    plugin.getAuthManager().setLoggedIn(p.getUniqueId(), true);
                    plugin.getAuthManager().updateLastActivity(p.getUniqueId());
                    p.sendMessage("");
                    p.sendMessage(org.bukkit.ChatColor.GREEN + "✅ Подтверждено! Приятной игры.");
                    p.sendMessage("");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    return true;
                } else if (result == TwoFactorManager.TwoFactorResult.WRONG_CODE) {
                    int remaining = plugin.getTwoFactorManager().getRemainingAttempts(p.getUniqueId());
                    p.sendMessage(org.bukkit.ChatColor.RED + "❌ Неверный код. Попыток: " + remaining);
                    return true;
                } else if (result == TwoFactorManager.TwoFactorResult.LOCKED) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "🔒 Слишком много попыток. Подожди 5 мин.");
                    return true;
                } else if (result == TwoFactorManager.TwoFactorResult.EXPIRED) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "⏰ Код просрочен. Перезайди на сервер.");
                    return true;
                }
            }

            if (!plugin.getAuthManager().isRegistered(p)) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_register_help")));
                return true;
            }
            if (plugin.getAuthManager().isLoggedIn(p)) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_already_logged")));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_login_help")));
                return true;
            }

            if (!plugin.getAuthManager().login(p, args[0])) {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_wrong_password")));
            }
            return true;
        }

        if (name.equals("logout")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            plugin.getAuthManager().logout(p);
            p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_logout_success")));
            return true;
        }
        
        if (name.equals("changepass")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 2) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Использование: /changepass <старый_пароль> <новый_пароль>");
                return true;
            }
            
            String newPass = args[1];
            if (newPass.length() < 6) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Новый пароль слишком короткий! Минимальная длина — 6 символов.");
                return true;
            }
            if (newPass.equalsIgnoreCase(p.getName())) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Новый пароль не должен совпадать с вашим ником!");
                return true;
            }

            if (plugin.getAuthManager().login(p, args[0])) {
                plugin.getAuthManager().changePassword(p, newPass);
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_pass_changed")));
                
                int vkId = plugin.getAuthManager().getLinkedVkId(p);
                if (vkId != -1) {
                    String ip = p.getAddress().getAddress().getHostAddress();
                    plugin.getVkManager().sendMessage(vkId, "🔐 Безопасность: Пароль к вашему игровому аккаунту '" + p.getName() + "' был успешно изменен с IP: " + ip + ".");
                }
            } else {
                p.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_wrong_password")));
            }
            return true;
        }
        
        if (name.equals("rep")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            
            int vkId = plugin.getAuthManager().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage("Твой аккаунт не привязан к ВК!");
                return true;
            }
            
            int currentRep = plugin.getReputationManager().getPoints(vkId);
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aТвоя репутация ВКонтакте: &e" + currentRep));
            return true;
        }
        
        if (name.equals("pay")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Эту команду может использовать только игрок!");
                return true;
            }
            
            Player p = (Player) sender;
            int senderVkId = plugin.getAuthManager().getLinkedVkId(p);
            if (senderVkId == -1) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Ваш аккаунт не привязан к ВК! Привяжите сначала через /vklink.");
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Использование: /pay <игрок> <количество>");
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Игрок не найден или оффлайн.");
                return true;
            }

            if (target.equals(p)) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Нельзя переводить репутацию самому себе!");
                return true;
            }

            int targetVkId = plugin.getAuthManager().getLinkedVkId(target);
            if (targetVkId == -1) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Игрок " + target.getName() + " не привязал свой аккаунт ВК!");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "❌ Сумма перевода должна быть положительным числом!");
                    return true;
                }
            } catch (NumberFormatException e) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Укажите корректное целое число для перевода.");
                return true;
            }

            int senderRep = plugin.getReputationManager().getPoints(senderVkId);
            if (senderRep < amount) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Недостаточно репутации! У вас на балансе: " + senderRep + " реп. ВК.");
                return true;
            }

            // Подтверждение для крупных сумм (>100 реп)
            if (amount > 100 && !args[args.length - 1].equals("confirm")) {
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "Вы переводите " + amount + " реп. ВК игроку " + target.getName() + "!");
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "Для подтверждения введите: " + org.bukkit.ChatColor.GREEN + "/pay " + target.getName() + " " + amount + " confirm");
                return true;
            }

            // Проводим транзакцию
            plugin.getReputationManager().deductPoints(senderVkId, amount);
            plugin.getReputationManager().addPoints(targetVkId, amount);

            p.sendMessage(org.bukkit.ChatColor.GREEN + "✓ [Репутация] Вы успешно перевели +" + amount + " репутации ВК игроку " + target.getName() + "!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

            target.sendMessage(org.bukkit.ChatColor.GREEN + "✓ [Репутация] Игрок " + p.getName() + " перевел вам +" + amount + " репутации ВК!");
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            return true;
        }


        if (name.equals("mute")) {
            if (!sender.hasPermission("vkchat.admin")) return true;
            if (args.length < 2) {
                sender.sendMessage("Использование: /mute <игрок> <минуты>");
                return true;
            }
            Player target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Игрок не найден.");
                return true;
            }
            try {
                int mins = Integer.parseInt(args[1]);
                plugin.getChatManager().mutePlayer(target.getUniqueId(), mins * 60000L);
                sender.sendMessage("Игрок " + target.getName() + " замучен на " + mins + " минут.");
            } catch (NumberFormatException e) {
                sender.sendMessage("Минуты должны быть числом.");
            }
            return true;
        }

        if (name.equals("unmute")) {
            if (!sender.hasPermission("vkchat.admin")) return true;
            if (args.length < 1) return false;
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
            plugin.getChatManager().unmutePlayer(op.getUniqueId());
            sender.sendMessage("Игрок размучен.");
            return true;
        }

        if (name.equals("ignore")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 1) {
                p.sendMessage("Использование: /ignore <игрок>");
                return true;
            }
            Player target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage("Игрок не найден.");
                return true;
            }
            if (target.equals(p)) {
                p.sendMessage("Нельзя игнорировать самого себя.");
                return true;
            }
            boolean ignored = plugin.getChatManager().toggleIgnore(p.getUniqueId(), target.getUniqueId());
            if (ignored) p.sendMessage(org.bukkit.ChatColor.YELLOW + "Вы добавили игрока " + target.getName() + " в черный список.");
            else p.sendMessage(org.bukkit.ChatColor.GREEN + "Вы убрали игрока " + target.getName() + " из черного списка.");
            return true;
        }
        if (name.equals("menu")) {
            if (sender instanceof Player) {
                plugin.getGuiListener().openServerMenu((Player) sender);
            }
            return true;
        }

        if (name.equals("bal") || name.equals("balance")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            int vkId = plugin.getAuthManager().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Аккаунт не привязан к ВК!");
                return true;
            }
            int rep = plugin.getReputationManager().getPoints(vkId);
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&6Ваш баланс: &e" + rep + " &6реп. ВК"));
            return true;
        }

        if (name.equals("online")) {
            int count = plugin.getServer().getOnlinePlayers().size();
            int max = plugin.getServer().getMaxPlayers();
            sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&aОнлайн: &e" + count + "&a/&e" + max));
            return true;
        }

        if (name.equals("lastseen")) {
            if (args.length < 1) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Использование: /lastseen <игрок>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target != null && target.isOnline()) {
                sender.sendMessage(org.bukkit.ChatColor.GREEN + args[0] + " сейчас онлайн!");
                return true;
            }
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                if (conn != null) {
                    UUID targetUuid = null;
                    try (PreparedStatement lookup = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE uuid = ?")) {
                        org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
                        if (offline != null && offline.hasPlayedBefore()) {
                            targetUuid = offline.getUniqueId();
                        }
                    }
                    if (targetUuid == null) {
                        sender.sendMessage(org.bukkit.ChatColor.GRAY + "Игрок " + args[0] + " не найден.");
                        return true;
                    }
                    try (PreparedStatement ps = conn.prepareStatement("SELECT reg_date FROM vkchat_auth WHERE uuid = ?")) {
                        ps.setString(1, targetUuid.toString());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                long lastLogin = rs.getLong("reg_date");
                                if (lastLogin > 0) {
                                    long diff = System.currentTimeMillis() - lastLogin;
                                    String timeAgo = formatTimeDiff(diff);
                                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + args[0] + " был(а) последний раз: " + timeAgo + " назад");
                                } else {
                                    sender.sendMessage(org.bukkit.ChatColor.GRAY + args[0] + " никогда не заходил(а).");
                                }
                            } else {
                                sender.sendMessage(org.bukkit.ChatColor.GRAY + "Игрок " + args[0] + " не найден.");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "Ошибка при поиске игрока.");
            }
            return true;
        }

        if (name.equals("vk")) {
            if (sender instanceof Player) {
                plugin.getGuiListener().openMainMenu((Player) sender);
            }
            return true;
        }

        if (name.equals("vkchat")) {
            if (!sender.hasPermission("vkchat.admin")) return true;
            if (args.length == 0) {
                sender.sendMessage("VKChat Admin Commands:");
                sender.sendMessage("/vkchat give <player> <amount> - Начислить репутацию игроку");
                sender.sendMessage("/vkchat set <player> <amount> - Установить репутацию игроку");
                sender.sendMessage("/vkchat wall <text>");
                sender.sendMessage("/vkchat promo <code> <uses> <reward> - Создать промокод для ВК");
                sender.sendMessage("/vkchat linked");
                sender.sendMessage("/vkchat migrate - Перенести данные из YML в MySQL/SQLite");
                sender.sendMessage("/vkchat economy - Создать аудит экономики");
                sender.sendMessage("/vkchat reload - Перезагрузить конфигурацию");
                return true;
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sender.sendMessage("Использование: /vkchat give <player> <amount>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Игрок не найден или оффлайн.");
                    return true;
                }
                int vkId = plugin.getAuthManager().getLinkedVkId(target);
                if (vkId == -1) {
                    sender.sendMessage("Игрок не привязал свой аккаунт ВК!");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    plugin.getReputationManager().addPoints(vkId, amount);
                    sender.sendMessage("Игроку " + target.getName() + " успешно начислено " + amount + " репутации ВК!");
                    target.sendMessage(org.bukkit.ChatColor.GREEN + "✓ [Репутация] Администратор начислил вам +" + amount + " репутации ВК!");
                } catch (NumberFormatException e) {
                    sender.sendMessage("Количество должно быть целым числом.");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("set")) {
                if (args.length < 3) {
                    sender.sendMessage("Использование: /vkchat set <player> <amount>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Игрок не найден или оффлайн.");
                    return true;
                }
                int vkId = plugin.getAuthManager().getLinkedVkId(target);
                if (vkId == -1) {
                    sender.sendMessage("Игрок не привязал свой аккаунт ВК!");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    plugin.getReputationManager().setPoints(vkId, amount);
                    sender.sendMessage("Игроку " + target.getName() + " успешно установлен баланс: " + amount + " репутации ВК!");
                    target.sendMessage(org.bukkit.ChatColor.GREEN + "✓ [Репутация] Администратор установил ваш баланс: " + amount + " репутации ВК.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("Количество должно быть целым числом.");
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAll();
                sender.sendMessage(org.bukkit.ChatColor.GREEN + "✓ VKChat конфигурация и сообщения успешно перезагружены!");
                return true;
            } else if (args[0].equalsIgnoreCase("economy")) {
                if (!sender.hasPermission("vkchat.admin")) return true;
                try {
                    java.io.File report = new java.io.File(plugin.getDataFolder(), "economy-report.md");
                    StringBuilder sb = new StringBuilder();
                    sb.append("# VKChat Economy Audit\n\n");
                    sb.append("Generated: ").append(new java.util.Date()).append("\n\n");
                    sb.append("## Key reputation sinks\n\n");
                    appendPluginValue(sb, "VKChatGear", "hardcore-forging.craft-cost", "Gear craft cost", 120);
                    appendPluginValue(sb, "VKChatGear", "hardcore-forging.reforge-cost", "Gear reforge cost", 650);
                    appendPluginValue(sb, "VKChatGear", "hardcore-forging.cleanse-cost", "Defect cleanse cost", 350);
                    appendPluginValue(sb, "VKChatGear", "hardcore-forging.rune-apply-cost", "Rune apply cost", 75);
                    appendPluginValue(sb, "VKChatGear", "hardcore-forging.crystal-apply-cost", "Crystal apply cost", 50);
                    appendPluginValue(sb, "VKChatMobs", "reputation.max-farm-per-hour", "Mob farm cap per hour", 300);
                    sb.append("\n## Warnings\n\n");
                    sb.append("- Check high pure-reputation rewards: prefer items/materials for repeatable content.\n");
                    sb.append("- Expensive/risky actions should be confirmed in GUI or explained in lore.\n");
                    sb.append("- Keep anti-farm enabled for mobs.\n");
                    java.nio.file.Files.write(report.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    sender.sendMessage("§aEconomy report created: " + report.getAbsolutePath());
                } catch (Exception ex) {
                    sender.sendMessage("§cFailed to create economy report: " + ex.getMessage());
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("migrate")) {
                sender.sendMessage("Начинаю миграцию...");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        File authFile = new File(plugin.getDataFolder(), "auth.yml");
                        FileConfiguration authConf = YamlConfiguration.loadConfiguration(authFile);
                        File repFile = new File(plugin.getDataFolder(), "reputation.yml");
                        FileConfiguration repConf = YamlConfiguration.loadConfiguration(repFile);
                        File statsFile = new File(plugin.getDataFolder(), "stats.yml");
                        FileConfiguration statsConf = YamlConfiguration.loadConfiguration(statsFile);
                        
                        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                        
                        // Migrating auth
                        if (authConf.getKeys(false) != null) {
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO vkchat_auth (uuid, vk_id, password, last_ip, reg_date) VALUES (?, ?, ?, ?, ?)")) {
                                for (String key : authConf.getKeys(false)) {
                                    if (key.length() < 30) continue;
                                    ps.setString(1, key);
                                    ps.setInt(2, authConf.getInt(key + ".vk_id", -1));
                                    ps.setString(3, authConf.getString(key + ".password", null));
                                    ps.setString(4, authConf.getString(key + ".last_ip", "127.0.0.1"));
                                    ps.setLong(5, authConf.getLong(key + ".reg_date", 0));
                                    ps.addBatch();
                                }
                                ps.executeBatch();
                            }
                        }
                        
                        sender.sendMessage("Миграция успешно завершена!");
                        }
                    } catch (Exception e) {
                        sender.sendMessage("Ошибка при миграции: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else if (args[0].equalsIgnoreCase("wall")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                sender.sendMessage("Анонс отправлен.");
} else if (args[0].equalsIgnoreCase("promo")) {
                if (args.length < 4) {
                    sender.sendMessage("Использование: /vkchat promo <code> <uses> <reward>");
                    return true;
                }
                try {
                    String code = args[1].toUpperCase();
                    int uses = Integer.parseInt(args[2]);
                    int reward = Integer.parseInt(args[3]);
                    // Промокоды остались в репутация.yml
                    plugin.getReputationManager().createPromo(code, reward, uses);
                    sender.sendMessage("Промокод " + code + " создан! (Использований: " + uses + ", Награда: " + reward + ")");
                } catch (NumberFormatException e) {
                    sender.sendMessage("Uses и reward должны быть числами.");
                }
            } else if (args[0].equalsIgnoreCase("linked")) {
                sender.sendMessage("Linked players checking can be done in DB.");
            } else if (args[0].equalsIgnoreCase("unlink")) {
                if (args.length > 1) {
                    Player target = plugin.getServer().getPlayer(args[1]);
                    if (target != null) {
                        plugin.getAuthManager().unlink(target);
                        sender.sendMessage("✓ Игрок " + target.getName() + " успешно отвязан от ВКонтакте.");
                    } else {
                        final String targetName = args[1];
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            @SuppressWarnings("deprecation")
                            org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(targetName);
                            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                                plugin.getAuthManager().getLinkStorage().unlink(offline.getUniqueId());
                                sender.sendMessage("✓ Оффлайн-игрок " + targetName + " успешно отвязан от ВКонтакте!");
                            } else {
                                sender.sendMessage("❌ Игрок " + targetName + " не найден на сервере.");
                            }
                        });
                    }
                }
            } else if (args[0].equalsIgnoreCase("stats")) {
                sender.sendMessage("Server Stats:");
                sender.sendMessage("Today joins: " + plugin.getStatsManager().getTodayJoins());
                sender.sendMessage("Total joins: " + plugin.getStatsManager().getTotalJoins());
            }
            return true;
        }

        return false;
    }

    private void appendPluginValue(StringBuilder sb, String pluginName, String path, String label, int def) {
        try {
            org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin(pluginName);
            int value = def;
            if (pl instanceof org.bukkit.plugin.java.JavaPlugin) {
                value = ((org.bukkit.plugin.java.JavaPlugin) pl).getConfig().getInt(path, def);
            }
            sb.append("- **").append(label).append("** (`").append(pluginName).append(":" ).append(path).append("`): ").append(value).append("\n");
        } catch (Exception e) {
            sb.append("- **").append(label).append("** (`").append(pluginName).append(":" ).append(path).append("`): ").append(def).append(" (default)\n");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        switch (name) {
            case "warn":
            case "unwarn":
            case "clearwarns":
            case "warns":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayerNames(last));
                }
                break;
            case "mute":
                if (args.length == 1) completions.addAll(getOnlinePlayerNames(last));
                else if (args.length == 2) completions.addAll(Arrays.asList("5", "10", "15", "30", "60"));
                break;
            case "unmute":
            case "ignore":
                if (args.length == 1) completions.addAll(getOnlinePlayerNames(last));
                break;
            case "pay":
                if (args.length == 1) completions.addAll(getOnlinePlayerNames(last));
                else if (args.length == 2) completions.addAll(Arrays.asList("10", "25", "50", "100", "confirm"));
                break;
            case "lastseen":
            case "был":
                if (args.length == 1) completions.addAll(getOnlinePlayerNames(last));
                break;
            case "vkchat":
                if (args.length == 1) {
                    completions.addAll(Arrays.asList("give", "add", "set", "wall", "promo", "linked", "unlink", "migrate", "economy", "stats", "reload"));
                } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("unlink"))) {
                    completions.addAll(getOnlinePlayerNames(last));
                }
                break;
            default:
                break;
        }
        return filterCompletions(completions, last);
    }

    private List<String> getOnlinePlayerNames(String prefix) {
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> prefix.isEmpty() || n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private List<String> filterCompletions(List<String> completions, String prefix) {
        if (prefix.isEmpty()) return completions;
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private String formatTimeDiff(long ms) {
        long sec = ms / 1000;
        if (sec < 60) return sec + " сек";
        long min = sec / 60;
        if (min < 60) return min + " мин";
        long hrs = min / 60;
        if (hrs < 24) return hrs + " ч " + (min % 60) + " мин";
        long days = hrs / 24;
        return days + " д " + (hrs % 24) + " ч";
    }

}
