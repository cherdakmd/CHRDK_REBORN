package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.lang.reflect.Method;

public class VKCommandHandler {
    private static final Map<Integer, Long> lastMessageTimes = new ConcurrentHashMap<>();

    public static void handle(VKChatPlugin plugin, String text, int fromId, int peer) {
        String[] args = text.split(" ");
        String cmd = args[0].toLowerCase();
        int mainChatId = plugin.getConfig().getInt("vk.peer-id");
        
        if (peer == mainChatId) {
            plugin.getReputationManager().addMessage(fromId, text);
        }

        if (cmd.startsWith("!")) {
            ru.example.vkchat.api.VKCommandEvent cmdEvent = new ru.example.vkchat.api.VKCommandEvent(peer, fromId, cmd, args);
            Bukkit.getPluginManager().callEvent(cmdEvent);
            if (cmdEvent.isCancelled()) return;
        }

        if (cmd.equals("!помощь") || cmd.equals("!help") || cmd.equals("!клавиатура")) {
            String help = getVkHelpMessage(plugin);
            
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, help, VKKeyboardBuilder.helpInlineKeyboard());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, help);
            }
        } else if (cmd.equals("!меню")) {
            // Главное меню в ЛС — переключение между режимами
            if (peer < 2000000000) {
                String welcome = "🏠 Главное меню CHRDK REBORN\n\n" +
                        "Выбери режим:\n" +
                        "⛏ Смены — шахтёрские смены через ВК\n" +
                        "👤 Аккаунт — управление профилем\n\n" +
                        "Или используй команды напрямую.";
                plugin.getVkManager().sendKeyboard(peer, welcome, VKKeyboardBuilder.mainDmMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "Используй эту команду в ЛС бота.");
            }
        } else if (cmd.equals("!online") || cmd.equals("!онлайн")) {
            int count = Bukkit.getOnlinePlayers().size();
            String players = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.joining(", "));
            if (players.isEmpty()) players = "Никого нет";
            String msg = plugin.getConfigManager().getMessage("vk_cmd_online")
                    .replace("{count}", String.valueOf(count))
                    .replace("{players}", players);
            plugin.getVkManager().sendMessage(peer, fromId, ChatColor.stripColor(msg));
        } else if (cmd.equals("!stats")) {
            int today = plugin.getStatsManager().getTodayJoins();
            int total = plugin.getStatsManager().getTotalJoins();
            String msg = plugin.getConfigManager().getMessage("vk_cmd_stats")
                    .replace("{today}", String.valueOf(today))
                    .replace("{total}", String.valueOf(total));
            plugin.getVkManager().sendMessage(peer, fromId, ChatColor.stripColor(msg));
        } else if (cmd.equals("!status")) {
            int count = Bukkit.getOnlinePlayers().size();
            String msg = plugin.getConfigManager().getMessage("vk_cmd_status")
                    .replace("{count}", String.valueOf(count));
            plugin.getVkManager().sendMessage(peer, fromId, ChatColor.stripColor(msg));
        } else if (cmd.equals("!топ") || cmd.equals("!top")) {
            String top = plugin.getStatsManager().getTopPlayersString();
            String msg = plugin.getConfigManager().getMessage("vk_cmd_top")
                    .replace("{top}", top);
            plugin.getVkManager().sendMessage(peer, fromId, ChatColor.stripColor(msg));
        } else if (cmd.equals("!топреп") || cmd.equals("!toprep")) {
            String top = plugin.getReputationManager().getTopReputation();
            plugin.getVkManager().sendMessage(peer, fromId, " Топ богачей чата (Репутация):\n" + top);
        } else if (cmd.equals("!промо") || cmd.equals("!promo")) {
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !промо <код>");
                return;
            }
            String code = args[1].toUpperCase();
            String result = plugin.getReputationManager().usePromo(fromId, code);
            plugin.getVkManager().sendMessage(peer, fromId, result);
        } else if (cmd.equals("!варн") || cmd.equals("!warn")) {
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "⛔ У вас нет прав выдавать варны.");
                return;
            }
            if (args.length < 3) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !варн <ник> <причина>");
                return;
            }
            String target = args[1];
            StringBuilder reason = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) reason.append(' ');
                reason.append(args[i]);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                int count = plugin.getWarnManager().warn(target, "VK:" + fromId, reason.toString());
                plugin.getVkManager().sendMessage(peer, fromId, "⚠ Игрок " + target + " получил варн " + count + "/3. Причина: " + reason);
            });

        } else if (cmd.equals("!unwarn") || cmd.equals("!снятьварн")) {
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "⛔ У вас нет прав снимать варны.");
                return;
            }
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !unwarn <ник> [кол-во]");
                return;
            }
            int amount = 1;
            if (args.length >= 3) {
                try { amount = Integer.parseInt(args[2]); } catch (Exception ignored) {}
            }
            int left = plugin.getWarnManager().removeWarn(args[1], amount);
            plugin.getVkManager().sendMessage(peer, fromId, "✅ Снято варнов: " + amount + ". Осталось у " + args[1] + ": " + left);

        } else if (cmd.equals("!warns") || cmd.equals("!варны")) {
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "⛔ У вас нет прав смотреть варны.");
                return;
            }
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !варны <ник>");
                return;
            }
            int count = plugin.getWarnManager().getWarns(args[1]);
            java.util.List<String> history = plugin.getWarnManager().getHistory(args[1]);
            StringBuilder out = new StringBuilder("⚠ Варны " + args[1] + ": " + count + "\n");
            int from = Math.max(0, history.size() - 5);
            for (int i = from; i < history.size(); i++) out.append("• ").append(history.get(i)).append("\n");
            plugin.getVkManager().sendMessage(peer, fromId, out.toString());

        } else if (cmd.equals("!cmd")) {
            // Админ-панель: Выполнение консольных команд из ВКонтакте
            // peer < 2000000000 означает, что это личные сообщения, а не беседа
            if (peer >= 2000000000) {
                return; // Игнорируем команду, если она написана в общей беседе
            }
            
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "⛔ У вас нет прав на выполнение консольных команд!");
                return;
            }

            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, " Использование: !cmd <команда>\nПример: !cmd say Привет всем!");
                return;
            }

            StringBuilder commandBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                commandBuilder.append(args[i]).append(" ");
            }
            String serverCommand = commandBuilder.toString().trim();

            plugin.getLogger().info("[VK Admin] " + fromId + " executed: " + serverCommand);

            // Выполняем команду в основном потоке сервера
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), serverCommand);
                if (success) {
                    plugin.getVkManager().sendMessage(peer, fromId, "✅ Команда '/" + serverCommand + "' успешно отправлена в консоль сервера.");
                } else {
                    plugin.getVkManager().sendMessage(peer, fromId, "⚠️ Команда отправлена, но возможно произошла ошибка выполнения.");
                }
            });

        } else if (cmd.equals("!профиль") || cmd.equals("!profile")) {
            UUID targetUuid = null;
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
                ps.setInt(1, fromId);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    targetUuid = UUID.fromString(rs.getString("uuid"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка ВК команды: " + e.getMessage());
            }
            
            if (targetUuid == null) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Твой аккаунт ВКонтакте не привязан к серверу Minecraft!");
                return;
            }
            
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(targetUuid);
            
            // Получаем актуальные данные из StatsManager (который сам работает с БД или кешем)
            int kills = plugin.getStatsManager().getKills(targetUuid);
            int deaths = plugin.getStatsManager().getDeaths(targetUuid);
            int blocks = plugin.getStatsManager().getBlocks(targetUuid);
            int achievements = plugin.getStatsManager().getAchievements(targetUuid);
            int rep = plugin.getReputationManager().getPoints(fromId);
            
            int rank = plugin.getStatsManager().getRank(targetUuid);
            int total = plugin.getStatsManager().getServerTotalPlayers();
            
            String balance = plugin.isVaultEnabled() && plugin.getStatsManager().getEconomy() != null ? String.format("%.2f", plugin.getStatsManager().getEconomy().getBalance(op)) + "$" : "Выкл";
            
            String nationName = "Нет фракции";
            try {
                org.bukkit.plugin.Plugin nationsPlugin = Bukkit.getPluginManager().getPlugin("VKChatNations");
                if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                    Method getNationMgr = nationsPlugin.getClass().getMethod("getNationManager");
                    Object nationMgr = getNationMgr.invoke(nationsPlugin);
                    Method getPlayerNation = nationMgr.getClass().getMethod("getPlayerNation", UUID.class);
                    Object result = getPlayerNation.invoke(nationMgr, targetUuid);
                    if (result != null) {
                        Method getNationName = nationMgr.getClass().getMethod("getNationNamePublic", String.class);
                        nationName = (String) getNationName.invoke(nationMgr, (String) result);
                        // Очищаем и §, и & цветовые коды
                        nationName = nationName.replaceAll("(?i)[&§][0-9a-f-l-or]", "");
                    }
                }
            } catch (Exception ignored) {}

            long joinTime = 0;
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT reg_date FROM vkchat_auth WHERE uuid = ?");
                ps.setString(1, targetUuid.toString());
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    joinTime = rs.getLong("reg_date");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка ВК команды: " + e.getMessage());
            }
            String regDate = joinTime > 0 ? new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(joinTime)) : "Неизвестно";
            
            double kdRatio = deaths == 0 ? kills : (double) kills / deaths;
            String kdFormatted = String.format("%.2f", kdRatio);

            int warns = plugin.getWarnManager() != null ? plugin.getWarnManager().getWarns(op.getName()) : 0;
            String warnStatus = warns <= 0 ? "✅ Нет предупреждений" : ("⚠ " + warns + "/3" + (warns >= 3 ? " (есть риск автобана)" : ""));

            String profile = " ПРОФИЛЬ ИГРОКА " + op.getName() + "\n" +
                             " Регистрация: " + regDate + "\n" +
                             " Фракция: " + nationName + "\n\n" +
                             " РЕЙТИНГ И ЭКОНОМИКА\n" +
                             " Место в топе сервера: " + rank + " из " + total + "\n" +
                             " Репутация ВК: " + rep + " очков\n" +
                             " Игровой баланс: " + balance + "\n\n" +
                             "⚠ МОДЕРАЦИЯ\n" +
                             " Варны: " + warnStatus + "\n\n" +
                             "⚔ СТАТИСТИКА ВЫЖИВАНИЯ\n" +
                             " Убийств: " + kills + "\n" +
                             " Смертей: " + deaths + "\n" +
                             " K/D Ratio (У/С): " + kdFormatted + "\n" +
                             "⛏ Сломано блоков: " + blocks + "\n" +
                             " Достижений: " + achievements;
            plugin.getVkManager().sendMessage(peer, fromId, profile);

        } else if (cmd.equals("!смена") || cmd.equals("!shift") || cmd.equals("!шахта")) {
            String shiftInfo = "⛏ Шахтёрские смены\n\n" +
                    "Отправляйся в шахту и получай ресурсы!\n" +
                    "Доступные длительности:\n" +
                    "• 1 час — базовые ресурсы\n" +
                    "• 3 часа — улучшенный лут\n" +
                    "• 8 часов — редкие ресурсы\n" +
                    "• 12 часов — максимальная награда\n\n" +
                    "Награду забирай через /stash в игре!";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, shiftInfo, VKKeyboardBuilder.mainDmMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "Эта команда работает в ЛС бота ВК. Напиши боту в личные сообщения!");
            }
        } else if (cmd.equals("!донат") || cmd.equals("!donate")) {
            String donateInfo = "💰 Поддержка сервера CHRDK REBORN\n\n" +
                    "5 статусов от 250₽ до 5000₽ на 30 дней!\n" +
                    "Скидки до -65% на длительные подписки.\n\n" +
                    "• Bronze — базовые бонусы\n" +
                    "• Silver — улучшенные награды\n" +
                    "• Gold — премиум доступ\n" +
                    "• Diamond — максимальные привилегии\n" +
                    "• Legend — эксклюзивный статус\n\n" +
                    "Подробнее: /donate info в игре\n" +
                    "Поддержать: https://donatepay.ru/don/CHRDK";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, donateInfo, VKKeyboardBuilder.mainDmMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, donateInfo);
            }
        } else if (cmd.equals("!работы") || cmd.equals("!jobs")) {
            plugin.getVkManager().sendMessage(peer, fromId, "Эта команда теперь обрабатывается модулем VKChatJobs.");
        } else if (cmd.equals("!анекдот") || cmd.equals("!joke")) {
            plugin.getVkManager().sendMessage(peer, fromId, " " + plugin.getGamesManager().getRandomJoke());
        } else if (cmd.equals("!сейф") || cmd.equals("!safe")) {
            plugin.getVkManager().sendMessage(peer, fromId, " Взлом сейфа!\nУгадай трехзначный код сейфа с помощью команды: !код <число>\nТекущая награда: " + plugin.getGamesManager().getLastSafeReward() + " очков!");
        } else if (cmd.equals("!код") || cmd.equals("!code")) {
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !код <число от 100 до 999>");
                return;
            }
            try {
                int code = Integer.parseInt(args[1]);
                plugin.getGamesManager().trySafe(fromId, code, peer);
            } catch (NumberFormatException e) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Введи числовое значение!");
            }
        } else if (cmd.equals("!рейтинг") || cmd.equals("!rating")) {
            int rep = plugin.getReputationManager().getPoints(fromId);
            int rank = plugin.getReputationManager().getRank(fromId);
            int total = plugin.getReputationManager().getTotalPlayers();
            plugin.getVkManager().sendMessage(peer, fromId, "Твоя репутация в чате: " + rep + " очков.\n Твое место в рейтинге: " + rank + " из " + total);
        } else if (cmd.equals("!клейм") || cmd.equals("!claim")) {
            if (!plugin.isVaultEnabled()) {
                plugin.getVkManager().sendMessage(peer, fromId, "На сервере отключена экономика Vault (Обмен недоступен).");
                return;
            }
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, org.bukkit.ChatColor.stripColor(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("exchange_invalid"))));
                return;
            }
            try {
                int amount = Integer.parseInt(args[1]);
                if (amount <= 0) throw new NumberFormatException();
                
                int currentRep = plugin.getReputationManager().getPoints(fromId);
                if (currentRep < amount) {
                    plugin.getVkManager().sendMessage(peer, fromId, org.bukkit.ChatColor.stripColor(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("exchange_fail").replace("{rep}", String.valueOf(currentRep)))));
                    return;
                }
                
                double rateVal = plugin.getConfig().getDouble("reputation.to-money-rate", 10.0);
                double moneyEarned = amount / rateVal;

                java.util.UUID targetUuid = null;
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                    java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
                    ps.setInt(1, fromId);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        targetUuid = java.util.UUID.fromString(rs.getString("uuid"));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка ВК команды: " + e.getMessage());
                }
                
                if (targetUuid == null) {
                    plugin.getVkManager().sendMessage(peer, fromId, "Твой аккаунт не привязан к серверу Minecraft!");
                    return;
                }
                
                plugin.getReputationManager().deductPoints(fromId, amount);
                org.bukkit.OfflinePlayer targetOp = org.bukkit.Bukkit.getOfflinePlayer(targetUuid);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getStatsManager().getEconomy().depositPlayer(targetOp, moneyEarned);
                });
                
                int newRep = plugin.getReputationManager().getPoints(fromId);
                String successMsg = plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("reputation_claim_success")
                        .replace("{money}", String.format("%.2f", moneyEarned))
                        .replace("{rep}", String.valueOf(amount)));
                plugin.getVkManager().sendMessage(peer, fromId, org.bukkit.ChatColor.stripColor(successMsg));
            } catch (NumberFormatException ex) {
                plugin.getVkManager().sendMessage(peer, fromId, org.bukkit.ChatColor.stripColor(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("exchange_invalid"))));
            }
        } else if (cmd.equals("!бонус") || cmd.equals("!bonus")) {
            int bonusAmount = plugin.getConfig().getInt("reputation.daily-join-reward", 10);
            if (plugin.getReputationManager().claimDailyBonus(fromId, bonusAmount)) {
                plugin.getVkManager().sendMessage(peer, fromId, " Ты получил свой ежедневный бонус: " + bonusAmount + " очков репутации!");
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "⏳ Ты уже получал бонус сегодня! Возвращайся завтра.");
            }
        } else if (cmd.equals("!казино") || cmd.equals("!casino")) {
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, " 🎰 Использование: !казино <ставка> или !казино <процент>%\nПример: !казино 100 или !казино 15%\nТвоя репутация: " + plugin.getReputationManager().getPoints(fromId));
                return;
            }
            int currentRep = plugin.getReputationManager().getPoints(fromId);
            String betStr = args[1].trim();
            int bet = 0;

            if (betStr.endsWith("%")) {
                try {
                    int percent = Integer.parseInt(betStr.replace("%", ""));
                    if (percent <= 0 || percent > 100) {
                        plugin.getVkManager().sendMessage(peer, fromId, "❌ Процент ставки должен быть от 1% до 100%!");
                        return;
                    }
                    bet = (int) Math.ceil(currentRep * (percent / 100.0));
                } catch (NumberFormatException e) {
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Неверный формат процента! Пример: !казино 50%");
                    return;
                }
            } else {
                try {
                    bet = Integer.parseInt(betStr);
                } catch (NumberFormatException e) {
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Неверная ставка! Укажите число или процент (например, !казино 25%).");
                    return;
                }
            }

            if (bet <= 0) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Ставка должна быть больше нуля!");
                return;
            }
            if (currentRep < bet) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Недостаточно репутации! Твоя репутация: " + currentRep + " (требуется ставка: " + bet + ")");
                return;
            }
            
            if (Math.random() < 0.48) {
                plugin.getReputationManager().addPoints(fromId, bet);
                plugin.getVkManager().sendMessage(peer, fromId, "🎉 Поздравляем! Ты выиграл " + bet + " очков! \nТвой баланс: " + plugin.getReputationManager().getPoints(fromId));
            } else {
                plugin.getReputationManager().deductPoints(fromId, bet);
                plugin.getVkManager().sendMessage(peer, fromId, "😭 Эх, ты проиграл " + bet + " очков... \nТвой баланс: " + plugin.getReputationManager().getPoints(fromId));
            }
        } else if (cmd.equals("!аккаунт") || cmd.equals("!account")) {
            // Меню управления аккаунтом (только в ЛС)
            if (peer < 2000000000) {
                String info = "👤 Управление аккаунтом\n\n" +
                        "Статус: " + (plugin.getAuthManager().isLinkedByVkId(fromId) ? "✅ Привязан" : "❌ Не привязан") + "\n" +
                        "Репутация: " + plugin.getReputationManager().getPoints(fromId) + "\n\n" +
                        "Выбери действие кнопкой ниже:";
                plugin.getVkManager().sendKeyboard(peer, info, VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "Эта команда работает только в ЛС бота.");
            }
        } else if (cmd.equals("!мойстатус") || cmd.equals("!mystatus")) {
            // Показать статус аккаунта
            String status = "📊 Статус аккаунта\n\n";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT * FROM vkchat_auth WHERE vk_id = ?");
                ps.setInt(1, fromId);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String lastIp = rs.getString("last_ip");
                    long regDate = rs.getLong("reg_date");
                    status += "UUID: " + (uuid != null ? uuid.substring(0, 8) + "..." : "Н/Д") + "\n";
                    status += "Последний IP: " + (lastIp != null ? lastIp : "Н/Д") + "\n";
                    status += "Последний вход: " + (regDate > 0 ? new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(regDate)) : "Н/Д") + "\n";
                } else {
                    status += "Аккаунт не найден в базе данных.\n";
                }
            } catch (Exception e) {
                status += "Ошибка получения данных.\n";
            }
            status += "\nРепутация: " + plugin.getReputationManager().getPoints(fromId) + "\n";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, status, VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, status);
            }
        } else if (cmd.equals("!инфоаккаунт") || cmd.equals("!accountinfo")) {
            // Информация о системе безопасности
            String info = "🛡️ Система безопасности CHRDK REBORN\n\n" +
                    "• 2FA защита при входе с нового IP\n" +
                    "• Автоматический вход с того же IP (24ч)\n" +
                    "• Блокировка при 3 неудачных попытках\n" +
                    "• Уведомления в ВК при входе/выходе\n" +
                    "• Таймаут сессии при неактивности\n\n" +
                    "Команды:\n" +
                    "• /vklink — привязать ВК\n" +
                    "• /register <пароль> — регистрация\n" +
                    "• /login <пароль> — вход\n" +
                    "• /2fa <код> — подтверждение 2FA\n" +
                    "• /logout — выход из аккаунта";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, info, VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, info);
            }
        } else if (cmd.equals("!сменитьпароль") || cmd.equals("!changepass")) {
            // Смена пароля — отправляем инструкцию
            String msg = "🔑 Смена пароля\n\n" +
                    "Для смены пароля используй команду в игре:\n" +
                    "/changepass <старый_пароль> <новый_пароль>\n\n" +
                    "После смены пароля вы получите уведомление в ВК.";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, msg, VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, msg);
            }
        } else if (cmd.equals("!выйти") || cmd.equals("!logout")) {
            // Выход из аккаунта
            String msg = "🚪 Выход из аккаунта\n\n" +
                    "Для выхода используй команду в игре:\n" +
                    "/logout\n\n" +
                    "После выхода вам нужно будет заново войти.";
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, msg, VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, msg);
            }
        } else if (cmd.equals("!блок") || cmd.equals("!block")) {
            // Блокировка входа по коду
            if (args.length >= 2) {
                String code = args[1];
                if (plugin.getAuthManager().blockLoginByCode(code)) {
                    plugin.getVkManager().sendMessage(peer, fromId, "🛡️ Вход по коду " + code + " заблокирован! Нарушитель кикнут.");
                } else {
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Код " + code + " не найден или уже обработан.");
                }
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !блок <код>");
            }
        } else if (cmd.equals("!вход") || cmd.equals("!login")) {
            // Вход по коду 2FA из ВК
            if (args.length >= 2) {
                String code = args[1];
                if (plugin.getAuthManager().is2faCode(code)) {
                    // Ищем UUID по коду
                    java.util.UUID targetUuid = null;
                    for (java.util.Map.Entry<java.util.UUID, String> entry : plugin.getAuthManager().getAwait2faEntries()) {
                        if (entry.getValue().equals(code)) {
                            targetUuid = entry.getKey();
                            break;
                        }
                    }
                    if (targetUuid != null) {
                        Player target = Bukkit.getPlayer(targetUuid);
                        if (target != null) {
                            int linkedVk = plugin.getAuthManager().getLinkedVkId(target);
                            if (linkedVk == fromId) {
                                plugin.getAuthManager().confirm2fa(targetUuid);
                                plugin.getVkManager().sendMessage(peer, fromId, "✅ Вход подтверждён! Добро пожаловать в игру.");
                                target.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_2fa_success")));
                                return;
                            }
                        }
                    }
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Код не найден или не принадлежит вам.");
                } else {
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Неверный код 2FA.");
                }
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !вход <код>");
            }
        } else if (cmd.equals("!2fa")) {
            // Подтверждение 2FA через кнопку
            if (args.length >= 2) {
                String code = args[1];
                if (plugin.getAuthManager().is2faCode(code)) {
                    java.util.UUID targetUuid = null;
                    for (java.util.Map.Entry<java.util.UUID, String> entry : plugin.getAuthManager().getAwait2faEntries()) {
                        if (entry.getValue().equals(code)) {
                            targetUuid = entry.getKey();
                            break;
                        }
                    }
                    if (targetUuid != null) {
                        Player target = Bukkit.getPlayer(targetUuid);
                        if (target != null) {
                            int linkedVk = plugin.getAuthManager().getLinkedVkId(target);
                            if (linkedVk == fromId) {
                                plugin.getAuthManager().confirm2fa(targetUuid);
                                plugin.getVkManager().sendMessage(peer, fromId, "✅ Вход подтверждён!");
                                target.sendMessage(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("auth_2fa_success")));
                                return;
                            }
                        }
                    }
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Код не найден или не принадлежит вам.");
                } else {
                    plugin.getVkManager().sendMessage(peer, fromId, "❌ Неверный код.");
                }
            }
        } else if (cmd.equals("!история") || cmd.equals("!history")) {
            // История входов
            java.util.UUID targetUuid = null;
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
                ps.setInt(1, fromId);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    targetUuid = java.util.UUID.fromString(rs.getString("uuid"));
                }
            } catch (Exception ignored) {}

            if (targetUuid != null) {
                java.util.List<String> history = plugin.getAuthManager().getLoginHistory(targetUuid);
                StringBuilder sb = new StringBuilder("📋 История входов\n\n");
                if (history.isEmpty()) {
                    sb.append("История пуста.");
                } else {
                    for (String entry : history) {
                        sb.append("• ").append(entry).append("\n");
                    }
                }
                if (peer < 2000000000) {
                    plugin.getVkManager().sendKeyboard(peer, sb.toString(), VKKeyboardBuilder.accountMenu());
                } else {
                    plugin.getVkManager().sendMessage(peer, fromId, sb.toString());
                }
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Аккаунт не привязан.");
            }
        } else if (cmd.equals("!безопасность") || cmd.equals("!security")) {
            // Статус безопасности
            StringBuilder sb = new StringBuilder("🛡️ Статус безопасности\n\n");
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT * FROM vkchat_auth WHERE vk_id = ?");
                ps.setInt(1, fromId);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String lastIp = rs.getString("last_ip");
                    long regDate = rs.getLong("reg_date");
                    boolean isFrozen = plugin.getAuthManager().isAccountFrozen(java.util.UUID.fromString(uuid));

                    sb.append("Статус: ").append(isFrozen ? "🔒 Заморожен" : "✅ Активен").append("\n");
                    sb.append("UUID: ").append(uuid != null ? uuid.substring(0, 8) + "..." : "Н/Д").append("\n");
                    sb.append("Последний IP: ").append(lastIp != null ? lastIp : "Н/Д").append("\n");
                    sb.append("Последний вход: ").append(regDate > 0 ? new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(regDate)) : "Н/Д").append("\n\n");
                    sb.append("Защита:\n");
                    sb.append("• 2FA: ").append(plugin.getConfig().getBoolean("security.2fa-enabled", true) ? "✅ Включена" : "❌ Выключена").append("\n");
                    sb.append("• Авто-вход: ").append(plugin.getConfig().getBoolean("auth.auto-login-ip", true) ? "✅ Включён" : "❌ Выключен").append("\n");
                    sb.append("• Таймаут: ").append(plugin.getConfig().getInt("auth.session-timeout-minutes", 30)).append(" мин.\n");
                } else {
                    sb.append("Аккаунт не найден.");
                }
            } catch (Exception e) {
                sb.append("Ошибка получения данных.");
            }
            if (peer < 2000000000) {
                plugin.getVkManager().sendKeyboard(peer, sb.toString(), VKKeyboardBuilder.accountMenu());
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, sb.toString());
            }
        } else if (cmd.equals("!отвязать") || cmd.equals("!unlink")) {
            // Отвязка VK от MC
            plugin.getVkManager().sendMessage(peer, fromId, "🔗 Для отвязки ВК от Minecraft используй команду в игре:\n/vkunlink\n\nПосле отвязки вы потеряете доступ к VK-функциям сервера.");
        } else if (cmd.equals("!заморозить") || cmd.equals("!freeze")) {
            // Заморозка аккаунта (админ)
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ У вас нет прав.");
                return;
            }
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !заморозить <ник>");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                plugin.getAuthManager().freezeAccount(target.getUniqueId());
                plugin.getVkManager().sendMessage(peer, fromId, "🔒 Аккаунт " + args[1] + " заморожен.");
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Игрок не найден.");
            }
        } else if (cmd.equals("!разморозить") || cmd.equals("!unfreeze")) {
            // Разморозка аккаунта (админ)
            java.util.List<Integer> admins = plugin.getConfig().getIntegerList("vk.admin-vk-ids");
            if (!admins.contains(fromId)) {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ У вас нет прав.");
                return;
            }
            if (args.length < 2) {
                plugin.getVkManager().sendMessage(peer, fromId, "Использование: !разморозить <ник>");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                plugin.getAuthManager().unfreezeAccount(target.getUniqueId());
                plugin.getVkManager().sendMessage(peer, fromId, "🔓 Аккаунт " + args[1] + " разморожен.");
            } else {
                plugin.getVkManager().sendMessage(peer, fromId, "❌ Игрок не найден.");
            }
        } else {
            if (peer == mainChatId && plugin.getRiddleManager().checkAnswer(fromId, text)) {
                return;
            }

            // [НОВОЕ] Обработка кнопки экстренной блокировки входа через ВК
            if (text.contains("БЛОКИРОВКА") || text.contains("Войти:") || text.startsWith("❌")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b\\d{4,6}\\b").matcher(text);
                if (m.find()) {
                    String code = m.group();
                    if (text.contains("БЛОКИРОВКА") || text.startsWith("❌")) {
                        if (plugin.getAuthManager().blockLoginByCode(code)) {
                            plugin.getVkManager().sendMessage(peer, fromId, "🛡️ [Безопасность] Вход по коду " + code + " успешно ЗАБЛОКИРОВАН. Подозрительный сеанс прерван, нарушитель кикнут!");
                            return;
                        }
                    }
                }
            }

            java.util.regex.Matcher codeMatcher = java.util.regex.Pattern.compile("\\b\\d{4,6}\\b").matcher(text);
            while (codeMatcher.find()) {
                String code = codeMatcher.group();
                if (plugin.getAuthManager().isValidCode(code) || plugin.getAuthManager().is2faCode(code)) {
                    if (plugin.getConfig().getBoolean("vk.require-membership", true)) {
                        if (!plugin.getVkManager().isMemberOfGroupAndChat(fromId)) {
                            String failMsg = plugin.getConfigManager().getMessage("vk_req_fail")
                                    .replace("{group_link}", plugin.getConfig().getString("vk.group-link", ""))
                                    .replace("{chat_link}", plugin.getConfig().getString("vk.chat-invite-link", ""));
                            plugin.getVkManager().sendMessage(peer, fromId, org.bukkit.ChatColor.stripColor(failMsg));
                            return;
                        }
                    }
                    if (plugin.getAuthManager().tryLink(fromId, code, peer)) {
                        return;
                    }
                }
            }
            
            if (peer == mainChatId) {
                int cooldown = plugin.getConfig().getInt("vk.anti-spam-cooldown", 3);
                if (cooldown > 0) {
                    long lastTime = lastMessageTimes.getOrDefault(fromId, 0L);
                    if (System.currentTimeMillis() - lastTime < (cooldown * 1000L)) {
                        plugin.getVkManager().sendMessage(peer, fromId, ChatColor.stripColor(plugin.getConfigManager().formatColor(plugin.getConfigManager().getMessage("vk_spam_warning").replace("{time}", String.valueOf(cooldown)))));
                        return;
                    }
                    lastMessageTimes.put(fromId, System.currentTimeMillis());
                }
                
                org.json.JSONObject user = plugin.getVkManager().getUserInfo(fromId);
                String name = user != null ? user.getString("first_name") + " " + user.getString("last_name") : "VK User";

                // Пробуем использовать VKChatChat плагин если есть
                org.bukkit.plugin.Plugin chatPlugin = Bukkit.getPluginManager().getPlugin("VKChatChat");
                if (chatPlugin != null && chatPlugin.isEnabled()) {
                    try {
                        chatPlugin.getClass().getMethod("getChatListener").invoke(chatPlugin)
                                .getClass().getMethod("onVkMessage", String.class, String.class)
                                .invoke(chatPlugin.getClass().getMethod("getChatListener").invoke(chatPlugin), name, text);
                    } catch (Exception ex) {
                        // Fallback
                        sendVkToMcLegacy(name, text);
                    }
                } else {
                    sendVkToMcLegacy(name, text);
                }
            } else {
                // В ЛС: если сообщение не распознано как команда — показываем главное меню
                if (peer < 2000000000) {
                    String welcome = "🏠 Главное меню CHRDK REBORN\n\n" +
                            "Выбери режим:\n" +
                            "👤 Аккаунт — управление профилем\n\n" +
                            "Или используй команды напрямую.";
                    plugin.getVkManager().sendKeyboard(peer, welcome, VKKeyboardBuilder.mainDmMenu());
                }
            }
        }
    }

    private static String getVkHelpMessage(VKChatPlugin plugin) {
        if (plugin.getConfig().getBoolean("vk-help.enabled", true)) {
            java.util.List<String> lines = plugin.getConfig().getStringList("vk-help.lines");
            if (lines != null && !lines.isEmpty()) {
                return String.join("\n", lines);
            }
        }
        return "╔═══════════════════════════╗\n" +
                "║   🎮 CHRDK REBORN 🎮      ║\n" +
                "╚═══════════════════════════╝\n\n" +
                " ⛏ Смены:\n" +
                " !шахта — Шахтёрские смены\n" +
                " !смена / !shift — То же самое\n\n" +
                " 📊 Профиль:\n" +
                " !профиль - Твоя статистика\n" +
                " !рейтинг - Репутация\n" +
                " !топ - Лучшие игроки\n" +
                " !топреп - Богачи чата\n\n" +
                " 💰 Экономика:\n" +
                " !бонус - Ежедневный бонус\n" +
                " !сейф - Взлом сейфа\n" +
                " !промо <код> - Активировать код\n" +
                " !донат / !donate - Поддержка сервера\n\n" +
                " 🔧 Сервер:\n" +
                " !online - Онлайн\n" +
                " !status - Статус сервера\n" +
                " !меню - Главное меню\n" +
                " !помощь - Это сообщение";
    }

    private static void sendVkToMcLegacy(String name, String text) {
        VKChatPlugin plugin = VKChatPlugin.getInstance();
        String mcFormat = plugin.getConfigManager().getMessage("vk_to_mc_format")
                .replace("{name}", name)
                .replace("{message}", text);

        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (text.toLowerCase().contains(p.getName().toLowerCase())) {
                p.sendMessage(mcFormat.replace(p.getName(), org.bukkit.ChatColor.GREEN + p.getName() + org.bukkit.ChatColor.RESET));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                p.sendMessage(mcFormat);
            }
        }
    }
}
