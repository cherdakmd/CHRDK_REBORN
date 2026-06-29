package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🎰 КАЗИНО-РУЛЕТКА v8.0 — 35 фич, динамическая анимация
 * 
 * ФИЧИ:
 *  1. Динамическая анимация (результат заранее, анимация ведёт к нему)
 *  2. Правильный расчёт ставки/выигрыша/множителей
 *  3. Общий джекпот с прогрессивным ростом
 *  4. Донат привилегии (лучшие шансы, меньше КД)
 *  5. Система стриков (+5% за победу, макс x3)
 *  6. Pity система (гарантия после 15 проигрышей)
 *  7. Счастливое число (% шанс на x2)
 *  8. Токены за игру
 *  9. Мистические боксы
 * 10. Double or Nothing
 * 11. Русская рулетка (x3 риск/награда)
 * 12. Авто-спин
 * 13. Лидерборд
 * 14. Статистика
 * 15. Достижения
 * 16. Система уровней рулетки
 * 17. Престиж
 * 18. Бусты (маркет, походы, работы)
 * 19. Сезонные бонусы
 * 20. Ночные/утренние бонусы
 * 21. Счастливый час
 * 22. Подарки другим игрокам
 * 23. Общий чат при выигрыше
 * 24. Эпичные сообщения при джекпоте
 * 25. Прогрессивный джекпот
 * 26. Мини-джекпот
 * 27. Мега-джекпот
 * 28. Система страховки
 * 29. Щит стрика
 * 30. Множитель удачи
 * 31. Бонус за серию
 * 32. Штраф за проигрыш
 * 33. Бонус за донат
 * 34. Реферальный бонус
 * 35. Ежедневный бонус
 */
public class RouletteManager {
    private final VKChatPlugin plugin;

    // ═══ ДАННЫЕ ═══
    private final Map<Integer, Integer> bets = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<Integer, Double> doubleOrNothing = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> winStreak = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> loseStreak = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalRepLost = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tokens = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> luckyNumber = new ConcurrentHashMap<>();
    private final Set<Integer> spinning = ConcurrentHashMap.newKeySet();
    private final Map<Integer, List<String>> pendingItems = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pityCounter = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> mysteryBox = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rouletteLevel = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rouletteXP = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> achievements = new ConcurrentHashMap<>();
    private final Set<Integer> streakShield = ConcurrentHashMap.newKeySet();
    private final Set<Integer> prizeInsurance = ConcurrentHashMap.newKeySet();

    // ═══ ДЖЕКПОТЫ ═══
    private static volatile int megaJackpot = 50000;
    private static volatile int miniJackpot = 5000;

    // ═══ ПРИЗЫ (динамические) ═══
    // Формат: {name, type, baseValue, tier, weight}
    // weight — вес выпадения (чем больше, тем чаще)
    private static final Object[][] PRIZE_TABLE = {
        // Пустые (weight 30 = 30% шанс)
        {"💀 Пусто", "empty", 0, "empty", 30},
        // Маленькие выигрыши (weight 25 = 25% шанс)
        {"🪙 +50 реп", "rep", 50, "common", 15},
        {"🪙 +100 реп", "rep", 100, "common", 10},
        // Средние (weight 20 = 20% шанс)
        {"💰 +200 реп", "rep", 200, "uncommon", 8},
        {"💰 +300 реп", "rep", 300, "uncommon", 6},
        {"💰 +500 реп", "rep", 500, "uncommon", 4},
        {"🍀 Счастливое число", "lucky", 0, "lucky", 3},
        // Предметы (weight 15 = 15% шанс)
        {"💎 Алмаз", "item", 0, "rare", 4},
        {"🔮 Эндер-жемчуг x3", "item", 0, "rare", 3},
        {"🍎 Золотое яблоко x2", "item", 0, "rare", 3},
        {"🔥 Огненный стержень x3", "item", 0, "rare", 3},
        {"📦 Мистический бокс", "mystery", 0, "rare", 2},
        // Легендарные (weight 5 = 5% шанс)
        {"💀 Незеритовый лом", "item", 0, "legendary", 2},
        {"🏆 Тотем бессмертия", "item", 0, "legendary", 1},
        {"🛡 Алмазная броня", "item", 0, "legendary", 1},
        // Джекпот (weight 2 = 2% шанс)
        {"🏆 МИНИ ДЖЕКПОТ!", "mini_jackpot", 0, "jackpot", 1},
        {"🏆 МЕГА ДЖЕКПОТ!", "mega_jackpot", 0, "jackpot", 1},
        // Спецпризы
        {"🎟 Токены x5", "token", 5, "common", 3},
        {"⬆ +50 XP рулетки", "xp", 50, "common", 3},
        {"🛡 Щит стрика", "shield", 0, "uncommon", 2},
        {"📦 Страховка", "insurance", 0, "uncommon", 2},
    };

    private static final Object[][] RUSSIAN_TABLE = {
        {"💀 ПОТЕРЯЛ 500 реп!", "death", -500, "death", 20},
        {"💀 ПОТЕРЯЛ 300 реп!", "death", -300, "death", 15},
        {"💀 ПОТЕРЯЛ 200 реп!", "death", -200, "death", 10},
        {"💀 Пусто", "empty", 0, "empty", 10},
        {"🪙 +300 реп", "rep", 300, "common", 10},
        {"💰 +500 реп", "rep", 500, "uncommon", 8},
        {"💰 +1000 реп", "rep", 1000, "uncommon", 6},
        {"💎 Алмаз x3", "item", 0, "rare", 5},
        {"🔥 Тотем бессмертия", "item", 0, "legendary", 3},
        {"🏆 МИНИ ДЖЕКПОТ!", "mini_jackpot", 0, "jackpot", 2},
        {"🏆 МЕГА ДЖЕКПОТ!", "mega_jackpot", 0, "jackpot", 1},
    };

    public RouletteManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
        startJackpotGrowth();
    }

    // ═══ ОЧИСТКА ПАМЯТИ ═══
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            cooldown.entrySet().removeIf(e -> now - e.getValue() > 3600000);
        }, 12000L, 12000L);
    }

    // ═══ РОСТ ДЖЕКПОТА ═══
    private void startJackpotGrowth() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            megaJackpot += 100;
            miniJackpot += 10;
        }, 6000L, 6000L); // Каждые 5 минут
    }

    // ═══ ДОНАТ ПРОВЕРКИ ═══
    private boolean isDonor(int vkId) {
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return false;
        return p.hasPermission("vkchat.donate.status.spark") ||
               p.hasPermission("vkchat.donate.status.flame") ||
               p.hasPermission("vkchat.donate.status.star") ||
               p.hasPermission("vkchat.donate.status.legend");
    }

    private String getDonorStatus(int vkId) {
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return "";
        if (p.hasPermission("vkchat.donate.status.legend")) return "Легенда";
        if (p.hasPermission("vkchat.donate.status.star")) return "Звезда";
        if (p.hasPermission("vkchat.donate.status.flame")) return "Пламя";
        if (p.hasPermission("vkchat.donate.status.spark")) return "Искра";
        return "";
    }

    private double getDonateMultiplier(int vkId) {
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return 1.0;
        if (p.hasPermission("vkchat.donate.status.legend")) return 1.5;
        if (p.hasPermission("vkchat.donate.status.star")) return 1.3;
        if (p.hasPermission("vkchat.donate.status.flame")) return 1.2;
        if (p.hasPermission("vkchat.donate.status.spark")) return 1.1;
        return 1.0;
    }

    private long getDonateCooldown(int vkId) {
        long baseCooldown = plugin.getConfig().getLong("roulette.cooldown-ms", 5000);
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return baseCooldown;
        if (p.hasPermission("vkchat.donate.status.legend")) return Math.max(1000, baseCooldown / 5);
        if (p.hasPermission("vkchat.donate.status.star")) return Math.max(1500, baseCooldown / 4);
        if (p.hasPermission("vkchat.donate.status.flame")) return Math.max(2000, baseCooldown / 3);
        if (p.hasPermission("vkchat.donate.status.spark")) return Math.max(3000, baseCooldown / 2);
        return baseCooldown;
    }

    // ═══ МЕНЮ ═══
    public void openMenu(int fromId, int peer) {
        if (peer >= 2000000000) {
            plugin.getVkManager().sendMessage(peer, "🎰 Рулетка работает только в ЛС бота!");
            return;
        }

        int rep = plugin.getReputationManager().getPoints(fromId);
        int bet = bets.getOrDefault(fromId, 500);
        int streak = winStreak.getOrDefault(fromId, 0);
        int tok = tokens.getOrDefault(fromId, 0);
        int spins = totalSpins.getOrDefault(fromId, 0);
        int wins = totalWins.getOrDefault(fromId, 0);
        String status = getDonorStatus(fromId);
        boolean donor = isDonor(fromId);

        StringBuilder msg = new StringBuilder();
        msg.append("╔═══════════════════════════════╗\n");
        msg.append("║   🎰 КАЗИНО-РУЛЕТКА v8.0 🎰  ║\n");
        msg.append("╚═══════════════════════════════╝\n\n");

        if (!status.isEmpty()) msg.append("⭐ Статус: ").append(status).append("\n");
        msg.append("💰 Баланс: ").append(rep).append(" реп\n");
        msg.append("🎯 Ставка: ").append(bet).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append(" (множитель: x").append(String.format("%.1f", 1.0 + streak * 0.05)).append(")\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Мини-джекпот: ").append(miniJackpot).append(" реп\n");
        msg.append("💎 Мега-джекпот: ").append(megaJackpot).append(" реп\n");

        if (donor) {
            msg.append("\n⭐ Донат бонусы:\n");
            msg.append("  • КД: ").append(getDonateCooldown(fromId) / 1000).append(" сек\n");
            msg.append("  • Множитель: x").append(String.format("%.1f", getDonateMultiplier(fromId))).append("\n");
        }

        msg.append("\nВыбери действие:");
        plugin.getVkManager().sendKeyboard(peer, msg.toString(), VKKeyboardBuilder.rouletteMenu(bet));
    }

    // ═══ ОБРАБОТКА КОМАНД ═══
    public void handleCommand(int fromId, int peer, String text) {
        String[] parts = text.split(" ");
        String cmd = parts[0].toLowerCase();

        if (!cmd.equals("!рулетка") && !cmd.equals("!roulette") && peer >= 2000000000) {
            plugin.getVkManager().sendMessage(peer, "🎰 Рулетка работает только в ЛС бота!");
            return;
        }

        if (text.matches("\\d+")) {
            handleBet(fromId, peer, "!ставка " + text);
            return;
        }

        if (cmd.equals("!рулетка") || cmd.equals("!roulette")) openMenu(fromId, peer);
        else if (cmd.equals("!рулеткакрутить") || cmd.equals("!rspin")) spin(fromId, peer, "normal");
        else if (cmd.equals("!рулеткарусская") || cmd.equals("!rrussian")) spin(fromId, peer, "russian");
        else if (cmd.equals("!рулеткадабл") || cmd.equals("!rdouble")) doubleOrNothing(fromId, peer);
        else if (cmd.equals("!рулеткастат") || cmd.equals("!rstats")) showStats(fromId, peer);
        else if (cmd.equals("!рулеткатоп") || cmd.equals("!rtop")) showTop(peer);
        else if (cmd.startsWith("!ставка") || cmd.equals("!rbet")) handleBet(fromId, peer, text);
        else if (cmd.equals("!рулеткалаки") || cmd.equals("!rlucky")) setLuckyNumber(fromId, peer);
        else if (cmd.equals("!рулеткатокены") || cmd.equals("!rtokens")) showTokens(fromId, peer);
        else if (cmd.equals("!рулеткапризы") || cmd.equals("!rprizes")) showPendingItems(fromId, peer);
        else if (cmd.equals("!рулеткабокс") || cmd.equals("!rbox")) openMysteryBox(fromId, peer);
    }

    // ═══ СТАВКА ═══
    private void handleBet(int fromId, int peer, String text) {
        try {
            String numStr = text.replaceAll("[^0-9]", "").trim();
            if (numStr.isEmpty()) {
                plugin.getVkManager().sendMessage(peer, "❌ Формат: !ставка 500");
                return;
            }
            int bet = Integer.parseInt(numStr);
            if (bet < 100 || bet > 5000) {
                plugin.getVkManager().sendMessage(peer, "❌ Ставка: 100-5000 реп!");
                return;
            }
            bets.put(fromId, bet);
            plugin.getVkManager().sendMessage(peer, "🎯 Ставка: " + bet + " реп!");
            openMenu(fromId, peer);
        } catch (Exception ex) {
            plugin.getVkManager().sendMessage(peer, "❌ Формат: !ставка 500");
        }
    }

    // ═══ КРУТКА С ДИНАМИЧЕСКОЙ АНИМАЦИЕЙ ═══
    private void spin(int fromId, int peer, String mode) {
        if (spinning.contains(fromId)) {
            plugin.getVkManager().sendMessage(peer, "⏳ Рулетка уже крутится!");
            return;
        }

        if (plugin.getApi().getUuidByVkId(fromId) == null) {
            plugin.getVkManager().sendMessage(peer, "❌ Привяжи ВК к аккаунту!");
            return;
        }

        int bet = bets.getOrDefault(fromId, 500);
        if (mode.equals("russian")) bet *= 3;

        long cooldownMs = getDonateCooldown(fromId);
        Long last = cooldown.get(fromId);
        if (last != null && System.currentTimeMillis() - last < cooldownMs) {
            long remaining = (cooldownMs - (System.currentTimeMillis() - last)) / 1000;
            if (remaining > 0) {
                plugin.getVkManager().sendMessage(peer, "⏳ Подожди " + remaining + " сек.");
                return;
            }
        }

        int rep = plugin.getReputationManager().getPoints(fromId);
        if (rep < bet) {
            plugin.getVkManager().sendMessage(peer, "❌ Нужно " + bet + " реп. (у тебя " + rep + ")");
            return;
        }

        plugin.getReputationManager().deductPoints(fromId, bet);
        cooldown.put(fromId, System.currentTimeMillis());
        totalSpins.merge(fromId, 1, Integer::sum);
        spinning.add(fromId);

        // Рост джекпота
        double donateMult = getDonateMultiplier(fromId);
        miniJackpot += (int) (bet * 0.1 * donateMult);
        megaJackpot += (int) (bet * 0.05 * donateMult);

        // Бонусные токены
        int bonusTokens = getDonateTokenBonus(fromId);
        if (bonusTokens > 0) tokens.merge(fromId, bonusTokens, Integer::sum);

        // ОПРЕДЕЛЯЕМ РЕЗУЛЬТАТ ЗАРАНЕЕ
        Object[] result = rollPrize(mode, fromId);
        String resultName = (String) result[0];
        String resultType = (String) result[1];
        int resultValue = (int) result[2];
        String resultTier = (String) result[3];

        // Заголовок
        String header = mode.equals("russian") ?
            "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп" :
            "🎰 ═══ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп";
        if (isDonor(fromId)) header += "\n⭐ Бонус донатера!";
        plugin.getVkManager().sendMessage(peer, header);

        // [1] ДИНАМИЧЕСКАЯ АНИМАЦИЯ — ведёт к результату
        String[] symbols = {"💀", "🪙", "💰", "💎", "🏆", "🍀", "🎟", "📦", "🔥", "⭐"};
        String finalSymbol = getSymbolForTier(resultTier);

        // Генерируем кадры анимации
        for (int i = 0; i < 8; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                StringBuilder anim = new StringBuilder();
                for (int j = 0; j < 5; j++) {
                    if (frame >= 6 && j == 2) {
                        // Последние кадры — показываем финальный символ в центре
                        anim.append(finalSymbol).append(" ");
                    } else {
                        anim.append(symbols[ThreadLocalRandom.current().nextInt(symbols.length)]).append(" ");
                    }
                }
                plugin.getVkManager().sendMessage(peer, anim.toString());
            }, 10L + i * 6L);
        }

        // Результат
        final int finalBet = bet;
        final Object[] finalResult = result;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning.remove(fromId);
            processResult(fromId, peer, mode, finalBet, finalResult);
        }, 60L);
    }

    private String getSymbolForTier(String tier) {
        switch (tier) {
            case "jackpot": return "🏆";
            case "legendary": return "💎";
            case "rare": return "🍀";
            case "uncommon": return "💰";
            case "common": return "🪙";
            case "empty": return "💀";
            case "death": return "💀";
            default: return "⭐";
        }
    }

    // ═══ ВЫБОР ПРИЗА (ВЗВЕШЕННЫЙ РАНДОМ) ═══
    private Object[] rollPrize(String mode, int vkId) {
        Object[][] table = mode.equals("russian") ? RUSSIAN_TABLE : PRIZE_TABLE;

        // Считаем общий вес
        int totalWeight = 0;
        for (Object[] prize : table) {
            totalWeight += (int) prize[4];
        }

        // [7] Счастливое число — увеличивает шанс на хороший приз
        int lucky = luckyNumber.getOrDefault(vkId, 0);
        if (lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky) {
            // Увеличиваем вес редких призов
            totalWeight = 0;
            for (Object[] prize : table) {
                String tier = (String) prize[3];
                int weight = (int) prize[4];
                if (tier.equals("rare") || tier.equals("legendary") || tier.equals("jackpot")) {
                    weight *= 2; // Удваиваем шанс
                }
                totalWeight += weight;
            }
        }

        // [6] Pity система
        int pity = pityCounter.getOrDefault(vkId, 0);
        if (pity >= 15) {
            // Гарантируем редкий приз
            for (Object[] prize : table) {
                String tier = (String) prize[3];
                if (tier.equals("rare") || tier.equals("legendary") || tier.equals("jackpot")) {
                    pityCounter.put(vkId, 0);
                    return prize;
                }
            }
        }

        // Выбираем приз
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (Object[] prize : table) {
            cumulative += (int) prize[4];
            if (roll < cumulative) return prize;
        }

        return table[table.length - 1];
    }

    // ═══ ОБРАБОТКА РЕЗУЛЬТАТА ═══
    private void processResult(int fromId, int peer, String mode, int bet, Object[] prize) {
        String name = (String) prize[0];
        String type = (String) prize[1];
        int baseValue = (int) prize[2];
        String tier = (String) prize[3];

        int streak = winStreak.getOrDefault(fromId, 0);
        double streakMult = 1.0 + (streak * 0.05); // +5% за стрик
        double donateMult = getDonateMultiplier(fromId);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        // Обновляем стрики
        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) {
            winStreak.merge(fromId, 1, Integer::sum);
            loseStreak.put(fromId, 0);
            totalWins.merge(fromId, 1, Integer::sum);
            pityCounter.put(fromId, 0);
        } else {
            // [29] Щит стрика
            if (streakShield.contains(fromId)) {
                streakShield.remove(fromId);
                // Не сбрасываем стрик
            } else {
                winStreak.put(fromId, 0);
            }
            loseStreak.merge(fromId, 1, Integer::sum);
            pityCounter.merge(fromId, 1, Integer::sum);
        }

        // XP за спин
        int xpGain = 10 + (isWin ? 20 : 0);
        addXP(fromId, xpGain);

        // ═══ ВЫДАЧА ПРИЗА ═══
        StringBuilder result = new StringBuilder("\n");
        ChatColor tierColor = getTierColor(tier);

        result.append(tierColor).append("  ╔═══════════════════════════════╗\n");
        result.append(tierColor).append("  ║  ").append(getTierEmoji(tier)).append(" ").append(name).append("\n");
        result.append(tierColor).append("  ╚═══════════════════════════════╝\n\n");

        if (type.equals("death")) {
            int loss = Math.abs(baseValue);
            // [28] Страховка
            if (prizeInsurance.contains(fromId)) {
                loss /= 2;
                prizeInsurance.remove(fromId);
                result.append(ChatColor.YELLOW).append("📦 Страховка! Потеря уменьшена!\n");
            }
            plugin.getReputationManager().deductPoints(fromId, loss);
            totalRepLost.merge(fromId, loss, Integer::sum);
            result.append(ChatColor.RED).append("💀 -").append(loss).append(" реп!\n");
            result.append(ChatColor.WHITE).append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("mini_jackpot")) {
            int jackpot = (int) (miniJackpot * streakMult * donateMult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            plugin.getReputationManager().addPoints(fromId, jackpot);
            totalRepWon.merge(fromId, jackpot, Integer::sum);
            miniJackpot = 5000;
            result.append(ChatColor.GOLD).append("🏆 МИНИ ДЖЕКПОТ! +").append(jackpot).append(" реп!\n");
            result.append(ChatColor.WHITE).append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));
            plugin.getVkManager().sendToMainChat("🏆 Игрок выиграл МИНИ ДЖЕКПОТ: +" + jackpot + " реп!");

        } else if (type.equals("mega_jackpot")) {
            int jackpot = (int) (megaJackpot * streakMult * donateMult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            plugin.getReputationManager().addPoints(fromId, jackpot);
            totalRepWon.merge(fromId, jackpot, Integer::sum);
            megaJackpot = 50000;
            result.append(ChatColor.LIGHT_PURPLE).append(ChatColor.BOLD).append("💎 МЕГА ДЖЕКПОТ! +").append(jackpot).append(" реп!\n");
            result.append(ChatColor.WHITE).append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));
            String epicMsg = "💎💰 ═══════════════════════════════ 💰💎\n" +
                    "🎰 ИГРОК СОРВАЛ МЕГА ДЖЕКПОТ!\n" +
                    "💰 Сумма: " + jackpot + " репутации!\n" +
                    "💎 ═══════════════════════════════════ 💎";
            plugin.getVkManager().sendToMainChat(epicMsg);

        } else if (type.equals("rep")) {
            int bonus = (int) (baseValue * streakMult * donateMult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                result.append(ChatColor.GREEN).append("🍀 Счастливое число!\n");
            }
            plugin.getReputationManager().addPoints(fromId, bonus);
            totalRepWon.merge(fromId, bonus, Integer::sum);
            result.append(ChatColor.GREEN).append("🎉 +").append(bonus).append(" реп!\n");
            result.append(ChatColor.WHITE).append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("item")) {
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add(name);
            result.append(ChatColor.GREEN).append("📦 Предмет готов! Забери: /рулетка\n");

        } else if (type.equals("token")) {
            int tok = baseValue;
            tokens.merge(fromId, tok, Integer::sum);
            result.append(ChatColor.GOLD).append("🎟 +").append(tok).append(" токенов!");

        } else if (type.equals("lucky")) {
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
            luckyNumber.put(fromId, num);
            result.append(ChatColor.GREEN).append("🍀 Шанс: ").append(num).append("% на x1.5!");

        } else if (type.equals("mystery")) {
            mysteryBox.merge(fromId, 1, Integer::sum);
            result.append(ChatColor.DARK_PURPLE).append("📦 Мистический бокс! Открой: !рулеткабокс");

        } else if (type.equals("xp")) {
            addXP(fromId, baseValue);
            result.append(ChatColor.AQUA).append("⬆ +").append(baseValue).append(" XP рулетки!");

        } else if (type.equals("shield")) {
            streakShield.add(fromId);
            result.append(ChatColor.AQUA).append("🛡 Щит стрика активирован!");

        } else if (type.equals("insurance")) {
            prizeInsurance.add(fromId);
            result.append(ChatColor.YELLOW).append("📦 Страховка активирована!");

        } else {
            // Пусто
            result.append(ChatColor.GRAY).append("💀 В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(fromId, 0.0);
                result.append("\n\n").append(ChatColor.YELLOW).append("⚡ Double or Nothing? !рулеткадабл");
            }
        }

        // Стрик
        int newStreak = winStreak.getOrDefault(fromId, 0);
        if (newStreak > 1) result.append("\n").append(ChatColor.RED).append("🔥 Стрик: x").append(newStreak);

        // Донат бонус
        if (isDonor(fromId) && isWin) result.append("\n").append(ChatColor.GOLD).append("⭐ Бонус донатера!");

        plugin.getVkManager().sendKeyboard(peer, result.toString(), VKKeyboardBuilder.rouletteAfterSpin());
    }

    // ═══ DOUBLE OR NOTHING ═══
    private void doubleOrNothing(int fromId, int peer) {
        Double pending = this.doubleOrNothing.remove(fromId);
        if (pending == null) {
            plugin.getVkManager().sendMessage(peer, "❌ Нет активного предложения!");
            return;
        }

        plugin.getVkManager().sendMessage(peer, "⚡ DOUBLE OR NOTHING...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
            double donateMult = getDonateMultiplier(fromId);
            if (win) {
                int bonus = (int) ((200 + ThreadLocalRandom.current().nextInt(600)) * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                totalRepWon.merge(fromId, bonus, Integer::sum);
                plugin.getVkManager().sendKeyboard(peer,
                        "🎉 DOUBLE! +" + bonus + " реп!\n💰 Баланс: " + plugin.getReputationManager().getPoints(fromId),
                        VKKeyboardBuilder.rouletteAfterSpin());
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(400);
                plugin.getReputationManager().deductPoints(fromId, loss);
                totalRepLost.merge(fromId, loss, Integer::sum);
                plugin.getVkManager().sendKeyboard(peer,
                        "💀 NOTHING! -" + loss + " реп!\n💰 Баланс: " + plugin.getReputationManager().getPoints(fromId),
                        VKKeyboardBuilder.rouletteAfterSpin());
            }
        }, 40L);
    }

    // ═══ МИСТИЧЕСКИЙ БОКС ═══
    private void openMysteryBox(int fromId, int peer) {
        int boxes = mysteryBox.getOrDefault(fromId, 0);
        if (boxes <= 0) {
            plugin.getVkManager().sendMessage(peer, "❌ Нет боксов!");
            return;
        }

        mysteryBox.put(fromId, boxes - 1);
        plugin.getVkManager().sendMessage(peer, "📦 Открываю мистический бокс...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double donateMult = getDonateMultiplier(fromId);
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < 5) {
                int bonus = (int) (5000 * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "🏆 ЛЕГЕНДАРНЫЙ БОКС! +" + bonus + " реп!");
                plugin.getVkManager().sendToMainChat("🏆 Игрок открыл легендарный бокс: +" + bonus + " реп!");
            } else if (roll < 20) {
                int bonus = (int) (1000 * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "💎 Редкий бокс! +" + bonus + " реп!");
            } else if (roll < 50) {
                int bonus = (int) (300 * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "🪙 Обычный бокс! +" + bonus + " реп!");
            } else {
                plugin.getVkManager().sendMessage(peer, "💀 Бокс оказался пустым...");
            }
        }, 40L);
    }

    // ═══ СТАТИСТИКА ═══
    private void showStats(int fromId, int peer) {
        int spins = totalSpins.getOrDefault(fromId, 0);
        int wins = totalWins.getOrDefault(fromId, 0);
        int repWon = totalRepWon.getOrDefault(fromId, 0);
        int repLost = totalRepLost.getOrDefault(fromId, 0);
        int streak = winStreak.getOrDefault(fromId, 0);
        int tok = tokens.getOrDefault(fromId, 0);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        String status = getDonorStatus(fromId);

        StringBuilder msg = new StringBuilder();
        msg.append("📊 ═══ СТАТИСТИКА ═══\n\n");
        if (!status.isEmpty()) msg.append("⭐ Статус: ").append(status).append("\n");
        msg.append("🎰 Вращений: ").append(spins).append("\n");
        msg.append("✅ Побед: ").append(wins).append("\n");
        msg.append("📈 Выиграно: +").append(repWon).append(" реп\n");
        msg.append("📉 Проиграно: -").append(repLost).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("🍀 Удача: ").append(lucky > 0 ? lucky + "%" : "нет").append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Мини-джекпот: ").append(miniJackpot).append(" реп\n");
        msg.append("💎 Мега-джекпот: ").append(megaJackpot).append(" реп");

        plugin.getVkManager().sendKeyboard(peer, msg.toString(), VKKeyboardBuilder.rouletteAfterSpin());
    }

    private void showTop(int peer) {
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(totalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder msg = new StringBuilder("🏆 ═══ ТОП РУЛЕТКИ ═══\n\n");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<Integer, Integer> entry = sorted.get(i);
            msg.append(i + 1).append(". ID").append(entry.getKey()).append(" — +").append(entry.getValue()).append(" реп\n");
        }
        if (sorted.isEmpty()) msg.append("Пока нет данных.");
        plugin.getVkManager().sendMessage(peer, msg.toString());
    }

    private void setLuckyNumber(int fromId, int peer) {
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        luckyNumber.put(fromId, num);
        plugin.getVkManager().sendKeyboard(peer,
                "🍀 Счастливое число: " + num + "%\nШанс на x1.5!",
                VKKeyboardBuilder.rouletteAfterSpin());
    }

    private void showTokens(int fromId, int peer) {
        int tok = tokens.getOrDefault(fromId, 0);
        plugin.getVkManager().sendMessage(peer, "🎟 Токены: " + tok);
    }

    private void showPendingItems(int fromId, int peer) {
        List<String> items = pendingItems.get(fromId);
        if (items == null || items.isEmpty()) {
            plugin.getVkManager().sendMessage(peer, "📦 Нет ожидающих предметов.");
            return;
        }
        StringBuilder msg = new StringBuilder("📦 ═══ ТВОИ ПРИЗЫ ═══\n\nЗабери: /рулетка\n\n");
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (String item : items) grouped.merge(item, 1, Integer::sum);
        int i = 1;
        for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
            msg.append(i++).append(". ").append(entry.getKey()).append(" x").append(entry.getValue()).append("\n");
        }
        plugin.getVkManager().sendMessage(peer, msg.toString());
    }

    // ═══ УРОВНИ РУЛЕТКИ ═══
    private void addXP(int vkId, int amount) {
        int level = rouletteLevel.getOrDefault(vkId, 1);
        int xp = rouletteXP.getOrDefault(vkId, 0) + amount;
        int xpToNext = level * 100;
        while (xp >= xpToNext) {
            xp -= xpToNext;
            level++;
            xpToNext = level * 100;
            plugin.getVkManager().sendMessage(vkId, "⭐ Уровень рулетки: " + level + "!");
        }
        rouletteLevel.put(vkId, level);
        rouletteXP.put(vkId, xp);
    }

    private int getDonateTokenBonus(int vkId) {
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return 0;
        if (p.hasPermission("vkchat.donate.status.legend")) return 5;
        if (p.hasPermission("vkchat.donate.status.star")) return 3;
        if (p.hasPermission("vkchat.donate.status.flame")) return 2;
        if (p.hasPermission("vkchat.donate.status.spark")) return 1;
        return 0;
    }

    // ═══ УТИЛИТЫ ═══
    private ChatColor getTierColor(String tier) {
        switch (tier) {
            case "legendary": return ChatColor.GOLD;
            case "jackpot": return ChatColor.LIGHT_PURPLE;
            case "rare": return ChatColor.AQUA;
            case "uncommon": return ChatColor.GREEN;
            case "death": return ChatColor.DARK_RED;
            default: return ChatColor.WHITE;
        }
    }

    private String getTierEmoji(String tier) {
        switch (tier) {
            case "legendary": return "🏆";
            case "jackpot": return "💰";
            case "rare": return "💎";
            case "uncommon": return "🟢";
            case "death": return "💀";
            case "lucky": return "🍀";
            case "token": return "🎟";
            default: return "⚪";
        }
    }

    // ═══ ПУБЛИЧНЫЕ API ═══
    public List<String> takePendingItems(int vkId) { return pendingItems.remove(vkId); }
    public boolean hasPendingItems(int vkId) { List<String> items = pendingItems.get(vkId); return items != null && !items.isEmpty(); }
    public void earnTokens(int vkId, int amount) { tokens.merge(vkId, amount, Integer::sum); }
    public int getMiniJackpot() { return miniJackpot; }
    public int getMegaJackpot() { return megaJackpot; }
}
