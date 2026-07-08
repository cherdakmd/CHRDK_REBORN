package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [8] Ежедневные/недельные испытания
 */
public class ChallengeManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Map<String, Integer>> dailyProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> dailyCompleted = new ConcurrentHashMap<>();
    private String currentDailyChallenge = "";
    private String currentWeeklyChallenge = "";

    // Типы испытаний
    private static final String[] DAILY_TYPES = {
        "mine_stone", "kill_mobs", "craft_items", "break_blocks", "eat_food"
    };

    public ChallengeManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        generateDailyChallenge();
    }

    private void generateDailyChallenge() {
        currentDailyChallenge = DAILY_TYPES[ThreadLocalRandom.current().nextInt(DAILY_TYPES.length)];
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (currentDailyChallenge.equals("mine_stone") || currentDailyChallenge.equals("break_blocks")) {
            Map<String, Integer> progress = dailyProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            int count = progress.getOrDefault(currentDailyChallenge, 0) + 1;
            progress.put(currentDailyChallenge, count);

            int target = currentDailyChallenge.equals("mine_stone") ? 64 : 128;
            if (count >= target && !dailyCompleted.getOrDefault(uuid, Collections.emptySet()).contains(currentDailyChallenge)) {
                dailyCompleted.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(currentDailyChallenge);
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatPlugin.getInstance().getApi().addReputation(vkId, 300);
                    p.sendMessage("§a🎯 Испытание выполнено! +300 реп!");
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        UUID uuid = p.getUniqueId();

        if (currentDailyChallenge.equals("kill_mobs")) {
            Map<String, Integer> progress = dailyProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            int count = progress.getOrDefault("kill_mobs", 0) + 1;
            progress.put("kill_mobs", count);

            if (count >= 50 && !dailyCompleted.getOrDefault(uuid, Collections.emptySet()).contains("kill_mobs")) {
                dailyCompleted.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add("kill_mobs");
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatPlugin.getInstance().getApi().addReputation(vkId, 300);
                    p.sendMessage("§a🎯 Испытание выполнено! +300 реп!");
                }
            }
        }
    }

    public String getCurrentDailyChallenge() { return currentDailyChallenge; }
    public String getCurrentWeeklyChallenge() { return currentWeeklyChallenge; }
}
