package ru.example.vkchatmarket.data;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.listeners.MarketGuiListener;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Рулетка в игре v2.0
 * 
 * Фичи:
 * 1. Выбор ставки кнопками
 * 2. Обычная крутка (без КД)
 * 3. Русская рулетка (x3)
 * 4. Double or Nothing
 * 5. Стрики (+10% за победу)
 * 6. Автоспин
 * 7. Счастливое число
 * 8. Токены
 * 9. Предметы в ожидающие
 * 10. Анимация
 * 11. Частицы и звуки
 * 12. Статистика
 */
public class MarketRoulette {
    private final VKChatMarketPlugin plugin;

    private final Map<String, Integer> currentBet = new ConcurrentHashMap<>();
    private final Map<String, Integer> winStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepLost = new ConcurrentHashMap<>();
    private final Map<String, Integer> luckyNumber = new ConcurrentHashMap<>();
    private final Map<String, Integer> spinTokens = new ConcurrentHashMap<>();
    private final Set<String> autoSpinEnabled = ConcurrentHashMap.newKeySet();
    private final Set<String> spinning = ConcurrentHashMap.newKeySet();
    private final Map<String, List<String>> pendingItems = new ConcurrentHashMap<>();
    private int jackpotPool = 5000;

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
        {"🧊 Алмазный блок", "item", "DIAMOND_BLOCK;1", "rare"},
        {"⚔ Алмазный меч", "item", "DIAMOND_SWORD;1", "uncommon"},
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

    public MarketRoulette(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══ GUI ═══

    public void openRouletteGUI(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int bet = currentBet.getOrDefault(p.getName(), 500);
        int streak = winStreak.getOrDefault(p.getName(), 0);
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        int spins = totalSpins.getOrDefault(p.getName(), 0);
        int wins = totalWins.getOrDefault(p.getName(), 0);
        int pending = getPendingCount(p.getName());

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "🎰 Рулетка");

        for (int i = 0; i < 54; i++) inv.setItem(i, glass(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(4, info(Material.BOOK, ChatColor.GOLD + "🎰 Рулетка",
                ChatColor.WHITE + "💰 Баланс: " + ChatColor.YELLOW + rep + " реп",
                ChatColor.WHITE + "🎯 Ставка: " + ChatColor.GREEN + bet + " реп",
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak,
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tokens,
                ChatColor.WHITE + "📊 Винрейт: " + (spins > 0 ? (wins * 100 / spins) : 0) + "%",
                ChatColor.WHITE + "🏆 Джекпот: " + jackpotPool + " реп",
                pending > 0 ? ChatColor.LIGHT_PURPLE + "📦 Призов: " + pending : ""));

        int[] bets = {100, 250, 500, 1000, 2500, 5000, 10000};
        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < bets.length; i++) {
            boolean selected = bets[i] == bet;
            inv.setItem(betSlots[i], glass(
                    selected ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                    (selected ? ChatColor.GREEN : ChatColor.YELLOW) + "" + bets[i] + " реп"));
        }

        inv.setItem(30, btn(Material.NETHER_STAR, ChatColor.GREEN + "🎰 КРУТИТЬ!", "Ставка: " + bet + " реп"));
        inv.setItem(31, btn(Material.BLAZE_POWDER, ChatColor.RED + "☠ РУССКАЯ", "x3 цена, x3 награда"));
        inv.setItem(32, btn(Material.TNT, ChatColor.YELLOW + "⚡ DOUBLE",
                hasDoubleOrNothing(p.getName()) ? ChatColor.GREEN + "Доступно!" : ChatColor.GRAY + "Нет"));

        inv.setItem(38, btn(Material.CLOCK, ChatColor.AQUA + "🔄 Авто-спин",
                autoSpinEnabled.contains(p.getName()) ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ"));
        inv.setItem(39, btn(Material.EMERALD, ChatColor.GREEN + "🍀 Удача",
                "Твоё: " + luckyNumber.getOrDefault(p.getName(), 0) + "%"));
        inv.setItem(40, btn(Material.PAPER, ChatColor.AQUA + "📊 Статистика", ""));

        inv.setItem(45, btn(Material.ARROW, ChatColor.WHITE + "🏠 Назад", ""));
        inv.setItem(47, btn(Material.ENDER_CHEST, ChatColor.LIGHT_PURPLE + "📦 Призы",
                pending > 0 ? ChatColor.GREEN + "Есть!" : ChatColor.GRAY + "Нет"));
        inv.setItem(49, btn(Material.CHEST, ChatColor.GOLD + "🎁 Бокс", "Боксов: 0"));
        inv.setItem(53, btn(Material.COMPASS, ChatColor.YELLOW + "🏆 Топ", ""));

        p.openInventory(inv);
    }

    // ═══ ОБРАБОТКА КЛИКОВ ═══

    public void handleClick(Player p, int slot) {
        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        int[] bets = {100, 250, 500, 1000, 2500, 5000, 10000};
        for (int i = 0; i < betSlots.length; i++) {
            if (slot == betSlots[i]) {
                currentBet.put(p.getName(), bets[i]);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openRouletteGUI(p);
                return;
            }
        }

        switch (slot) {
            case 30: spin(p, "normal"); break;
            case 31: spin(p, "russian"); break;
            case 32: doubleOrNothing(p); break;
            case 38: toggleAutoSpin(p); openRouletteGUI(p); break;
            case 39: setLuckyNumber(p); break;
            case 40: p.sendMessage(getFullStats(p)); break;
            case 45: MarketGuiListener.openCategoryMenu(plugin, p); break;
            case 47: claimVKPrizes(p); break;
            case 53: showLeaderboard(p); break;
        }
    }

    // ═══ КРУТКА (БЕЗ КД) ═══

    public void spin(Player p, String mode) {
        if (spinning.contains(p.getName())) {
            p.sendMessage(ChatColor.RED + "⏳ Рулетка уже крутится!");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        int bet = currentBet.getOrDefault(p.getName(), 500);
        if (mode.equals("russian")) bet *= 3;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < bet) {
            p.sendMessage(ChatColor.RED + "Нужно " + bet + " реп. (у тебя " + rep + ")");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, bet);
        totalSpins.merge(p.getName(), 1, Integer::sum);
        spinning.add(p.getName());
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
            spinning.remove(p.getName());
            if (p.isOnline()) {
                processResult(p, mode, finalBet);
                // Автоспин
                if (autoSpinEnabled.contains(p.getName()) && p.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && autoSpinEnabled.contains(p.getName())) {
                            spin(p, mode);
                        }
                    }, 20L);
                }
            }
        }, 35L);
    }

    private void processResult(Player p, String mode, int bet) {
        String[][] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : PRIZES;
        String[] prize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
        String name = prize[0];
        String type = prize[1];
        String data = prize[2];
        String tier = prize[3];

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        int streak = winStreak.getOrDefault(p.getName(), 0);
        double mult = 1.0 + (streak * 0.1);
        int lucky = luckyNumber.getOrDefault(p.getName(), 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) {
            winStreak.merge(p.getName(), 1, Integer::sum);
            totalWins.merge(p.getName(), 1, Integer::sum);
        } else {
            winStreak.put(p.getName(), 0);
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
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
            totalRepLost.merge(p.getName(), loss, Integer::sum);
            p.sendMessage(ChatColor.RED + "💀 -" + loss + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (jackpotPool * mult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            totalRepWon.merge(p.getName(), jackpot, Integer::sum);
            jackpotPool = 5000;
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 +" + jackpot + " РЕП!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + p.getName() + " СОРВАЛ ДЖЕКПОТ! +" + jackpot + " реп!");

        } else if (type.equals("token")) {
            int tok = Integer.parseInt(data);
            spinTokens.merge(p.getName(), tok, Integer::sum);
            p.sendMessage(ChatColor.GOLD + "🎟 +" + tok + " токенов!");

        } else if (type.equals("lucky")) {
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
            luckyNumber.put(p.getName(), num);
            p.sendMessage(ChatColor.GREEN + "🍀 Шанс: " + num + "% на x1.5!");

        } else if (type.equals("rep")) {
            int bonus = (int) (Integer.parseInt(data) * mult);
            if (isLucky) {
                bonus = (int) (bonus * 1.5);
                p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число! x1.5!");
            }
            VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
            totalRepWon.merge(p.getName(), bonus, Integer::sum);
            p.sendMessage(ChatColor.GREEN + "🎉 +" + bonus + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));

        } else if (type.equals("item")) {
            String[] parts = data.split(";");
            pendingItems.putIfAbsent(p.getName(), new ArrayList<>());
            pendingItems.get(p.getName()).add(data);
            p.sendMessage(ChatColor.GREEN + "📦 Предмет готов! Забери: /рулетка");

        } else {
            p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing? Нажми кнопку!");
            }
        }

        int newStreak = winStreak.getOrDefault(p.getName(), 0);
        if (newStreak > 1) p.sendMessage(ChatColor.RED + "🔥 Стрик: x" + newStreak);
        if (isLucky && !type.equals("lucky")) p.sendMessage(ChatColor.GREEN + "🍀 Удача!");

        p.sendMessage("");
        p.sendMessage(ChatColor.GRAY + "Напиши " + ChatColor.GREEN + "/рулетка" + ChatColor.GRAY + " чтобы крутить снова!");
    }

    // ═══ DOUBLE OR NOTHING ═══

    public void doubleOrNothing(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        p.sendMessage(ChatColor.YELLOW + "⚡ DOUBLE OR NOTHING...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(800);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
                totalRepWon.merge(p.getName(), bonus, Integer::sum);
                p.sendMessage(ChatColor.GREEN + "🎉 DOUBLE! +" + bonus + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(400);
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
                totalRepLost.merge(p.getName(), loss, Integer::sum);
                p.sendMessage(ChatColor.RED + "💀 NOTHING! -" + loss + " реп!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));
        }, 30L);
    }

    // ═══ АВТОСПИН ═══

    private void toggleAutoSpin(Player p) {
        if (autoSpinEnabled.contains(p.getName())) {
            autoSpinEnabled.remove(p.getName());
            p.sendMessage(ChatColor.RED + "🔄 Авто-спин выключен");
        } else {
            autoSpinEnabled.add(p.getName());
            p.sendMessage(ChatColor.GREEN + "🔄 Авто-спин включён");
        }
    }

    // ═══ СЧАСТЛИВОЕ ЧИСЛО ═══

    private void setLuckyNumber(Player p) {
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        luckyNumber.put(p.getName(), num);
        p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число: " + num + "%");
        openRouletteGUI(p);
    }

    // ═══ ПРЕДМЕТЫ ═══

    public void claimVKPrizes(Player p) {
        List<String> items = pendingItems.remove(p.getName());
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
        openRouletteGUI(p);
    }

    public boolean hasPendingItems(String playerName) {
        List<String> items = pendingItems.get(playerName);
        return items != null && !items.isEmpty();
    }

    public int getPendingCount(String playerName) {
        List<String> items = pendingItems.get(playerName);
        return items != null ? items.size() : 0;
    }

    public void earnTokens(String playerName, int amount) {
        spinTokens.merge(playerName, amount, Integer::sum);
    }

    public boolean hasDoubleOrNothing(String playerName) {
        return true; // Всегда доступно для простоты
    }

    // ═══ СТАТИСТИКА ═══

    public String getFullStats(Player p) {
        String name = p.getName();
        int spins = totalSpins.getOrDefault(name, 0);
        int wins = totalWins.getOrDefault(name, 0);
        int repWon = totalRepWon.getOrDefault(name, 0);
        int repLost = totalRepLost.getOrDefault(name, 0);
        int streak = winStreak.getOrDefault(name, 0);
        int tokens = spinTokens.getOrDefault(name, 0);

        return ChatColor.GOLD + "═══ 📊 СТАТИСТИКА ═══\n\n" +
                ChatColor.WHITE + "🎰 Вращений: " + ChatColor.YELLOW + spins + "\n" +
                ChatColor.WHITE + "✅ Побед: " + ChatColor.GREEN + wins + "\n" +
                ChatColor.WHITE + "📈 Выиграно: " + ChatColor.GREEN + "+" + repWon + " реп\n" +
                ChatColor.WHITE + "📉 Проиграно: " + ChatColor.RED + "-" + repLost + " реп\n" +
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak + "\n" +
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tokens + "\n" +
                ChatColor.WHITE + "📊 Винрейт: " + ChatColor.YELLOW + (spins > 0 ? (wins * 100 / spins) : 0) + "%\n" +
                ChatColor.WHITE + "🏆 Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп";
    }

    public void showLeaderboard(Player p) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder msg = new StringBuilder(ChatColor.GOLD + "═══ 🏆 ТОП ═══\n\n");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            msg.append(ChatColor.YELLOW).append(i + 1).append(". ").append(entry.getKey())
               .append(ChatColor.WHITE).append(" — ").append(ChatColor.GREEN).append("+").append(entry.getValue()).append(" реп\n");
        }
        if (sorted.isEmpty()) msg.append(ChatColor.GRAY).append("Пока нет данных.");
        p.sendMessage(msg.toString());
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
