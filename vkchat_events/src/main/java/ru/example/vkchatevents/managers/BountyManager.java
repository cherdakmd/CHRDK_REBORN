package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BountyManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Integer> bounties = new HashMap<>();

    public BountyManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Integer> getBounties() {
        return bounties;
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        String msg = e.getMessage();
        if (msg.toLowerCase().startsWith("!заказ")) {
            String[] args = msg.split(" ");
            if (args.length < 3) return;
            
            String targetName = args[1];
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch(Exception ex) { return; }
            
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeer(), "❌ Игрок не в сети.");
                return;
            }
            
            int min = plugin.getConfig().getInt("bounty.min_rep", 100);
            if (amount < min) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeer(), "❌ Минимальный заказ: " + min);
                return;
            }
            
            int vkId = e.getSenderId();
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < amount) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeer(), "❌ Недостаточно репутации ВК!");
                return;
            }
            
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, amount);
            int current = bounties.getOrDefault(target.getUniqueId(), 0);
            bounties.put(target.getUniqueId(), current + amount);
            
            String bc = " ЗАКАЗ! На голову " + target.getName() + " добавлено " + amount + " репутации! (Всего: " + (current+amount) + ")";
            Bukkit.broadcastMessage(ChatColor.RED + bc);
            VKChatPlugin.getInstance().getApi().sendToMainChat(bc);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null && killer != victim && bounties.containsKey(victim.getUniqueId())) {
            int reward = bounties.remove(victim.getUniqueId());
            int killerVkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(killer);
            
            if (killerVkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(killerVkId, reward);
                String msg = "⚔ Наемник " + killer.getName() + " убил " + victim.getName() + " и забрал награду " + reward + " репутации!";
                Bukkit.broadcastMessage(ChatColor.GREEN + msg);
                VKChatPlugin.getInstance().getApi().sendToMainChat(msg);
            }
        }
    }
}
