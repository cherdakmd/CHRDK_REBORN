package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер анонсов: смерти, убийства, ранги, интеграции с плагинами
 */
public class BroadcastManager implements Listener {
    private final VKChatChatPlugin plugin;
    private final Random rnd = new Random();
    private final ConcurrentHashMap<UUID, Integer> lastJobLevel = new ConcurrentHashMap<>();

    public BroadcastManager(VKChatChatPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startJobLevelCheck();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        String vkStatus = getStatus(victim);

        if (killer != null) {
            String kStatus = getStatus(killer);
            List<String> msgs = plugin.getConfig().getStringList("broadcasts.kill-messages");
            if (msgs.isEmpty()) msgs = List.of(
                    "&c☠ {killer} &7убил &c{victim}",
                    "&c⚔ {killer} &7расправился с &c{victim}",
                    "&c💀 {killer} &7отправил &c{victim} &7на респаун",
                    "&c🗡 {killer} &7победил в дуэли &c{victim}",
                    "&c🔥 {killer} &7уничтожил &c{victim}"
            );
            String msg = msgs.get(rnd.nextInt(msgs.size()));
            msg = msg.replace("{killer}", kStatus + " &r" + killer.getName())
                     .replace("{victim}", vkStatus + " &r" + victim.getName());
            broadcast(ChatColor.translateAlternateColorCodes('&', msg));
        } else {
            List<String> msgs = plugin.getConfig().getStringList("broadcasts.death-messages");
            if (msgs.isEmpty()) msgs = List.of(
                    "&7☠ {player} &7погиб",
                    "&7💀 {player} &7отправился в последний путь",
                    "&7🪦 {player} &7ушёл в мир иной",
                    "&7⚰ {player} &7встретил свою смерть",
                    "&7🕯 {player} &7пал в бою"
            );
            String msg = msgs.get(rnd.nextInt(msgs.size()));
            msg = msg.replace("{player}", vkStatus + " &r" + victim.getName());
            broadcast(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Треккинг уровня Jobs для анонсов
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkJobLevel(p), 40L);
    }

    private void startJobLevelCheck() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkJobLevel(p);
        }, 200L, 200L);
    }

    private void checkJobLevel(Player p) {
        try {
            org.bukkit.plugin.Plugin jobs = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobs == null || !jobs.isEnabled()) return;
            Object dataMgr = jobs.getClass().getMethod("getJobsDataManager").invoke(jobs);
            if (dataMgr == null) return;
            int totalLvl = (int) dataMgr.getClass().getMethod("getTotalLevel", Player.class).invoke(dataMgr, p);
            int prev = lastJobLevel.getOrDefault(p.getUniqueId(), 0);
            if (totalLvl > prev && prev > 0) {
                List<String> msgs = plugin.getConfig().getStringList("broadcasts.job-level-messages");
                if (msgs.isEmpty()) msgs = List.of(
                        "&e⭐ {player} &7достиг &e{level} &7уровня профессий!",
                        "&e📈 {player} &7прокачал профессии до &e{level}",
                        "&e🏆 {player} &7— &e{level} &7уровень профессий!"
                );
                String msg = msgs.get(rnd.nextInt(msgs.size()));
                msg = msg.replace("{player}", getStatus(p) + " &r" + p.getName())
                         .replace("{level}", String.valueOf(totalLvl));
                broadcast(ChatColor.translateAlternateColorCodes('&', msg));
            }
            lastJobLevel.put(p.getUniqueId(), totalLvl);
        } catch (Exception ignored) {}
    }

    /**
     * Вызвать анонс из любого плагина
     */
    public void announce(String message) {
        broadcast(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Вызвать анонс с вариациями (случайный выбор из списка)
     */
    public void announceRandom(List<String> messages, String player, String extra) {
        if (messages.isEmpty()) return;
        String msg = messages.get(rnd.nextInt(messages.size()));
        msg = msg.replace("{player}", getStatus(Bukkit.getPlayer(player)) + " &r" + player);
        if (extra != null) msg = msg.replace("{extra}", extra);
        broadcast(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        if (plugin.getConfig().getBoolean("broadcasts.send-to-vk", true)) {
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(msg)); } catch (Exception ignored) {}
        }
    }

    private String getStatus(Player p) {
        if (p == null) return "&7";
        if (p.hasPermission("vkchat.donate.overlord")) return "&d&lВЛАСТЕЛИН";
        if (p.hasPermission("vkchat.donate.legend")) return "&5&lЛЕГЕНДА";
        if (p.hasPermission("vkchat.donate.star")) return "&e&lЗВЕЗДА";
        if (p.hasPermission("vkchat.donate.flame")) return "&6&lПЛАМЯ";
        if (p.hasPermission("vkchat.donate.spark")) return "&b&lИСКРА";
        return "&7";
    }
}
