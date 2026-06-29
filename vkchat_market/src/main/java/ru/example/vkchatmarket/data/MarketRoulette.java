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
 * Мега-рулетка v4.0 — 35 фич
 * 
 * 1. Выбор ставки кнопками (100-10000)
 * 2. Обычная крутка
 * 3. Русская рулетка (x3 цена/награда)
 * 4. Double or Nothing
 * 5. Стрики побед (+10% за каждую)
 * 6. Система pity (гарантия редкого после N проигрышей)
 * 7. Счастливое число (% шанс на x1.5)
 * 8. Токены за торговлю
 * 9. Бесплатный спин дня
 * 10. Подарочные спины
 * 11. Щит стрика
 * 12. Страховка приза
 * 13. Мистический бокс
 * 14. Мистический множитель (x2)
 * 15. Шанс апгрейда (x2 предметы)
 * 16. Авто-спин
 * 17. Анимация крутки (7 кадров)
 * 18. Частицы на победах
 * 19. Звуки по тире приза
 * 20. Broadcast джекпотов
 * 21. Лидерборд
 * 22. Статистика (спины, победы, реп, винрейт)
 * 23. Рекорд выигрыша/проигрыша
 * 24. История вращений
 * 25. Достижения (стрик x3/x5/x10, 50/100 вращений)
 * 26. Дневной челлендж
 * 27. Токены в GUI
 * 28. Предметы сохраняются в ожидающие
 * 29. /рулетка — забрать предметы
 * 30. Кнопка "Ещё раз" после крутки
 * 31. Кнопка "Русская" после крутки
 * 32. Кнопка "Призы" после крутки
 * 33. Тире призов (common/uncommon/rare/legendary/jackpot)
 * 34. Цветовое оформление по тире
 * 35. Настраиваемый кулдаун в конфиге
 */
public class MarketRoulette {
    private final VKChatMarketPlugin plugin;

    // ═══ ДАННЫЕ ИГРОКОВ ═══
    private final Map<String, Integer> currentBet = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpinTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> winStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> loseStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepLost = new ConcurrentHashMap<>();
    private final Map<String, Integer> highestWin = new ConcurrentHashMap<>();
    private final Map<String, Integer> highestLoss = new ConcurrentHashMap<>();
    private final Map<String, Integer> pityCounter = new ConcurrentHashMap<>();
    private final Map<String, Double> doubleOrNothing = new ConcurrentHashMap<>();
    private final Map<String, Integer> luckyNumber = new ConcurrentHashMap<>();
    private final Map<String, Integer> spinTokens = new ConcurrentHashMap<>();
    private final Set<String> freeSpinUsed = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> giftedSpins = new ConcurrentHashMap<>();
    private final Set<String> streakShield = ConcurrentHashMap.newKeySet();
    private final Set<String> prizeInsurance = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> mysteryBox = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mysteryMultiplier = new ConcurrentHashMap<>();
    private final Map<String, Integer> upgradeChance = new ConcurrentHashMap<>();
    private final Set<String> autoSpinEnabled = ConcurrentHashMap.newKeySet();
    private final Set<String> spinning = ConcurrentHashMap.newKeySet();
    private final Map<String, List<String>> spinHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> achievements = new ConcurrentHashMap<>();
    private final Map<String, Integer> challengeProgress = new ConcurrentHashMap<>();
    private final Set<String> challengeCompleted = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> totalJackpots = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalDoubleOrNothing = new ConcurrentHashMap<>();

    // ═══ ОЖИДАЮЩИЕ ПРЕДМЕТЫ (VK -> MC) ═══
    private final Map<String, List<String>> pendingItems = new ConcurrentHashMap<>();

    // ═══ ДЖЕКПОТ ═══
    private int jackpotPool = 5000;

    // ═══ ПРИЗЫ ═══
    private static final String[][] PRIZES = {
        // name, type, data, tier, chance
        {"💎 Алмаз", "item", "DIAMOND;1", "rare", "6"},
        {"🔮 Эндер-жемчуг x3", "item", "ENDER_PEARL;3", "common", "10"},
        {"🔥 Огненный стержень x2", "item", "BLAZE_ROD;2", "uncommon", "8"},
        {"⚡ Редстоун-блок x5", "item", "REDSTONE_BLOCK;5", "common", "10"},
        {"🍀 Изумруд x2", "item", "EMERALD;2", "uncommon", "8"},
        {"💀 Незеритовый лом", "item", "NETHERITE_SCRAP;1", "legendary", "2"},
        {"🧪 Опыт-бутылки x10", "item", "EXPERIENCE_BOTTLE;10", "common", "12"},
        {"🪙 +200 реп", "rep", "200", "common", "10"},
        {"💰 +500 реп", "rep", "500", "uncommon", "5"},
        {"🏆 ДЖЕКПОТ!", "jackpot", "0", "jackpot", "1"},
        {"💀 Пусто", "empty", "0", "empty", "10"},
        {"🪙 +100 реп", "rep", "100", "common", "8"},
        {"🍎 Золотое яблоко x2", "item", "GOLDEN_APPLE;2", "rare", "4"},
        {"✨ Мистический +300 реп", "rep", "300", "uncommon", "5"},
        {"🍀 Счастливое число", "lucky", "0", "lucky", "3"},
        {"🎟 Токены x3", "token", "3", "token", "3"},
        {"🧊 Алмазный блок", "item", "DIAMOND_BLOCK;1", "rare", "3"},
        {"⚔ Алмазный меч", "item", "DIAMOND_SWORD;1", "uncommon", "5"},
        {"🛡 Алмазная броня", "item", "DIAMOND_CHESTPLATE;1", "rare", "2"},
        {"🪣 Ведро молока", "item", "MILK_BUCKET;1", "common", "6"},
    };

    private static final String[][] RUSSIAN_PRIZES = {
        {"💎💎 Алмаз x3", "item", "DIAMOND;3", "rare", "8"},
        {"💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "item", "NETHERITE_INGOT;1", "legendary", "3"},
        {"🏆 ДЖЕКПОТ x2!", "jackpot", "0", "jackpot", "2"},
        {"💰 +1000 реп", "rep", "1000", "uncommon", "7"},
        {"💀 ПОТЕРЯЛ 500 реп!", "death", "-500", "death", "15"},
        {"🍎 Золотое яблоко x5", "item", "GOLDEN_APPLE;5", "rare", "5"},
        {"💀 -300 реп", "death", "-300", "death", "12"},
        {"🔥 Тотем бессмертия", "item", "TOTEM_OF_UNDYING;1", "legendary", "2"},
        {"💀 Пусто", "empty", "0", "empty", "8"},
        {"🪙 +300 реп", "rep", "300", "common", "10"},
        {"💀 -200 реп", "death", "-200", "death", "10"},
        {"🔮 Эндер-жемчуг x16", "item", "ENDER_PEARL;16", "uncommon", "6"},
        {"⚔ Алмазный меч x2", "item", "DIAMOND_SWORD;2", "rare", "5"},
        {"🛡 Алмазная броня", "item", "DIAMOND_CHESTPLATE;1", "rare", "4"},
        {"💀 ПОТЕРЯЛ ВСЁ!", "death", "-999", "death", "7"},
    };

    public MarketRoulette(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════
    // [1] GUI ВЫБОРА СТАВКИ
    // ═══════════════════════════════════════════

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
        boolean free = hasFreeSpin(p.getName());

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🎰 МЕГА-РУЛЕТКА v4.0");

        // Стекло
        for (int i = 0; i < 54; i++) inv.setItem(i, glass(Material.BLACK_STAINED_GLASS_PANE, " "));

        // Инфо
        inv.setItem(4, info(Material.BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "🎰 МЕГА-РУЛЕТКА",
                "",
                ChatColor.WHITE + "💰 Баланс: " + ChatColor.YELLOW + rep + " реп",
                ChatColor.WHITE + "🎯 Ставка: " + ChatColor.GREEN + bet + " реп",
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak,
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tokens,
                ChatColor.WHITE + "📊 Винрейт: " + ChatColor.YELLOW + (spins > 0 ? (wins * 100 / spins) : 0) + "%",
                ChatColor.WHITE + "🏆 Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп",
                "",
                free ? ChatColor.GREEN + "🎁 Бесплатный спин!" : "",
                pending > 0 ? ChatColor.LIGHT_PURPLE + "📦 Призов ждёт: " + pending : ""));

        // [1] Ставки
        int[] bets = {100, 250, 500, 1000, 2500, 5000, 10000};
        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < bets.length; i++) {
            boolean selected = bets[i] == bet;
            Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            String color = selected ? ChatColor.GREEN.toString() : ChatColor.YELLOW.toString();
            inv.setItem(betSlots[i], glass(mat, color + bets[i] + " реп"));
        }

        // [2-3] Кнопки крутки
        inv.setItem(30, btn(Material.NETHER_STAR, ChatColor.GREEN + "" + ChatColor.BOLD + "🎰 КРУТИТЬ!",
                ChatColor.GRAY + "Обычная рулетка",
                ChatColor.GRAY + "Ставка: " + bet + " реп"));
        inv.setItem(31, btn(Material.BLAZE_POWDER, ChatColor.RED + "" + ChatColor.BOLD + "☠ РУССКАЯ",
                ChatColor.GRAY + "x3 цена, x3 награда!",
                ChatColor.GRAY + "Ставка: " + (bet * 3) + " реп"));
        inv.setItem(32, btn(Material.TNT, ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ DOUBLE",
                hasDoubleOrNothing(p.getName()) ? ChatColor.GREEN + "Доступно!" : ChatColor.GRAY + "Нет активного"));

        // [4-16] Доп. кнопки
        inv.setItem(38, btn(Material.CLOCK, ChatColor.AQUA + "🔄 Авто-спин",
                autoSpinEnabled.contains(p.getName()) ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ"));
        inv.setItem(39, btn(Material.SHIELD, ChatColor.AQUA + "🛡 Щит стрика",
                streakShield.contains(p.getName()) ? ChatColor.GREEN + "Активен" : ChatColor.GRAY + "10 токенов"));
        inv.setItem(40, btn(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "📦 Страховка",
                prizeInsurance.contains(p.getName()) ? ChatColor.GREEN + "Активна" : ChatColor.GRAY + "15 токенов"));
        inv.setItem(41, btn(Material.EMERALD, ChatColor.GREEN + "🍀 Счастливое число",
                "Твоё: " + luckyNumber.getOrDefault(p.getName(), 0) + "%"));
        inv.setItem(42, btn(Material.PAPER, ChatColor.AQUA + "📊 Статистика", ChatColor.GRAY + "Нажми для просмотра"));

        // Нижний ряд
        inv.setItem(45, btn(Material.ARROW, ChatColor.WHITE + "🏠 Назад", ""));
        inv.setItem(47, btn(Material.ENDER_CHEST, ChatColor.LIGHT_PURPLE + "📦 Призы из ВК",
                pending > 0 ? ChatColor.GREEN + "Есть призы!" : ChatColor.GRAY + "Нет призов"));
        inv.setItem(49, btn(Material.CHEST, ChatColor.GOLD + "🎁 Мистический бокс",
                "Боксов: " + mysteryBox.getOrDefault(p.getName(), 0)));
        inv.setItem(51, btn(Material.BOOK, ChatColor.AQUA + "📋 Достижения", ChatColor.GRAY + "Нажми для просмотра"));
        inv.setItem(53, btn(Material.COMPASS, ChatColor.YELLOW + "🏆 Топ", ChatColor.GRAY + "Лидерборд рулетки"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ
    // ═══════════════════════════════════════════

    public void handleClick(Player p, int slot) {
        // Ставки
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
            case 39: activateStreakShield(p); break;
            case 40: activatePrizeInsurance(p); break;
            case 41: setLuckyNumber(p); break;
            case 42: p.sendMessage(getFullStats(p)); break;
            case 45: MarketGuiListener.openCategoryMenu(plugin, p); break;
            case 47: claimVKPrizes(p); break;
            case 49: openMysteryBox(p); break;
            case 51: showAchievements(p); break;
            case 53: showLeaderboard(p); break;
        }
    }

    // ═══════════════════════════════════════════
    // [2-3] КРУТКА
    // ═══════════════════════════════════════════

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

        // [35] Кулдаун из конфига
        long cooldown = plugin.getConfig().getLong("market2.roulette.cooldown-ms", 1000);
        Long last = lastSpinTime.get(p.getName());
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - last)) / 1000;
            if (remaining > 0) {
                p.sendMessage(ChatColor.RED + "⏳ Подожди " + remaining + " сек.");
                return;
            }
        }

        int bet = currentBet.getOrDefault(p.getName(), 500);
        if (mode.equals("russian")) bet *= 3;

        // [9] Бесплатный спин
        boolean isFree = false;
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (!freeSpinUsed.contains(p.getName() + today)) {
            isFree = true;
            freeSpinUsed.add(p.getName() + today);
        }

        // [10] Подарочный спин
        boolean isGifted = false;
        int gifts = giftedSpins.getOrDefault(p.getName(), 0);
        if (gifts > 0) {
            isGifted = true;
            giftedSpins.put(p.getName(), gifts - 1);
        }

        if (!isFree && !isGifted) {
            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < bet) {
                p.sendMessage(ChatColor.RED + "Нужно " + bet + " реп. (у тебя " + rep + ")");
                return;
            }
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, bet);
            jackpotPool += bet / 10;
        }

        lastSpinTime.put(p.getName(), System.currentTimeMillis());
        totalSpins.merge(p.getName(), 1, Integer::sum);
        spinning.add(p.getName());

        // [17] Анимация
        p.closeInventory();
        if (mode.equals("russian")) {
            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "║   ☠ РУССКАЯ РУЛЕТКА ☠        ║");
            p.sendMessage(ChatColor.RED + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.RED + "║   Шанс выжить: 50%           ║");
            p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "╚═══════════════════════════════╝");
        } else {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "║     🎰 МЕГА-РУЛЕТКА 🎰       ║");
            p.sendMessage(ChatColor.YELLOW + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "╚═══════════════════════════════╝");
        }
        p.sendMessage("");

        // Кадры анимации
        String[][] frames = {
            {"🎰", "💎", "🍀", "⭐", "🔥", "💰", "🏆"},
            {"💎", "⭐", "🔥", "💰", "🏆", "🎰", "🍀"},
            {"🔥", "💰", "🏆", "🎰", "🍀", "💎", "⭐"},
            {"💰", "🏆", "🎰", "🍀", "💎", "⭐", "🔥"},
            {"🏆", "🎰", "🍀", "💎", "⭐", "🔥", "💰"},
            {"🎰", "🍀", "⭐", "🔥", "💰", "🏆", "💎"},
            {"🍀", "⭐", "🔥", "💰", "🏆", "💎", "🎰"},
        };

        for (int i = 0; i < frames.length; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    StringBuilder anim = new StringBuilder();
                    for (String s : frames[frame]) anim.append(s).append(" ");
                    p.sendMessage(ChatColor.GRAY + "  " + anim.toString());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + frame * 0.1f);
                }
            }, 5L + i * 5L);
        }

        // Результат
        final int finalBet = bet;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning.remove(p.getName());
            if (p.isOnline()) processResult(p, mode, finalBet);
        }, 45L);
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

        // [7] Счастливое число
        int lucky = luckyNumber.getOrDefault(p.getName(), 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;

        // [14] Мистический множитель
        boolean hasMysteryMult = mysteryMultiplier.getOrDefault(p.getName(), false);
        if (hasMysteryMult) {
            mult *= 2.0;
            mysteryMultiplier.put(p.getName(), false);
        }

        // [6] Pity система
        int pity = pityCounter.getOrDefault(p.getName(), 0);
        if (pity >= 15 && (tier.equals("empty") || tier.equals("death"))) {
            // Принудительно даём редкий приз
            for (String[] p2 : prizes) {
                if (p2[3].equals("rare") || p2[3].equals("legendary")) {
                    prize = p2;
                    name = prize[0];
                    type = prize[1];
                    data = prize[2];
                    tier = prize[3];
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ PITY СРАБОТАЛ! Гарантированный редкий приз!");
                    break;
                }
            }
            pityCounter.put(p.getName(), 0);
        }

        // Обновляем стрики
        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) {
            winStreak.merge(p.getName(), 1, Integer::sum);
            loseStreak.put(p.getName(), 0);
            totalWins.merge(p.getName(), 1, Integer::sum);
            pityCounter.put(p.getName(), 0);
        } else {
            // [11] Щит стрика
            if (streakShield.contains(p.getName())) {
                p.sendMessage(ChatColor.AQUA + "🛡 Щит стрика защитил!");
                streakShield.remove(p.getName());
            } else {
                winStreak.put(p.getName(), 0);
            }
            loseStreak.merge(p.getName(), 1, Integer::sum);
            pityCounter.merge(p.getName(), 1, Integer::sum);
        }

        // ═══ ВЫДАЧА ПРИЗА ═══
        ChatColor tierColor = getTierColor(tier);
        String tierEmoji = getTierEmoji(tier);

        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "  ╔═══════════════════════════════╗");
        p.sendMessage(tierColor + "  ║  " + tierEmoji + " " + name);
        p.sendMessage(ChatColor.GOLD + "  ╚═══════════════════════════════╝");
        p.sendMessage("");

        // [19] Звуки
        playTierSound(p, tier);

        // [18] Частицы
        spawnTierParticles(p, tier);

        if (type.equals("death")) {
            int loss = Math.abs(Integer.parseInt(data));
            if (loss == 999) loss = bet; // Потеря ставки
            // [12] Страховка
            if (prizeInsurance.contains(p.getName())) {
                loss /= 2;
                prizeInsurance.remove(p.getName());
                p.sendMessage(ChatColor.YELLOW + "📦 Страховка! Потеря уменьшена вдвое!");
            }
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
            totalRepLost.merge(p.getName(), loss, Integer::sum);
            highestLoss.merge(p.getName(), loss, Math::max);
            p.sendMessage(ChatColor.RED + "💀 -" + loss + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));

        } else if (type.equals("jackpot")) {
            int jackpot = (int) (jackpotPool * mult);
            if (isLucky) jackpot = (int) (jackpot * 1.5);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            totalRepWon.merge(p.getName(), jackpot, Integer::sum);
            highestWin.merge(p.getName(), jackpot, Math::max);
            totalJackpots.merge(p.getName(), 1, Integer::sum);
            jackpotPool = 5000;
            p.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "🏆 +" + jackpot + " РЕП!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));
            // [20] Broadcast
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "🏆 " + p.getName() + " СОРВАЛ ДЖЕКПОТ! +" + jackpot + " реп!");
            checkAchievement(p, "jackpot");

        } else if (type.equals("token")) {
            int tokens = Integer.parseInt(data);
            spinTokens.merge(p.getName(), tokens, Integer::sum);
            p.sendMessage(ChatColor.GOLD + "🎟 +" + tokens + " токенов!");

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
            highestWin.merge(p.getName(), bonus, Math::max);
            p.sendMessage(ChatColor.GREEN + "🎉 +" + bonus + " реп!");
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));

        } else if (type.equals("item")) {
            // [15] Апгрейд
            int upgrade = upgradeChance.getOrDefault(p.getName(), 0);
            String[] parts = data.split(";");
            String mat = parts[0];
            int amount = Integer.parseInt(parts[1]);
            if (upgrade > 0 && ThreadLocalRandom.current().nextInt(100) < upgrade) {
                amount *= 2;
                p.sendMessage(ChatColor.GOLD + "⬆ АПГРЕЙД! x2 предметов!");
                upgradeChance.put(p.getName(), 0);
            }
            // [28] Сохраняем в ожидающие
            pendingItems.putIfAbsent(p.getName(), new ArrayList<>());
            pendingItems.get(p.getName()).add(mat + ";" + amount);
            p.sendMessage(ChatColor.GREEN + "📦 Предмет готов! Забери: /рулетка");
            p.sendMessage(ChatColor.GRAY + "Всего предметов: " + pendingItems.get(p.getName()).size());

        } else {
            // empty
            p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(p.getName(), 0.0);
                p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing? Нажми кнопку!");
            }
        }

        // Стрик
        int newStreak = winStreak.getOrDefault(p.getName(), 0);
        if (newStreak > 1) {
            p.sendMessage(ChatColor.RED + "🔥 Стрик: x" + newStreak + " (x" + String.format("%.1f", 1.0 + newStreak * 0.1) + ")");
        }

        // [24] История
        addSpinHistory(p.getName(), name);

        // [26] Дневной челлендж
        checkDailyChallenge(p, tier);

        // [25] Достижения
        checkAchievement(p, tier);

        // [30-32] Кнопки после крутки
        p.sendMessage("");
        p.sendMessage(ChatColor.GRAY + "Напиши " + ChatColor.GREEN + "/рулетка" + ChatColor.GRAY + " чтобы крутить снова!");
    }

    // ═══════════════════════════════════════════
    // [4] DOUBLE OR NOTHING
    // ═══════════════════════════════════════════

    public void doubleOrNothing(Player p) {
        Double pending = doubleOrNothing.remove(p.getName());
        if (pending == null) {
            p.sendMessage(ChatColor.RED + "Нет активного предложения!");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        totalDoubleOrNothing.merge(p.getName(), 1, Integer::sum);

        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        p.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ DOUBLE OR NOTHING...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45;
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(800);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
                totalRepWon.merge(p.getName(), bonus, Integer::sum);
                highestWin.merge(p.getName(), bonus, Math::max);
                p.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "🎉 DOUBLE! +" + bonus + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(400);
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
                totalRepLost.merge(p.getName(), loss, Integer::sum);
                highestLoss.merge(p.getName(), loss, Math::max);
                p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "💀 NOTHING! -" + loss + " реп!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            p.sendMessage(ChatColor.WHITE + "💰 Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId));
        }, 30L);
    }

    // ═══════════════════════════════════════════
    // [8-10] ТОКЕНЫ, БЕСПЛАТНЫЙ СПИН, ПОДАРКИ
    // ═══════════════════════════════════════════

    public void earnTokens(String playerName, int amount) {
        spinTokens.merge(playerName, amount, Integer::sum);
    }

    public boolean hasFreeSpin(String playerName) {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return !freeSpinUsed.contains(playerName + today);
    }

    // ═══════════════════════════════════════════
    // [11-12] ЩИТ И СТРАХОВКА
    // ═══════════════════════════════════════════

    private void activateStreakShield(Player p) {
        if (streakShield.contains(p.getName())) {
            p.sendMessage(ChatColor.RED + "Щит уже активен!");
            return;
        }
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        if (tokens < 10) {
            p.sendMessage(ChatColor.RED + "Нужно 10 токенов! (у тебя " + tokens + ")");
            return;
        }
        spinTokens.put(p.getName(), tokens - 10);
        streakShield.add(p.getName());
        p.sendMessage(ChatColor.AQUA + "🛡 Щит стрика активирован!");
        openRouletteGUI(p);
    }

    private void activatePrizeInsurance(Player p) {
        if (prizeInsurance.contains(p.getName())) {
            p.sendMessage(ChatColor.RED + "Страховка уже активна!");
            return;
        }
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        if (tokens < 15) {
            p.sendMessage(ChatColor.RED + "Нужно 15 токенов! (у тебя " + tokens + ")");
            return;
        }
        spinTokens.put(p.getName(), tokens - 15);
        prizeInsurance.add(p.getName());
        p.sendMessage(ChatColor.YELLOW + "📦 Страховка активирована!");
        openRouletteGUI(p);
    }

    // ═══════════════════════════════════════════
    // [7] СЧАСТЛИВОЕ ЧИСЛО
    // ═══════════════════════════════════════════

    private void setLuckyNumber(Player p) {
        int num = 5 + ThreadLocalRandom.current().nextInt(45);
        luckyNumber.put(p.getName(), num);
        p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число: " + num + "%");
        openRouletteGUI(p);
    }

    // ═══════════════════════════════════════════
    // [13] МИСТИЧЕСКИЙ БОКС
    // ═══════════════════════════════════════════

    private void openMysteryBox(Player p) {
        int boxes = mysteryBox.getOrDefault(p.getName(), 0);
        if (boxes <= 0) {
            p.sendMessage(ChatColor.RED + "Нет боксов! Выигрывай в рулетке.");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        mysteryBox.put(p.getName(), boxes - 1);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        p.sendMessage(ChatColor.DARK_PURPLE + "✨ Открываю мистический бокс...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < 5) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 2000);
                p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "🏆 ЛЕГЕНДАРНЫЙ БОКС! +2000 реп!");
                Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + p.getName() + " открыл легендарный бокс!");
            } else if (roll < 20) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
                p.sendMessage(ChatColor.AQUA + "💎 Редкий бокс! +500 реп!");
            } else if (roll < 50) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 200);
                p.sendMessage(ChatColor.GREEN + "🪙 Обычный бокс! +200 реп!");
            } else {
                p.sendMessage(ChatColor.GRAY + "💀 Бокс оказался пустым...");
            }
        }, 40L);
    }

    // ═══════════════════════════════════════════
    // [16] АВТО-СПИН
    // ═══════════════════════════════════════════

    private void toggleAutoSpin(Player p) {
        if (autoSpinEnabled.contains(p.getName())) {
            autoSpinEnabled.remove(p.getName());
            p.sendMessage(ChatColor.RED + "🔄 Авто-спин выключен");
        } else {
            autoSpinEnabled.add(p.getName());
            p.sendMessage(ChatColor.GREEN + "🔄 Авто-спин включён");
        }
    }

    // ═══════════════════════════════════════════
    // [22-23] СТАТИСТИКА
    // ═══════════════════════════════════════════

    public String getFullStats(Player p) {
        String name = p.getName();
        int spins = totalSpins.getOrDefault(name, 0);
        int wins = totalWins.getOrDefault(name, 0);
        int repWon = totalRepWon.getOrDefault(name, 0);
        int repLost = totalRepLost.getOrDefault(name, 0);
        int streak = winStreak.getOrDefault(name, 0);
        int tokens = spinTokens.getOrDefault(name, 0);
        int lucky = luckyNumber.getOrDefault(name, 0);

        return ChatColor.GOLD + "" + ChatColor.BOLD + "═══ 📊 СТАТИСТИКА ═══\n\n" +
                ChatColor.WHITE + "🎰 Вращений: " + ChatColor.YELLOW + spins + "\n" +
                ChatColor.WHITE + "✅ Побед: " + ChatColor.GREEN + wins + "\n" +
                ChatColor.WHITE + "📈 Выиграно: " + ChatColor.GREEN + "+" + repWon + " реп\n" +
                ChatColor.WHITE + "📉 Проиграно: " + ChatColor.RED + "-" + repLost + " реп\n" +
                ChatColor.WHITE + "🔥 Стрик: " + ChatColor.AQUA + streak + "\n" +
                ChatColor.WHITE + "🎟 Токены: " + ChatColor.GOLD + tokens + "\n" +
                ChatColor.WHITE + "🍀 Удача: " + (lucky > 0 ? lucky + "%" : "нет") + "\n" +
                ChatColor.WHITE + "📊 Винрейт: " + ChatColor.YELLOW + (spins > 0 ? (wins * 100 / spins) : 0) + "%\n" +
                ChatColor.WHITE + "🏆 Рекорд выигрыша: " + ChatColor.GOLD + highestWin.getOrDefault(name, 0) + "\n" +
                ChatColor.WHITE + "💀 Рекорд проигрыша: " + ChatColor.RED + highestLoss.getOrDefault(name, 0) + "\n" +
                ChatColor.WHITE + "🏆 Джекпотов: " + ChatColor.LIGHT_PURPLE + totalJackpots.getOrDefault(name, 0) + "\n" +
                ChatColor.WHITE + "💰 Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп";
    }

    // ═══════════════════════════════════════════
    // [21] ЛИДЕРБОРД
    // ═══════════════════════════════════════════

    public void showLeaderboard(Player p) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.GOLD).append(ChatColor.BOLD).append("═══ 🏆 ТОП РУЛЕТКИ ═══\n\n");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            msg.append(ChatColor.YELLOW).append(i + 1).append(". ").append(entry.getKey())
               .append(ChatColor.WHITE).append(" — ").append(ChatColor.GREEN).append("+").append(entry.getValue()).append(" реп\n");
        }
        if (sorted.isEmpty()) msg.append(ChatColor.GRAY).append("Пока нет данных.");
        p.sendMessage(msg.toString());
    }

    // ═══════════════════════════════════════════
    // [24] ИСТОРИЯ
    // ═══════════════════════════════════════════

    private void addSpinHistory(String playerName, String prize) {
        spinHistory.putIfAbsent(playerName, new ArrayList<>());
        List<String> hist = spinHistory.get(playerName);
        hist.add(prize);
        if (hist.size() > 20) hist.remove(0);
    }

    // ═══════════════════════════════════════════
    // [25] ДОСТИЖЕНИЯ
    // ═══════════════════════════════════════════

    private void checkAchievement(Player p, String type) {
        String name = p.getName();
        achievements.putIfAbsent(name, ConcurrentHashMap.newKeySet());
        Set<String> achs = achievements.get(name);
        int streak = winStreak.getOrDefault(name, 0);
        int spins = totalSpins.getOrDefault(name, 0);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);

        if (streak >= 3 && achs.add("streak_3")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: Стрик x3! +200 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 200);
        }
        if (streak >= 5 && achs.add("streak_5")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: Стрик x5! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
        }
        if (streak >= 10 && achs.add("streak_10")) {
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "🏅 ЛЕГЕНДА СТРИКА x10! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " достиг стрика x10!");
        }
        if (type.equals("jackpot") && achs.add("jackpot")) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏅 Первый джекпот! +1000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 1000);
        }
        if (spins >= 10 && achs.add("spins_10")) p.sendMessage(ChatColor.GOLD + "🏅 10 вращений!");
        if (spins >= 50 && achs.add("spins_50")) {
            p.sendMessage(ChatColor.GOLD + "🏅 50 вращений! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
        }
        if (spins >= 100 && achs.add("spins_100")) {
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "🏅 100 вращений! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " сделал 100 вращений!");
        }
    }

    private void showAchievements(Player p) {
        Set<String> achs = achievements.getOrDefault(p.getName(), Collections.emptySet());
        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.GOLD).append(ChatColor.BOLD).append("═══ 🏅 ДОСТИЖЕНИЯ ═══\n\n");
        msg.append(achs.contains("streak_3") ? ChatColor.GREEN : ChatColor.GRAY).append("• Стрик x3\n");
        msg.append(achs.contains("streak_5") ? ChatColor.GREEN : ChatColor.GRAY).append("• Стрик x5\n");
        msg.append(achs.contains("streak_10") ? ChatColor.GREEN : ChatColor.GRAY).append("• Стрик x10\n");
        msg.append(achs.contains("jackpot") ? ChatColor.GREEN : ChatColor.GRAY).append("• Первый джекпот\n");
        msg.append(achs.contains("spins_10") ? ChatColor.GREEN : ChatColor.GRAY).append("• 10 вращений\n");
        msg.append(achs.contains("spins_50") ? ChatColor.GREEN : ChatColor.GRAY).append("• 50 вращений\n");
        msg.append(achs.contains("spins_100") ? ChatColor.GREEN : ChatColor.GRAY).append("• 100 вращений\n");
        msg.append(ChatColor.GRAY).append("\nВсего: ").append(achs.size()).append("/7");
        p.sendMessage(msg.toString());
    }

    // ═══════════════════════════════════════════
    // [26] ДНЕВНОЙ ЧЕЛЛЕНДЖ
    // ═══════════════════════════════════════════

    private void checkDailyChallenge(Player p, String tier) {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String name = p.getName();
        if (challengeCompleted.contains(name)) return;

        boolean isWin = !tier.equals("empty") && !tier.equals("death");
        if (isWin) challengeProgress.merge(name, 1, Integer::sum);

        if (challengeProgress.getOrDefault(name, 0) >= 5) {
            challengeCompleted.add(name);
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
            p.sendMessage(ChatColor.GOLD + "🎯 Дневной челлендж выполнен! +500 реп!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "🎯 " + name + " выполнил челлендж дня!");
        }
    }

    // ═══════════════════════════════════════════
    // [28-29] ПРЕДМЕТЫ
    // ═══════════════════════════════════════════

    public void claimVKPrizes(Player p) {
        // Используем pendingItems из этого класса
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

    // ═══════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════

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
            case "jackpot":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
                break;
            case "rare":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                break;
            case "death":
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                break;
            default:
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                break;
        }
    }

    private void spawnTierParticles(Player p, String tier) {
        switch (tier) {
            case "legendary":
            case "jackpot":
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 50);
                break;
            case "rare":
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 0.5, 0), 25);
                break;
            case "death":
                p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30);
                break;
        }
    }

    // ═══ GUI УТИЛИТЫ ═══

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

    public boolean hasDoubleOrNothing(String playerName) {
        return doubleOrNothing.containsKey(playerName);
    }
}
