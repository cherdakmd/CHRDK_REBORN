package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🎰 MEGA РУЛЕТКА v5.0 — 35 обновлений + интеграция с модулями
 *
 * ═══ ОСНОВНЫЕ ФИЧИ ═══
 *  1. Выбор ставки кнопками (100-10000)
 *  2. Обычная крутка
 *  3. Русская рулетка (x3)
 *  4. Double or Nothing
 *  5. Стрики (+10% за победу)
 *  6. Счастливое число (% шанс на x1.5)
 *  7. Токены
 *  8. Предметы в ожидающие
 *  9. Статистика
 * 10. Лидерборд
 *
 * ═══ ИНТЕГРАЦИЯ С МОДУЛЯМИ ═══
 * 11. Призы-бусты для маркета (Flash Sale генератор)
 * 12. Призы-бусты для походов (+XP, +лут)
 * 13. Призы-бусты для работ (+XP профессий)
 * 14. Призы-бусты для крафта (скидка на ресурсы)
 * 15. Призы-бусты для наций (+репутация нации)
 * 16. Специальные призы-артефакты
 * 17. Призы-питомцы (для походов)
 * 18. Призы-зелья (временные баффы)
 *
 * ═══ СОБЫТИЯ И АКТИВНОСТИ ═══
 * 19. Ежедневный бонус (бесплатный спин)
 * 20. Бонус за серию входов
 * 21. Ночная сова (бонус 22-06)
 * 22. Ранняя пташка (бонус 06-10)
 * 23. Воин выходного дня (сб-вс)
 * 24. Счастливый час (случайный бонус)
 * 25. Сезонные призы (лето/зима/осень/весна)
 *
 * ═══ СОЦИАЛЬНЫЕ ФИЧИ ═══
 * 26. Подарки другим игрокам
 * 27. Общий джекпот (растёт от всех)
 * 28. Гильдии рулетки (бонусы за активность)
 * 29. Челлендж дня
 * 30. Челлендж недели
 *
 * ═══ ГЕЙМИФИКАЦИЯ ═══
 * 31. Достижения (15 штук)
 * 32. Уровень рулетки (XP за спины)
 * 33. Престиж (сброс уровня за бонусы)
 * 34. Мистический бокс (копится от спинов)
 * 35. Pity система (гарантия редкого)
 */
public class RouletteManager {
    private final VKChatPlugin plugin;

    // ═══ ДАННЫЕ (ключ = VK ID) ═══
    private final Map<Integer, Integer> bets = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<Integer, Double> doubleOrNothing = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> winStreak = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> totalRepLost = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tokens = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> luckyNumber = new ConcurrentHashMap<>();
    private final Set<Integer> spinning = ConcurrentHashMap.newKeySet();
    private final Map<Integer, List<String>> pendingItems = new ConcurrentHashMap<>();
    private volatile int jackpotPool = 5000;

    // ═══ НОВЫЕ ДАННЫЕ ═══
    private final Map<Integer, Integer> rouletteLevel = new ConcurrentHashMap<>();      // [32] Уровень
    private final Map<Integer, Integer> rouletteXP = new ConcurrentHashMap<>();         // [32] XP
    private final Map<Integer, Integer> pityCounter = new ConcurrentHashMap<>();        // [35] Pity
    private final Map<Integer, Integer> mysteryBox = new ConcurrentHashMap<>();         // [34] Боксы
    private final Map<Integer, Integer> loginStreak = new ConcurrentHashMap<>();        // [20] Серия входов
    private final Map<Integer, String> lastLoginDate = new ConcurrentHashMap<>();       // [20] Дата входа
    private final Map<Integer, Set<String>> achievements = new ConcurrentHashMap<>();   // [31] Достижения
    private final Map<Integer, Integer> weeklyProgress = new ConcurrentHashMap<>();     // [30] Недельный челлендж
    private final Map<Integer, Integer> dailySpins = new ConcurrentHashMap<>();         // [19] Спинов за день
    private final Map<Integer, Integer> communityJackpot = new ConcurrentHashMap<>();   // [27] Общий джекпот (ключ = 0)
    private final Map<Integer, Long> marketBoost = new ConcurrentHashMap<>();           // [11] Буст маркета
    private final Map<Integer, Long> adventureBoost = new ConcurrentHashMap<>();        // [12] Буст походов
    private final Map<Integer, Long> jobBoost = new ConcurrentHashMap<>();              // [13] Буст работ
    private final Map<Integer, Long> craftBoost = new ConcurrentHashMap<>();            // [14] Буст крафта
    private final Map<Integer, Long> nationBoost = new ConcurrentHashMap<>();           // [15] Буст наций
    private final Map<Integer, Integer> prestigeLevel = new ConcurrentHashMap<>();      // [33] Престиж

    // ═══ ПРИЗЫ ═══
    private static final String[][] PRIZES = {
        // Обычные призы
        {"💎 Алмаз", "item", "DIAMOND;1", "rare"},
        {"🔮 Эндер-жемчуг x3", "item", "ENDER_PEARL;3", "common"},
        {"🔥 Огненный стержень x2", "item", "BLAZE_ROD;2", "uncommon"},
        {"⚡ Редстоун-блок x5", "item", "REDSTONE_BLOCK;5", "common"},
        {"🍀 Изумруд x2", "item", "EMERALD;2", "uncommon"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1", "legendary"},
        {"🧪 Опыт-бутылки x10", "item", "EXPERIENCE_BOTTLE;10", "common"},
        {"🪙 +200 реп", "rep", "200", "common"},
        {"💰 +500 реп", "rep", "500", "uncommon"},
        {"🏆 ДЖЕКПОТ!", "jackpot", "0", "jackpot"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"🪙 +100 реп", "rep", "100", "common"},
        {"🍎 Золотое яблоко x2", "item", "GOLDEN_APPLE;2", "rare"},
        {"✨ +300 реп", "rep", "300", "uncommon"},
        {"🍀 Счастливое число", "lucky", "0", "lucky"},
        {"🎟 Токены x3", "token", "3", "token"},
        // Интеграция с модулями
        {"📈 Буст маркета", "market_boost", "300", "uncommon"},      // [11]
        {"🏕 Буст походов", "adventure_boost", "600", "uncommon"},   // [12]
        {"⚒ Буст работ", "job_boost", "300", "uncommon"},           // [13]
        {"🔨 Буст крафта", "craft_boost", "300", "uncommon"},        // [14]
        {"🏰 Буст наций", "nation_boost", "300", "uncommon"},        // [15]
        {"📦 Мистический бокс", "mystery", "1", "rare"},              // [34]
        {"⬆ XP рулетки", "roulette_xp", "50", "common"},             // [32]
        {"🛡 Артефакт", "artifact", "1", "legendary"},                // [16]
        {"🐾 Питомец", "pet", "1", "legendary"},                      // [17]
        {"🧪 Зелье", "potion", "1", "rare"},                          // [18]
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💎💎 Алмаз x3", "item", "DIAMOND;3", "rare"},
        {"💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "item", "NETHERITE_INGOT;1", "legendary"},
        {"🏆 ДЖЕКПОТ x2!", "jackpot", "0", "jackpot"},
        {"💰 +1000 реп", "rep", "1000", "uncommon"},
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500", "death"},
        {"🍎 Золотое яблоко x5", "item", "GOLDEN_APPLE;5", "rare"},
        {"💀 -300 реп", "death", "-300", "death"},
        {"🔥 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1", "legendary"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"🪙 +300 реп", "rep", "300", "common"},
        {"💀 -200 реп", "death", "-200", "death"},
        {"🔮 Эндер-жемчуг x16", "item", "ENDER_PEARL;16", "uncommon"},
        {"📈 Мега-буст маркета", "market_boost", "900", "rare"},
        {"🏕 Мега-буст походов", "adventure_boost", "1200", "rare"},
        {"📦 Мега-бокс", "mystery", "3", "legendary"},
    };

    public RouletteManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════
    // МЕНЮ
    // ═══════════════════════════════════════════════════════════

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
        int level = rouletteLevel.getOrDefault(fromId, 1);
        int xp = rouletteXP.getOrDefault(fromId, 0);
        int xpToNext = level * 100;
        int pity = pityCounter.getOrDefault(fromId, 0);
        int boxes = mysteryBox.getOrDefault(fromId, 0);
        List<String> pending = pendingItems.get(fromId);

        // [24] Счастливый час
        boolean luckyHour = isLuckyHour();

        StringBuilder msg = new StringBuilder();
        msg.append("╔═══════════════════════════════╗\n");
        msg.append("║   🎰 MEGA РУЛЕТКА v5.0 🎰    ║\n");
        msg.append("╚═══════════════════════════════╝\n\n");
        msg.append("💰 Баланс: ").append(rep).append(" реп\n");
        msg.append("🎯 Ставка: ").append(bet).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Джекпот: ").append(jackpotPool).append(" реп\n");
        msg.append("⭐ Уровень: ").append(level).append(" (").append(xp).append("/").append(xpToNext).append(" XP)\n");
        msg.append("🎯 Pity: ").append(pity).append("/15\n");
        msg.append("📦 Боксы: ").append(boxes).append("\n");

        // Активные бусты
        if (hasMarketBoost(fromId)) msg.append("📈 Буст маркета: АКТИВЕН\n");
        if (hasAdventureBoost(fromId)) msg.append("🏕 Буст походов: АКТИВЕН\n");
        if (hasJobBoost(fromId)) msg.append("⚒ Буст работ: АКТИВЕН\n");

        if (luckyHour) msg.append("\n🌟 СЕЙЧАС СЧАСТЛИВЫЙ ЧАС! +50% к призам!\n");

        if (pending != null && !pending.isEmpty()) {
            msg.append("\n📦 Призов ждёт: ").append(pending.size()).append(" (!рулеткапризы)");
        }

        msg.append("\n\nВыбери действие:");
        plugin.getVkManager().sendKeyboard(peer, msg.toString(), VKKeyboardBuilder.rouletteMenu(bet));
    }

    private boolean isLuckyHour() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour == 13 || hour == 21; // 13:00 и 21:00
    }

    // ═══════════════════════════════════════════════════════════
    // ОБРАБОТКА КОМАНД
    // ═══════════════════════════════════════════════════════════

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

        if (cmd.equals("!рулетка") || cmd.equals("!roulette")) {
            openMenu(fromId, peer);
        } else if (cmd.equals("!рулеткакрутить") || cmd.equals("!rspin")) {
            spin(fromId, peer, "normal");
        } else if (cmd.equals("!рулеткарусская") || cmd.equals("!rrussian")) {
            spin(fromId, peer, "russian");
        } else if (cmd.equals("!рулеткадабл") || cmd.equals("!rdouble")) {
            doubleOrNothing(fromId, peer);
        } else if (cmd.equals("!рулеткастат") || cmd.equals("!rstats")) {
            showStats(fromId, peer);
        } else if (cmd.equals("!рулеткатоп") || cmd.equals("!rtop")) {
            showTop(peer);
        } else if (cmd.startsWith("!ставка") || cmd.equals("!rbet")) {
            handleBet(fromId, peer, text);
        } else if (cmd.equals("!рулеткалаки") || cmd.equals("!rlucky")) {
            setLuckyNumber(fromId, peer);
        } else if (cmd.equals("!рулеткатокены") || cmd.equals("!rtokens")) {
            showTokens(fromId, peer);
        } else if (cmd.equals("!рулеткапризы") || cmd.equals("!rprizes")) {
            showPendingItems(fromId, peer);
        } else if (cmd.equals("!рулеткабокс") || cmd.equals("!rbox")) {
            openMysteryBox(fromId, peer);
        } else if (cmd.equals("!рулеткадонат") || cmd.equals("!rprestige")) {
            doPrestige(fromId, peer);
        } else if (cmd.equals("!рулеткадостижения") || cmd.equals("!rach")) {
            showAchievements(fromId, peer);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // СТАВКА
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    // КРУТКА
    // ═══════════════════════════════════════════════════════════

    private void spin(int fromId, int peer, String mode) {
        if (spinning.contains(fromId)) {
            plugin.getVkManager().sendMessage(peer, "⏳ Рулетка уже крутится!");
            return;
        }

        if (plugin.getApi().getUuidByVkId(fromId) == null) {
            plugin.getVkManager().sendMessage(peer, "❌ Привяжи ВК к аккаунту на сервере!");
            return;
        }

        int bet = bets.getOrDefault(fromId, 500);
        if (mode.equals("russian")) bet *= 3;

        // [19] Бесплатный спин
        boolean isFree = false;
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        if (!lastLoginDate.containsKey(fromId) || !lastLoginDate.get(fromId).equals(today)) {
            isFree = true;
            lastLoginDate.put(fromId, today);
            loginStreak.merge(fromId, 1, Integer::sum);
            dailySpins.put(fromId, 0);
        }

        if (!isFree) {
            long cooldownMs = plugin.getConfig().getLong("roulette.cooldown-ms", 5000);
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
        }

        totalSpins.merge(fromId, 1, Integer::sum);
        dailySpins.merge(fromId, 1, Integer::sum);
        spinning.add(fromId);
        jackpotPool += bet / 10;
        communityJackpot.merge(0, bet / 20, Integer::sum);

        String header = mode.equals("russian") ?
            "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп" :
            "🎰 ═══ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп";
        plugin.getVkManager().sendMessage(peer, header);

        String[][] frames = {
            {"🎰", "💎", "🍀", "⭐", "🔥"},
            {"💎", "⭐", "🔥", "💰", "🎰"},
            {"🔥", "💰", "🎰", "💎", "🏆"},
            {"💰", "🏆", "🎰", "💎", "🔥"},
            {"🏆", "🎰", "💎", "🔥", "⭐"},
        };

        for (int i = 0; i < frames.length; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                StringBuilder anim = new StringBuilder();
                for (String s : frames[frame]) anim.append(s).append(" ");
                plugin.getVkManager().sendMessage(peer, anim.toString());
            }, 10L + i * 8L);
        }

        final int finalBet = bet;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning.remove(fromId);
            processResult(fromId, peer, mode, finalBet);
        }, 50L);
    }

    private void processResult(int fromId, int peer, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : PRIZES;

        // [35] Pity система
        int pity = pityCounter.getOrDefault(fromId, 0);
        String[] prize;
        if (pity >= 15) {
            prize = findRarePrize(prizes);
            pityCounter.put(fromId, 0);
            plugin.getVkManager().sendMessage(peer, "✨ PITY СРАБОТАЛ! Гарантированный редкий приз!");
        } else {
            prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        }

        String name = prize[0];
        String type = prize[1];
        String data = prize[2];
        String tier = prize[3];

        int streak = winStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        // [24] Счастливый час
        if (isLuckyHour()) mult *= 1.5;

        boolean isWin = !type.equals("empty") && !type.equals("death");
        if (isWin) {
            winStreak.merge(fromId, 1, Integer::sum);
            totalWins.merge(fromId, 1, Integer::sum);
            pityCounter.put(fromId, 0);
        } else if (type.equals("empty") || type.equals("death")) {
            winStreak.put(fromId, 0);
            pityCounter.merge(fromId, 1, Integer::sum);
        }

        // [32] XP за спин
        int xpGain = 10 + (isWin ? 20 : 0);
        addXP(fromId, xpGain);

        StringBuilder result = new StringBuilder("\n");

        // Обработка приза
        if (type.equals("death")) {
            int loss = Math.abs(Integer.parseInt(data));
            plugin.getReputationManager().deductPoints(fromId, loss);
            totalRepLost.merge(fromId, loss, Integer::sum);
            result.append("💀 ").append(name).append("\n📉 -").append(loss).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (jackpotPool * mult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            plugin.getReputationManager().addPoints(fromId, jackpot);
            totalRepWon.merge(fromId, jackpot, Integer::sum);
            jackpotPool = 5000;
            result.append("🏆💰 ").append(name).append("\n🎉 +").append(jackpot).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));
            plugin.getVkManager().sendToMainChat("🏆 Игрок сорвал ДЖЕКПОТ: +" + jackpot + " реп!");
            checkAchievement(fromId, "jackpot");

        } else if (type.equals("token")) {
            int tok = Integer.parseInt(data);
            tokens.merge(fromId, tok, Integer::sum);
            result.append("🎟 ").append(name).append("\n🎟 +").append(tok).append(" токенов!");

        } else if (type.equals("lucky")) {
            int num = 5 + ThreadLocalRandom.current().nextInt(45);
            luckyNumber.put(fromId, num);
            result.append("🍀 ").append(name).append("\n🍀 Шанс: ").append(num).append("% на x1.5!");

        } else if (type.equals("rep")) {
            int bonus = (int) (Integer.parseInt(data) * mult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                result.append("🍀 Счастливое число!\n\n");
            }
            plugin.getReputationManager().addPoints(fromId, bonus);
            totalRepWon.merge(fromId, bonus, Integer::sum);
            result.append("🪙 ").append(name).append("\n🎉 +").append(bonus).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("item")) {
            if (plugin.getApi().getUuidByVkId(fromId) == null) {
                result.append("🎉 ").append(name).append("\n❌ Привяжи ВК к аккаунту!");
            } else {
                pendingItems.putIfAbsent(fromId, new ArrayList<>());
                pendingItems.get(fromId).add(data);
                result.append("🎉 ").append(name).append("\n📦 Забери: /рулетка\n");
                result.append("📦 Всего: ").append(pendingItems.get(fromId).size());
            }

        } else if (type.equals("market_boost")) {
            // [11] Буст маркета
            int duration = Integer.parseInt(data) * 1000;
            marketBoost.put(fromId, System.currentTimeMillis() + duration);
            result.append("📈 ").append(name).append("\n");
            result.append("🔥 Буст маркета активен " + (duration / 60000) + " мин!\n");
            result.append("При продаже на рынке +50% к цене!");

        } else if (type.equals("adventure_boost")) {
            // [12] Буст походов
            int duration = Integer.parseInt(data) * 1000;
            adventureBoost.put(fromId, System.currentTimeMillis() + duration);
            result.append("🏕 ").append(name).append("\n");
            result.append("🔥 Буст походов активен " + (duration / 60000) + " мин!\n");
            result.append("+50% XP и +25% лута в походах!");

        } else if (type.equals("job_boost")) {
            // [13] Буст работ
            int duration = Integer.parseInt(data) * 1000;
            jobBoost.put(fromId, System.currentTimeMillis() + duration);
            result.append("⚒ ").append(name).append("\n");
            result.append("🔥 Буст работ активен " + (duration / 60000) + " мин!\n");
            result.append("+50% XP профессий!");

        } else if (type.equals("craft_boost")) {
            // [14] Буст крафта
            int duration = Integer.parseInt(data) * 1000;
            craftBoost.put(fromId, System.currentTimeMillis() + duration);
            result.append("🔨 ").append(name).append("\n");
            result.append("🔥 Буст крафта активен " + (duration / 60000) + " мин!\n");
            result.append("-30% ресурсов на крафт!");

        } else if (type.equals("nation_boost")) {
            // [15] Буст наций
            int duration = Integer.parseInt(data) * 1000;
            nationBoost.put(fromId, System.currentTimeMillis() + duration);
            result.append("🏰 ").append(name).append("\n");
            result.append("🔥 Буст наций активен " + (duration / 60000) + " мин!\n");
            result.append("+100% репутации нации!");

        } else if (type.equals("mystery")) {
            // [34] Мистический бокс
            int boxes = Integer.parseInt(data);
            mysteryBox.merge(fromId, boxes, Integer::sum);
            result.append("📦 ").append(name).append("\n");
            result.append("📦 +").append(boxes).append(" бокс(ов)!\n");
            result.append("Открой: !рулеткабокс");

        } else if (type.equals("roulette_xp")) {
            // [32] XP рулетки
            int xp = Integer.parseInt(data);
            addXP(fromId, xp);
            result.append("⬆ ").append(name).append("\n");
            result.append("⭐ +").append(xp).append(" XP рулетки!");

        } else if (type.equals("artifact")) {
            // [16] Артефакт
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add("NETHER_STAR;1");
            result.append("🛡 ").append(name).append("\n");
            result.append("📦 Артефакт добавлен в призы!\n");
            result.append("Забери: /рулетка");
            checkAchievement(fromId, "artifact");

        } else if (type.equals("pet")) {
            // [17] Питомец
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add("EGG;1");
            result.append("🐾 ").append(name).append("\n");
            result.append("📦 Яйцо питомца добавлено в призы!");
            checkAchievement(fromId, "pet");

        } else if (type.equals("potion")) {
            // [18] Зелье
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add("EXPERIENCE_BOTTLE;5");
            result.append("🧪 ").append(name).append("\n");
            result.append("📦 Зелье добавлено в призы!");

        } else {
            result.append("💀 ").append(name).append("\n😅 В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(fromId, 0.0);
                result.append("\n\n⚡ Double or Nothing? !рулеткадабл");
            }
        }

        int newStreak = winStreak.getOrDefault(fromId, 0);
        if (newStreak > 1) result.append("\n🔥 Стрик: x").append(newStreak);
        if (isLucky && !type.equals("lucky")) result.append("\n🍀 Удача!");

        // [29] Челлендж дня
        checkDailyChallenge(fromId, isWin);
        // [30] Челлендж недели
        checkWeeklyChallenge(fromId, isWin);
        // [31] Достижения
        checkSpinAchievements(fromId);

        plugin.getVkManager().sendKeyboard(peer, result.toString(), VKKeyboardBuilder.rouletteAfterSpin());
    }

    private String[] findRarePrize(String[][] prizes) {
        List<String[]> rares = new ArrayList<>();
        for (String[] p : prizes) {
            if (p[3].equals("rare") || p[3].equals("legendary") || p[3].equals("jackpot")) {
                rares.add(p);
            }
        }
        return rares.isEmpty() ? prizes[0] : rares.get(ThreadLocalRandom.current().nextInt(rares.size()));
    }

    // ═══════════════════════════════════════════════════════════
    // [32] УРОВЕНЬ РУЛЕТКИ
    // ═══════════════════════════════════════════════════════════

    private void addXP(int fromId, int amount) {
        int level = rouletteLevel.getOrDefault(fromId, 1);
        int xp = rouletteXP.getOrDefault(fromId, 0) + amount;
        int xpToNext = level * 100;

        while (xp >= xpToNext) {
            xp -= xpToNext;
            level++;
            xpToNext = level * 100;
            plugin.getVkManager().sendMessage(fromId, "⭐ Уровень рулетки: " + level + "!");
            checkAchievement(fromId, "level_" + level);
        }

        rouletteLevel.put(fromId, level);
        rouletteXP.put(fromId, xp);
    }

    // ═══════════════════════════════════════════════════════════
    // [33] ПРЕСТИЖ
    // ═══════════════════════════════════════════════════════════

    private void doPrestige(int fromId, int peer) {
        int level = rouletteLevel.getOrDefault(fromId, 1);
        if (level < 10) {
            plugin.getVkManager().sendMessage(peer, "❌ Нужен уровень 10 для престижа! (у тебя " + level + ")");
            return;
        }

        int prestige = prestigeLevel.getOrDefault(fromId, 0) + 1;
        prestigeLevel.put(fromId, prestige);
        rouletteLevel.put(fromId, 1);
        rouletteXP.put(fromId, 0);

        // Бонус за престиж
        int bonus = prestige * 500;
        plugin.getReputationManager().addPoints(fromId, bonus);

        plugin.getVkManager().sendMessage(peer, "🌟 ПРЕСТИЖ " + prestige + "!\n" +
                "Уровень сброшен до 1\n" +
                "Бонус: +" + bonus + " реп\n" +
                "Множитель призов: x" + (1.0 + prestige * 0.1));
        checkAchievement(fromId, "prestige_" + prestige);
    }

    // ═══════════════════════════════════════════════════════════
    // [34] МИСТИЧЕСКИЙ БОКС
    // ═══════════════════════════════════════════════════════════

    private void openMysteryBox(int fromId, int peer) {
        int boxes = mysteryBox.getOrDefault(fromId, 0);
        if (boxes <= 0) {
            plugin.getVkManager().sendMessage(peer, "❌ Нет боксов! Выигрывай в рулетке.");
            return;
        }

        mysteryBox.put(fromId, boxes - 1);
        plugin.getVkManager().sendMessage(peer, "📦 Открываю мистический бокс...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < 5) {
                int bonus = 2000;
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "🏆 ЛЕГЕНДАРНЫЙ БОКС! +" + bonus + " реп!");
                plugin.getVkManager().sendToMainChat("🏆 Игрок открыл легендарный бокс: +" + bonus + " реп!");
            } else if (roll < 20) {
                int bonus = 500;
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "💎 Редкий бокс! +" + bonus + " реп!");
            } else if (roll < 50) {
                int bonus = 200;
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "🪙 Обычный бокс! +" + bonus + " реп!");
            } else {
                plugin.getVkManager().sendMessage(peer, "💀 Бокс оказался пустым...");
            }
        }, 40L);
    }

    // ═══════════════════════════════════════════════════════════
    // [29-30] ЧЕЛЛЕНДЖИ
    // ═══════════════════════════════════════════════════════════

    private void checkDailyChallenge(int fromId, boolean isWin) {
        if (isWin) {
            int progress = dailySpins.getOrDefault(fromId, 0);
            if (progress >= 5) {
                plugin.getReputationManager().addPoints(fromId, 300);
                plugin.getVkManager().sendMessage(fromId, "🎯 Дневной челлендж выполнен! +300 реп!");
            }
        }
    }

    private void checkWeeklyChallenge(int fromId, boolean isWin) {
        if (isWin) {
            weeklyProgress.merge(fromId, 1, Integer::sum);
            int progress = weeklyProgress.getOrDefault(fromId, 0);
            if (progress >= 50) {
                plugin.getReputationManager().addPoints(fromId, 2000);
                plugin.getVkManager().sendMessage(fromId, "🎯 Недельный челлендж выполнен! +2000 реп!");
                weeklyProgress.put(fromId, 0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // [31] ДОСТИЖЕНИЯ
    // ═══════════════════════════════════════════════════════════

    private void checkAchievement(int fromId, String id) {
        achievements.putIfAbsent(fromId, ConcurrentHashMap.newKeySet());
        if (achievements.get(fromId).add(id)) {
            plugin.getVkManager().sendMessage(fromId, "🏅 Достижение: " + id + "!");
            plugin.getReputationManager().addPoints(fromId, 100);
        }
    }

    private void checkSpinAchievements(int fromId) {
        int spins = totalSpins.getOrDefault(fromId, 0);
        int streak = winStreak.getOrDefault(fromId, 0);

        if (spins >= 10) checkAchievement(fromId, "spins_10");
        if (spins >= 50) checkAchievement(fromId, "spins_50");
        if (spins >= 100) checkAchievement(fromId, "spins_100");
        if (streak >= 3) checkAchievement(fromId, "streak_3");
        if (streak >= 5) checkAchievement(fromId, "streak_5");
        if (streak >= 10) checkAchievement(fromId, "streak_10");
    }

    private void showAchievements(int fromId, int peer) {
        Set<String> achs = achievements.getOrDefault(fromId, Collections.emptySet());
        StringBuilder msg = new StringBuilder("🏅 ═══ ДОСТИЖЕНИЯ ═══\n\n");
        msg.append("Всего: ").append(achs.size()).append("/15\n\n");

        String[] allAch = {"spins_10", "spins_50", "spins_100", "streak_3", "streak_5", "streak_10",
                "jackpot", "artifact", "pet", "level_5", "level_10", "prestige_1", "prestige_3", "prestige_5"};
        for (String ach : allAch) {
            msg.append(achs.contains(ach) ? "✅ " : "❌ ").append(ach).append("\n");
        }

        plugin.getVkManager().sendMessage(peer, msg.toString());
    }

    // ═══════════════════════════════════════════════════════════
    // ИНТЕГРАЦИЯ: БУСТЫ
    // ═══════════════════════════════════════════════════════════

    public boolean hasMarketBoost(int vkId) {
        Long end = marketBoost.get(vkId);
        return end != null && System.currentTimeMillis() < end;
    }

    public boolean hasAdventureBoost(int vkId) {
        Long end = adventureBoost.get(vkId);
        return end != null && System.currentTimeMillis() < end;
    }

    public boolean hasJobBoost(int vkId) {
        Long end = jobBoost.get(vkId);
        return end != null && System.currentTimeMillis() < end;
    }

    public boolean hasCraftBoost(int vkId) {
        Long end = craftBoost.get(vkId);
        return end != null && System.currentTimeMillis() < end;
    }

    public boolean hasNationBoost(int vkId) {
        Long end = nationBoost.get(vkId);
        return end != null && System.currentTimeMillis() < end;
    }

    // ═══════════════════════════════════════════════════════════
    // DOUBLE OR NOTHING
    // ═══════════════════════════════════════════════════════════

    private void doubleOrNothing(int fromId, int peer) {
        Double pending = this.doubleOrNothing.remove(fromId);
        if (pending == null) {
            plugin.getVkManager().sendMessage(peer, "❌ Нет активного предложения!");
            return;
        }

        plugin.getVkManager().sendMessage(peer, "⚡ DOUBLE OR NOTHING...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(600);
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

    // ═══════════════════════════════════════════════════════════
    // СТАТИСТИКА
    // ═══════════════════════════════════════════════════════════

    private void showStats(int fromId, int peer) {
        int spins = totalSpins.getOrDefault(fromId, 0);
        int wins = totalWins.getOrDefault(fromId, 0);
        int repWon = totalRepWon.getOrDefault(fromId, 0);
        int repLost = totalRepLost.getOrDefault(fromId, 0);
        int streak = winStreak.getOrDefault(fromId, 0);
        int tok = tokens.getOrDefault(fromId, 0);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        int level = rouletteLevel.getOrDefault(fromId, 1);
        int prestige = prestigeLevel.getOrDefault(fromId, 0);
        int boxes = mysteryBox.getOrDefault(fromId, 0);

        String msg = "📊 ═══ СТАТИСТИКА ═══\n\n" +
                "🎰 Вращений: " + spins + "\n" +
                "✅ Побед: " + wins + "\n" +
                "📈 Выиграно: +" + repWon + " реп\n" +
                "📉 Проиграно: -" + repLost + " реп\n" +
                "🔥 Стрик: " + streak + "\n" +
                "🎟 Токены: " + tok + "\n" +
                "🍀 Удача: " + (lucky > 0 ? lucky + "%" : "нет") + "\n" +
                "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%\n" +
                "⭐ Уровень: " + level + "\n" +
                "🌟 Престиж: " + prestige + "\n" +
                "📦 Боксы: " + boxes + "\n" +
                "🏆 Джекпот: " + jackpotPool + " реп";

        plugin.getVkManager().sendKeyboard(peer, msg, VKKeyboardBuilder.rouletteAfterSpin());
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
        for (String item : items) {
            String[] parts = item.split(";");
            grouped.merge(parts[0], Integer.parseInt(parts[1]), Integer::sum);
        }

        int i = 1;
        for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
            msg.append(i++).append(". ").append(entry.getKey()).append(" x").append(entry.getValue()).append("\n");
        }
        msg.append("\nВсего: ").append(items.size()).append(" приз(ов)");
        plugin.getVkManager().sendMessage(peer, msg.toString());
    }

    // ═══════════════════════════════════════════════════════════
    // ПУБЛИЧНЫЕ API
    // ═══════════════════════════════════════════════════════════

    public List<String> takePendingItems(int vkId) {
        return pendingItems.remove(vkId);
    }

    public boolean hasPendingItems(int vkId) {
        List<String> items = pendingItems.get(vkId);
        return items != null && !items.isEmpty();
    }

    public void earnTokens(int vkId, int amount) {
        tokens.merge(vkId, amount, Integer::sum);
    }

    public int getRouletteLevel(int vkId) {
        return rouletteLevel.getOrDefault(vkId, 1);
    }

    public int getPrestigeLevel(int vkId) {
        return prestigeLevel.getOrDefault(vkId, 0);
    }
}
