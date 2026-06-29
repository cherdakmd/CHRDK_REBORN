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
 * [7] Ежедневные награды — только через команду !бонус
 */
public class DailyRewardManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Long> lastDailyClaim = new ConcurrentHashMap<>();

    public DailyRewardManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить ежедневную награду (вызывается из команды)
     */
    public String claimDailyReward(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastDailyClaim.get(uuid);

        if (last != null && now - last < 86400000L) {
            long remaining = (86400000L - (now - last)) / 1000 / 60;
            return "§c⏳ Ты уже получил награду сегодня! Подожди " + remaining + " минут.";
        }

        lastDailyClaim.put(uuid, now);

        int baseReward = plugin.getConfig().getInt("daily-rewards.base", 50);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return "§c❌ Привяжи ВК!";

        VKChatPlugin.getInstance().getApi().addReputation(vkId, baseReward);
        return "§a🎁 Ежедневная награда: §e+" + baseReward + " реп!";
    }

    public boolean canClaim(UUID uuid) {
        Long last = lastDailyClaim.get(uuid);
        return last == null || System.currentTimeMillis() - last >= 86400000L;
    }
}
