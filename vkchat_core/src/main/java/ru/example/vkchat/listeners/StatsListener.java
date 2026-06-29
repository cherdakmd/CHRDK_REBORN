package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsListener implements Listener {
    private final VKChatPlugin plugin;

    // Антифарм: кулдаун на пару убийца-жертва
    private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();
    // Серии убийств
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    // Серии смертей
    private final Map<UUID, Integer> deathStreaks = new ConcurrentHashMap<>();
    // Кулдаун VK-сообщений для PvP
    private long lastPvpVkMessage = 0L;
    private static final long PVP_VK_MSG_COOLDOWN_MS = 10000L; // 10 секунд между PvP VK-сообщениями

    public StatsListener(VKChatPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * [FIX] Периодическая очистка неактивных данных
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupInactiveData();
        }, 12000L, 12000L); // Каждые 10 минут
    }

    private void cleanupInactiveData() {
        long now = System.currentTimeMillis();
        long maxInactive = 3600000; // 1 час

        // Очищаем killCooldowns старше 1 часа
        killCooldowns.entrySet().removeIf(e -> now - e.getValue() > maxInactive);

        // Очищаем killStreaks для оффлайн игроков
        killStreaks.entrySet().removeIf(e -> {
            Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });

        // Очищаем deathStreaks для оффлайн игроков
        deathStreaks.entrySet().removeIf(e -> {
            Player p = plugin.getServer().getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getStatsManager().addJoin();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        plugin.getStatsManager().addBlockBreak(e.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.getStatsManager().addDeath(victim);

        Player killer = victim.getKiller();
        int victimVkId = plugin.getAuthManager().getLinkedVkId(victim.getUniqueId());

        if (killer != null && killer != victim) {
            plugin.getStatsManager().addKill(killer);
            int killerVkId = plugin.getAuthManager().getLinkedVkId(killer.getUniqueId());

            handlePvpKill(killer, victim, killerVkId, victimVkId, e);
        } else {
            handlePveDeath(victim, victimVkId, e);
        }
    }

    private void handlePvpKill(Player killer, Player victim, int killerVkId, int victimVkId, PlayerDeathEvent e) {
        // ===== АНТИФАРМ =====
        String pairKey = killer.getUniqueId().toString().compareTo(victim.getUniqueId().toString()) < 0
                ? killer.getUniqueId() + ":" + victim.getUniqueId()
                : victim.getUniqueId() + ":" + killer.getUniqueId();

        int cooldownMinutes = plugin.getConfig().getInt("reputation.pvp.cooldown-minutes", 5);
        long cooldownMs = cooldownMinutes * 60000L;
        long now = System.currentTimeMillis();

        if (killCooldowns.containsKey(pairKey) && now - killCooldowns.get(pairKey) < cooldownMs) {
            long remaining = (cooldownMs - (now - killCooldowns.get(pairKey))) / 1000;
            killer.sendMessage(ChatColor.GRAY + "⏳ Антифарм: нельзя получать репутацию за этого игрока ещё " + remaining + " сек.");
            sendDeathMessage(victim, killer, e, "");
            return;
        }

        // ===== ЗАЩИТА НОВИЧКОВ =====
        int minRepToSteal = plugin.getConfig().getInt("reputation.pvp.min-victim-rep", 50);
        if (victimVkId == -1 || plugin.getReputationManager().getPoints(victimVkId) < minRepToSteal) {
            killer.sendMessage(ChatColor.GRAY + "🛡 У жертвы слишком мало репутации для кражи.");
            sendDeathMessage(victim, killer, e, "");
            return;
        }

        // ===== РАСЧЁТ ПОТЕРИ =====
        int victimRep = plugin.getReputationManager().getPoints(victimVkId);
        double lossPercent = plugin.getConfig().getDouble("reputation.pvp.loss-percent", 5.0);
        int minLoss = plugin.getConfig().getInt("reputation.pvp.min-loss", 10);
        int maxLoss = plugin.getConfig().getInt("reputation.pvp.max-loss", 500);

        int actualLoss = (int) Math.ceil(victimRep * (lossPercent / 100.0));
        actualLoss = Math.max(minLoss, Math.min(maxLoss, actualLoss));
        actualLoss = Math.min(victimRep, actualLoss); // Не больше чем есть

        // ===== МАСШТАБИРОВАНИЕ ПО РАЗНИЦЕ РЕПУТАЦИИ =====
        double scalingEnabled = plugin.getConfig().getDouble("reputation.pvp.rep-diff-scaling", 0.0);
        if (scalingEnabled > 0 && killerVkId != -1) {
            int killerRep = plugin.getReputationManager().getPoints(killerVkId);
            double diff = Math.abs(killerRep - victimRep);
            double threshold = plugin.getConfig().getDouble("reputation.pvp.rep-diff-threshold", 500.0);

            if (killerRep > victimRep + threshold) {
                // Убийца намного сильнее — уменьшаем награду
                double reduction = Math.min(0.8, (diff - threshold) / 1000.0 * scalingEnabled);
                actualLoss = (int) Math.max(minLoss, actualLoss * (1.0 - reduction));
                killer.sendMessage(ChatColor.GRAY + "⚔ Жертва слабее тебя — награда уменьшена.");
            } else if (victimRep > killerRep + threshold) {
                // Жертва намного сильнее — увеличиваем награду
                double bonus = Math.min(1.0, (diff - threshold) / 500.0 * scalingEnabled);
                actualLoss = (int) Math.min(maxLoss, actualLoss * (1.0 + bonus));
                killer.sendMessage(ChatColor.GOLD + "⚔ Жертва сильнее тебя — бонусная награда!");
            }
        }

        // ===== ЗАЩИТА ОТ СМЕРТЕЙ ПОДРЯД (DEATH STREAK) =====
        UUID victimUuid = victim.getUniqueId();
        int deathStreak = deathStreaks.getOrDefault(victimUuid, 0) + 1;
        deathStreaks.put(victimUuid, deathStreak);

        int deathStreakThreshold = plugin.getConfig().getInt("reputation.pvp.death-streak.threshold", 3);
        double deathStreakReduction = plugin.getConfig().getDouble("reputation.pvp.death-streak.loss-reduction", 0.5);

        if (deathStreak >= deathStreakThreshold) {
            actualLoss = (int) Math.max(minLoss, actualLoss * (1.0 - deathStreakReduction));
            victim.sendMessage(ChatColor.YELLOW + "💀 Серия смертей x" + deathStreak + "! Потеря репутации снижена.");
        }

        // Сброс серии смертей убийцы
        deathStreaks.put(killer.getUniqueId(), 0);

        // ===== СЕРИЯ УБИЙСТВ (KILL STREAK) =====
        UUID killerUuid = killer.getUniqueId();
        int killStreak = killStreaks.getOrDefault(killerUuid, 0) + 1;
        killStreaks.put(killerUuid, killStreak);

        int streakBonusThreshold = plugin.getConfig().getInt("reputation.pvp.killstreak.bonus-threshold", 3);
        double streakBonusPercent = plugin.getConfig().getDouble("reputation.pvp.killstreak.bonus-percent", 10.0);

        int bonusRep = 0;
        if (killStreak >= streakBonusThreshold) {
            bonusRep = (int) Math.ceil(actualLoss * (streakBonusPercent / 100.0) * (killStreak - streakBonusThreshold + 1));
            bonusRep = Math.min(plugin.getConfig().getInt("reputation.pvp.killstreak.max-bonus", 100), bonusRep);
        }

        // ===== ПРИМЕНЕНИЕ РЕПУТАЦИИ =====
        int totalGain = actualLoss + bonusRep;

        plugin.getReputationManager().deductPoints(victimVkId, actualLoss);
        if (killerVkId != -1) {
            plugin.getReputationManager().addPoints(killerVkId, totalGain);
        }

        // Запоминаем кулдаун
        killCooldowns.put(pairKey, now);

        // ===== СООБЩЕНИЯ =====
        String repMsg;
        if (killerVkId != -1) {
            repMsg = ChatColor.RED + " -" + actualLoss + " реп" + ChatColor.GRAY + " → " + ChatColor.GREEN + killer.getName() + " +" + totalGain + " реп";
        } else {
            repMsg = ChatColor.RED + " -" + actualLoss + " реп (убийца не привязан к ВК)";
        }

        if (killStreak >= streakBonusThreshold) {
            repMsg += ChatColor.GOLD + " [Серия x" + killStreak + " +" + bonusRep + "]";
        }

        killer.sendMessage(ChatColor.GREEN + "⚔ Убийство: " + victim.getName() + "! " + repMsg);
        victim.sendMessage(ChatColor.RED + "☠ Вас убил " + killer.getName() + "! " + repMsg);

        // Звуки
        try {
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.8f);
        } catch (Exception ignored) {}

        sendDeathMessage(victim, killer, e, repMsg);
    }

    private void handlePveDeath(Player victim, int victimVkId, PlayerDeathEvent e) {
        UUID victimUuid = victim.getUniqueId();

        // Сброс серии убийств при PvE смерти
        killStreaks.put(victimUuid, 0);

        int deathStreak = deathStreaks.getOrDefault(victimUuid, 0) + 1;
        deathStreaks.put(victimUuid, deathStreak);

        String repMsg = "";
        if (victimVkId != -1) {
            int currentRep = plugin.getReputationManager().getPoints(victimVkId);
            double lossPercent = plugin.getConfig().getDouble("reputation.pve.loss-percent", 2.0);
            int minLoss = plugin.getConfig().getInt("reputation.pve.min-loss", 5);
            int maxLoss = plugin.getConfig().getInt("reputation.pve.max-loss", 100);

            int actualLoss = (int) Math.ceil(currentRep * (lossPercent / 100.0));
            actualLoss = Math.max(minLoss, Math.min(maxLoss, actualLoss));
            actualLoss = Math.min(currentRep, actualLoss);

            // Защита от серий смертей
            int deathStreakThreshold = plugin.getConfig().getInt("reputation.pvp.death-streak.threshold", 3);
            double deathStreakReduction = plugin.getConfig().getDouble("reputation.pvp.death-streak.loss-reduction", 0.5);

            if (deathStreak >= deathStreakThreshold) {
                actualLoss = (int) Math.max(minLoss, actualLoss * (1.0 - deathStreakReduction));
                victim.sendMessage(ChatColor.YELLOW + "💀 Серия смертей x" + deathStreak + "! Потеря репутации снижена.");
            }

            if (actualLoss > 0) {
                plugin.getReputationManager().deductPoints(victimVkId, actualLoss);
                repMsg = " \n " + ChatColor.RED + "Потеряно " + actualLoss + " репутации (" + lossPercent + "%)";
            }
        }

        sendDeathMessage(victim, null, e, repMsg);
    }

    private void sendDeathMessage(Player victim, Player killer, PlayerDeathEvent e, String repMsg) {
        String cleanMsg = org.bukkit.ChatColor.stripColor(e.getDeathMessage());
        if (cleanMsg == null) return;

        cleanMsg = cleanMsg.replaceAll("(?i)was slain by", "был убит");
        cleanMsg = cleanMsg.replaceAll("(?i)using", "используя");
        cleanMsg = cleanMsg.replaceAll("(?i)fell from a high place", "упал с высокого места");
        cleanMsg = cleanMsg.replaceAll("(?i)burned to death", "сгорел заживо");
        cleanMsg = cleanMsg.replaceAll("(?i)tried to swim in lava", "решил поплавать в лаве");
        cleanMsg = cleanMsg.replaceAll("(?i)drowned", "утонул");
        cleanMsg = cleanMsg.replaceAll("(?i)blew up", "взорвался");
        cleanMsg = cleanMsg.replaceAll("(?i)was blown up by", "был взорван");
        cleanMsg = cleanMsg.replaceAll("(?i)starved to death", "умер от голода");
        cleanMsg = cleanMsg.replaceAll("(?i)withered away", "иссох");
        cleanMsg = cleanMsg.replaceAll("§[0-9a-fk-or]", "");

        String msg = " " + cleanMsg + repMsg;
        plugin.getVkManager().sendToMainChat(msg);
    }
}
