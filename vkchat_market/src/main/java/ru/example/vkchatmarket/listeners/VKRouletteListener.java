package ru.example.vkchatmarket.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.data.MarketFun;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VKRouletteListener implements Listener {
    private final VKChatMarketPlugin plugin;
    private final MarketFun marketFun;

    // Ставки ВК пользователей
    private final Map<Integer, Integer> vkBets = new ConcurrentHashMap<>();
    // Режим ВК пользователей
    private final Map<Integer, String> vkModes = new ConcurrentHashMap<>();
    // Кулдаун
    private final Map<Integer, Long> vkCooldown = new ConcurrentHashMap<>();
    // Double or Nothing
    private final Map<Integer, Double> vkDoubleOrNothing = new ConcurrentHashMap<>();
    // Стрики
    private final Map<Integer, Integer> vkWinStreak = new ConcurrentHashMap<>();
    // Статистика
    private final Map<Integer, Integer> vkTotalSpins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> vkTotalWins = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> vkTotalRepWon = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> vkTotalRepLost = new ConcurrentHashMap<>();
    // Токены
    private final Map<Integer, Integer> vkTokens = new ConcurrentHashMap<>();
    // Счастливое число
    private final Map<Integer, Integer> vkLuckyNumber = new ConcurrentHashMap<>();

    // Призы
    private static final String[][] NORMAL_PRIZES = {
        {"💎 Алмаз", "rare", "150"},
        {"🔮 Эндер-жемчуг x3", "common", "80"},
        {"🔥 Огненный стержень x2", "uncommon", "120"},
        {"⚡ Редстоун-блок x5", "common", "60"},
        {"🍀 Изумруд x2", "uncommon", "100"},
        {"💀 Незеритовый лом", "legendary", "500"},
        {"🧪 Опыт-бутылки x10", "common", "50"},
        {"🪙 Бонус +200 реп", "common", "200"},
        {"💰 Бонус +500 реп", "uncommon", "500"},
        {"🏆 ДЖЕКПОТ!", "jackpot", "2000"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 Бонус +100 реп", "common", "100"},
        {"🍎 Золотое яблоко", "rare", "200"},
        {"✨ Мистический бонус", "mystery", "300"},
        {"🍀 Счастливое число", "lucky", "0"},
        {"🎟 Токен x2", "token", "0"},
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💎💎 Алмаз x3", "rare", "450"},
        {"💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "legendary", "1500"},
        {"🏆 ДЖЕКПОТ x2!", "jackpot", "5000"},
        {"💰 +1000 реп", "uncommon", "1000"},
        {"💀 ПОТЕРЯЛ ВСЁ!", "death", "-500"},
        {"🍎 Золотое яблоко x5", "rare", "600"},
        {"💀 -500 реп", "death", "-500"},
        {"🔥 Тотем бессмертия", "legendary", "2000"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 +300 реп", "common", "300"},
        {"💀 -200 реп", "death", "-200"},
        {"🔮 Эндер-жемчуг x16", "uncommon", "400"},
    };

    public VKRouletteListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.marketFun = plugin.getMarketFun();
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand().toLowerCase();
        int fromId = e.getSenderVkId();
        int peer = e.getPeerId();

        if (cmd.equals("!вкрулетка") || cmd.equals("!vkroulette")) {
            e.setCancelled(true);
            openBetMenu(fromId, peer);
        } else if (cmd.equals("!вкрулеткарутить") || cmd.equals("!vkspin")) {
            e.setCancelled(true);
            spinVK(fromId, peer, "normal");
        } else if (cmd.equals("!вкрулеткарусская") || cmd.equals("!vkrussian")) {
            e.setCancelled(true);
            spinVK(fromId, peer, "russian");
        } else if (cmd.equals("!вкрулеткадабл") || cmd.equals("!vkdouble")) {
            e.setCancelled(true);
            doubleOrNothingVK(fromId, peer);
        } else if (cmd.equals("!вкрулеткастат") || cmd.equals("!vkstats")) {
            e.setCancelled(true);
            showStats(fromId, peer);
        } else if (cmd.equals("!вкрулеткатоп") || cmd.equals("!vktop")) {
            e.setCancelled(true);
            showTop(peer);
        } else if (cmd.startsWith("!вкставка") || cmd.startsWith("!vkbet")) {
            e.setCancelled(true);
            handleBet(fromId, peer, cmd);
        } else if (cmd.equals("!вклаки") || cmd.equals("!vklucky")) {
            e.setCancelled(true);
            setLuckyNumber(fromId, peer);
        } else if (cmd.equals("!вктокены") || cmd.equals("!vktokens")) {
            e.setCancelled(true);
            showTokens(fromId, peer);
        }
    }

    // ========================================
    // МЕНЮ ВЫБОРА СТАВКИ
    // ========================================

    private void openBetMenu(int fromId, int peer) {
        int rep = VKChatPlugin.getInstance().getApi().getReputation(fromId);
        int bet = vkBets.getOrDefault(fromId, 500);
        String mode = vkModes.getOrDefault(fromId, "normal");
        int streak = vkWinStreak.getOrDefault(fromId, 0);
        int tokens = vkTokens.getOrDefault(fromId, 0);

        String msg = "🎰 ═══ РУЛЕТКА ВК ═══\n\n" +
                "💰 Баланс: " + rep + " реп\n" +
                "🎯 Ставка: " + bet + " реп\n" +
                "📊 Режим: " + (mode.equals("russian") ? "☠ Русская (x3)" : "🎯 Обычная") + "\n" +
                "🔥 Стрик: " + streak + "\n" +
                "🎟 Токены: " + tokens + "\n\n" +
                "Выбери ставку кнопкой:";

        String keyboard = buildBetKeyboard(bet, tokens);
        VKChatPlugin.getInstance().getApi().sendKeyboard(peer, msg, keyboard);
    }

    private String buildBetKeyboard(int currentBet, int tokens) {
        StringBuilder kb = new StringBuilder("{\"inline\":true,\"buttons\":[");

        // Ряд 1: Ставки
        kb.append("[");
        int[] bets = {100, 250, 500, 1000, 2500};
        for (int i = 0; i < bets.length; i++) {
            if (i > 0) kb.append(",");
            String color = bets[i] == currentBet ? "positive" : "secondary";
            kb.append("{\"action\":{\"type\":\"text\",\"label\":\"" + bets[i] + "\",\"payload\":\"{\\\"cmd\\\":\\\"!вкставка " + bets[i] + "\\\"}\"},\"color\":\"" + color + "\"}");
        }
        kb.append("],");

        // Ряд 2: Действия
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🎰 Крутить\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарутить\\\"}\"},\"color\":\"positive\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"☠ Русская\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарусская\\\"}\"},\"color\":\"negative\"}");
        kb.append(",");
        if (tokens >= 5) {
            kb.append("{\"action\":{\"type\":\"text\",\"label\":\"⚡ DoN\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткадабл\\\"}\"},\"color\":\"primary\"}");
        } else {
            kb.append("{\"action\":{\"type\":\"text\",\"label\":\"⚡ DoN\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткадабл\\\"}\"},\"color\":\"secondary\"}");
        }
        kb.append("],");

        // Ряд 3: Доп
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🍀 Удача\",\"payload\":\"{\\\"cmd\\\":\\\"!вклаки\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📊 Стат\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткастат\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🏆 Топ\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткатоп\\\"}\"},\"color\":\"secondary\"}");
        kb.append("]");

        kb.append("]}");
        return kb.toString();
    }

    // ========================================
    // ОБРАБОТКА СТАВКИ
    // ========================================

    private void handleBet(int fromId, int peer, String cmd) {
        try {
            String[] parts = cmd.split(" ");
            int bet = Integer.parseInt(parts[parts.length - 1]);
            if (bet < 100 || bet > 10000) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Ставка: 100-10000 реп!");
                return;
            }
            vkBets.put(fromId, bet);
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "🎯 Ставка: " + bet + " реп!");
            openBetMenu(fromId, peer);
        } catch (Exception ex) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Формат: !вкставка 500");
        }
    }

    // ========================================
    // КРУТКА
    // ========================================

    private void spinVK(int fromId, int peer, String mode) {
        // Проверка линка
        if (VKChatPlugin.getInstance().getApi().getUuidByVkId(fromId) == null) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Привяжи ВК к аккаунту на сервере!");
            return;
        }

        int bet = vkBets.getOrDefault(fromId, 500);
        if (mode.equals("russian")) bet *= 3;

        // Кулдаун
        long cooldown = 120000; // 2 мин
        Long last = vkCooldown.get(fromId);
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - last)) / 1000;
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "⏳ Подожди " + remaining + " сек.");
            return;
        }

        int rep = VKChatPlugin.getInstance().getApi().getReputation(fromId);
        if (rep < bet) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Нужно " + bet + " реп. (у тебя " + rep + ")");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(fromId, bet);
        vkCooldown.put(fromId, System.currentTimeMillis());
        vkModes.put(fromId, mode);
        vkTotalSpins.merge(fromId, 1, Integer::sum);

        // Анимация
        String[] frames = {"🎰", "💎", "🍀", "⭐", "🔥", "💰", "🏆"};
        StringBuilder anim = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            anim.append(frames[ThreadLocalRandom.current().nextInt(frames.length)]).append(" ");
        }

        String header;
        if (mode.equals("russian")) {
            header = "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n" +
                    "💰 Ставка: " + bet + " реп\n" +
                    "⚠ Шанс выжить: 50%\n\n";
        } else {
            header = "🎰 ═══ РУЛЕТКА ═══\n" +
                    "💰 Ставка: " + bet + " реп\n\n";
        }

        VKChatPlugin.getInstance().getApi().sendMessage(peer, header + anim.toString() + "\n\nКрутится...");

        // Результат через 2 сек
        final int finalBet = bet;
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                processResult(fromId, peer, mode, finalBet);
            }
        }, 2000);
    }

    private void processResult(int fromId, int peer, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : NORMAL_PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];

        String name = prize[0];
        String tier = prize[1];
        int value = Integer.parseInt(prize[2]);

        // Стрик множитель
        int streak = vkWinStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);

        // Счастливое число
        int lucky = vkLuckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        // Обновляем стрик
        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) {
            vkWinStreak.merge(fromId, 1, Integer::sum);
            vkTotalWins.merge(fromId, 1, Integer::sum);
        } else {
            vkWinStreak.put(fromId, 0);
        }

        // Формируем результат
        String result;
        String tierEmoji = getTierEmoji(tier);

        if (tier.equals("death")) {
            int loss = Math.abs(value);
            VKChatPlugin.getInstance().getApi().takeReputation(fromId, loss);
            vkTotalRepLost.merge(fromId, loss, Integer::sum);
            result = tierEmoji + " " + name + "\n" +
                    "💀 -" + loss + " реп!\n" +
                    "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId);
        } else if (tier.equals("jackpot")) {
            int jackpot = (int) (2000 * mult);
            VKChatPlugin.getInstance().getApi().addReputation(fromId, jackpot);
            vkTotalRepWon.merge(fromId, jackpot, Integer::sum);
            result = tierEmoji + " " + name + "\n" +
                    "🎉 +" + jackpot + " реп!\n" +
                    "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId);
            // Broadcast
            VKChatPlugin.getInstance().getApi().sendToMainChat("🏆 Игрок сорвал ДЖЕКПОТ в рулетке: +" + jackpot + " реп!");
        } else if (tier.equals("token")) {
            int bonusTokens = 3 + ThreadLocalRandom.current().nextInt(5);
            vkTokens.merge(fromId, bonusTokens, Integer::sum);
            result = tierEmoji + " " + name + "\n" +
                    "🎟 +" + bonusTokens + " токенов!\n" +
                    "💰 Токенов: " + vkTokens.get(fromId);
        } else if (tier.equals("lucky")) {
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
            vkLuckyNumber.put(fromId, num);
            result = tierEmoji + " " + name + "\n" +
                    "🍀 Шанс: " + num + "% на x1.5!";
        } else if (tier.equals("mystery")) {
            int bonus = (int) ((300 + ThreadLocalRandom.current().nextInt(500)) * mult);
            VKChatPlugin.getInstance().getApi().addReputation(fromId, bonus);
            vkTotalRepWon.merge(fromId, bonus, Integer::sum);
            result = tierEmoji + " " + name + "\n" +
                    "✨ +" + bonus + " реп!\n" +
                    "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId);
        } else if (value == 0) {
            result = tierEmoji + " " + name + "\n" +
                    "💀 Пусто! В следующий раз повезёт!";
            // Double or Nothing
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                vkDoubleOrNothing.put(fromId, 0.0);
                result += "\n\n⚡ Double or Nothing? Напиши !вкрулеткадабл";
            }
        } else {
            int bonus = (int) (value * mult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                result = "🍀 Счастливое число!\n\n";
            } else {
                result = "";
            }
            VKChatPlugin.getInstance().getApi().addReputation(fromId, bonus);
            vkTotalRepWon.merge(fromId, bonus, Integer::sum);
            result += tierEmoji + " " + name + "\n" +
                    "🎉 +" + bonus + " реп!\n" +
                    "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId);
        }

        // Стрик
        int newStreak = vkWinStreak.getOrDefault(fromId, 0);
        if (newStreak > 1) {
            result += "\n🔥 Стрик: x" + newStreak + " (x" + String.format("%.1f", 1.0 + newStreak * 0.1) + ")";
        }

        VKChatPlugin.getInstance().getApi().sendMessage(peer, result);
    }

    private String getTierEmoji(String tier) {
        switch (tier) {
            case "legendary": return "🏆";
            case "jackpot": return "💰";
            case "rare": return "💎";
            case "uncommon": return "🟢";
            case "death": return "💀";
            case "mystery": return "✨";
            case "lucky": return "🍀";
            case "token": return "🎟";
            default: return "⚪";
        }
    }

    // ========================================
    // DOUBLE OR NOTHING
    // ========================================

    private void doubleOrNothingVK(int fromId, int peer) {
        Double pending = vkDoubleOrNothing.remove(fromId);
        if (pending == null) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Нет активного предложения!");
            return;
        }

        VKChatPlugin.getInstance().getApi().sendMessage(peer, "⚡ Double or Nothing...");

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
                if (win) {
                    int bonus = 200 + ThreadLocalRandom.current().nextInt(600);
                    VKChatPlugin.getInstance().getApi().addReputation(fromId, bonus);
                    vkTotalRepWon.merge(fromId, bonus, Integer::sum);
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "🎉 DOUBLE! +" + bonus + " реп!\n💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId));
                } else {
                    int loss = 100 + ThreadLocalRandom.current().nextInt(300);
                    VKChatPlugin.getInstance().getApi().takeReputation(fromId, loss);
                    vkTotalRepLost.merge(fromId, loss, Integer::sum);
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "💀 NOTHING! -" + loss + " реп!\n💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId));
                }
            }
        }, 1500);
    }

    // ========================================
    // СТАТИСТИКА
    // ========================================

    private void showStats(int fromId, int peer) {
        int spins = vkTotalSpins.getOrDefault(fromId, 0);
        int wins = vkTotalWins.getOrDefault(fromId, 0);
        int repWon = vkTotalRepWon.getOrDefault(fromId, 0);
        int repLost = vkTotalRepLost.getOrDefault(fromId, 0);
        int streak = vkWinStreak.getOrDefault(fromId, 0);
        int tokens = vkTokens.getOrDefault(fromId, 0);
        int lucky = vkLuckyNumber.getOrDefault(fromId, 0);

        String msg = "📊 ═══ СТАТИСТИКА РУЛЕТКИ ═══\n\n" +
                "🎰 Вращений: " + spins + "\n" +
                "✅ Побед: " + wins + "\n" +
                "📈 Выиграно: +" + repWon + " реп\n" +
                "📉 Проиграно: -" + repLost + " реп\n" +
                "🔥 Стрик: " + streak + "\n" +
                "🎟 Токены: " + tokens + "\n" +
                "🍀 Счастливое число: " + (lucky > 0 ? lucky + "%" : "нет") + "\n" +
                "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%";

        VKChatPlugin.getInstance().getApi().sendMessage(peer, msg);
    }

    private void showTop(int peer) {
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(vkTotalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder msg = new StringBuilder("🏆 ═══ ТОП РУЛЕТКИ ═══\n\n");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<Integer, Integer> entry = sorted.get(i);
            msg.append(i + 1).append(". ID").append(entry.getKey()).append(" — +").append(entry.getValue()).append(" реп\n");
        }

        if (sorted.isEmpty()) {
            msg.append("Пока нет данных.");
        }

        VKChatPlugin.getInstance().getApi().sendMessage(peer, msg.toString());
    }

    // ========================================
    // СЧАСТЛИВОЕ ЧИСЛО И ТОКЕНЫ
    // ========================================

    private void setLuckyNumber(int fromId, int peer) {
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        vkLuckyNumber.put(fromId, num);
        VKChatPlugin.getInstance().getApi().sendMessage(peer, "🍀 Счастливое число: " + num + "%\nШанс на x1.5 к выигрышу!");
    }

    private void showTokens(int fromId, int peer) {
        int tokens = vkTokens.getOrDefault(fromId, 0);
        VKChatPlugin.getInstance().getApi().sendMessage(peer, "🎟 Токены: " + tokens + "\n\n" +
                "Нужно 5 для бесплатного спина\n" +
                "Зарабатываешь за торговлю на рынке");
    }

    // ========================================
    // ПУБЛИЧНЫЕ МЕТОДЫ
    // ========================================

    public void earnTokens(int vkId, int amount) {
        vkTokens.merge(vkId, amount, Integer::sum);
    }
}
