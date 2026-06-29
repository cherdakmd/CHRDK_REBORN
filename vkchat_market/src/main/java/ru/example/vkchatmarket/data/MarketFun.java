package ru.example.vkchatmarket.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.listeners.MarketGuiListener;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MarketFun {
    private final VKChatMarketPlugin plugin;

    // Flash Sale
    private String flashSaleItemId = null;
    private double flashSaleDiscount = 0;
    private long flashSaleEndTime = 0;

    // Квесты дня
    private String questDate = "";
    private String questItemId = null;
    private int questTarget = 0;
    private String questType = "sell";
    private final Map<String, Integer> questProgress = new ConcurrentHashMap<>();
    private final Set<String> questCompleted = ConcurrentHashMap.newKeySet();

    // ========================================
    // 🎰 РУЛЕТКА — ВСЕ ФИЧИ
    // ========================================

    // Основные
    private final Map<String, Long> rouletteCooldown = new ConcurrentHashMap<>();
    private int jackpotPool = 5000;
    private final Map<String, Integer> winStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> loseStreak = new ConcurrentHashMap<>();
    private final Map<String, List<String>> spinHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepLost = new ConcurrentHashMap<>();
    private final Set<String> freeSpinUsed = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> giftedSpins = new ConcurrentHashMap<>();
    private final Map<String, Double> streakMultiplier = new ConcurrentHashMap<>();
    private final Map<String, Double> doubleOrNothing = new ConcurrentHashMap<>();
    private final Map<String, String> rouletteMode = new ConcurrentHashMap<>();
    private final Map<String, String> lastPrize = new ConcurrentHashMap<>();
    private final Map<String, Boolean> showParticles = new ConcurrentHashMap<>();
    private final Map<String, Integer> pityCounter = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> achievements = new ConcurrentHashMap<>();
    private final Set<String> autoSpinEnabled = ConcurrentHashMap.newKeySet();

    // [36-70] Новые фичи
    private final Map<String, Integer> currentBet = new ConcurrentHashMap<>();        // [36] Текущая ставка
    private final Map<String, Integer> highestWin = new ConcurrentHashMap<>();         // [37] Рекорд выигрыша
    private final Map<String, Integer> highestLoss = new ConcurrentHashMap<>();        // [38] Рекорд проигрыша
    private final Map<String, Long> lastSpinTime = new ConcurrentHashMap<>();          // [39] Время последнего спина
    private final Map<String, Integer> spinTokens = new ConcurrentHashMap<>();         // [40] Токены за торговлю
    private final Map<String, Integer> dailySpins = new ConcurrentHashMap<>();         // [41] Спинов за день
    private final Map<String, String> dailyChallenge = new ConcurrentHashMap<>();      // [42] Дневной челлендж
    private final Map<String, Integer> challengeProgress = new ConcurrentHashMap<>();  // [43] Прогресс челленджа
    private final Set<String> challengeCompleted = ConcurrentHashMap.newKeySet();      // [44] Челлендж выполнен
    private final Map<String, Integer> luckyNumber = new ConcurrentHashMap<>();        // [45] Счастливое число
    private final Map<String, Boolean> streakShield = new ConcurrentHashMap<>();       // [46] Щит стрика
    private final Map<String, Boolean> prizeInsurance = new ConcurrentHashMap<>();     // [47] Страховка приза
    private final Map<String, Integer> mysteryBox = new ConcurrentHashMap<>();         // [48] Мистический бокс
    private final Map<String, Integer> prizeCollection = new ConcurrentHashMap<>();    // [49] Коллекция призов
    private final Map<String, Integer> allInStreak = new ConcurrentHashMap<>();        // [50] All-in серии
    private final Map<String, Long> nightOwlBonus = new ConcurrentHashMap<>();         // [51] Бонус ночного игрока
    private final Map<String, Long> earlyBirdBonus = new ConcurrentHashMap<>();        // [52] Бонус ранней пташки
    private final Map<String, Integer> weekendWarrior = new ConcurrentHashMap<>();     // [53] Воин выходного дня
    private final Map<String, Integer> referralCount = new ConcurrentHashMap<>();      // [54] Рефералы
    private final Map<String, Integer> communityJackpot = new ConcurrentHashMap<>();   // [55] Общий джекпот
    private final Map<String, Integer> dailyLoginStreak = new ConcurrentHashMap<>();   // [56] Стрик входов
    private final Map<String, String> lastLoginDate = new ConcurrentHashMap<>();       // [57] Дата последнего входа
    private final Map<String, Integer> prizeMultiplier = new ConcurrentHashMap<>();    // [58] Множитель приза
    private final Map<String, Boolean> mysteryMultiplier = new ConcurrentHashMap<>();  // [59] Мистический множитель
    private final Map<String, Integer> upgradeChance = new ConcurrentHashMap<>();      // [60] Шанс апгрейда
    private final Map<String, Integer> totalJackpots = new ConcurrentHashMap<>();      // [61] Всего джекпотов
    private final Map<String, Integer> totalDoubleOrNothing = new ConcurrentHashMap<>(); // [62] Всего DoN
    private final Map<String, Integer> totalGifted = new ConcurrentHashMap<>();        // [63] Всего подарено
    private final Map<String, Integer> totalInsurance = new ConcurrentHashMap<>();     // [64] Всего страховок
    private final Map<String, Integer> totalMystery = new ConcurrentHashMap<>();       // [65] Всего мистических
    private final Map<String, Integer> totalLucky = new ConcurrentHashMap<>();         // [66] Всего удачных
    private final Map<String, Integer> totalNightOwl = new ConcurrentHashMap<>();      // [67] Всего ночных
    private final Map<String, Integer> totalEarlyBird = new ConcurrentHashMap<>();     // [68] Всего утренних
    private final Map<String, Integer> totalWeekend = new ConcurrentHashMap<>();       // [69] Всего выходных
    private final Map<String, Integer> totalCommunity = new ConcurrentHashMap<>();     // [70] Всего общих

    // Ставки
    private static final int[] BET_AMOUNTS = {100, 250, 500, 1000, 2500, 5000, 10000};

    // Призовая таблица
    private static final RoulettePrize[] NORMAL_PRIZES = {
        new RoulettePrize("💎 Алмаз", "DIAMOND", 1, 0.06, "rare"),
        new RoulettePrize("🔮 Эндер-жемчуг", "ENDER_PEARL", 3, 0.10, "common"),
        new RoulettePrize("🔥 Огненный стержень", "BLAZE_ROD", 2, 0.08, "uncommon"),
        new RoulettePrize("⚡ Редстоун-блок", "REDSTONE_BLOCK", 5, 0.10, "common"),
        new RoulettePrize("🍀 Изумруд", "EMERALD", 2, 0.08, "uncommon"),
        new RoulettePrize("💀 Незеритовый лом", "NETHERITE_SCRAP", 1, 0.02, "legendary"),
        new RoulettePrize("🧪 Опыт-бутылки", "EXPERIENCE_BOTTLE", 10, 0.12, "common"),
        new RoulettePrize("🪙 Бонус +200 реп", null, 200, 0.10, "common"),
        new RoulettePrize("💰 Бонус +500 реп", null, 500, 0.05, "uncommon"),
        new RoulettePrize("🏆 ДЖЕКПОТ!", null, -1, 0.01, "jackpot"),
        new RoulettePrize("💀 Пусто", null, 0, 0.10, "empty"),
        new RoulettePrize("🪙 Бонус +100 реп", null, 100, 0.08, "common"),
        new RoulettePrize("🍎 Золотое яблоко", "GOLDEN_APPLE", 1, 0.04, "rare"),
        new RoulettePrize("🧊 Лёд", "BLUE_ICE", 16, 0.06, "common"),
        new RoulettePrize("✨ Мистический бокс", null, -3, 0.03, "mystery"),
        new RoulettePrize("🛡 Щит стрика", null, -4, 0.02, "shield"),
        new RoulettePrize("📦 Страховка", null, -5, 0.02, "insurance"),
        new RoulettePrize("🍀 Счастливое число", null, -6, 0.02, "lucky"),
        new RoulettePrize("🎟 Токен x2", null, -7, 0.03, "token"),
    };

    private static final RoulettePrize[] RUSSIAN_PRIZES = {
        new RoulettePrize("💎💎 Алмаз x3", "DIAMOND", 3, 0.08, "rare"),
        new RoulettePrize("💀💀 НЕЗЕРИТОВЫЙ СЛИТОК", "NETHERITE_INGOT", 1, 0.03, "legendary"),
        new RoulettePrize("🏆 ДЖЕКПОТ x2!", null, -2, 0.02, "jackpot"),
        new RoulettePrize("💰 +1000 реп", null, 1000, 0.07, "uncommon"),
        new RoulettePrize("💀 ПОТЕРЯЛ ВСЁ!", null, -100, 0.15, "death"),
        new RoulettePrize("🍎 Золотое яблоко x5", "GOLDEN_APPLE", 5, 0.05, "rare"),
        new RoulettePrize("💀 -500 реп", null, -500, 0.12, "death"),
        new RoulettePrize("🔥 Тотем бессмертия", "TOTEM_OF_UNDYING", 1, 0.02, "legendary"),
        new RoulettePrize("💀 Пусто", null, 0, 0.08, "empty"),
        new RoulettePrize("🪙 +300 реп", null, 300, 0.10, "common"),
        new RoulettePrize("💀 -200 реп", null, -200, 0.10, "death"),
        new RoulettePrize("🔮 Эндер-жемчуг x16", "ENDER_PEARL", 16, 0.06, "uncommon"),
        new RoulettePrize("💀 ПОТЕРЯЛ ВСЁ!", null, -100, 0.12, "death"),
    };

    public MarketFun(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ========================================
    // [36] GUI ВЫБОРА СТАВКИ
    // ========================================

    public void openBetGUI(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int currentBetAmount = currentBet.getOrDefault(p.getName(), 500);
        String mode = rouletteMode.getOrDefault(p.getName(), "normal");
        int streak = winStreak.getOrDefault(p.getName(), 0);
        double mult = streakMultiplier.getOrDefault(p.getName(), 1.0);
        boolean free = hasFreeSpin(p.getName());
        int gifts = getGiftedSpins(p.getName());
        int tokens = spinTokens.getOrDefault(p.getName(), 0);

        String title = ChatColor.DARK_PURPLE + "🎰 Рулетка — Ставка";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Стекло
        for (int i = 0; i < 54; i++) inv.setItem(i, glassItem(Material.BLACK_STAINED_GLASS_PANE, " "));

        // Инфо
        inv.setItem(4, infoItem(Material.BOOK, ChatColor.GOLD + "🎰 Рулетка",
                ChatColor.GRAY + "Баланс: " + ChatColor.YELLOW + rep + " реп",
                ChatColor.GRAY + "Ставка: " + ChatColor.GREEN + currentBetAmount + " реп",
                ChatColor.GRAY + "Режим: " + (mode.equals("russian") ? ChatColor.RED + "Русская" : ChatColor.GREEN + "Обычная"),
                ChatColor.GRAY + "Стрик: " + ChatColor.AQUA + streak + " (x" + String.format("%.1f", mult) + ")",
                ChatColor.GRAY + "Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп",
                free ? ChatColor.GREEN + "🎁 Бесплатный спин!" : ChatColor.GRAY + "",
                gifts > 0 ? ChatColor.GREEN + "🎁 Подарков: " + gifts : "",
                tokens > 0 ? ChatColor.YELLOW + "🎟 Токенов: " + tokens : ""));

        // Ставки
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < BET_AMOUNTS.length && i < slots.length; i++) {
            int bet = BET_AMOUNTS[i];
            boolean selected = bet == currentBetAmount;
            Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
            ChatColor color = selected ? ChatColor.GREEN : ChatColor.YELLOW;
            inv.setItem(slots[i], betItem(mat, color + "" + bet + " реп",
                    selected ? ChatColor.GREEN + "✓ Выбрано" : ChatColor.GRAY + "Нажми для выбора",
                    rep < bet ? ChatColor.RED + "Недостаточно реп!" : ""));
        }

        // Кнопки действий
        inv.setItem(30, actionItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "🎰 Крутить!",
                ChatColor.GRAY + "Обычная рулетка",
                ChatColor.GRAY + "Ставка: " + currentBetAmount + " реп"));
        inv.setItem(31, actionItem(Material.BLAZE_POWDER, ChatColor.RED + "☠ Русская рулетка",
                ChatColor.GRAY + "x3 цена, x3 награда!",
                ChatColor.GRAY + "Ставка: " + (currentBetAmount * 3) + " реп"));
        inv.setItem(32, actionItem(Material.GOLD_NUGGET, ChatColor.YELLOW + "⚡ Double or Nothing",
                hasDoubleOrNothing(p.getName()) ? ChatColor.GREEN + "Доступно!" : ChatColor.GRAY + "Нет активного"));

        // Режимы и доп. кнопки
        inv.setItem(38, actionItem(Material.CLOCK, ChatColor.AQUA + "🔄 Авто-спин",
                autoSpinEnabled.contains(p.getName()) ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ"));
        inv.setItem(39, actionItem(Material.SHIELD, ChatColor.AQUA + "🛡 Щит стрика",
                streakShield.getOrDefault(p.getName(), false) ? ChatColor.GREEN + "Активен" : ChatColor.GRAY + "Нет"));
        inv.setItem(40, actionItem(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "📦 Страховка",
                prizeInsurance.getOrDefault(p.getName(), false) ? ChatColor.GREEN + "Активна" : ChatColor.GRAY + "Нет"));
        inv.setItem(41, actionItem(Material.EMERALD, ChatColor.GREEN + "🍀 Счастливое число",
                "Твоё: " + luckyNumber.getOrDefault(p.getName(), 0)));
        inv.setItem(42, actionItem(Material.PAPER, ChatColor.AQUA + "📊 Статистика",
                ChatColor.GRAY + "Нажми для просмотра"));

        // Нижний ряд
        inv.setItem(45, actionItem(Material.ARROW, ChatColor.WHITE + "🏠 Назад", ""));
        inv.setItem(47, actionItem(Material.ENDER_CHEST, ChatColor.LIGHT_PURPLE + "📦 Призы из ВК",
                "Забрать выигранные предметы",
                plugin.getVKRouletteListener() != null && plugin.getVKRouletteListener().hasPendingItems(vkId) ?
                        ChatColor.GREEN + "Есть призы!" : ChatColor.GRAY + "Нет призов"));
        inv.setItem(49, actionItem(Material.CHEST, ChatColor.GOLD + "🎁 Мистический бокс",
                "Боксов: " + mysteryBox.getOrDefault(p.getName(), 0)));
        inv.setItem(53, actionItem(Material.COMPASS, ChatColor.AQUA + "📋 Квест дня",
                getQuestInfo()));

        p.openInventory(inv);
    }

    private ItemStack glassItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack betItem(Material mat, String name, String... lore) {
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

    private ItemStack actionItem(Material mat, String name, String... lore) {
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

    // ========================================
    // ОБРАБОТКА КЛИКОВ В GUI
    // ========================================

    public void handleGUIClick(Player p, int slot) {
        // Ставки
        int[] betSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < betSlots.length; i++) {
            if (slot == betSlots[i] && i < BET_AMOUNTS.length) {
                currentBet.put(p.getName(), BET_AMOUNTS[i]);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openBetGUI(p);
                return;
            }
        }

        switch (slot) {
            case 30: // Крутить
                spinRoulette(p, "normal");
                break;
            case 31: // Русская
                spinRoulette(p, "russian");
                break;
            case 32: // Double or Nothing
                if (hasDoubleOrNothing(p.getName())) {
                    doubleOrNothing(p);
                } else {
                    p.sendMessage(ChatColor.RED + "Нет активного предложения!");
                }
                break;
            case 38: // Авто-спин
                toggleAutoSpin(p);
                openBetGUI(p);
                break;
            case 39: // Щит стрика
                activateStreakShield(p);
                break;
            case 40: // Страховка
                activatePrizeInsurance(p);
                break;
            case 41: // Счастливое число
                setLuckyNumber(p);
                break;
            case 42: // Статистика
                p.sendMessage(getFullStats(p));
                break;
            case 45: // Назад
                MarketGuiListener.openCategoryMenu(plugin, p);
                break;
            case 47: // Призы из ВК
                claimVKPrizes(p);
                break;
            case 49: // Мистический бокс
                openMysteryBox(p);
                break;
            case 53: // Квест
                p.sendMessage(ChatColor.AQUA + "📋 " + getQuestInfo());
                break;
        }
    }

    // ========================================
    // РУЛЕТКА — ОСНОВНАЯ ЛОГИКА
    // ========================================

    public void spinRoulette(Player p) {
        spinRoulette(p, "normal");
    }

    public void spinRoulette(Player p, String mode) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink) для рулетки!");
            return;
        }

        // [7] Бесплатный спин
        boolean isFree = false;
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (!freeSpinUsed.contains(p.getName() + today)) {
            isFree = true;
            freeSpinUsed.add(p.getName() + today);
        }

        // [8] Подарочные спины
        boolean isGifted = false;
        int giftCount = giftedSpins.getOrDefault(p.getName(), 0);
        if (giftCount > 0) {
            isGifted = true;
            giftedSpins.put(p.getName(), giftCount - 1);
        }

        // [40] Токены
        boolean isToken = false;
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        if (tokens >= 5) {
            isToken = true;
            spinTokens.put(p.getName(), tokens - 5);
        }

        int bet = currentBet.getOrDefault(p.getName(), 500);
        if (mode.equals("russian")) bet *= 3;

        if (!isFree && !isGifted && !isToken) {
            long cooldown = plugin.getConfig().getLong("market2.roulette.cooldown-ms", 300000);
            Long last = rouletteCooldown.get(p.getName());
            if (last != null && System.currentTimeMillis() - last < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "Рулетка перезаряжается! " + remaining + " сек.");
                return;
            }

            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < bet) {
                p.sendMessage(ChatColor.RED + "Нужно " + bet + " реп. (у тебя " + rep + ")");
                return;
            }

            VKChatPlugin.getInstance().getApi().takeReputation(vkId, bet);
            rouletteCooldown.put(p.getName(), System.currentTimeMillis());
            jackpotPool += bet / 10;

            // [55] Общий джекпот
            communityJackpot.merge("global", bet / 20, Integer::sum);
        }

        rouletteMode.put(p.getName(), mode);
        totalSpins.merge(p.getName(), 1, Integer::sum);
        dailySpins.merge(p.getName(), 1, Integer::sum);
        lastSpinTime.put(p.getName(), System.currentTimeMillis());

        // [51-53] Бонусы по времени
        checkTimeBonuses(p);

        // Анимация
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        if (mode.equals("russian")) {
            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_RED + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.RED + "║   ☠ РУССКАЯ РУЛЕТКА ☠        ║");
            p.sendMessage(ChatColor.RED + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.RED + "║   Награда: x3 от обычной!    ║");
            p.sendMessage(ChatColor.DARK_RED + "╚═══════════════════════════════╝");
            p.sendMessage("");
        } else {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.YELLOW + "║   🎰 РУЛЕТКА КРУТИТСЯ...     ║");
            p.sendMessage(ChatColor.YELLOW + "║   Ставка: " + bet + " реп            ║");
            p.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════╝");
            p.sendMessage("");
        }

        int pity = pityCounter.getOrDefault(p.getName(), 0);
        final int finalBet = bet;

        // Анимация 7 кадров
        for (int i = 0; i < 7; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (frame < 6) {
                    String[] symbols = {"🎰", "💎", "🍀", "⭐", "🔥", "💰", "🏆"};
                    p.sendMessage(ChatColor.GRAY + "  " + symbols[frame] + " " + symbols[(frame + 1) % 7] + " " + symbols[(frame + 2) % 7]);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + frame * 0.15f);
                }
            }, i * 6L);
        }

        // Финал
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RoulettePrize prize;
            String currentMode = rouletteMode.getOrDefault(p.getName(), "normal");

            if (pity >= 15) {
                prize = findRarePrize(currentMode);
                pityCounter.put(p.getName(), 0);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ PITY! Гарантированный редкий приз!");
            } else {
                prize = currentMode.equals("russian") ? rollRussianPrize() : rollNormalPrize();
            }

            int streak = winStreak.getOrDefault(p.getName(), 0);
            double streakMult = 1.0 + (streak * 0.1);
            streakMultiplier.put(p.getName(), streakMult);

            givePrize(p, prize, vkId, finalBet);
        }, 50L);
    }

    private RoulettePrize findRarePrize(String mode) {
        RoulettePrize[] prizes = mode.equals("russian") ? RUSSIAN_PRIZES : NORMAL_PRIZES;
        List<RoulettePrize> rares = new ArrayList<>();
        for (RoulettePrize p : prizes) {
            if (p.tier.equals("rare") || p.tier.equals("legendary") || p.tier.equals("jackpot")) {
                rares.add(p);
            }
        }
        return rares.isEmpty() ? prizes[0] : rares.get(ThreadLocalRandom.current().nextInt(rares.size()));
    }

    private RoulettePrize rollNormalPrize() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (RoulettePrize prize : NORMAL_PRIZES) {
            cumulative += prize.chance;
            if (roll < cumulative) return prize;
        }
        return NORMAL_PRIZES[NORMAL_PRIZES.length - 1];
    }

    private RoulettePrize rollRussianPrize() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (RoulettePrize prize : RUSSIAN_PRIZES) {
            cumulative += prize.chance;
            if (roll < cumulative) return prize;
        }
        return RUSSIAN_PRIZES[RUSSIAN_PRIZES.length - 1];
    }

    // ========================================
    // ВЫДАЧА ПРИЗА
    // ========================================

    private void givePrize(Player p, RoulettePrize prize, int vkId, int bet) {
        String playerName = p.getName();
        double streakMult = streakMultiplier.getOrDefault(playerName, 1.0);

        // [45] Счастливое число — шанс на бонус
        int lucky = luckyNumber.getOrDefault(playerName, 0);
        boolean isLucky = lucky > 0 && ThreadLocalRandom.current().nextInt(100) < lucky;
        if (isLucky) {
            streakMult *= 1.5;
            p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число сработало! x1.5!");
            totalLucky.merge(playerName, 1, Integer::sum);
        }

        // [59] Мистический множитель
        if (mysteryMultiplier.getOrDefault(playerName, false)) {
            streakMult *= 2.0;
            p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Мистический множитель! x2!");
            mysteryMultiplier.put(playerName, false);
        }

        // Анимация
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "  ╔═══════════════════════════════╗");

        ChatColor tierColor;
        switch (prize.tier) {
            case "legendary": tierColor = ChatColor.GOLD; break;
            case "jackpot": tierColor = ChatColor.LIGHT_PURPLE; break;
            case "rare": tierColor = ChatColor.AQUA; break;
            case "uncommon": tierColor = ChatColor.GREEN; break;
            case "death": tierColor = ChatColor.DARK_RED; break;
            case "mystery": tierColor = ChatColor.DARK_PURPLE; break;
            case "shield": tierColor = ChatColor.AQUA; break;
            case "insurance": tierColor = ChatColor.YELLOW; break;
            case "lucky": tierColor = ChatColor.GREEN; break;
            case "token": tierColor = ChatColor.GOLD; break;
            default: tierColor = ChatColor.WHITE; break;
        }

        p.sendMessage(tierColor + "  ║  " + prize.name);
        p.sendMessage(ChatColor.GOLD + "  ╚═══════════════════════════════╝");
        p.sendMessage("");

        // Звуки и частицы
        switch (prize.tier) {
            case "legendary":
            case "jackpot":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 50);
                break;
            case "rare":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 25);
                break;
            case "death":
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30);
                break;
            default:
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                break;
        }

        // Стрики
        boolean isWin = !prize.tier.equals("empty") && !prize.tier.equals("death");
        if (isWin) {
            winStreak.merge(playerName, 1, Integer::sum);
            loseStreak.put(playerName, 0);
            totalWins.merge(playerName, 1, Integer::sum);
            pityCounter.put(playerName, 0);
        } else {
            // [46] Щит стрика
            if (streakShield.getOrDefault(playerName, false)) {
                p.sendMessage(ChatColor.AQUA + "🛡 Щит стрика защитил!");
                streakShield.put(playerName, false);
                totalInsurance.merge(playerName, 1, Integer::sum);
            } else {
                winStreak.put(playerName, 0);
            }
            loseStreak.merge(playerName, 1, Integer::sum);
            pityCounter.merge(playerName, 1, Integer::sum);
        }

        // Обработка специальных призов
        if (prize.amount == -7) {
            // Токены
            int bonusTokens = 5 + ThreadLocalRandom.current().nextInt(10);
            spinTokens.merge(playerName, bonusTokens, Integer::sum);
            p.sendMessage(ChatColor.GOLD + "🎟 Получено " + bonusTokens + " токенов! (нужно 5 для спина)");
            addHistory("🎟 " + playerName + " получил " + bonusTokens + " токенов");
            return;
        }
        if (prize.amount == -6) {
            // Счастливое число
            int num = 10 + ThreadLocalRandom.current().nextInt(40);
            luckyNumber.put(playerName, num);
            p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число: " + num + "% шанс на x1.5!");
            addHistory("🍀 " + playerName + " получил счастливое число " + num + "%");
            return;
        }
        if (prize.amount == -5) {
            // Страховка
            prizeInsurance.put(playerName, true);
            p.sendMessage(ChatColor.YELLOW + "📦 Страховка активирована! Следующий проигрыш = компенсация.");
            addHistory("📦 " + playerName + " получил страховку");
            return;
        }
        if (prize.amount == -4) {
            // Щит стрика
            streakShield.put(playerName, true);
            p.sendMessage(ChatColor.AQUA + "🛡 Щит стрика активирован! Защитит от сброса.");
            addHistory("🛡 " + playerName + " получил щит стрика");
            return;
        }
        if (prize.amount == -3) {
            // Мистический бокс
            mysteryBox.merge(playerName, 1, Integer::sum);
            p.sendMessage(ChatColor.DARK_PURPLE + "✨ Мистический бокс получен! Открой в GUI.");
            addHistory("✨ " + playerName + " получил мистический бокс");
            return;
        }

        // Основные призы
        if (prize.amount == -100) {
            // Потеря всего (русская рулетка)
            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            int loss = Math.min(currentRep, bet + ThreadLocalRandom.current().nextInt(bet));
            // [47] Страховка
            if (prizeInsurance.getOrDefault(playerName, false)) {
                loss /= 2;
                prizeInsurance.put(playerName, false);
                p.sendMessage(ChatColor.YELLOW + "📦 Страховка сработала! Потеря уменьшена.");
            }
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
            p.sendMessage(ChatColor.DARK_RED + "💀 Потеряно " + loss + " репутации!");
            totalRepLost.merge(playerName, loss, Integer::sum);
            highestLoss.merge(playerName, loss, Math::max);
            broadcastLoss(p, loss);
            addHistory("💀 " + playerName + " проиграл " + loss + " реп в русской рулетке!");
            return;
        }

        if (prize.amount == -2) {
            // Джекпот x2
            int jackpot = (int) (jackpotPool * 2 * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆🏆 ДЖЕКПОТ x2! +" + jackpot + " реп!");
            jackpotPool = 5000;
            broadcastJackpot(p, jackpot);
            addHistory("🏆🏆 " + playerName + " сорвал ДЖЕКПОТ x2: " + jackpot + " реп!");
            totalRepWon.merge(playerName, jackpot, Integer::sum);
            highestWin.merge(playerName, jackpot, Math::max);
            totalJackpots.merge(playerName, 1, Integer::sum);
            checkAchievements(p, "jackpot");
            return;
        }

        if (prize.amount == -1) {
            // Обычный джекпот
            int jackpot = (int) (jackpotPool * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ДЖЕКПОТ! +" + jackpot + " реп!");
            jackpotPool = 5000;
            broadcastJackpot(p, jackpot);
            addHistory("🏆 " + playerName + " сорвал ДЖЕКПОТ: " + jackpot + " реп!");
            totalRepWon.merge(playerName, jackpot, Integer::sum);
            highestWin.merge(playerName, jackpot, Math::max);
            totalJackpots.merge(playerName, 1, Integer::sum);
            checkAchievements(p, "jackpot");
            return;
        }

        if (prize.material == null) {
            // Репутация
            int bonus = (int) (prize.amount * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
            p.sendMessage(ChatColor.GREEN + "🪙 +" + bonus + " репутации!");
            totalRepWon.merge(playerName, bonus, Integer::sum);
            highestWin.merge(playerName, bonus, Math::max);
            checkAchievements(p, "rep_" + bonus);
        } else if (prize.amount == 0) {
            // Пусто
            p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
            // [47] Страховка — компенсация
            if (prizeInsurance.getOrDefault(playerName, false)) {
                int compensation = bet / 2;
                VKChatPlugin.getInstance().getApi().addReputation(vkId, compensation);
                p.sendMessage(ChatColor.YELLOW + "📦 Страховка: +" + compensation + " реп компенсации!");
                prizeInsurance.put(playerName, false);
                totalInsurance.merge(playerName, 1, Integer::sum);
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(playerName, 0.0);
                p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing? /m double");
            }
        } else {
            // Предмет
            Material mat;
            try { mat = Material.valueOf(prize.material); } catch (Exception e) {
                p.sendMessage(ChatColor.RED + "Ошибка приза!");
                return;
            }

            int amount = (int) (prize.amount * streakMult);
            // [60] Шанс апгрейда
            int upgrade = upgradeChance.getOrDefault(playerName, 0);
            if (upgrade > 0 && ThreadLocalRandom.current().nextInt(100) < upgrade) {
                amount *= 2;
                p.sendMessage(ChatColor.GOLD + "⬆ АПГРЕЙД! x2 предметов!");
                upgradeChance.put(playerName, 0);
            }

            if (p.getInventory().addItem(new ItemStack(mat, amount)).isEmpty()) {
                p.sendMessage(ChatColor.GREEN + "🎉 " + prize.name + " x" + amount + "!");
                broadcastWin(p, prize.name + " x" + amount);
                addHistory("🎰 " + playerName + " выиграл: " + prize.name + " x" + amount);
                prizeCollection.merge(playerName, 1, Integer::sum);
                checkAchievements(p, "item_" + prize.material);
            } else {
                p.sendMessage(ChatColor.RED + "Инвентарь полон!");
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 100);
                p.sendMessage(ChatColor.YELLOW + "Компенсация: +100 реп.");
            }
        }

        addSpinHistory(playerName, prize.name);
        checkDailyChallenge(p, prize);
    }

    // ========================================
    // DOUBLE OR NOTHING
    // ========================================

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
        p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing крутится...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean win = ThreadLocalRandom.current().nextDouble() < 0.45; // 45% шанс
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(800);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
                p.sendMessage(ChatColor.GREEN + "🎉 DOUBLE! +" + bonus + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                totalRepWon.merge(p.getName(), bonus, Integer::sum);
                highestWin.merge(p.getName(), bonus, Math::max);
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(400);
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
                p.sendMessage(ChatColor.RED + "💀 NOTHING! -" + loss + " реп!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                totalRepLost.merge(p.getName(), loss, Integer::sum);
                highestLoss.merge(p.getName(), loss, Math::max);
            }
        }, 30L);
    }

    // ========================================
    // [48] МИСТИЧЕСКИЙ БОКС
    // ========================================

    public void openMysteryBox(Player p) {
        int boxes = mysteryBox.getOrDefault(p.getName(), 0);
        if (boxes <= 0) {
            p.sendMessage(ChatColor.RED + "Нет мистических боксов! Выиграй в рулетке.");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        mysteryBox.put(p.getName(), boxes - 1);
        totalMystery.merge(p.getName(), 1, Integer::sum);

        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        p.sendMessage(ChatColor.DARK_PURPLE + "✨ Открываю мистический бокс...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < 5) {
                // Легендарный
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 2000);
                p.sendMessage(ChatColor.GOLD + "🏆 ЛЕГЕНДАРНЫЙ БОКС! +2000 реп!");
                broadcastJackpot(p, 2000);
            } else if (roll < 20) {
                // Редкий
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
                p.sendMessage(ChatColor.AQUA + "💎 Редкий бокс! +500 реп!");
            } else if (roll < 50) {
                // Обычный
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 200);
                p.sendMessage(ChatColor.GREEN + "🪙 Обычный бокс! +200 реп!");
            } else {
                // Пусто
                p.sendMessage(ChatColor.GRAY + "💀 Бокс оказался пустым...");
            }
        }, 40L);
    }

    // ========================================
    // ЗАБОР ПРИЗОВ ИЗ VK РУЛЕТКИ
    // ========================================

    public void claimVKPrizes(Player p) {
        if (plugin.getVKRouletteListener() == null) {
            p.sendMessage(ChatColor.RED + "Модуль рулетки не загружен!");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return;
        }

        if (!plugin.getVKRouletteListener().hasPendingItems(vkId)) {
            p.sendMessage(ChatColor.GRAY + "Нет ожидающих предметов. Играй в рулетку в ВК!");
            return;
        }

        java.util.List<String> items = plugin.getVKRouletteListener().takePendingItems(vkId);
        if (items == null || items.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Нет предметов.");
            return;
        }

        int given = 0;
        int lost = 0;
        for (String item : items) {
            String[] parts = item.split(";");
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                if (p.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount)).isEmpty()) {
                    given++;
                } else {
                    lost++;
                }
            } catch (Exception e) {
                lost++;
            }
        }

        p.sendMessage(ChatColor.GREEN + "📦 Получено предметов: " + given);
        if (lost > 0) {
            p.sendMessage(ChatColor.RED + "⚠ Не удалось выдать: " + lost + " (инвентарь полон)");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        openBetGUI(p);
    }

    // ========================================
    // [46-47] ЩИТ И СТРАХОВКА
    // ========================================

    public void activateStreakShield(Player p) {
        if (streakShield.getOrDefault(p.getName(), false)) {
            p.sendMessage(ChatColor.RED + "Щит уже активен!");
            return;
        }
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        if (tokens < 10) {
            p.sendMessage(ChatColor.RED + "Нужно 10 токенов! (у тебя " + tokens + ")");
            return;
        }
        spinTokens.put(p.getName(), tokens - 10);
        streakShield.put(p.getName(), true);
        p.sendMessage(ChatColor.AQUA + "🛡 Щит стрика активирован за 10 токенов!");
    }

    public void activatePrizeInsurance(Player p) {
        if (prizeInsurance.getOrDefault(p.getName(), false)) {
            p.sendMessage(ChatColor.RED + "Страховка уже активна!");
            return;
        }
        int tokens = spinTokens.getOrDefault(p.getName(), 0);
        if (tokens < 15) {
            p.sendMessage(ChatColor.RED + "Нужно 15 токенов! (у тебя " + tokens + ")");
            return;
        }
        spinTokens.put(p.getName(), tokens - 15);
        prizeInsurance.put(p.getName(), true);
        p.sendMessage(ChatColor.YELLOW + "📦 Страховка активирована за 15 токенов!");
    }

    // ========================================
    // [45] СЧАСТЛИВОЕ ЧИСЛО
    // ========================================

    public void setLuckyNumber(Player p) {
        int num = 1 + ThreadLocalRandom.current().nextInt(50);
        luckyNumber.put(p.getName(), num);
        p.sendMessage(ChatColor.GREEN + "🍀 Счастливое число: " + num + "% шанс на x1.5!");
    }

    // ========================================
    // [51-53] БОНУСЫ ПО ВРЕМЕНИ
    // ========================================

    private void checkTimeBonuses(Player p) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        // [51] Ночная сова (22:00 - 06:00)
        if (hour >= 22 || hour < 6) {
            if (!nightOwlBonus.containsKey(p.getName())) {
                nightOwlBonus.put(p.getName(), System.currentTimeMillis());
                spinTokens.merge(p.getName(), 3, Integer::sum);
                p.sendMessage(ChatColor.BLUE + "🌙 Бонус ночной совы! +3 токена!");
                totalNightOwl.merge(p.getName(), 1, Integer::sum);
            }
        }

        // [52] Ранняя пташка (06:00 - 10:00)
        if (hour >= 6 && hour < 10) {
            if (!earlyBirdBonus.containsKey(p.getName())) {
                earlyBirdBonus.put(p.getName(), System.currentTimeMillis());
                spinTokens.merge(p.getName(), 2, Integer::sum);
                p.sendMessage(ChatColor.YELLOW + "🌅 Бонус ранней пташки! +2 токена!");
                totalEarlyBird.merge(p.getName(), 1, Integer::sum);
            }
        }

        // [53] Воин выходного дня (Сб, Вс)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            weekendWarrior.merge(p.getName(), 1, Integer::sum);
            if (weekendWarrior.get(p.getName()) >= 5) {
                spinTokens.merge(p.getName(), 10, Integer::sum);
                p.sendMessage(ChatColor.GOLD + "🎉 Воин выходного дня! +10 токенов за 5 спинов!");
                weekendWarrior.put(p.getName(), 0);
                totalWeekend.merge(p.getName(), 1, Integer::sum);
            }
        }
    }

    // ========================================
    // [42-44] ДНЕВНОЙ ЧЕЛЛЕНДЖ
    // ========================================

    private void checkDailyChallenge(Player p, RoulettePrize prize) {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (!dailyChallenge.containsKey(today)) {
            String[] challenges = {"win_5", "spin_10", "rare_1", "jackpot_1", "streak_3"};
            dailyChallenge.put(today, challenges[ThreadLocalRandom.current().nextInt(challenges.length)]);
        }

        String challenge = dailyChallenge.get(today);
        String name = p.getName();

        if (challengeCompleted.contains(name)) return;

        int progress = challengeProgress.getOrDefault(name, 0);

        switch (challenge) {
            case "win_5":
                if (!prize.tier.equals("empty") && !prize.tier.equals("death")) {
                    challengeProgress.merge(name, 1, Integer::sum);
                }
                break;
            case "spin_10":
                challengeProgress.merge(name, 1, Integer::sum);
                break;
            case "rare_1":
                if (prize.tier.equals("rare") || prize.tier.equals("legendary")) {
                    challengeProgress.put(name, 100);
                }
                break;
            case "jackpot_1":
                if (prize.tier.equals("jackpot")) {
                    challengeProgress.put(name, 100);
                }
                break;
            case "streak_3":
                challengeProgress.put(name, winStreak.getOrDefault(name, 0));
                break;
        }

        int target = challenge.startsWith("win") ? 5 : challenge.startsWith("spin") ? 10 : 1;
        if (challengeProgress.getOrDefault(name, 0) >= target) {
            challengeCompleted.add(name);
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
                p.sendMessage(ChatColor.GOLD + "🎯 Челлендж дня выполнен! +500 реп!");
                Bukkit.broadcastMessage(ChatColor.GOLD + "🎯 " + name + " выполнил челлендж дня!");
            }
        }
    }

    // ========================================
    // ПОДАРКИ И РЕЖИМЫ
    // ========================================

    public void giftSpin(Player from, String toName) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(from);
        if (vkId == -1) return;

        int cost = plugin.getConfig().getInt("market2.roulette.gift-cost", 1000);
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            from.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.!");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        giftedSpins.merge(toName, 1, Integer::sum);
        totalGifted.merge(from.getName(), 1, Integer::sum);
        from.sendMessage(ChatColor.GREEN + "🎁 Подарил спин " + toName + "!");
        addHistory("🎁 " + from.getName() + " подарил спин " + toName);

        Player to = Bukkit.getPlayer(toName);
        if (to != null && to.isOnline()) {
            to.sendMessage(ChatColor.GREEN + "🎁 " + from.getName() + " подарил тебе спин! /m roulette");
        }
    }

    public void toggleMode(Player p) {
        String current = rouletteMode.getOrDefault(p.getName(), "normal");
        String newMode = current.equals("normal") ? "russian" : "normal";
        rouletteMode.put(p.getName(), newMode);
        if (newMode.equals("russian")) {
            p.sendMessage(ChatColor.RED + "☠ Режим: РУССКАЯ РУЛЕТКА");
        } else {
            p.sendMessage(ChatColor.GREEN + "🎰 Режим: Обычная рулетка");
        }
    }

    public void toggleAutoSpin(Player p) {
        if (autoSpinEnabled.contains(p.getName())) {
            autoSpinEnabled.remove(p.getName());
            p.sendMessage(ChatColor.RED + "🎰 Авто-спин выключен");
        } else {
            autoSpinEnabled.add(p.getName());
            p.sendMessage(ChatColor.GREEN + "🎰 Авто-спин включён");
        }
    }

    // ========================================
    // СТАТИСТИКА
    // ========================================

    public String getStats(Player p) {
        String name = p.getName();
        int spins = totalSpins.getOrDefault(name, 0);
        int wins = totalWins.getOrDefault(name, 0);
        int repWon = totalRepWon.getOrDefault(name, 0);
        int repLost = totalRepLost.getOrDefault(name, 0);
        int streak = winStreak.getOrDefault(name, 0);

        return ChatColor.GOLD + "═══ 🎰 Статистика ═══\n" +
               ChatColor.WHITE + "Вращений: " + ChatColor.YELLOW + spins + "\n" +
               ChatColor.WHITE + "Побед: " + ChatColor.GREEN + wins + "\n" +
               ChatColor.WHITE + "Выиграно: " + ChatColor.GREEN + "+" + repWon + " реп\n" +
               ChatColor.WHITE + "Проиграно: " + ChatColor.RED + "-" + repLost + " реп\n" +
               ChatColor.WHITE + "Текущий стрик: " + ChatColor.AQUA + streak + "\n" +
               ChatColor.WHITE + "Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп";
    }

    public String getFullStats(Player p) {
        String name = p.getName();
        return ChatColor.GOLD + "═══ 🎰 ПОЛНАЯ СТАТИСТИКА ═══\n" +
               ChatColor.WHITE + "Вращений: " + ChatColor.YELLOW + totalSpins.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Побед: " + ChatColor.GREEN + totalWins.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Выиграно: " + ChatColor.GREEN + "+" + totalRepWon.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Проиграно: " + ChatColor.RED + "-" + totalRepLost.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Рекорд выигрыша: " + ChatColor.GOLD + highestWin.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Рекорд проигрыша: " + ChatColor.RED + highestLoss.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Стрик: " + ChatColor.AQUA + winStreak.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Джекпотов: " + ChatColor.LIGHT_PURPLE + totalJackpots.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Double or Nothing: " + ChatColor.YELLOW + totalDoubleOrNothing.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Подарков: " + ChatColor.GREEN + totalGifted.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Боксов: " + ChatColor.DARK_PURPLE + mysteryBox.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Токенов: " + ChatColor.GOLD + spinTokens.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Ночных бонусов: " + ChatColor.BLUE + totalNightOwl.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Утренних бонусов: " + ChatColor.YELLOW + totalEarlyBird.getOrDefault(name, 0) + "\n" +
               ChatColor.WHITE + "Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool;
    }

    private void checkAchievements(Player p, String type) {
        String name = p.getName();
        achievements.putIfAbsent(name, ConcurrentHashMap.newKeySet());
        Set<String> achs = achievements.get(name);
        int streak = winStreak.getOrDefault(name, 0);
        int spins = totalSpins.getOrDefault(name, 0);

        if (streak >= 3 && achs.add("streak_3")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Стрик x3! +200 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 200);
        }
        if (streak >= 5 && achs.add("streak_5")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Стрик x5! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 500);
        }
        if (streak >= 10 && achs.add("streak_10")) {
            p.sendMessage(ChatColor.GOLD + "🏅 ЛЕГЕНДА СТРИКА x10! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " достиг стрика x10!");
        }
        if (type.equals("jackpot") && achs.add("jackpot")) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏅 Первый джекпот! +1000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 1000);
        }
        if (spins >= 10 && achs.add("spins_10")) {
            p.sendMessage(ChatColor.GOLD + "🏅 10 вращений!");
        }
        if (spins >= 50 && achs.add("spins_50")) {
            p.sendMessage(ChatColor.GOLD + "🏅 50 вращений! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 500);
        }
        if (spins >= 100 && achs.add("spins_100")) {
            p.sendMessage(ChatColor.GOLD + "🏅 100 вращений! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " сделал 100 вращений!");
        }
    }

    private void addSpinHistory(String playerName, String prize) {
        spinHistory.putIfAbsent(playerName, new ArrayList<>());
        List<String> hist = spinHistory.get(playerName);
        hist.add(prize);
        if (hist.size() > 20) hist.remove(0);
    }

    public List<String> getSpinHistory(String playerName) {
        return spinHistory.getOrDefault(playerName, Collections.emptyList());
    }

    private void broadcastWin(Player p, String prize) {
        Bukkit.broadcastMessage(ChatColor.GOLD + "🎰 " + p.getName() + " выиграл: " + ChatColor.YELLOW + prize + ChatColor.GOLD + "!");
    }

    private void broadcastJackpot(Player p, int amount) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "╔═══════════════════════════════╗");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "║  🏆 " + p.getName() + " СОРВАЛ ДЖЕКПОТ!  ║");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "║  💰 +" + amount + " репутации!            ║");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "╚═══════════════════════════════╝");
        Bukkit.broadcastMessage("");
    }

    private void broadcastLoss(Player p, int amount) {
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "💀 " + p.getName() + " проиграл " + amount + " реп!");
    }

    public List<String> getLeaderboard(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totalRepWon.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            result.add((i + 1) + ". " + e.getKey() + " — +" + e.getValue() + " реп");
        }
        return result;
    }

    public double getStreakMultiplier(String playerName) { return streakMultiplier.getOrDefault(playerName, 1.0); }
    public int getJackpotPool() { return jackpotPool; }
    public boolean hasDoubleOrNothing(String playerName) { return doubleOrNothing.containsKey(playerName); }
    public boolean hasFreeSpin(String playerName) {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return !freeSpinUsed.contains(playerName + today);
    }
    public int getGiftedSpins(String playerName) { return giftedSpins.getOrDefault(playerName, 0); }
    public int getCurrentBet(String playerName) { return currentBet.getOrDefault(playerName, 500); }
    public int getSpinTokens(String playerName) { return spinTokens.getOrDefault(playerName, 0); }
    public int getMysteryBoxes(String playerName) { return mysteryBox.getOrDefault(playerName, 0); }
    public boolean hasStreakShield(String playerName) { return streakShield.getOrDefault(playerName, false); }
    public boolean hasPrizeInsurance(String playerName) { return prizeInsurance.getOrDefault(playerName, false); }
    public int getLuckyNumber(String playerName) { return luckyNumber.getOrDefault(playerName, 0); }
    public int getCommunityJackpot() { return communityJackpot.getOrDefault("global", 0); }

    // [40] Заработать токены за торговлю
    public void earnTokens(String playerName, int amount) {
        spinTokens.merge(playerName, amount, Integer::sum);
    }

    static class RoulettePrize {
        final String name;
        final String material;
        final int amount;
        final double chance;
        final String tier;

        RoulettePrize(String name, String material, int amount, double chance, String tier) {
            this.name = name;
            this.material = material;
            this.amount = amount;
            this.chance = chance;
            this.tier = tier;
        }
    }

    // ========================================
    // ⚡ FLASH SALE
    // ========================================

    public void checkFlashSale() {
        if (System.currentTimeMillis() < flashSaleEndTime) return;
        double chance = plugin.getConfig().getDouble("market2.flash-sale.chance", 0.10);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        flashSaleItemId = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        flashSaleDiscount = 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
        long duration = plugin.getConfig().getLong("market2.flash-sale.duration-ms", 300000);
        flashSaleEndTime = System.currentTimeMillis() + duration;

        String name = plugin.getConfig().getString("items." + flashSaleItemId + ".name", flashSaleItemId);
        int percent = (int) (flashSaleDiscount * 100);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "⚡ [Flash Sale] " + ChatColor.YELLOW + name +
                ChatColor.LIGHT_PURPLE + " -" + percent + "% на 5 минут!");
        addHistory("⚡ Flash Sale: " + name + " -" + percent + "%");
    }

    public boolean isFlashSaleActive(String itemId) {
        return flashSaleItemId != null && flashSaleItemId.equals(itemId) && System.currentTimeMillis() < flashSaleEndTime;
    }

    public double getFlashSaleDiscount() { return System.currentTimeMillis() < flashSaleEndTime ? flashSaleDiscount : 0; }
    public String getFlashSaleItemId() { return flashSaleItemId; }
    public long getFlashSaleEndTime() { return flashSaleEndTime; }

    // ========================================
    // 📋 КВЕСТЫ ДНЯ
    // ========================================

    public void ensureDailyQuest() {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (today.equals(questDate) && questItemId != null) return;
        questDate = today;
        questProgress.clear();
        questCompleted.clear();
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        questItemId = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        questTarget = 16 + ThreadLocalRandom.current().nextInt(48);
        questType = ThreadLocalRandom.current().nextBoolean() ? "sell" : "buy";

        String name = plugin.getConfig().getString("items." + questItemId + ".name", questItemId);
        Bukkit.broadcastMessage(ChatColor.AQUA + "📋 [Квест Дня] " + ChatColor.YELLOW +
                (questType.equals("sell") ? "Продай" : "Купи") + " " + name + " x" + questTarget +
                ChatColor.AQUA + " → награда 1000 реп!");
        addHistory("📋 Квест: " + questType + " " + questItemId + " x" + questTarget);
    }

    public void recordQuestProgress(Player p, String itemId, int amount, String type) {
        if (!itemId.equals(questItemId) || !type.equals(questType)) return;
        if (questCompleted.contains(p.getName())) return;
        int current = questProgress.getOrDefault(p.getName(), 0) + amount;
        questProgress.put(p.getName(), current);
        if (current >= questTarget) {
            questCompleted.add(p.getName());
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                int reward = plugin.getConfig().getInt("market2.quest.reward", 1000);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
                p.sendMessage(ChatColor.GREEN + "📋 Квест выполнен! +" + reward + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Bukkit.broadcastMessage(ChatColor.AQUA + "📋 " + p.getName() + " выполнил квест дня!");
            }
        } else {
            p.sendMessage(ChatColor.GRAY + "📋 Квест: " + current + "/" + questTarget);
        }
    }

    public String getQuestInfo() {
        if (questItemId == null) return "Нет активного квеста";
        String name = plugin.getConfig().getString("items." + questItemId + ".name", questItemId);
        return (questType.equals("sell") ? "Продай" : "Купи") + " " + name + " x" + questTarget + " → 1000 реп";
    }

    public String getQuestItemId() { return questItemId; }
    public String getQuestType() { return questType; }
    public int getQuestTarget() { return questTarget; }
    public int getPlayerQuestProgress(String playerName) { return questProgress.getOrDefault(playerName, 0); }
    public boolean isQuestCompleted(String playerName) { return questCompleted.contains(playerName); }

    private void addHistory(String line) {
        plugin.getMarketManager().addHistory(line);
    }

    public void saveAll() {}
}
