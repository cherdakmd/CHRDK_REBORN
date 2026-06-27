package ru.example.vkchat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchat.VKChatPlugin;

public class StatsListener implements Listener {
    private final VKChatPlugin plugin;

    public StatsListener(VKChatPlugin plugin) {
        this.plugin = plugin;
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
        
        if (killer != null) {
            plugin.getStatsManager().addKill(killer);
            int killerVkId = plugin.getAuthManager().getLinkedVkId(killer.getUniqueId());
            
            boolean steal = plugin.getConfig().getBoolean("reputation.pvp-steal", true);
            
            String repMsg = "";
            if (victimVkId != -1) {
                int currentRep = plugin.getReputationManager().getPoints(victimVkId);
                // Штраф в PvP: 5% от баланса (минимум 10)
                int actualLoss = (int) Math.ceil(currentRep * 0.05);
                if (actualLoss < 10) actualLoss = 10;
                actualLoss = Math.min(currentRep, actualLoss);

                if (actualLoss > 0) {
                    plugin.getReputationManager().deductPoints(victimVkId, actualLoss);
                    if (steal && killerVkId != -1) {
                        plugin.getReputationManager().addPoints(killerVkId, actualLoss);
                        repMsg = " \n Убийца украл " + actualLoss + " репутации (5% от баланса жертвы)!";
                    } else {
                        repMsg = " \n Потеряно " + actualLoss + " репутации (5% от баланса)!";
                    }
                }
            }
            
            String msg = "⚔ Игрок " + killer.getName() + " безжалостно убил " + victim.getName() + "!" + repMsg;
            plugin.getVkManager().sendToMainChat(msg);
        } else {
            String repMsg = "";
            if (victimVkId != -1) {
                int currentRep = plugin.getReputationManager().getPoints(victimVkId);
                // Штраф в PvE: 2% от баланса (минимум 5)
                int actualLoss = (int) Math.ceil(currentRep * 0.02);
                if (actualLoss < 5) actualLoss = 5;
                actualLoss = Math.min(currentRep, actualLoss);

                if (actualLoss > 0) {
                    plugin.getReputationManager().deductPoints(victimVkId, actualLoss);
                    repMsg = " \n Потеряно " + actualLoss + " репутации (2% от баланса)!";
                }
            }

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
}