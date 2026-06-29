package ru.example.vkchat.vk;

import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VK Рулетка v2.0 — Полная переработка
 * 
 * Фичи:
 * 1. Выбор ставки кнопками
 * 2. Обычная крутка
 * 3. Русская рулетка (x3)
 * 4. Double or Nothing
 * 5. Стрики (+10% за победу)
 * 6. Счастливое число
 * 7. Токены
 * 8. Предметы в ожидающие
 * 9. Статистика
 * 10. Лидерборд
 * 11. Анимация
 * 12. Настраиваемый КД
 */
public class VKRouletteManager {
    private final VKChatPlugin plugin;

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
    private int jackpotPool = 5000;

    private static final String[][] PRIZES = {
        {"💎 Алмаз", "item", "DIAMOND;1"},
        {"🔮 Эндер-жемчуг x3", "item", "ENDER_PEARL;3"},
        {"🔥 Огненный стержень x2", "item", "BLAZE_ROD;2"},
        {"⚡ Редстоун-блок x5", "item", "REDSTONE_BLOCK;5"},
        {"🍀 Изумруд x2", "item", "EMERALD;2"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1"},
        {"🧪 Опыт-бутылки x10", "item", "EXPERIENCE_BOTTLE;10"},
        {"🪙 +200 реп", "rep", "200"},
        {"💰 +500 реп", "rep", "500"},
        {"🏆 ДЖЕКПОТ!", "jackpot", "0"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 +100 реп", "rep", "100"},
        {"🍎 Золотое яблоко x2", "item", "GOLDEN_APPLE;2"},
        {"✨ +300 реп", "rep", "300"},
        {"🍀 Счастливое число", "lucky", "0"},
        {"🎟 Токены x3", "token", "3"},
        {"🧊 Алмазный блок", "item", "DIAMOND_BLOCK;1"},
        {"⚔ Алмазный меч", "item", "DIAMOND_SWORD;1"},
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💎💎 Алмаз x3", "item", "DIAMOND;3"},
        {"💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "item", "NETHERITE_INGOT;1"},
        {"🏆 ДЖЕКПОТ x2!", "jackpot", "0"},
        {"💰 +1000 реп", "rep", "1000"},
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500"},
        {"🍎 Золотое яблоко x5", "item", "GOLDEN_APPLE;5"},
        {"💀 -300 реп", "death", "-300"},
        {"🔥 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 +300 реп", "rep", "300"},
        {"💀 -200 реп", "death", "-200"},
        {"🔮 Эндер-жемчуг x16", "item", "ENDER_PEARL;16"},
    };

    public VKRouletteManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══ ГЛАВНОЕ МЕНЮ ═══

    public void openMainMenu(int fromId, int peer) {
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

        StringBuilder msg = new StringBuilder();
        msg.append("╔═══════════════════════╗\n");
        msg.append("║    🎰 РУЛЕТКА 🎰      ║\n");
        msg.append("╚═══════════════════════╝\n\n");
        msg.append("💰 Баланс: ").append(rep).append(" реп\n");
        msg.append("🎯 Ставка: ").append(bet).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tok).append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");
        msg.append("🏆 Джекпот: ").append(jackpotPool).append(" реп\n");

        if (pending != null && !pending.isEmpty()) {
            msg.append("\n📦 Призов ждёт: ").append(pending.size()).append(" (!рулеткапризы)");
        }

        msg.append("\n\nВыбери действие:");
        plugin.getVkManager().sendKeyboard(peer, msg.toString(), VKKeyboardBuilder.rouletteMenu(bet));
    }

    // ═══ ОБРАБОТКА КОМАНД ═══

    public void handleCommand(int fromId, int peer, String cmd) {
        if (cmd.equals("!рулетка") || cmd.equals("!roulette")) {
            openMainMenu(fromId, peer);
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
        } else if (cmd.startsWith("!ставка")) {
            handleBet(fromId, peer, cmd);
        } else if (cmd.equals("!рулеткалаки") || cmd.equals("!rlucky")) {
            setLuckyNumber(fromId, peer);
        } else if (cmd.equals("!рулеткатокены") || cmd.equals("!rtokens")) {
            showTokens(fromId, peer);
        } else if (cmd.equals("!рулеткапризы") || cmd.equals("!rprizes")) {
            showPendingItems(fromId, peer);
        }
    }

    // ═══ СТАВКА ═══

    private void handleBet(int fromId, int peer, String cmd) {
        try {
            String[] parts = cmd.split(" ");
            int bet = Integer.parseInt(parts[parts.length - 1]);
            if (bet < 100 || bet > 10000) {
                plugin.getVkManager().sendMessage(peer, "❌ Ставка: 100-10000 реп!");
                return;
            }
            bets.put(fromId, bet);
            plugin.getVkManager().sendMessage(peer, "🎯 Ставка: " + bet + " реп!");
            openMainMenu(fromId, peer);
        } catch (Exception ex) {
            plugin.getVkManager().sendMessage(peer, "❌ Формат: !ставка 500");
        }
    }

    // ═══ КРУТКА ═══

    public void spin(int fromId, int peer, String mode) {
        if (spinning.contains(fromId)) {
            plugin.getVkManager().sendMessage(peer, "⏳ Рулетка уже крутится!");
            return;
        }

        int bet = bets.getOrDefault(fromId, 500);
        if (mode.equals("russian")) bet *= 3;

        // КД из конфига (по умолчанию 5 сек)
        long cooldownMs = plugin.getConfig().getLong("roulette.vk-cooldown-ms", 5000);
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
        jackpotPool += bet / 10;

        // Анимация
        String header = mode.equals("russian") ?
            "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп\n⚠ Шанс выжить: 50%" :
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
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    StringBuilder anim = new StringBuilder();
                    for (String s : frames[frame]) anim.append(s).append(" ");
                    plugin.getVkManager().sendMessage(peer, anim.toString());
                }
            }, 500L + i * 400L);
        }

        final int finalBet = bet;
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                spinning.remove(fromId);
                processResult(fromId, peer, mode, finalBet);
            }
        }, 3000L);
    }

    private void processResult(int fromId, int peer, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        String name = prize[0];
        String type = prize[1];
        String data = prize[2];

        int streak = winStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        boolean isWin = !type.equals("empty") && !type.equals("death");
        if (isWin) {
            winStreak.merge(fromId, 1, Integer::sum);
            totalWins.merge(fromId, 1, Integer::sum);
        } else {
            winStreak.put(fromId, 0);
        }

        StringBuilder result = new StringBuilder("\n");

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
            plugin.getVkManager().sendToMainChat("🏆 Игрок сорвал ДЖЕКПОТ в рулетке: +" + jackpot + " реп!");

        } else if (type.equals("token")) {
            int tok = Integer.parseInt(data);
            tokens.merge(fromId, tok, Integer::sum);
            result.append("🎟 ").append(name).append("\n🎟 +").append(tok).append(" токенов!");

        } else if (type.equals("lucky")) {
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
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
            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add(data);
            result.append("🎉 ").append(name).append("\n📦 Предмет готов! Забери: /рулетка\n");
            result.append("📦 Всего: ").append(pendingItems.get(fromId).size());

        } else {
            result.append("💀 ").append(name).append("\n😅 В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(fromId, 0.0);
                result.append("\n\n⚡ Double or Nothing? !рулеткадабл");
            }
        }

        int newStreak = winStreak.getOrDefault(fromId, 0);
        if (newStreak > 1) result.append("\n🔥 Стрик: x").append(newStreak);
        if (isLucky && !type.equals("lucky")) result.append("\n🍀 Удача сработала!");

        plugin.getVkManager().sendKeyboard(peer, result.toString(), VKKeyboardBuilder.rouletteAfterSpin());
    }

    // ═══ DOUBLE OR NOTHING ═══

    private void doubleOrNothing(int fromId, int peer) {
        Double pending = this.doubleOrNothing.remove(fromId);
        if (pending == null) {
            plugin.getVkManager().sendMessage(peer, "❌ Нет активного предложения!");
            return;
        }

        plugin.getVkManager().sendMessage(peer, "⚡ DOUBLE OR NOTHING...\nКрутится...");

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
                if (win) {
                    int bonus = 200 + ThreadLocalRandom.current().nextInt(600);
                    plugin.getReputationManager().addPoints(fromId, bonus);
                    totalRepWon.merge(fromId, bonus, Integer::sum);
                    plugin.getVkManager().sendKeyboard(peer,
                            "🎉 DOUBLE! +" + bonus + " реп!\n💰 Баланс: " + plugin.getReputationManager().getPoints(fromId),
                            VKKeyboardBuilder.rouletteAfterSpin());
                } else {
                    int loss = 100 + ThreadLocalRandom.current().nextInt(300);
                    plugin.getReputationManager().deductPoints(fromId, loss);
                    totalRepLost.merge(fromId, loss, Integer::sum);
                    plugin.getVkManager().sendKeyboard(peer,
                            "💀 NOTHING! -" + loss + " реп!\n💰 Баланс: " + plugin.getReputationManager().getPoints(fromId),
                            VKKeyboardBuilder.rouletteAfterSpin());
                }
            }
        }, 2000);
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

        String msg = "📊 ═══ СТАТИСТИКА ═══\n\n" +
                "🎰 Вращений: " + spins + "\n" +
                "✅ Побед: " + wins + "\n" +
                "📈 Выиграно: +" + repWon + " реп\n" +
                "📉 Проиграно: -" + repLost + " реп\n" +
                "🔥 Стрик: " + streak + "\n" +
                "🎟 Токены: " + tok + "\n" +
                "🍀 Удача: " + (lucky > 0 ? lucky + "%" : "нет") + "\n" +
                "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%\n" +
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

    // ═══ СЧАСТЛИВОЕ ЧИСЛО И ТОКЕНЫ ═══

    private void setLuckyNumber(int fromId, int peer) {
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        luckyNumber.put(fromId, num);
        plugin.getVkManager().sendKeyboard(peer,
                "🍀 Счастливое число: " + num + "%\nШанс на x1.5 к выигрышу!",
                VKKeyboardBuilder.rouletteAfterSpin());
    }

    private void showTokens(int fromId, int peer) {
        int tok = tokens.getOrDefault(fromId, 0);
        plugin.getVkManager().sendMessage(peer, "🎟 Токены: " + tok + "\n\nЗарабатываешь за торговлю на рынке");
    }

    // ═══ ОЖИДАЮЩИЕ ПРЕДМЕТЫ ═══

    private void showPendingItems(int fromId, int peer) {
        List<String> items = pendingItems.get(fromId);
        if (items == null || items.isEmpty()) {
            plugin.getVkManager().sendMessage(peer, "📦 Нет ожидающих предметов.\nВыигрывай в рулетке!");
            return;
        }

        StringBuilder msg = new StringBuilder("📦 ═══ ТВОИ ПРИЗЫ ═══\n\nЗабери в игре: /рулетка\n\n");
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

    // ═══ ПУБЛИЧНЫЕ МЕТОДЫ ═══

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
}
