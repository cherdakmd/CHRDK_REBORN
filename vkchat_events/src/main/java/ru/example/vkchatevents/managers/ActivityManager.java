package ru.example.vkchatevents.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [15-20] Предсказания, Торговля, Крафт, Добыча, Рыбалка, Строительство
 * Объединены в один менеджер для экономии ресурсов
 */
public class ActivityManager implements Listener {
    private final VKChatEventsPlugin plugin;

    // Предсказания
    private final Map<UUID, String> predictions = new ConcurrentHashMap<>();
    // Торговля
    private final Map<UUID, Integer> tradeCount = new ConcurrentHashMap<>();
    // Крафт
    private final Map<UUID, Integer> craftCount = new ConcurrentHashMap<>();
    // Добыча
    private final Map<UUID, Integer> mineCount = new ConcurrentHashMap<>();
    // Рыбалка
    private final Map<UUID, Integer> fishCount = new ConcurrentHashMap<>();
    // Строительство
    private final Map<UUID, Integer> buildCount = new ConcurrentHashMap<>();

    private static final String[] PREDICTIONS = {
        "Удача будет благоволить тебе сегодня!",
        "Остерегайся зомби в темноте...",
        "Сегодня найдёшь алмаз!",
        "Лучше не ходи в шахту один.",
        "Тебя ждёт неожиданная встреча!",
        "Сегодня удачный день для торговли!",
        "Береги свои ресурсы...",
        "Впереди тебя ждёт сокровище!",
        "Не бери лишнего в поход.",
        "Сегодня день для подвигов!"
    };

    public ActivityManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    // Предсказание
    public String getPrediction(UUID uuid) {
        String pred = predictions.get(uuid);
        if (pred == null || !pred.equals(getToday())) {
            pred = PREDICTIONS[ThreadLocalRandom.current().nextInt(PREDICTIONS.length)];
            predictions.put(uuid, pred);
        }
        return pred;
    }

    private String getToday() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    // Торговля
    public void recordTrade(UUID uuid) {
        tradeCount.merge(uuid, 1, Integer::sum);
        checkTradeAchievement(uuid);
    }

    private void checkTradeAchievement(UUID uuid) {
        int count = tradeCount.getOrDefault(uuid, 0);
        if (count >= 100) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                VKChatBridge.addPoints(
                        VKChatBridge.getLinkedVkId(p), 500);
            }
        }
    }

    // Крафт
    public void recordCraft(UUID uuid) {
        craftCount.merge(uuid, 1, Integer::sum);
    }

    // Добыча
    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        mineCount.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    // Рыбалка
    public void recordFish(UUID uuid) {
        fishCount.merge(uuid, 1, Integer::sum);
    }

    // Строительство
    public void recordBuild(UUID uuid) {
        buildCount.merge(uuid, 1, Integer::sum);
    }

    // Статистика
    public String getStats(UUID uuid) {
        return "📊 Активность:\n" +
                "• Торговля: " + tradeCount.getOrDefault(uuid, 0) + "\n" +
                "• Крафт: " + craftCount.getOrDefault(uuid, 0) + "\n" +
                "• Добыча: " + mineCount.getOrDefault(uuid, 0) + "\n" +
                "• Рыбалка: " + fishCount.getOrDefault(uuid, 0) + "\n" +
                "• Строительство: " + buildCount.getOrDefault(uuid, 0);
    }
}
