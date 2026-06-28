package ru.example.vkchatmarket.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;

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
    // 🎰 РУЛЕТКА 35 ФИЧ
    // ========================================

    // [1] Кулдаун
    private final Map<String, Long> rouletteCooldown = new ConcurrentHashMap<>();
    // [2] Джекпот (растёт от покупок)
    private int jackpotPool = 5000;
    // [3] Стрики побед
    private final Map<String, Integer> winStreak = new ConcurrentHashMap<>();
    // [4] Стрики проигрышей (для pity)
    private final Map<String, Integer> loseStreak = new ConcurrentHashMap<>();
    // [5] История вращений
    private final Map<String, List<String>> spinHistory = new ConcurrentHashMap<>();
    // [6] Статистика
    private final Map<String, Integer> totalSpins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalWins = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepWon = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalRepLost = new ConcurrentHashMap<>();
    // [7] Бесплатный спин дня
    private final Set<String> freeSpinUsed = ConcurrentHashMap.newKeySet();
    // [8] Подарочные спины
    private final Map<String, Integer> giftedSpins = new ConcurrentHashMap<>();
    // [9] Текущий множитель от стрика
    private final Map<String, Double> streakMultiplier = new ConcurrentHashMap<>();
    // [10] Double or Nothing ожидание
    private final Map<String, Double> doubleOrNothing = new ConcurrentHashMap<>();
    // [11] Режим рулетки (normal/russian)
    private final Map<String, String> rouletteMode = new ConcurrentHashMap<>();
    // [12] Последний приз (для анимации)
    private final Map<String, String> lastPrize = new ConcurrentHashMap<>();
    // [13] Частицы на экране
    private final Map<String, Boolean> showParticles = new ConcurrentHashMap<>();
    // [14] Прогресс до гарантии (pity)
    private final Map<String, Integer> pityCounter = new ConcurrentHashMap<>();
    // [15] Достижения
    private final Map<String, Set<String>> achievements = new ConcurrentHashMap<>();
    // [16] Авто-спин
    private final Set<String> autoSpinEnabled = ConcurrentHashMap.newKeySet();

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
    // [17-21] АНИМАЦИЯ КРУТКИ
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

        // [7] Проверяем бесплатный спин
        boolean isFree = false;
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (!freeSpinUsed.contains(p.getName() + today)) {
            isFree = true;
            freeSpinUsed.add(p.getName() + today);
        }

        // [8] Проверяем подарочные спины
        boolean isGifted = false;
        int giftCount = giftedSpins.getOrDefault(p.getName(), 0);
        if (giftCount > 0) {
            isGifted = true;
            giftedSpins.put(p.getName(), giftCount - 1);
        }

        if (!isFree && !isGifted) {
            // [1] Кулдаун
            long cooldown = plugin.getConfig().getLong("market2.roulette.cooldown-ms", 300000);
            Long last = rouletteCooldown.get(p.getName());
            if (last != null && System.currentTimeMillis() - last < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "Рулетка перезаряжается! Подожди " + remaining + " сек.");
                return;
            }

            // Стоимость зависит от режима
            int cost = plugin.getConfig().getInt("market2.roulette.cost", 500);
            if (mode.equals("russian")) cost *= 3;

            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
                return;
            }

            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            rouletteCooldown.put(p.getName(), System.currentTimeMillis());

            // [2] Джекпот растёт
            jackpotPool += cost / 10;
        }

        // [11] Сохраняем режим
        rouletteMode.put(p.getName(), mode);

        // [15] Обновляем статистику
        totalSpins.merge(p.getName(), 1, Integer::sum);

        // [17-21] Анимация
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        if (mode.equals("russian")) {
            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_RED + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.RED + "║   ☠ РУССКАЯ РУЛЕТКА ☠        ║");
            p.sendMessage(ChatColor.RED + "║   Шанс выжить: 50%           ║");
            p.sendMessage(ChatColor.RED + "║   Награда: x3 от обычной!    ║");
            p.sendMessage(ChatColor.DARK_RED + "╚═══════════════════════════════╝");
            p.sendMessage("");
        } else {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════╗");
            p.sendMessage(ChatColor.YELLOW + "║   🎰 РУЛЕТКА КРУТИТСЯ...     ║");
            p.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════╝");
            p.sendMessage("");
        }

        // [14] Проверяем pity (гарантия после 20 проигрышей)
        int pity = pityCounter.getOrDefault(p.getName(), 0);

        // Анимация: 5 кадров
        for (int i = 0; i < 5; i++) {
            final int frame = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (frame < 4) {
                    String[] symbols = {"🎰", "💎", "🍀", "⭐", "🔥"};
                    p.sendMessage(ChatColor.GRAY + "  " + symbols[frame] + " " + symbols[(frame + 1) % 5] + " " + symbols[(frame + 2) % 5]);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + frame * 0.2f);
                }
            }, i * 8L);
        }

        // Финал через 1.5 сек
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RoulettePrize prize;
            String currentMode = rouletteMode.getOrDefault(p.getName(), "normal");

            // [14] Pity система
            if (pity >= 20) {
                prize = findRarePrize(currentMode);
                pityCounter.put(p.getName(), 0);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ PITY СРАБОТАЛ! Гарантированный редкий приз!");
            } else {
                prize = currentMode.equals("russian") ? rollRussianPrize() : rollNormalPrize();
            }

            // [3] Стрик множитель
            int streak = winStreak.getOrDefault(p.getName(), 0);
            double streakMult = 1.0 + (streak * 0.1); // +10% за каждую победу подряд
            streakMultiplier.put(p.getName(), streakMult);

            givePrize(p, prize, vkId);
        }, 45L);
    }

    // [22] Поиск редкого приза для pity
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
    // [23-28] ВЫДАЧА ПРИЗА
    // ========================================

    private void givePrize(Player p, RoulettePrize prize, int vkId) {
        String playerName = p.getName();
        double streakMult = streakMultiplier.getOrDefault(playerName, 1.0);

        // [23] Анимация финала
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD + "  ╔═══════════════════════╗");

        // [24] Цвет приза по тире
        ChatColor tierColor;
        switch (prize.tier) {
            case "legendary": tierColor = ChatColor.GOLD; break;
            case "jackpot": tierColor = ChatColor.LIGHT_PURPLE; break;
            case "rare": tierColor = ChatColor.AQUA; break;
            case "uncommon": tierColor = ChatColor.GREEN; break;
            case "death": tierColor = ChatColor.DARK_RED; break;
            default: tierColor = ChatColor.WHITE; break;
        }

        p.sendMessage(tierColor + "  ║  " + prize.name);
        p.sendMessage(ChatColor.GOLD + "  ╚═══════════════════════╝");
        p.sendMessage("");

        // [25] Звуки по тире
        switch (prize.tier) {
            case "legendary":
            case "jackpot":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
                // [26] Частицы
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 30);
                break;
            case "rare":
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 15);
                break;
            case "death":
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                p.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 20);
                break;
            default:
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                break;
        }

        // [3] Обновляем стрики
        if (prize.tier.equals("empty") || prize.tier.equals("death")) {
            winStreak.put(playerName, 0);
            loseStreak.merge(playerName, 1, Integer::sum);
            // [14] Pity
            pityCounter.merge(playerName, 1, Integer::sum);
        } else {
            winStreak.merge(playerName, 1, Integer::sum);
            loseStreak.put(playerName, 0);
            totalWins.merge(playerName, 1, Integer::sum);
            pityCounter.put(playerName, 0);
        }

        // Обработка приза
        if (prize.amount == -100) {
            // Специальный: потеря всего (русская рулетка)
            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            int loss = Math.min(currentRep, 500 + ThreadLocalRandom.current().nextInt(500));
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
            p.sendMessage(ChatColor.DARK_RED + "💀 Потеряно " + loss + " репутации!");
            totalRepLost.merge(playerName, loss, Integer::sum);
            broadcastLoss(p, loss);
            addHistory("💀 " + playerName + " проиграл " + loss + " реп в русской рулетке!");
            return;
        }

        if (prize.amount == -2) {
            // Джекпот x2
            int jackpot = (int) (jackpotPool * 2 * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆🏆 ДЖЕКПОТ x2! +" + jackpot + " репутации!");
            jackpotPool = 5000; // Сброс
            broadcastJackpot(p, jackpot);
            addHistory("🏆🏆 " + playerName + " сорвал ДЖЕКПОТ x2: " + jackpot + " реп!");
            totalRepWon.merge(playerName, jackpot, Integer::sum);
            checkAchievements(p, "jackpot");
            return;
        }

        if (prize.amount == -1) {
            // Обычный джекпот
            int jackpot = (int) (jackpotPool * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, jackpot);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ДЖЕКПОТ! +" + jackpot + " репутации!");
            jackpotPool = 5000;
            broadcastJackpot(p, jackpot);
            addHistory("🏆 " + playerName + " сорвал ДЖЕКПОТ: " + jackpot + " реп!");
            totalRepWon.merge(playerName, jackpot, Integer::sum);
            checkAchievements(p, "jackpot");
            return;
        }

        if (prize.material == null) {
            // Репутация
            int bonus = (int) (prize.amount * streakMult);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
            p.sendMessage(ChatColor.GREEN + "🪙 +" + bonus + " репутации!");
            totalRepWon.merge(playerName, bonus, Integer::sum);
            checkAchievements(p, "rep_" + bonus);
        } else if (prize.amount == 0) {
            // Пусто
            p.sendMessage(ChatColor.GRAY + "💀 Пусто! В следующий раз повезёт!");
            // [27] Double or Nothing
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                doubleOrNothing.put(playerName, 0.0);
                p.sendMessage(ChatColor.YELLOW + "⚡ Предложение: Double or Nothing? /m double");
            }
        } else {
            // Предмет
            Material mat;
            try { mat = Material.valueOf(prize.material); } catch (Exception e) {
                p.sendMessage(ChatColor.RED + "Ошибка приза!");
                return;
            }

            int amount = (int) (prize.amount * streakMult);
            if (p.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount)).isEmpty()) {
                p.sendMessage(ChatColor.GREEN + "🎉 " + prize.name + " x" + amount + "!");
                broadcastWin(p, prize.name + " x" + amount);
                addHistory("🎰 " + playerName + " выиграл: " + prize.name + " x" + amount);
                checkAchievements(p, "item_" + prize.material);
            } else {
                p.sendMessage(ChatColor.RED + "Инвентарь полон! Приз потерян...");
                // Компенсация реп
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 100);
                p.sendMessage(ChatColor.YELLOW + "Компенсация: +100 реп.");
            }
        }

        // [5] История
        addSpinHistory(playerName, prize.name);
    }

    // ========================================
    // [27] DOUBLE OR NOTHING
    // ========================================

    public void doubleOrNothing(Player p) {
        Double pending = doubleOrNothing.remove(p.getName());
        if (pending == null) {
            p.sendMessage(ChatColor.RED + "Нет активного предложения Double or Nothing!");
            return;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return;

        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        p.sendMessage(ChatColor.YELLOW + "⚡ Double or Nothing крутится...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean win = ThreadLocalRandom.current().nextBoolean();
            if (win) {
                int bonus = 200 + ThreadLocalRandom.current().nextInt(300);
                VKChatPlugin.getInstance().getApi().addReputation(vkId, bonus);
                p.sendMessage(ChatColor.GREEN + "🎉 DOUBLE! +" + bonus + " реп!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                totalRepWon.merge(p.getName(), bonus, Integer::sum);
            } else {
                int loss = 100 + ThreadLocalRandom.current().nextInt(200);
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, loss);
                p.sendMessage(ChatColor.RED + "💀 NOTHING! -" + loss + " реп!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                totalRepLost.merge(p.getName(), loss, Integer::sum);
            }
        }, 30L);
    }

    // ========================================
    // [8] ПОДАРОЧНЫЕ СПИНЫ
    // ========================================

    public void giftSpin(Player from, String toName) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(from);
        if (vkId == -1) return;

        int cost = plugin.getConfig().getInt("market2.roulette.gift-cost", 1000);
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            from.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. для подарка!");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        giftedSpins.merge(toName, 1, Integer::sum);
        from.sendMessage(ChatColor.GREEN + "🎁 Подарил спин " + toName + "!");
        addHistory("🎁 " + from.getName() + " подарил спин " + toName);

        Player to = Bukkit.getPlayer(toName);
        if (to != null && to.isOnline()) {
            to.sendMessage(ChatColor.GREEN + "🎁 " + from.getName() + " подарил тебе бесплатный спин! /m roulette");
        }
    }

    // ========================================
    // [11] РЕЖИМЫ
    // ========================================

    public void toggleMode(Player p) {
        String current = rouletteMode.getOrDefault(p.getName(), "normal");
        String newMode = current.equals("normal") ? "russian" : "normal";
        rouletteMode.put(p.getName(), newMode);
        if (newMode.equals("russian")) {
            p.sendMessage(ChatColor.RED + "☠ Режим: РУССКАЯ РУЛЕТКА (x3 цена, x3 награда!)");
        } else {
            p.sendMessage(ChatColor.GREEN + "🎰 Режим: Обычная рулетка");
        }
    }

    // ========================================
    // [16] АВТО-СПИН
    // ========================================

    public void toggleAutoSpin(Player p) {
        if (autoSpinEnabled.contains(p.getName())) {
            autoSpinEnabled.remove(p.getName());
            p.sendMessage(ChatColor.RED + "🎰 Авто-спин выключен");
        } else {
            autoSpinEnabled.add(p.getName());
            p.sendMessage(ChatColor.GREEN + "🎰 Авто-спин включён (крутится при /m roulette)");
        }
    }

    // ========================================
    // [28] СТАТИСТИКА И ДОСТИЖЕНИЯ
    // ========================================

    public String getStats(Player p) {
        String name = p.getName();
        int spins = totalSpins.getOrDefault(name, 0);
        int wins = totalWins.getOrDefault(name, 0);
        int repWon = totalRepWon.getOrDefault(name, 0);
        int repLost = totalRepLost.getOrDefault(name, 0);
        int streak = winStreak.getOrDefault(name, 0);
        int bestStreak = achievements.getOrDefault(name, Collections.emptySet()).contains("streak_5") ? 5 : 0;

        return ChatColor.GOLD + "═══ 🎰 Статистика ═══\n" +
               ChatColor.WHITE + "Вращений: " + ChatColor.YELLOW + spins + "\n" +
               ChatColor.WHITE + "Побед: " + ChatColor.GREEN + wins + "\n" +
               ChatColor.WHITE + "Выиграно: " + ChatColor.GREEN + "+" + repWon + " реп\n" +
               ChatColor.WHITE + "Проиграно: " + ChatColor.RED + "-" + repLost + " реп\n" +
               ChatColor.WHITE + "Текущий стрик: " + ChatColor.AQUA + streak + "\n" +
               ChatColor.WHITE + "Джекпот: " + ChatColor.LIGHT_PURPLE + jackpotPool + " реп";
    }

    // [28] Достижения
    private void checkAchievements(Player p, String type) {
        String name = p.getName();
        achievements.putIfAbsent(name, ConcurrentHashMap.newKeySet());
        Set<String> achs = achievements.get(name);

        int streak = winStreak.getOrDefault(name, 0);

        if (streak >= 3 && achs.add("streak_3")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: Стрик x3!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 200);
        }
        if (streak >= 5 && achs.add("streak_5")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: Стрик x5! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 500);
        }
        if (streak >= 10 && achs.add("streak_10")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: ЛЕГЕНДА СТРИКА x10! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " достиг стрика x10 в рулетке!");
        }
        if (type.equals("jackpot") && achs.add("jackpot")) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🏅 Достижение: Первый джекпот! +1000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 1000);
        }

        int spins = totalSpins.getOrDefault(name, 0);
        if (spins >= 10 && achs.add("spins_10")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: 10 вращений!");
        }
        if (spins >= 50 && achs.add("spins_50")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: 50 вращений! +500 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 500);
        }
        if (spins >= 100 && achs.add("spins_100")) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: 100 вращений! +2000 реп!");
            VKChatPlugin.getInstance().getApi().addReputation(VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 2000);
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + name + " сделал 100 вращений в рулетке!");
        }
    }

    // [5] История
    private void addSpinHistory(String playerName, String prize) {
        spinHistory.putIfAbsent(playerName, new ArrayList<>());
        List<String> hist = spinHistory.get(playerName);
        hist.add(prize);
        if (hist.size() > 20) hist.remove(0);
    }

    public List<String> getSpinHistory(String playerName) {
        return spinHistory.getOrDefault(playerName, Collections.emptyList());
    }

    // [29] Broadcast
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
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "💀 " + p.getName() + " проиграл " + amount + " реп в русской рулетке!");
    }

    // ========================================
    // [30-35] ДОПОЛНИТЕЛЬНЫЕ ФИЧИ
    // ========================================

    // [30] Лидерборд
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

    // [31] Текущий множитель стрика
    public double getStreakMultiplier(String playerName) {
        return streakMultiplier.getOrDefault(playerName, 1.0);
    }

    // [32] Джекпот
    public int getJackpotPool() { return jackpotPool; }

    // [33] Есть ли Double or Nothing
    public boolean hasDoubleOrNothing(String playerName) {
        return doubleOrNothing.containsKey(playerName);
    }

    // [34] Бесплатный спин доступен
    public boolean hasFreeSpin(String playerName) {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return !freeSpinUsed.contains(playerName + today);
    }

    // [35] Подарочные спины
    public int getGiftedSpins(String playerName) {
        return giftedSpins.getOrDefault(playerName, 0);
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

    public double getFlashSaleDiscount() {
        return System.currentTimeMillis() < flashSaleEndTime ? flashSaleDiscount : 0;
    }

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
                p.sendMessage(ChatColor.GREEN + "📋 Квест выполнен! +" + reward + " репутации!");
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

    private static class ItemStack extends org.bukkit.inventory.ItemStack {
        ItemStack(Material material, int amount) { super(material, amount); }
    }
}
