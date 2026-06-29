package ru.example.vkchat.vk;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Единая рулетка для VK и In-game
 * 
 * Все данные хранятся по VK ID (int) — единый ключ.
 * In-game игроки преобразуются через getLinkedVkId().
 * 
 * Фичи:
 * 1. VK меню (ЛС бота)
 * 2. In-game GUI (Майнкрафт)
 * 3. Выбор ставки
 * 4. Обычная крутка
 * 5. Русская рулетка (x3)
 * 6. Double or Nothing
 * 7. Стрики (+10% за победу)
 * 8. Счастливое число
 * 9. Токены
 * 10. Предметы в ожидающие
 * 11. Автоспин (in-game)
 * 12. Анимация
 * 13. Статистика
 * 14. Лидерборд
 * 15. Настраиваемый КД
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
    private final Set<Integer> autoSpinEnabled = ConcurrentHashMap.newKeySet();
    private final Map<Integer, List<String>> pendingItems = new ConcurrentHashMap<>();
    private volatile int jackpotPool = 5000;

    // ═══ ПРИЗЫ ═══
    private static final String[][] PRIZES = {
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
    };

    public RouletteManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════
    // VK МЕНЮ
    // ═══════════════════════════════════════════════════════════

    public void openVKMenu(int fromId, int peer) {
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

    // ═══════════════════════════════════════════════════════════
    // VK ОБРАБОТКА КОМАНД
    // ═══════════════════════════════════════════════════════════

    public void handleVKCommand(int fromId, int peer, String cmd) {
        if (!cmd.equals("!рулетка") && !cmd.equals("!roulette") && peer >= 2000000000) {
            plugin.getVkManager().sendMessage(peer, "🎰 Рулетка работает только в ЛС бота!");
            return;
        }

        if (cmd.matches("\\d+")) {
            handleBet(fromId, peer, "!ставка " + cmd);
            return;
        }

        if (cmd.equals("!рулетка") || cmd.equals("!roulette")) {
            openVKMenu(fromId, peer);
        } else if (cmd.equals("!рулеткакрутить") || cmd.equals("!rspin")) {
            spinVK(fromId, peer, "normal");
        } else if (cmd.equals("!рулеткарусская") || cmd.equals("!rrussian")) {
            spinVK(fromId, peer, "russian");
        } else if (cmd.equals("!рулеткадабл") || cmd.equals("!rdouble")) {
            doubleOrNothingVK(fromId, peer);
        } else if (cmd.equals("!рулеткастат") || cmd.equals("!rstats")) {
            showStatsVK(fromId, peer);
        } else if (cmd.equals("!рулеткатоп") || cmd.equals("!rtop")) {
            showTopVK(peer);
        } else if (cmd.startsWith("!ставка") || cmd.equals("!rbet")) {
            handleBet(fromId, peer, cmd);
        } else if (cmd.equals("!рулеткалаки") || cmd.equals("!rlucky")) {
            setLuckyNumber(fromId, peer);
        } else if (cmd.equals("!рулеткатокены") || cmd.equals("!rtokens")) {
            showTokensVK(fromId, peer);
        } else if (cmd.equals("!рулеткапризы") || cmd.equals("!rprizes")) {
            showPendingItemsVK(fromId, peer);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VK СТАВКА
    // ═══════════════════════════════════════════════════════════

    private void handleBet(int fromId, int peer, String cmd) {
        try {
            String numStr = cmd.replaceAll("[^0-9]", "").trim();
            if (numStr.isEmpty()) {
                plugin.getVkManager().sendMessage(peer, "❌ Формат: !ставка 500");
                return;
            }
            int bet = Integer.parseInt(numStr);
            if (bet < 100 || bet > 10000) {
                plugin.getVkManager().sendMessage(peer, "❌ Ставка: 100-10000 реп!");
                return;
            }
            bets.put(fromId, bet);
            plugin.getVkManager().sendMessage(peer, "🎯 Ставка: " + bet + " реп!");
            openVKMenu(fromId, peer);
        } catch (Exception ex) {
            plugin.getVkManager().sendMessage(peer, "❌ Формат: !ставка 500");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VK КРУТКА
    // ═══════════════════════════════════════════════════════════

    private void spinVK(int fromId, int peer, String mode) {
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
        totalSpins.merge(fromId, 1, Integer::sum);
        spinning.add(fromId);
        jackpotPool += bet / 10;

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
            processResultVK(fromId, peer, mode, finalBet);
        }, 50L);
    }

    private void processResultVK(int fromId, int peer, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        String name = prize[0];
        String type = prize[1];
        String data = prize[2];

        int streak = winStreak.getOrDefault(fromId, 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(fromId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        boolean isWin = type.equals("rep") || type.equals("item") || type.equals("jackpot");
        if (isWin) {
            winStreak.merge(fromId, 1, Integer::sum);
            totalWins.merge(fromId, 1, Integer::sum);
        } else if (type.equals("empty") || type.equals("death")) {
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
            plugin.getVkManager().sendToMainChat("🏆 Игрок сорвал ДЖЕКПОТ: +" + jackpot + " реп!");

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

        plugin.getVkManager().sendKeyboard(peer, result.toString(), VKKeyboardBuilder.rouletteAfterSpin());
    }

    // ═══════════════════════════════════════════════════════════
    // VK DOUBLE OR NOTHING
    // ═══════════════════════════════════════════════════════════

    private void doubleOrNothingVK(int fromId, int peer) {
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
    // VK СТАТИСТИКА
    // ═══════════════════════════════════════════════════════════

    private void showStatsVK(int fromId, int peer) {
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

    private void showTopVK(int peer) {
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

    private void showTokensVK(int fromId, int peer) {
        int tok = tokens.getOrDefault(fromId, 0);
        plugin.getVkManager().sendMessage(peer, "🎟 Токены: " + tok);
    }

    private void showPendingItemsVK(int fromId, int peer) {
        List<String> items = pendingItems.get(fromId);
        if (items == null || items.isEmpty()) {
            plugin.getVkManager().sendMessage(peer, "📦 Нет ожидающих предметов.");
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

    // ═══════════════════════════════════════════════════════════
    // IN-GAME GUI
    // ═══════════════════════════════════════════════════════════

    public void openInGameGUI(Player p) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        int rep = plugin.getReputationManager().getPoints(vkId);
        int bet = bets.getOrDefault(vkId, 500);
        int streak = winStreak.getOrDefault(vkId, 0);
        int tok = tokens.getOrDefault(vkId, 0);
        int spins = totalSpins.getOrDefault(vkId, 0);
        int wins = totalWins.getOrDefault(vkId, 0);
        int pending = getPendingCount(vkId);

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "🎰 Рулетка");

        for (int i = 0; i < 54; i++) inv.setItem(i, glass(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(4, info(Material.BOOK, ChatColor.GOLD + "🎰 Рулетка",
                ChatColor.WHITE + "💰 Баланс: " + ChatColor.YELLOW + rep + " реп",
                ChatColor.WHITE + "🎯 Ставка: " + ChatColor.GREEN + bet + " реп",
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak,
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tok,
                ChatColor.WHITE + "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%",
                ChatColor.WHITE + "🏆 Джекпот: " + jackpotPool + " реп",
                pending > 0 ? ChatColor.LIGHT_PURPLE + "📦 Призов: " + pending : ""));

        int[] betsArr = {100, 250, 500, 1000, 2500, 5000, 10000};
        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < betsArr.length; i++) {
            boolean selected = betsArr[i] == bet;
            inv.setItem(betSlots[i], glass(
                    selected ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                    (selected ? ChatColor.GREEN : ChatColor.YELLOW) + "" + betsArr[i] + " реп"));
        }

        inv.setItem(30, btn(Material.NETHER_STAR, ChatColor.GREEN + "🎰 КРУТИТЬ!", "Ставка: " + bet + " реп"));
        inv.setItem(31, btn(Material.BLAZE_POWDER, ChatColor.RED + "☠ РУССКАЯ", "x3 цена, x3 награда"));
        boolean hasDoN = doubleOrNothing.containsKey(vkId);
        inv.setItem(32, btn(Material.TNT, ChatColor.YELLOW + "⚡ DOUBLE",
                hasDoN ? ChatColor.GREEN + "Доступно!" : ChatColor.GRAY + "Нет"));

        inv.setItem(38, btn(Material.CLOCK, ChatColor.AQUA + "🔄 Авто-спин",
                autoSpinEnabled.contains(vkId) ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ"));
        inv.setItem(39, btn(Material.EMERALD, ChatColor.GREEN + "🍀 Удача",
                "Твоё: " + luckyNumber.getOrDefault(vkId, 0) + "%"));
        inv.setItem(40, btn(Material.PAPER, ChatColor.AQUA + "📊 Статистика", ""));

        inv.setItem(45, btn(Material.ARROW, ChatColor.WHITE + "🏠 Назад", ""));
        inv.setItem(47, btn(Material.ENDER_CHEST, ChatColor.LIGHT_PURPLE + "📦 Призы",
                pending > 0 ? ChatColor.GREEN + "Есть!" : ChatColor.GRAY + "Нет"));
        inv.setItem(53, btn(Material.COMPASS, ChatColor.YELLOW + "🏆 Топ", ""));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    // IN-GAME ОБРАБОТКА КЛИКОВ
    // ═══════════════════════════════════════════════════════════

    public void handleInGameClick(Player p, int slot) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        int[] betsArr = {100, 250, 500, 1000, 2500, 5000, 10000};
        for (int i = 0; i < betSlots.length; i++) {
            if (slot == betSlots[i]) {
                bets.put(vkId, betsArr[i]);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openInGameGUI(p);
                return;
            }
        }

        switch (slot) {
            case 30: spinInGame(p, "normal"); break;
            case 31: spinInGame(p, "russian"); break;
            case 32: doubleOrNothingInGame(p); break;
            case 38: toggleAutoSpin(vkId); openInGameGUI(p); break;
            case 39: setLuckyNumberInGame(p); break;
            case 40: p.sendMessage(getFullStats(vkId)); break;
            case 45: break; // Назад — обрабатывается в MarketGuiListener
            case 47: claimPrizes(p); break;
            case 53: showLeaderboard(p); break;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // IN-GAME КРУТКА
    // ═══════════════════════════════════════════════════════════

    private void spinInGame(Player p, String mode) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        if (spinning.contains(vkId)) {
            p.sendMessage(ChatColor.RED + "⏳ Рулетка уже крутится!");
            return;
        }

        int bet = bets.getOrDefault(vkId, 500);
        if (mode.equals("russian")) bet *= 3;

        int rep = plugin.getReputationManager().getPoints(vkId);
        if (rep < bet) {
            p.sendMessage(ChatColor.RED + "Нужно " + bet + " реп. (у тебя " + rep + ")");
            return;
        }

        plugin.getReputationManager().deductPoints(vkId, bet);
        totalSpins.merge(vkId, 1, Integer::sum);
        spinning.add(vkId);
        jackpotPool += bet / 10;

        p.closeInventory();

        if (mode.equals("russian")) {
            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_RED + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.RED + "║   ☠ РУССКАЯ РУЛЕТКА ☠        ║");
            p.sendMessage(ChatColor.RED + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.DARK_RED + "╚═══════════════════════════════╝");
        } else {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.YELLOW + "║     🎰 РУЛЕТКА 🎰            ║");
            p.sendMessage(ChatColor.YELLOW + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════╝");
        }
        p.sendMessage("");

        String[][] frames = {
            {"🎰", "💎", "🍀", "⭐", "🔥", "💰", "🏆"},
            {"💎", "⭐", "🔥", "💰", "🏆", "🎰", "🍀"},
            {"🔥", "💰", "🏆", "🎰", "🍀", "💎", "⭐"},
            {"💰", "🏆", "🎰", "🍀", "💎", "⭐", "🔥"},
            {"🏆", "🎰", "🍀", "💎", "⭐", "🔥", "💰"},
        };

        for (int i = 0; i < frames.length; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    StringBuilder anim = new StringBuilder();
                    for (String s : frames[frame]) anim.append(s).append(" ");
                    p.sendMessage(ChatColor.GRAY + "  " + anim);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + frame * 0.1f);
                }
            }, 5L + i * 5L);
        }

        final int finalBet = bet;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning.remove(vkId);
            if (p.isOnline()) {
                processResultInGame(p, vkId, mode, finalBet);
                if (autoSpinEnabled.contains(vkId) && p.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && autoSpinEnabled.contains(vkId)) {
                            spinInGame(p, mode);
                        }
                    }, 20L);
                }
            }
        }, 35L);
    }

    private void processResultInGame(Player p, int vkId, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        String name = prize[0];
        String type = prize[1];
        String data = prize[2];
        String tier = prize[3];

        int streak = winStreak.getOrDefault(vkId, 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(vkId, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        boolean isWin = type.equals("rep") || type.equals("item") || type.equals("jackpot");
        if (isWin) {
            winStreak.merge(vkId, 1, Integer::sum);
            totalWins.merge(vkId, 1, Integer::sum);
        } else if (type.equals("empty") || type.equals("death")) {
            winStreak.put(vkId, 0);
        }

        ChatColor tierColor = getTierColor(tier);
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "  ╔═══════════════════════════════╗");
        p.sendMessage(tierColor + "  ║  " + getTierEmoji(tier) + " " + name);
        p.sendMessage(ChatColor.GOLD + "  ╚═══════════════════════════════╝");
        p.sendMessage("");

        playTierSound(p, tier);
        spawnTierParticles(p, tier);

        if (type.equals("death")) {
            int loss = Math.abs(Integer.parseInt(data));
            plugin.getReputationManager().deductPoints(vkId, loss);
            totalRepLost.merge(vkId, loss, Integer::sum);
            p.sendMessage(ChatColor.RED + "💀 -" + loss + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + plugin.getReputationManager().getPoints(vkId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (jackpotPool * mult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            plugin.getReputationManager().addPoints(vkId, jackpot);
            totalRepWon.merge(vkId, jackpot, Integer::sum);
            jackpotPool = 5000;
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 +" + jackpot + " РЕП!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + plugin.getReputationManager().getPoints(vkId));
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + p.getName() + " СОРВАЛ ДЖЕКПОТ! +" + jackpot + " реп!");

        } else if (type.equals("token")) {
            int tok = Integer.parseInt(data);
            tokens.merge(vkId, tok, Integer::sum);
            p.sendMessage(ChatColor.GOLD + "🎟 +" + tok + " токенов!");

        } else if (type.equals("lucky")) {
            int num = 5 + ThreadLocalRandom.current().nextInt(45);
            luckyNumber.put(vkId, num);
            p.sendMessage(ChatColor.GREEN + "🍀 Шанс: " + num + "% на x1.5!");

        } else if (type.equals("rep")) {
            int bonus = (int) (Integer.parseInt(data) * mult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число! x1.5!");
            }
            plugin.getReputationManager().addPoints(vkId, bonus);
            totalRepWon.merge(vkId, bonus, Integer::sum);
            p.sendMessage(ChatColor.GREEN + "🎉 +" + bonus + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + plugin.getReputationManager().getPoints(vkId));

        } else if (type.equals("item")) {
            pendingItems.putIfAbsent(vkId, new ArrayList<>());
            pendingItems.get(vkId).add(data);
            p.sendMessage(ChatColor.GREEN + "📦 Предмет готов! Забери: /рулетка");

        } else {
            p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
            doubleOrNothing.put(vkId, 0.0);
            p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing? Нажми кнопку!");
        }

        int newStreak = winStreak.getOrDefault(vkId, 0);
        if (newStreak > 1) p.sendMessage(ChatColor.RED + "🔥 Стрик: x" + newStreak);
        if (isLucky && !type.equals("lucky")) p.sendMessage(ChatColor.GREEN + "🍀 Удача!");

        p.sendMessage("");
        p.sendMessage(ChatColor.GRAY + "Напиши " + ChatColor.GREEN + "/рулетка" + ChatColor.GRAY + " чтобы крутить снова!");
    }

    // ═══════════════════════════════════════════════════════════
    // IN-GAME DOUBLE OR NOTHING
    // ═══════════════════════════════════════════════════════════

    private void doubleOrNothingInGame(Player p) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        Double pending = doubleOrNothing.remove(vkId);
        if (pending == null) {
            p.sendMessage(ChatColor.RED + "Нет активного предложения!");
            return;
        }

        p.sendMessage(ChatColor.YELLOW + "⚡ DOUBLE OR NOTHING...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(600);
                plugin.getReputationManager().addPoints(vkId, bonus);
                totalRepWon.merge(vkId, bonus, Integer::sum);
                p.sendMessage(ChatColor.GREEN + "🎉 DOUBLE! +" + bonus + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(400);
                plugin.getReputationManager().deductPoints(vkId, loss);
                totalRepLost.merge(vkId, loss, Integer::sum);
                p.sendMessage(ChatColor.RED + "💀 NOTHING! -" + loss + " реп!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + plugin.getReputationManager().getPoints(vkId));
        }, 30L);
    }

    // ═══════════════════════════════════════════════════════════
    // IN-GAME УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════

    private void toggleAutoSpin(int vkId) {
        if (autoSpinEnabled.contains(vkId)) {
            autoSpinEnabled.remove(vkId);
        } else {
            autoSpinEnabled.add(vkId);
        }
    }

    private void setLuckyNumberInGame(Player p) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) return;
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        luckyNumber.put(vkId, num);
        p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число: " + num + "%");
        openInGameGUI(p);
    }

    public void claimPrizes(Player p) {
        int vkId = plugin.getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        List<String> items = pendingItems.remove(vkId);
        if (items == null || items.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Нет ожидающих предметов.");
            return;
        }

        int given = 0, lost = 0;
        for (String item : items) {
            String[] parts = item.split(";");
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                if (p.getInventory().addItem(new ItemStack(mat, amount)).isEmpty()) given++;
                else lost++;
            } catch (Exception e) { lost++; }
        }

        p.sendMessage(ChatColor.GREEN + "📦 Получено: " + given);
        if (lost > 0) p.sendMessage(ChatColor.RED + "⚠ Не удалось: " + lost + " (инвентарь полон)");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        openInGameGUI(p);
    }

    public String getFullStats(int vkId) {
        int spins = totalSpins.getOrDefault(vkId, 0);
        int wins = totalWins.getOrDefault(vkId, 0);
        int repWon = totalRepWon.getOrDefault(vkId, 0);
        int repLost = totalRepLost.getOrDefault(vkId, 0);
        int streak = winStreak.getOrDefault(vkId, 0);
        int tok = tokens.getOrDefault(vkId, 0);

        return ChatColor.GOLD + "═══ 📊 СТАТИСТИКА ═══\n\n" +
                ChatColor.WHITE + "🎰 Вращений: " + ChatColor.YELLOW + spins + "\n" +
                ChatColor.WHITE + "✅ Побед: " + ChatColor.GREEN + wins + "\n" +
                ChatColor.WHITE + "📈 Выиграно: " + ChatColor.GREEN + "+" + repWon + " реп\n" +
                ChatColor.WHITE + "📉 Проиграно: " + ChatColor.RED + "-" + repLost + " реп\n" +
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak + "\n" +
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tok + "\n" +
                ChatColor.WHITE + "📊 Винрейт: " + ChatColor.YELLOW + (spins > 0 ? (wins * 100 / spins) : 0) + "%\n" +
                ChatColor.WHITE + "🏆 Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп";
    }

    public void showLeaderboard(Player p) {
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(totalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder msg = new StringBuilder(ChatColor.GOLD + "═══ 🏆 ТОП ═══\n\n");
        int i = 1;
        for (Map.Entry<Integer, Integer> entry : sorted) {
            if (i > 10) break;
            String name = "ID" + entry.getKey();
            try {
                UUID uuid = plugin.getApi().getUuidByVkId(entry.getKey());
                if (uuid != null) {
                    String n = Bukkit.getOfflinePlayer(uuid).getName();
                    if (n != null) name = n;
                }
            } catch (Exception ignored) {}
            msg.append(ChatColor.YELLOW).append(i++).append(". ").append(name)
               .append(ChatColor.WHITE).append(" — ").append(ChatColor.GREEN).append("+").append(entry.getValue()).append(" реп\n");
        }
        if (sorted.isEmpty()) msg.append(ChatColor.GRAY).append("Пока нет данных.");
        p.sendMessage(msg.toString());
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

    public int getPendingCount(int vkId) {
        List<String> items = pendingItems.get(vkId);
        return items != null ? items.size() : 0;
    }

    public void earnTokens(int vkId, int amount) {
        tokens.merge(vkId, amount, Integer::sum);
    }

    public boolean hasAutoSpin(int vkId) {
        return autoSpinEnabled.contains(vkId);
    }

    // ═══════════════════════════════════════════════════════════
    // GUI УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════

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

    private void playTierSound(Player p, String tier) {
        switch (tier) {
            case "legendary":
            case "jackpot": p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f); break;
            case "rare": p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); break;
            case "death": p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f); break;
            default: p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f); break;
        }
    }

    private void spawnTierParticles(Player p, String tier) {
        switch (tier) {
            case "legendary":
            case "jackpot": p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 50); break;
            case "rare": p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 0.5, 0), 25); break;
            case "death": p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30); break;
        }
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack info(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String l : lore) if (!l.isEmpty()) loreList.add(l);
        meta.setLore(loreList);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack btn(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String l : lore) if (!l.isEmpty()) loreList.add(l);
        meta.setLore(loreList);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
