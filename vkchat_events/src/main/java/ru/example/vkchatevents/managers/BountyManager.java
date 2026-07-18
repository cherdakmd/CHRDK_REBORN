package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BountyManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Integer> bounties = new ConcurrentHashMap<>();
    private final File dataFile;

    public BountyManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bounties.yml");
        loadBounties();
    }

    public Map<UUID, Integer> getBounties() {
        return bounties;
    }

    // --- Persistence ---
    private void loadBounties() {
        if (!dataFile.exists()) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            for (String uuidStr : cfg.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int amount = cfg.getInt(uuidStr);
                    if (amount > 0) bounties.put(uuid, amount);
                } catch (IllegalArgumentException ignored) {}
            }
            plugin.getLogger().info("Загружены баунти для " + bounties.size() + " игроков.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка загрузки bounties.yml: " + e.getMessage());
        }
    }

    private final Object saveLock = new Object();

    public void saveBounties() {
        synchronized (saveLock) {
            try {
                FileConfiguration cfg = new YamlConfiguration();
                for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
                    cfg.set(entry.getKey().toString(), entry.getValue());
                }
                cfg.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка сохранения bounties.yml: " + e.getMessage());
            }
        }
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
                VKChatBridge.sendMessage(e.getPeer(), "❌ Игрок не в сети.");
                return;
            }
            
            int min = plugin.getConfig().getInt("bounty.min_rep", 100);
            if (amount < min) {
                VKChatBridge.sendMessage(e.getPeer(), "❌ Минимальный заказ: " + min);
                return;
            }
            
            int vkId = e.getSenderId();
            if (VKChatBridge.getReputation(vkId) < amount) {
                VKChatBridge.sendMessage(e.getPeer(), "❌ Недостаточно репутации ВК!");
                return;
            }
            
            VKChatBridge.takeReputation(vkId, amount);
            int current = bounties.getOrDefault(target.getUniqueId(), 0);
            bounties.put(target.getUniqueId(), current + amount);
            saveBounties();
            
            String bc = " ЗАКАЗ! На голову " + target.getName() + " добавлено " + amount + " репутации! (Всего: " + (current+amount) + ")";
            Bukkit.broadcastMessage(ChatColor.RED + bc);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null && killer != victim && bounties.containsKey(victim.getUniqueId())) {
            int reward = bounties.remove(victim.getUniqueId());
            saveBounties();
            int killerVkId = VKChatBridge.getLinkedVkId(killer);
            
            if (killerVkId != -1) {
                VKChatBridge.addPoints(killerVkId, reward);
                String msg = "⚔ Наемник " + killer.getName() + " убил " + victim.getName() + " и забрал награду " + reward + " репутации!";
                Bukkit.broadcastMessage(ChatColor.GREEN + msg);
            } else {
                // Killer unlinked — refund bounty to victim's orderer or keep it pending
                bounties.put(victim.getUniqueId(), reward);
                saveBounties();
                String msg = "⚔ " + killer.getName() + " убил " + victim.getName() + ", но не привязал ВК — награда " + reward + " реп. сохранена.";
                Bukkit.broadcastMessage(ChatColor.YELLOW + msg);
            }
        }
    }
}
