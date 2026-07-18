package ru.example.vkchatevents.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [21-25] Выживание, PvP, PvE, Командная работа, Саботаж
 */
public class CombatManager implements Listener {
    private final VKChatEventsPlugin plugin;

    // PvP статистика
    private final Map<UUID, Integer> pvpKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pvpDeaths = new ConcurrentHashMap<>();
    // PvE статистика
    private final Map<UUID, Integer> pveKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pveDeaths = new ConcurrentHashMap<>();
    // Командная работа
    private final Map<UUID, Integer> teamKills = new ConcurrentHashMap<>();
    // Саботаж
    private final Map<UUID, Integer> sabotageCount = new ConcurrentHashMap<>();

    public CombatManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && killer != victim) {
            // PvP
            pvpKills.merge(killer.getUniqueId(), 1, Integer::sum);
            pvpDeaths.merge(victim.getUniqueId(), 1, Integer::sum);
            checkPvPAchievement(killer.getUniqueId());
        } else {
            // PvE
            pveDeaths.merge(victim.getUniqueId(), 1, Integer::sum);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
            // PvP урон
        }
    }

    private void checkPvPAchievement(UUID uuid) {
        int kills = pvpKills.getOrDefault(uuid, 0);
        if (kills >= 100) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                VKChatBridge.addPoints(
                        VKChatBridge.getLinkedVkId(p), 1000);
            }
        }
    }

    public String getPvPStats(UUID uuid) {
        return "⚔ PvP:\n" +
                "• Убийств: " + pvpKills.getOrDefault(uuid, 0) + "\n" +
                "• Смертей: " + pvpDeaths.getOrDefault(uuid, 0);
    }
}
