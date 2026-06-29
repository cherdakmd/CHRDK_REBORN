package ru.example.vkchatmarket.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VKRouletteListener implements Listener {
    private final VKChatMarketPlugin plugin;

    // Ставки
    private final Map<Integer, Integer> vkBets = new ConcurrentHashMap<>();
    // Режим
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
    // Анимация в процессе
    private final Set<Integer> spinning = ConcurrentHashMap.newKeySet();
    // Ожидающие предметы (vkId -> список "MATERIAL;amount")
    private final Map<Integer, List<String>> pendingItems = new ConcurrentHashMap<>();

    // Призы с предметами
    private static final String[][] NORMAL_PRIZES = {
        {"💎 Алмаз", "item", "DIAMOND;1"},
        {"🔮 Эндер-жемчуг x3", "item", "ENDER_PEARL;3"},
        {"🔥 Огненный стержень x2", "item", "BLAZE_ROD;2"},
        {"⚡ Редстоун-блок x5", "item", "REDSTONE_BLOCK;5"},
        {"🍀 Изумруд x2", "item", "EMERALD;2"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1"},
        {"🧪 Опыт-бутылки x10", "item", "EXPERIENCE_BOTTLE;10"},
        {"🪙 +200 реп", "rep", "200"},
        {"💰 +500 реп", "rep", "500"},
        {"🏆 ДЖЕКПОТ!", "jackpot", "2000"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 +100 реп", "rep", "100"},
        {"🍎 Золотое яблоко x2", "item", "GOLDEN_APPLE;2"},
        {"✨ Мистический +300 реп", "rep", "300"},
        {"🍀 Счастливое число", "lucky", "0"},
        {"🎟 Токены x3", "token", "3"},
        {"🧊 Алмазный блок", "item", "DIAMOND_BLOCK;1"},
        {"🪣 Ведро молока", "item", "MILK_BUCKET;1"},
        {"🏹 Лук", "item", "BOW;1"},
        {"⚔ Алмазный меч", "item", "DIAMOND_SWORD;1"},
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💎💎 Алмаз x3", "item", "DIAMOND;3"},
        {"💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "item", "NETHERITE_INGOT;1"},
        {"🏆 ДЖЕКПОТ x2!", "jackpot", "5000"},
        {"💰 +1000 реп", "rep", "1000"},
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500"},
        {"🍎 Золотое яблоко x5", "item", "GOLDEN_APPLE;5"},
        {"💀 -300 реп", "death", "-300"},
        {"🔥 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1"},
        {"💀 Пусто", "empty", "0"},
        {"🪙 +300 реп", "rep", "300"},
        {"💀 -200 реп", "death", "-200"},
        {"🔮 Эндер-жемчуг x16", "item", "ENDER_PEARL;16"},
        {"⚔ Алмазный меч x2", "item", "DIAMOND_SWORD;2"},
        {"🛡 Алмазная броня", "item", "DIAMOND_CHESTPLATE;1"},
    };

    public VKRouletteListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand().toLowerCase();
        int fromId = e.getSenderVkId();
        int peer = e.getPeerId();

        // ═══ ТОЛЬКО В ЛС БОТА ═══
        if (peer >= 2000000000) {
            if (cmd.startsWith("!вкрулетка") || cmd.startsWith("!vkroulette") ||
                cmd.startsWith("!вкставка") || cmd.startsWith("!vkbet") ||
                cmd.startsWith("!вклаки") || cmd.startsWith("!vklucky") ||
                cmd.startsWith("!вктокены") || cmd.startsWith("!vktokens") ||
                cmd.startsWith("!вкрулеткарутить") || cmd.startsWith("!vkspin") ||
                cmd.startsWith("!вкрулеткарусская") || cmd.startsWith("!vkrussian") ||
                cmd.startsWith("!вкрулеткадабл") || cmd.startsWith("!vkdouble") ||
                cmd.startsWith("!вкрулеткастат") || cmd.startsWith("!vkstats") ||
                cmd.startsWith("!вкрулеткатоп") || cmd.startsWith("!vktop") ||
                cmd.startsWith("!вкпризы") || cmd.startsWith("!vkprizes")) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "🎰 Рулетка работает только в ЛС бота!\nНапиши мне в личные сообщения.");
                e.setCancelled(true);
                return;
            }
        }

        if (cmd.equals("!вкрулетка") || cmd.equals("!vkroulette")) {
            e.setCancelled(true);
            openMainMenu(fromId, peer);
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
        } else if (cmd.equals("!вкпризы") || cmd.equals("!vkprizes")) {
            e.setCancelled(true);
            showPendingItems(fromId, peer);
        }
    }

    // ========================================
    // ГЛАВНОЕ МЕНЮ
    // ========================================

    private void openMainMenu(int fromId, int peer) {
        int rep = VKChatPlugin.getInstance().getApi().getReputation(fromId);
        int bet = vkBets.getOrDefault(fromId, 500);
        int streak = vkWinStreak.getOrDefault(fromId, 0);
        int tokens = vkTokens.getOrDefault(fromId, 0);
        int spins = vkTotalSpins.getOrDefault(fromId, 0);
        int wins = vkTotalWins.getOrDefault(fromId, 0);
        List<String> pending = pendingItems.get(fromId);

        StringBuilder msg = new StringBuilder();
        msg.append("╔═══════════════════════════╗\n");
        msg.append("║     🎰 РУЛЕТКА ВК 🎰      ║\n");
        msg.append("╚═══════════════════════════╝\n\n");
        msg.append("💰 Баланс: ").append(rep).append(" реп\n");
        msg.append("🎯 Ставка: ").append(bet).append(" реп\n");
        msg.append("🔥 Стрик: ").append(streak).append("\n");
        msg.append("🎟 Токены: ").append(tokens).append("\n");
        msg.append("📊 Винрейт: ").append(spins > 0 ? (wins * 100 / spins) : 0).append("%\n");

        if (pending != null && !pending.isEmpty()) {
            msg.append("\n📦 Предметов ждёт: ").append(pending.size()).append(" (напиши !вкпризы)");
        }

        msg.append("\n\nВыбери действие:");

        VKChatPlugin.getInstance().getApi().sendKeyboard(peer, msg.toString(), buildMainMenu(bet));
    }

    private String buildMainMenu(int currentBet) {
        StringBuilder kb = new StringBuilder("{\"inline\":true,\"buttons\":[");

        // Ряд 1: Ставки
        kb.append("[");
        int[] bets = {100, 250, 500, 1000, 5000};
        for (int i = 0; i < bets.length; i++) {
            if (i > 0) kb.append(",");
            String color = bets[i] == currentBet ? "positive" : "secondary";
            kb.append("{\"action\":{\"type\":\"text\",\"label\":\"").append(bets[i]).append("\",\"payload\":\"{\\\"cmd\\\":\\\"!вкставка ").append(bets[i]).append("\\\"}\"},\"color\":\"").append(color).append("\"}");
        }
        kb.append("],");

        // Ряд 2: Крутить / Русская
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🎰 Крутить\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарутить\\\"}\"},\"color\":\"positive\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"☠ Русская (x3)\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарусская\\\"}\"},\"color\":\"negative\"}");
        kb.append("],");

        // Ряд 3: Double / Удача / Токены
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"⚡ Double\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткадабл\\\"}\"},\"color\":\"primary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🍀 Удача\",\"payload\":\"{\\\"cmd\\\":\\\"!вклаки\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🎟 Токены\",\"payload\":\"{\\\"cmd\\\":\\\"!вктокены\\\"}\"},\"color\":\"secondary\"}");
        kb.append("],");

        // Ряд 4: Статистика / Топ / Призы
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📊 Статистика\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткастат\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🏆 Топ\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткатоп\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📦 Призы\",\"payload\":\"{\\\"cmd\\\":\\\"!вкпризы\\\"}\"},\"color\":\"primary\"}");
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
            openMainMenu(fromId, peer);
        } catch (Exception ex) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Формат: !вкставка 500");
        }
    }

    // ========================================
    // КРУТКА С АНИМАЦИЕЙ
    // ========================================

    private void spinVK(int fromId, int peer, String mode) {
        if (spinning.contains(fromId)) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "⏳ Рулетка уже крутится!");
            return;
        }

        if (VKChatPlugin.getInstance().getApi().getUuidByVkId(fromId) == null) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Привяжи ВК к аккаунту на сервере!");
            return;
        }

        int bet = vkBets.getOrDefault(fromId, 500);
        if (mode.equals("russian")) bet *= 3;

        long cooldown = 60000;
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
        spinning.add(fromId);

        // ═══ АНИМАЦИЯ ═══
        String header;
        if (mode.equals("russian")) {
            header = "☠ ═══ РУССКАЯ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп\n⚠ Шанс выжить: 50%";
        } else {
            header = "🎰 ═══ РУЛЕТКА ═══\n💰 Ставка: " + bet + " реп";
        }

        VKChatPlugin.getInstance().getApi().sendMessage(peer, header);

        // Кадры анимации
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
                    for (String s : frames[frame]) {
                        anim.append(s).append(" ");
                    }
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, anim.toString());
                }
            }, 500L + i * 400L);
        }

        // Результат через 3 сек
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
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : NORMAL_PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];

        String name = prize[0];
        String type = prize[1];
        String data = prize[2];

        int streak = vkWinStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);

        int lucky = vkLuckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        boolean isWin = !type.equals("empty") && !type.equals("death");
        if (isWin) {
            vkWinStreak.merge(fromId, 1, Integer::sum);
            vkTotalWins.merge(fromId, 1, Integer::sum);
        } else {
            vkWinStreak.put(fromId, 0);
        }

        StringBuilder result = new StringBuilder();
        result.append("\n");

        if (type.equals("death")) {
            int loss = Math.abs(Integer.parseInt(data));
            VKChatPlugin.getInstance().getApi().takeReputation(fromId, loss);
            vkTotalRepLost.merge(fromId, loss, Integer::sum);
            result.append("💀 ").append(name).append("\n");
            result.append("📉 -").append(loss).append(" реп!\n");
            result.append("💰 Баланс: ").append(VKChatPlugin.getInstance().getApi().getReputation(fromId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (Integer.parseInt(data) * mult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            VKChatPlugin.getInstance().getApi().addReputation(fromId, jackpot);
            vkTotalRepWon.merge(fromId, jackpot, Integer::sum);
            result.append("🏆💰 ").append(name).append("\n");
            result.append("🎉 +").append(jackpot).append(" реп!\n");
            result.append("💰 Баланс: ").append(VKChatPlugin.getInstance().getApi().getReputation(fromId));
            VKChatPlugin.getInstance().getApi().sendToMainChat("🏆 Игрок сорвал ДЖЕКПОТ в рулетке: +" + jackpot + " реп!");

        } else if (type.equals("token")) {
            int tokens = Integer.parseInt(data);
            vkTokens.merge(fromId, tokens, Integer::sum);
            result.append("🎟 ").append(name).append("\n");
            result.append("🎟 +").append(tokens).append(" токенов!\n");
            result.append("💰 Всего: ").append(vkTokens.get(fromId));

        } else if (type.equals("lucky")) {
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
            vkLuckyNumber.put(fromId, num);
            result.append("🍀 ").append(name).append("\n");
            result.append("🍀 Шанс: ").append(num).append("% на x1.5!");

        } else if (type.equals("rep")) {
            int bonus = (int) (Integer.parseInt(data) * mult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                result.append("🍀 Счастливое число!\n\n");
            }
            VKChatPlugin.getInstance().getApi().addReputation(fromId, bonus);
            vkTotalRepWon.merge(fromId, bonus, Integer::sum);
            result.append("🪙 ").append(name).append("\n");
            result.append("🎉 +").append(bonus).append(" реп!\n");
            result.append("💰 Баланс: ").append(VKChatPlugin.getInstance().getApi().getReputation(fromId));

        } else if (type.equals("item")) {
            // ═══ ПРЕДМЕТ — СОХРАНЯЕМ В ОЖИДАНИЕ ═══
            String[] parts = data.split(";");
            String material = parts[0];
            int amount = Integer.parseInt(parts[1]);

            pendingItems.putIfAbsent(fromId, new ArrayList<>());
            pendingItems.get(fromId).add(data);

            result.append("🎉 ").append(name).append("\n");
            result.append("📦 Предмет готов! Забери на сервере командой /рулетка\n");
            result.append("📦 Всего предметов: ").append(pendingItems.get(fromId).size());

        } else {
            // empty
            result.append("💀 ").append(name).append("\n");
            result.append("😅 В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                vkDoubleOrNothing.put(fromId, 0.0);
                result.append("\n\n⚡ Double or Nothing? Напиши !вкрулеткадабл");
            }
        }

        int newStreak = vkWinStreak.getOrDefault(fromId, 0);
        if (newStreak > 1) {
            result.append("\n🔥 Стрик: x").append(newStreak);
        }

        if (isLucky && !type.equals("lucky")) {
            result.append("\n🍀 Удача сработала!");
        }

        // Клавиатура после результата
        VKChatPlugin.getInstance().getApi().sendKeyboard(peer, result.toString(), buildAfterSpinKeyboard());
    }

    private String buildAfterSpinKeyboard() {
        return "{\"inline\":true,\"buttons\":[" +
                "[{\"action\":{\"type\":\"text\",\"label\":\"🎰 Ещё раз\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарутить\\\"}\"},\"color\":\"positive\"}," +
                "{\"action\":{\"type\":\"text\",\"label\":\"☠ Русская\",\"payload\":\"{\\\"cmd\\\":\\\"!вкрулеткарусская\\\"}\"},\"color\":\"negative\"}," +
                "{\"action\":{\"type\":\"text\",\"label\":\"📦 Призы\",\"payload\":\"{\\\"cmd\\\":\\\"!вкпризы\\\"}\"},\"color\":\"primary\"}]" +
                "]}";
    }

    // ========================================
    // ОЖИДАЮЩИЕ ПРЕДМЕТЫ
    // ========================================

    private void showPendingItems(int fromId, int peer) {
        List<String> items = pendingItems.get(fromId);
        if (items == null || items.isEmpty()) {
            VKChatPlugin.getInstance().getApi().sendMessage(peer, "📦 Нет ожидающих предметов.\nВыигрывай в рулетке!");
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("📦 ═══ ТВОИ ПРИЗЫ ═══\n\n");
        msg.append("Забери в игре: /рулетка\n\n");

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

        VKChatPlugin.getInstance().getApi().sendMessage(peer, msg.toString());
    }

    // Публичный метод для забора предметов
    public List<String> takePendingItems(int vkId) {
        return pendingItems.remove(vkId);
    }

    public boolean hasPendingItems(int vkId) {
        List<String> items = pendingItems.get(vkId);
        return items != null && !items.isEmpty();
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

        VKChatPlugin.getInstance().getApi().sendMessage(peer, "⚡ ═══ DOUBLE OR NOTHING ═══\n\nКрутится...");

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
                if (win) {
                    int bonus = 200 + ThreadLocalRandom.current().nextInt(600);
                    VKChatPlugin.getInstance().getApi().addReputation(fromId, bonus);
                    vkTotalRepWon.merge(fromId, bonus, Integer::sum);
                    VKChatPlugin.getInstance().getApi().sendKeyboard(peer,
                            "🎉 ═══ DOUBLE! ═══\n\n" +
                            "✅ +" + bonus + " реп!\n" +
                            "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId),
                            buildAfterSpinKeyboard());
                } else {
                    int loss = 100 + ThreadLocalRandom.current().nextInt(300);
                    VKChatPlugin.getInstance().getApi().takeReputation(fromId, loss);
                    vkTotalRepLost.merge(fromId, loss, Integer::sum);
                    VKChatPlugin.getInstance().getApi().sendKeyboard(peer,
                            "💀 ═══ NOTHING! ═══\n\n" +
                            "❌ -" + loss + " реп!\n" +
                            "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(fromId),
                            buildAfterSpinKeyboard());
                }
            }
        }, 2000);
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

        String msg = "📊 ═══ СТАТИСТИКА ═══\n\n" +
                "🎰 Вращений: " + spins + "\n" +
                "✅ Побед: " + wins + "\n" +
                "📈 Выиграно: +" + repWon + " реп\n" +
                "📉 Проиграно: -" + repLost + " реп\n" +
                "🔥 Стрик: " + streak + "\n" +
                "🎟 Токены: " + tokens + "\n" +
                "🍀 Удача: " + (lucky > 0 ? lucky + "%" : "нет") + "\n" +
                "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%";

        VKChatPlugin.getInstance().getApi().sendKeyboard(peer, msg, buildAfterSpinKeyboard());
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
        VKChatPlugin.getInstance().getApi().sendKeyboard(peer,
                "🍀 Счастливое число: " + num + "%\nШанс на x1.5 к выигрышу!",
                buildAfterSpinKeyboard());
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
