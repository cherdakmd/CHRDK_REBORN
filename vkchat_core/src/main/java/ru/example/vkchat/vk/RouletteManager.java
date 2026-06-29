package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🎰 Рулетка v7.0 — Полная интеграция доната
 *
 * Донат привилегии:
 * - Уменьшенный КД (Spark 4с, Flame 3с, Star 2с, Legend 1с)
 * - Увеличенный шанс на хороший приз
 * - Специальные донат-призы
 * - Больше токенов за спин
 * - Увеличенный вклад в джекпот
 * - Отображение статуса в меню
 */
public class RouletteManager {
    private final VKChatPlugin plugin;

    // Данные (ключ = VK ID)
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
    private final Map<Integer, Integer> pityCounter = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> mysteryBox = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> achievements = new ConcurrentHashMap<>();

    // Общий джекпот
    private static volatile int communityJackpot = 10000;

    // Призы для обычных игроков
    private static final String[][] PRIZES_NORMAL = {
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Ничего", "empty", "0", "empty"},
        {"💀 Повезёт в следующий раз", "empty", "0", "empty"},
        {"💀 Сегодня не твой день", "empty", "0", "empty"},
        {"💀 Увы", "empty", "0", "empty"},
        {"🪙 +50 реп", "rep", "50", "common"},
        {"🪙 +50 реп", "rep", "50", "common"},
        {"🪙 +100 реп", "rep", "100", "common"},
        {"🪙 +100 реп", "rep", "100", "common"},
        {"🪙 +150 реп", "rep", "150", "common"},
        {"💰 +300 реп", "rep", "300", "uncommon"},
        {"💰 +300 реп", "rep", "300", "uncommon"},
        {"💰 +500 реп", "rep", "500", "uncommon"},
        {"🍀 Счастливое число", "lucky", "0", "lucky"},
        {"🎟 Токены x2", "token", "2", "token"},
        {"💎 Алмаз", "item", "DIAMOND;1", "rare"},
        {"💎 Алмаз x2", "item", "DIAMOND;2", "rare"},
        {"🔮 Эндер-жемчуг x5", "item", "ENDER_PEARL;5", "rare"},
        {"🍎 Золотое яблоко x3", "item", "GOLDEN_APPLE;3", "rare"},
        {"🔥 Огненный стержень x3", "item", "BLAZE_ROD;3", "rare"},
        {"📦 Мистический бокс", "mystery", "1", "rare"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1", "legendary"},
        {"🏆 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1", "legendary"},
        {"🛡 Алмазная броня", "item", "DIAMOND_CHESTPLATE;1", "legendary"},
        {"🏆 ОБЩИЙ ДЖЕКПОТ!", "jackpot", "0", "jackpot"},
    };

    // Призы для донатеров (лучшие шансы!)
    private static final String[][] PRIZES_DONOR = {
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Ничего", "empty", "0", "empty"},
        {"🪙 +100 реп", "rep", "100", "common"},
        {"🪙 +100 реп", "rep", "100", "common"},
        {"🪙 +150 реп", "rep", "150", "common"},
        {"🪙 +200 реп", "rep", "200", "common"},
        {"💰 +300 реп", "rep", "300", "uncommon"},
        {"💰 +300 реп", "rep", "300", "uncommon"},
        {"💰 +500 реп", "rep", "500", "uncommon"},
        {"💰 +500 реп", "rep", "500", "uncommon"},
        {"🍀 Счастливое число", "lucky", "0", "lucky"},
        {"🎟 Токены x3", "token", "3", "token"},
        {"🎟 Токены x5", "token", "5", "token"},
        {"💎 Алмаз x2", "item", "DIAMOND;2", "rare"},
        {"💎 Алмаз x3", "item", "DIAMOND;3", "rare"},
        {"🔮 Эндер-жемчуг x5", "item", "ENDER_PEARL;5", "rare"},
        {"🍎 Золотое яблоко x3", "item", "GOLDEN_APPLE;3", "rare"},
        {"🔥 Огненный стержень x3", "item", "BLAZE_ROD;3", "rare"},
        {"📦 Мистический бокс", "mystery", "1", "rare"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1", "legendary"},
        {"🏆 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1", "legendary"},
        {"🛡 Алмазная броня", "item", "DIAMOND_CHESTPLATE;1", "legendary"},
        {"⚔ Незеритовый меч", "item", "NETHERITE_SWORD;1", "legendary"},
        {"🏆 ОБЩИЙ ДЖЕКПОТ!", "jackpot", "0", "jackpot"},
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500", "death"},
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500", "death"},
        {"💀 ПОТЕРЯЛ 300 реп!", "death", "-300", "death"},
        {"💀 ПОТЕРЯЛ 300 реп!", "death", "-300", "death"},
        {"💀 ПОТЕРЯЛ 200 реп!", "death", "-200", "death"},
        {"💀 ПОТЕРЯЛ 200 реп!", "death", "-200", "death"},
        {"💀 ПОТЕРЯЛ 100 реп!", "death", "-100", "death"},
        {"💀 ПОТЕРЯЛ 100 реп!", "death", "-100", "death"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"💀 Пусто", "empty", "0", "empty"},
        {"🪙 +300 реп", "rep", "300", "common"},
        {"🪙 +500 реп", "rep", "500", "common"},
        {"💰 +1000 реп", "rep", "1000", "uncommon"},
        {"💎 Алмаз x3", "item", "DIAMOND;3", "rare"},
        {"🔥 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1", "legendary"},
        {"🏆 ОБЩИЙ ДЖЕКПОТ!", "jackpot", "0", "jackpot"},
    };

    public RouletteManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══ ДОНATE ПРОВЕРКИ ═══

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

    private int getDonateTokenBonus(int vkId) {
        org.bukkit.entity.Player p = plugin.getApi().getPlayerByVkId(vkId);
        if (p == null) return 0;
        if (p.hasPermission("vkchat.donate.status.legend")) return 5;
        if (p.hasPermission("vkchat.donate.status.star")) return 3;
        if (p.hasPermission("vkchat.donate.status.flame")) return 2;
        if (p.hasPermission("vkchat.donate.status.spark")) return 1;
        return 0;
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
        List<String> pending = pendingItems.get(fromId);
        String status = getDonorStatus(fromId);
        boolean donor = isDonor(fromId);

        StringBuilder msg = new StringBuilder();
        msg.append("╔═══════════════════════════╗\n");
        msg.append("║    🎰 РУЛЕТКА 🎰          ║\n");
        msg.append("╚═══════════════════════════╝\n\n");

        if (!status.isEmpty()) {
            msg.append("⭐ Статус: ").append(status).append("\n");
        }

        msg.append("💰 Баланс: ").append(rep).append(" реп\n");
        msg.append("🎯 Ставка: ").append(bet).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Общий джекпот: ").append(communityJackpot).append(" реп\n");

        if (donor) {
            msg.append("\n⭐ Донат бонусы:\n");
            msg.append("  • КД: ").append(getDonateCooldown(fromId) / 1000).append(" сек\n");
            msg.append("  • Множитель: x").append(String.format("%.1f", getDonateMultiplier(fromId))).append("\n");
            msg.append("  • Токены: +").append(getDonateTokenBonus(fromId)).append(" за спин\n");
            msg.append("  • Лучшие призы!\n");
        }

        if (pending != null && !pending.isEmpty()) {
            msg.append("\n📦 Призов ждёт: ").append(pending.size()).append(" (!рулеткапризы)");
        }

        msg.append("\n\nВыбери действие:");
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
        }
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

    // ═══ КРУТКА ═══

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

        // [ДОНАТ] Уменьшенный КД
        long cooldownMs = getDonateCooldown(fromId);
        Long last = cooldown.get(fromId);
        if (last != null && System.currentTimeMillis() - last < cooldownMs) {
            long remaining = (cooldownMs - (System.currentTimeMillis() - last)) / 1000;
            if (remaining > 0) {
                plugin.getVkManager().sendMessage(peer, "⏳ Подожди " + remaining + " сек.");
                if (!isDonor(fromId)) {
                    plugin.getVkManager().sendMessage(peer, "💡 Донатеры имеют уменьшенный КД! !донат");
                }
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

        // [ДОНАТ] Увеличенный вклад в джекпот
        double donateMult = getDonateMultiplier(fromId);
        communityJackpot += (int) (bet / 5 * donateMult);

        // [ДОНАТ] Бонусные токены
        int bonusTokens = getDonateTokenBonus(fromId);
        if (bonusTokens > 0) {
            tokens.merge(fromId, bonusTokens, Integer::sum);
        }

        String header = mode.equals("russian") ?
            "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп" :
            "🎰 ═══ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп";

        if (isDonor(fromId)) {
            header += "\n⭐ Бонус донатера активен!";
        }

        plugin.getVkManager().sendMessage(peer, header);

        // Анимация
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
        // [ДОНАТ] Донатеры получают лучшие призы
        boolean donor = isDonor(fromId);
        String[][] prizes;
        if (mode.equals("russian")) {
            prizes = RUSSIAN_PRIZES;
        } else {
            prizes = donor ? PRIZES_DONOR : PRIZES_NORMAL;
        }

        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        String name = prize[0];
        String type = prize[1];
        String data = prize[2];
        String tier = prize[3];

        int streak = winStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        // [ДОНАТ] Увеличенный множитель
        double donateMult = getDonateMultiplier(fromId);

        // Pity система
        int pity = pityCounter.getOrDefault(fromId, 0);
        if (pity >= 15 && (tier.equals("empty") || tier.equals("death"))) {
            prize = findRarePrize(prizes);
            name = prize[0];
            type = prize[1];
            data = prize[2];
            tier = prize[3];
            pityCounter.put(fromId, 0);
            plugin.getVkManager().sendMessage(peer, "✨ PITY СРАБОТАЛ! Гарантированный редкий приз!");
        }

        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) {
            winStreak.merge(fromId, 1, Integer::sum);
            totalWins.merge(fromId, 1, Integer::sum);
            pityCounter.put(fromId, 0);
        } else {
            winStreak.put(fromId, 0);
            pityCounter.merge(fromId, 1, Integer::sum);
        }

        StringBuilder result = new StringBuilder("\n");

        if (type.equals("death")) {
            int loss = Math.abs(Integer.parseInt(data));
            plugin.getReputationManager().deductPoints(fromId, loss);
            totalRepLost.merge(fromId, loss, Integer::sum);
            result.append("💀 ").append(name).append("\n📉 -").append(loss).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (communityJackpot * mult * donateMult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            plugin.getReputationManager().addPoints(fromId, jackpot);
            totalRepWon.merge(fromId, jackpot, Integer::sum);
            communityJackpot = 10000;

            String epicMessage = "🏆💰 ═══════════════════════════════ 💰🏆\n" +
                    "🎰 ИГРОК СОРВАЛ ОБЩИЙ ДЖЕКПОТ!\n" +
                    "💰 Сумма: " + jackpot + " репутации!\n" +
                    "🏆 Поздравляем победителя!\n" +
                    "🎰 ═══════════════════════════════════ 🎰";
            plugin.getVkManager().sendToMainChat(epicMessage);

            result.append("🏆💰 ОБЩИЙ ДЖЕКПОТ!\n");
            result.append("🎉 +").append(jackpot).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

        } else if (type.equals("rep")) {
            int bonus = (int) (Integer.parseInt(data) * mult * donateMult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                result.append("🍀 Счастливое число!\n\n");
            }
            plugin.getReputationManager().addPoints(fromId, bonus);
            totalRepWon.merge(fromId, bonus, Integer::sum);
            result.append("🪙 ").append(name).append("\n🎉 +").append(bonus).append(" реп!\n");
            result.append("💰 Баланс: ").append(plugin.getReputationManager().getPoints(fromId));

            if (bonus >= 500) {
                plugin.getVkManager().sendToMainChat("🎰 Игрок выиграл " + bonus + " реп в рулетке! 🎉");
            }

        } else if (type.equals("item")) {
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add(data);
            result.append("🎉 ").append(name).append("\n📦 Забери: /рулетка\n");
            result.append("📦 Всего: ").append(pendingItems.get(fromId).size());

            if (tier.equals("rare") || tier.equals("legendary")) {
                plugin.getVkManager().sendToMainChat("🎰 Игрок выиграл " + name + " в рулетке! 💎");
            }

        } else if (type.equals("token")) {
            int tok = Integer.parseInt(data);
            tokens.merge(fromId, tok, Integer::sum);
            result.append("🎟 ").append(name).append("\n🎟 +").append(tok).append(" токенов!");

        } else if (type.equals("lucky")) {
            int num = 5 + ThreadLocalRandom.current().nextInt(45);
            luckyNumber.put(fromId, num);
            result.append("🍀 ").append(name).append("\n🍀 Шанс: ").append(num).append("% на x1.5!");

        } else if (type.equals("mystery")) {
            mysteryBox.merge(fromId, 1, Integer::sum);
            result.append("📦 ").append(name).append("\nОткрой: !рулеткабокс");

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
        if (donor && isWin) result.append("\n⭐ Бонус донатера!");

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
                int bonus = (int) (2000 * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "🏆 ЛЕГЕНДАРНЫЙ БОКС! +" + bonus + " реп!");
                plugin.getVkManager().sendToMainChat("🏆 Игрок открыл легендарный бокс: +" + bonus + " реп!");
            } else if (roll < 20) {
                int bonus = (int) (500 * donateMult);
                plugin.getReputationManager().addPoints(fromId, bonus);
                plugin.getVkManager().sendMessage(peer, "💎 Редкий бокс! +" + bonus + " реп!");
            } else if (roll < 50) {
                int bonus = (int) (200 * donateMult);
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

        if (!status.isEmpty()) {
            msg.append("⭐ Статус: ").append(status).append("\n");
        }

        msg.append("🎰 Вращений: ").append(spins).append("\n");
        msg.append("✅ Побед: ").append(wins).append("\n");
        msg.append("📈 Выиграно: +").append(repWon).append(" реп\n");
        msg.append("📉 Проиграно: -").append(repLost).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("🍀 Удача: ").append(lucky > 0 ? lucky + "%" : "нет").append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Общий джекпот: ").append(communityJackpot).append(" реп");

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

    // ═══ ПУБЛИЧНЫЕ API ═══

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

    public int getCommunityJackpot() {
        return communityJackpot;
    }
}
