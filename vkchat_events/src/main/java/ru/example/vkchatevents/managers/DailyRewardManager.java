package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [7] Ежедневные награды за вход
 */
public class DailyRewardManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, String> lastDailyClaim = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dailyStreak = new ConcurrentHashMap<>();

    public DailyRewardManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        String last = lastDailyClaim.get(uuid);

        if (last != null && last.equals(today)) return; // Уже получил сегодня

        int streak = dailyStreak.getOrDefault(uuid, 0);
        String yesterday = new java.text.SimpleDateFormat("yyyyMMdd").format(
                new Date(System.currentTimeMillis() - 86400000L));

        if (last != null && last.equals(yesterday)) {
            streak++;
        } else if (last != null) {
            streak = 1; // Сброс стрика
        }

        dailyStreak.put(uuid, streak);
        lastDailyClaim.put(uuid, today);

        // Награда зависит от стрика
        int baseReward = plugin.getConfig().getInt("daily-rewards.base", 50);
        int streakBonus = plugin.getConfig().getInt("daily-rewards.streak-bonus", 10);
        int reward = baseReward + (streak * streakBonus);
        int maxReward = plugin.getConfig().getInt("daily-rewards.max", 500);
        reward = Math.min(reward, maxReward);

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
            p.sendMessage("§a🎁 Ежедневная награда: §e+" + reward + " реп.§7 (Серия: " + streak + " дней)");
            if (streak >= 7) {
                p.sendMessage("§6🔥 Бонус за серию 7 дней! +200 реп бонус!");
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 200);
            }
        }
    }

    public int getDailyStreak(UUID uuid) {
        return dailyStreak.getOrDefault(uuid, 0);
    }
}
