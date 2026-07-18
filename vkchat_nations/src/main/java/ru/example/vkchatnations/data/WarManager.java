package ru.example.vkchatnations.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.example.vkchatnations.VKChatNationsPlugin;

import ru.example.vkchat.util.VKChatBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarManager {
    private final VKChatNationsPlugin plugin;
    
    // Active wars: nation pair (sorted) -> end timestamp
    private final Map<String, Long> activeWars = new ConcurrentHashMap<>();
    
    // Cooldowns: playerUUID -> last action timestamp
    private final Map<UUID, Long> declareCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> peaceCooldowns = new ConcurrentHashMap<>();

    public WarManager(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private String getWarKey(String nation1, String nation2) {
        String a = nation1.compareTo(nation2) < 0 ? nation1 : nation2;
        String b = nation1.compareTo(nation2) < 0 ? nation2 : nation1;
        return a + ":" + b;
    }

    private String[] parseWarKey(String key) {
        return key.split(":");
    }

    public boolean declareWar(Player declarer, String attackerNation, String defenderNation) {
        if (!isWarEnabled()) {
            declarer.sendMessage(ChatColor.RED + "Система войн отключена в конфиге!");
            return false;
        }

        if (attackerNation.equals(defenderNation)) {
            declarer.sendMessage(ChatColor.RED + "Нельзя объявить войну самому себе!");
            return false;
        }

        String warKey = getWarKey(attackerNation, defenderNation);
        if (activeWars.containsKey(warKey)) {
            declarer.sendMessage(ChatColor.RED + "Между этими нациями уже идёт война!");
            return false;
        }

        // Check cooldown
        long lastDeclare = declareCooldowns.getOrDefault(declarer.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastDeclare < 60000) {
            long left = (60000 - (System.currentTimeMillis() - lastDeclare)) / 1000;
            declarer.sendMessage(ChatColor.RED + "Подождите " + left + " сек. перед следующим объявлением войны!");
            return false;
        }

        // Check reputation cost
        int vkId = VKChatBridge.getLinkedVkId(declarer);
        if (!VKChatBridge.hasVkOrPass(declarer)) {
            declarer.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink)!");
            return false;
        }

        int cost = plugin.getConfig().getInt("war.declare-cost", 5000);
        int rep = VKChatBridge.getReputation(vkId);
        if (rep < cost) {
            declarer.sendMessage(ChatColor.RED + "Недостаточно репутации! Требуется: " + cost + " (у вас: " + rep + ")");
            return false;
        }

        // Take reputation
        VKChatBridge.takeReputation(vkId, cost);

        // Create war
        int durationMinutes = plugin.getConfig().getInt("war.duration-minutes", 60);
        long endTime = System.currentTimeMillis() + (durationMinutes * 60L * 1000L);
        activeWars.put(warKey, endTime);
        declareCooldowns.put(declarer.getUniqueId(), System.currentTimeMillis());

        // Broadcast to both nations
        String attackerName = plugin.getNationManager().getNationNamePublic(attackerNation);
        String defenderName = plugin.getNationManager().getNationNamePublic(defenderNation);

        String warMessage = ChatColor.RED + "⚔ ВОЙНА ОБЪЯВЛЕНА! " + ChatColor.GOLD + attackerName +
                ChatColor.RED + " vs " + ChatColor.GOLD + defenderName +
                ChatColor.GRAY + " (на " + durationMinutes + " мин.)";

        broadcastToNation(attackerNation, warMessage);
        broadcastToNation(defenderNation, warMessage);

        declarer.sendMessage(ChatColor.GREEN + "✓ Война объявлена! Списано " + cost + " реп. ВК");
        declarer.playSound(declarer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);

        return true;
    }

    public boolean sueForPeace(Player requester, String nation) {
        if (!isWarEnabled()) {
            requester.sendMessage(ChatColor.RED + "Система войн отключена в конфиге!");
            return false;
        }

        // Check cooldown
        long lastPeace = peaceCooldowns.getOrDefault(requester.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastPeace < 30000) {
            long left = (30000 - (System.currentTimeMillis() - lastPeace)) / 1000;
            requester.sendMessage(ChatColor.RED + "Подождите " + left + " сек. перед следующим предложением мира!");
            return false;
        }

        // Check reputation cost
        int vkId = VKChatBridge.getLinkedVkId(requester);
        if (!VKChatBridge.hasVkOrPass(requester)) {
            requester.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink)!");
            return false;
        }

        int cost = plugin.getConfig().getInt("war.peace-cost", 2000);
        int rep = VKChatBridge.getReputation(vkId);
        if (rep < cost) {
            requester.sendMessage(ChatColor.RED + "Недостаточно репутации! Требуется: " + cost + " (у вас: " + rep + ")");
            return false;
        }

        // Find and end all wars involving this nation
        List<String> endedWars = new ArrayList<>();
        for (Map.Entry<String, Long> entry : activeWars.entrySet()) {
            String[] nations = parseWarKey(entry.getKey());
            if (nations[0].equals(nation) || nations[1].equals(nation)) {
                endedWars.add(entry.getKey());
            }
        }

        if (endedWars.isEmpty()) {
            requester.sendMessage(ChatColor.YELLOW + "Ваша нация не находится в состоянии войны.");
            return false;
        }

        // Take reputation
        VKChatBridge.takeReputation(vkId, cost);

        // End wars
        for (String warKey : endedWars) {
            activeWars.remove(warKey);
            String[] nations = parseWarKey(warKey);
            String otherNation = nations[0].equals(nation) ? nations[1] : nations[0];

            String peaceMessage = ChatColor.GREEN + "☮ МИР УСТАНОВЛЕН! " +
                    ChatColor.GOLD + plugin.getNationManager().getNationNamePublic(nation) +
                    ChatColor.GREEN + " и " +
                    ChatColor.GOLD + plugin.getNationManager().getNationNamePublic(otherNation) +
                    ChatColor.GREEN + " заключили мир.";

            broadcastToNation(nation, peaceMessage);
            broadcastToNation(otherNation, peaceMessage);
        }

        peaceCooldowns.put(requester.getUniqueId(), System.currentTimeMillis());
        requester.sendMessage(ChatColor.GREEN + "✓ Мир заключён! Списано " + cost + " реп. ВК");

        return true;
    }

    public boolean areAtWar(String nation1, String nation2) {
        if (nation1 == null || nation2 == null) return false;
        String warKey = getWarKey(nation1, nation2);
        Long endTime = activeWars.get(warKey);
        if (endTime == null) return false;
        
        // Check if war expired
        if (System.currentTimeMillis() > endTime) {
            activeWars.remove(warKey);
            String[] nations = parseWarKey(warKey);
            String expireMessage = ChatColor.YELLOW + "⚔ Война между " +
                    plugin.getNationManager().getNationNamePublic(nations[0]) +
                    " и " + plugin.getNationManager().getNationNamePublic(nations[1]) +
                    " окончилась по времени.";
            broadcastToNation(nations[0], expireMessage);
            broadcastToNation(nations[1], expireMessage);
            return false;
        }
        return true;
    }

    public long getWarEndTime(String nation1, String nation2) {
        String warKey = getWarKey(nation1, nation2);
        Long endTime = activeWars.get(warKey);
        if (endTime == null) return -1;
        if (System.currentTimeMillis() > endTime) {
            activeWars.remove(warKey);
            return -1;
        }
        return endTime;
    }

    public List<String[]> getActiveWarsFor(String nation) {
        List<String[]> wars = new ArrayList<>();
        for (Map.Entry<String, Long> entry : activeWars.entrySet()) {
            String[] nations = parseWarKey(entry.getKey());
            if (nations[0].equals(nation) || nations[1].equals(nation)) {
                if (System.currentTimeMillis() <= entry.getValue()) {
                    wars.add(nations);
                }
            }
        }
        return wars;
    }

    public void showWarStatus(Player player, String nation) {
        List<String[]> wars = getActiveWarsFor(nation);
        
        player.sendMessage(ChatColor.GOLD + "=== Текущие войны ===");
        
        if (wars.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Нет активных войн.");
            return;
        }

        for (String[] warNations : wars) {
            String enemyNation = warNations[0].equals(nation) ? warNations[1] : warNations[0];
            String enemyName = plugin.getNationManager().getNationNamePublic(enemyNation);
            long endTime = getWarEndTime(warNations[0], warNations[1]);
            long remainingMinutes = (endTime - System.currentTimeMillis()) / 60000;
            
            player.sendMessage(ChatColor.RED + "⚔ " + ChatColor.GOLD + enemyName + 
                    ChatColor.GRAY + " (осталось " + remainingMinutes + " мин.)");
        }
    }

    public void showAllWars(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Все активные войны ===");
        
        boolean hasWars = false;
        for (Map.Entry<String, Long> entry : activeWars.entrySet()) {
            if (System.currentTimeMillis() > entry.getValue()) continue;
            
            String[] nations = parseWarKey(entry.getKey());
            String name1 = plugin.getNationManager().getNationNamePublic(nations[0]);
            String name2 = plugin.getNationManager().getNationNamePublic(nations[1]);
            long remainingMinutes = (entry.getValue() - System.currentTimeMillis()) / 60000;
            
            player.sendMessage(ChatColor.RED + "⚔ " + ChatColor.GOLD + name1 + 
                    ChatColor.RED + " vs " + ChatColor.GOLD + name2 +
                    ChatColor.GRAY + " (" + remainingMinutes + " мин.)");
            hasWars = true;
        }
        
        if (!hasWars) {
            player.sendMessage(ChatColor.GRAY + "Нет активных войн.");
        }
    }

    private boolean isWarEnabled() {
        return plugin.getConfig().getBoolean("war.enabled", true);
    }

    private void broadcastToNation(String nationId, String message) {
        if (nationId == null) return;
        for (Map.Entry<UUID, String> entry : plugin.getNationManager().getPlayerNations().entrySet()) {
            if (nationId.equals(entry.getValue())) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }
    }

    private void startCleanupTask() {
        // Clean up expired wars every 2 minutes
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<String, Long>> it = activeWars.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                if (System.currentTimeMillis() > entry.getValue()) {
                    String[] nations = parseWarKey(entry.getKey());
                    String expireMessage = ChatColor.YELLOW + "⚔ Война между " +
                            plugin.getNationManager().getNationNamePublic(nations[0]) +
                            " и " + plugin.getNationManager().getNationNamePublic(nations[1]) +
                            " окончилась по времени.";
                    broadcastToNation(nations[0], expireMessage);
                    broadcastToNation(nations[1], expireMessage);
                    it.remove();
                }
            }
        }, 2400L, 2400L); // Every 2 minutes (2400 ticks)
    }
}
