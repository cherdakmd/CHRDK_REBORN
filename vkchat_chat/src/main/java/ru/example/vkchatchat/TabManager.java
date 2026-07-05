package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TabManager implements Listener {
    private final VKChatChatPlugin plugin;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private volatile int statsIndex = 0;
    private final String[] statsLines = new String[4];
    private final Random rnd = new Random();

    public TabManager(VKChatChatPlugin plugin) {
        this.plugin = plugin;
        setupTeams();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int ticks = plugin.getConfig().getInt("tab.update-ticks", 20);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sendTab(p);
        }, 20L, ticks);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::updateStats, 60L, 100L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            assignTeam(p);
            sendTab(p);
        }, 10L);
        e.setJoinMessage(getJoinMessage(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(getQuitMessage(e.getPlayer()));
    }

    private String getJoinMessage(Player p) {
        String prefix = getPrefix(p);
        boolean isDonator = p.hasPermission("vkchat.donate.spark");
        boolean isNew = !p.hasPlayedBefore();

        List<String> pool;
        if (isNew && isDonator) {
            pool = plugin.getConfig().getStringList("broadcasts.join-donator-new");
        } else if (isDonator) {
            pool = plugin.getConfig().getStringList("broadcasts.join-donator");
        } else if (isNew) {
            pool = plugin.getConfig().getStringList("broadcasts.join-new");
        } else {
            pool = plugin.getConfig().getStringList("broadcasts.join-messages");
        }

        if (pool.isEmpty()) {
            pool = isNew && isDonator ? generateJoinNewDonator()
                    : isDonator ? generateJoinDonator()
                    : isNew ? generateJoinNew()
                    : generateJoins();
        }

        String msg = pool.get(rnd.nextInt(pool.size()));
        msg = ChatColor.translateAlternateColorCodes('&',
                msg.replace("{prefix}", prefix).replace("{player}", p.getName()));
        if (plugin.getConfig().getBoolean("broadcasts.vk-join-quit", true))
            sendToVk(ChatColor.stripColor(msg).replace("  ", " ").trim());
        return msg;
    }

    private String getQuitMessage(Player p) {
        String prefix = getPrefix(p);
        boolean isDonator = p.hasPermission("vkchat.donate.spark");

        List<String> pool = isDonator
                ? plugin.getConfig().getStringList("broadcasts.quit-donator")
                : plugin.getConfig().getStringList("broadcasts.quit-messages");

        if (pool.isEmpty()) {
            pool = isDonator ? generateQuitDonator() : generateQuits();
        }

        String msg = pool.get(rnd.nextInt(pool.size()));
        msg = ChatColor.translateAlternateColorCodes('&',
                msg.replace("{prefix}", prefix).replace("{player}", p.getName()));
        if (plugin.getConfig().getBoolean("broadcasts.vk-join-quit", true))
            sendToVk(ChatColor.stripColor(msg).replace("  ", " ").trim());
        return msg;
    }

    private void setupTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String[] groups = {"overlord", "legend", "star", "flame", "spark", "default"};
        String[] prefixes = {"&d&lВЛАСТЕЛИН ", "&5&lЛЕГЕНДА ", "&e&lЗВЕЗДА ", "&6&lПЛАМЯ ", "&b&lИСКРА ", "&7"};
        for (int i = 0; i < groups.length; i++) {
            Team team = sb.getTeam("z" + i + "_" + groups[i]);
            if (team == null) team = sb.registerNewTeam("z" + i + "_" + groups[i]);
            team.setPrefix(ChatColor.translateAlternateColorCodes('&', prefixes[i]));
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
    }

    public void assignTeam(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String group = getGroup(p);
        playerTeams.put(p.getUniqueId(), group);
        for (Player online : Bukkit.getOnlinePlayers()) {
            Team team = sb.getTeam("z" + getGroupIndex(online) + "_" + getGroup(online));
            if (team != null) team.addEntry(online.getName());
        }
    }

    private String getGroup(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "overlord";
        if (p.hasPermission("vkchat.donate.legend")) return "legend";
        if (p.hasPermission("vkchat.donate.star")) return "star";
        if (p.hasPermission("vkchat.donate.flame")) return "flame";
        if (p.hasPermission("vkchat.donate.spark")) return "spark";
        return "default";
    }

    private int getGroupIndex(Player p) {
        return switch (getGroup(p)) {
            case "overlord" -> 0; case "legend" -> 1; case "star" -> 2;
            case "flame" -> 3; case "spark" -> 4; default -> 5;
        };
    }

    private void sendTab(Player p) {
        String header = String.join("\n", plugin.getConfig().getStringList("tab.header"))
                .replace("%player%", p.getName()).replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        String footer = String.join("\n", plugin.getConfig().getStringList("tab.footer"))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%vkchat_reputation%", getReputation(p))
                .replace("%luckperms-prefix%", getPrefix(p))
                .replace("%stats%", getStats());
        header = ChatColor.translateAlternateColorCodes('&', header);
        footer = ChatColor.translateAlternateColorCodes('&', footer);
        p.setPlayerListHeaderFooter(header, footer);
    }

    private String getReputation(Player p) {
        try {
            ru.example.vkchat.VKChatPlugin vk = ru.example.vkchat.VKChatPlugin.getInstance();
            int vkId = vk.getApi().getLinkedVkId(p);
            if (vkId != -1) return String.valueOf(vk.getApi().getReputation(vkId));
        } catch (Exception ignored) {}
        return "0";
    }

    private String getPrefix(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "&d&lВЛАСТЕЛИН";
        if (p.hasPermission("vkchat.donate.legend")) return "&5&lЛЕГЕНДА";
        if (p.hasPermission("vkchat.donate.star")) return "&e&lЗВЕЗДА";
        if (p.hasPermission("vkchat.donate.flame")) return "&6&lПЛАМЯ";
        if (p.hasPermission("vkchat.donate.spark")) return "&b&lИСКРА";
        return "&7Игрок";
    }

    private void updateStats() {
        statsLines[0] = "&c❤ " + getTopRep();
        statsLines[1] = "&e💰 " + getTopDonator();
        statsLines[2] = "&a⏱ " + getUptime();
        statsLines[3] = "&b🌍 " + Bukkit.getOfflinePlayers().length + " игроков";
        statsIndex = (statsIndex + 1) % statsLines.length;
    }

    private String getStats() {
        if (statsLines[statsIndex] == null) updateStats();
        return statsLines[statsIndex] + "   &8|   " + statsLines[(statsIndex + 1) % statsLines.length];
    }

    private String getTopRep() {
        try {
            java.lang.reflect.Method m = ru.example.vkchat.VKChatPlugin.getInstance()
                    .getReputationManager().getClass().getMethod("getTopReputation");
            String top = (String) m.invoke(ru.example.vkchat.VKChatPlugin.getInstance().getReputationManager());
            if (top != null && top.contains("\n"))
                return "Топ реп: " + top.split("\n")[0].replaceAll("\\d+\\.\\s*", "");
        } catch (Exception ignored) {}
        return "Топ репутации";
    }

    private String getTopDonator() {
        try {
            org.bukkit.plugin.Plugin dp = Bukkit.getPluginManager().getPlugin("VKChatDonate");
            if (dp != null && dp.isEnabled()) {
                Object mgr = dp.getClass().getMethod("getDonateManager").invoke(dp);
                java.util.List<?> top = (java.util.List<?>) mgr.getClass().getMethod("getTopDonors", int.class).invoke(mgr, 1);
                if (top != null && !top.isEmpty()) {
                    Object entry = top.get(0);
                    String name = (String) ((java.util.Map.Entry<?,?>) entry).getKey();
                    double amount = (Double) ((java.util.Map.Entry<?,?>) entry).getValue();
                    return "Топ донат: " + name + " (" + (int)amount + "₽)";
                }
            }
        } catch (Exception ignored) {}
        return "Топ донатов";
    }

    private String getUptime() {
        long millis = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long days = millis / 86400000;
        long hours = (millis % 86400000) / 3600000;
        long mins = (millis % 3600000) / 60000;
        if (days > 0) return "Аптайм: " + days + "д " + hours + "ч";
        return "Аптайм: " + hours + "ч " + mins + "м";
    }

    private void sendToVk(String msg) {
        if (!plugin.getConfig().getBoolean("broadcasts.vk-join-quit", true)) return;
        try { VKChatPlugin.getInstance().getApi().sendToMainChat(msg); } catch (Exception ignored) {}
    }

    // ═══ ГЕНЕРАТОРЫ ВАРИАНТОВ ═══
    private static final String[] JOIN_VERBS = {"зашёл","появился","присоединился","подключился","вошёл","прибыл","залетел","ворвался","материализовался","загрузился","пришёл","активировался","приземлился","телепортировался","добрался","заглянул","влетел","ворвался в чат","нырнул","включился"};
    private static final String[] JOIN_NOUNS = {"на сервер","в мир","в игру","в чат","на огонёк","в матрицу","на тусовку","домой","в систему","в реальность","на радар","в сеть","на сервак","в битву","на локацию","к нам","в квадрат","на базу","в сборку","в дурку"};
    private static final String[] QUIT_VERBS = {"вышел","покинул сервер","ушёл","отключился","пропал","испарился","растворился","свалил","улетел","отчалил","выпал","отбыл","сделал ручкой","пошёл спать","ушёл есть","закрыл лавочку","взял паузу","ушёл в закат","отключил комп","скрылся"};

    private List<String> generateJoins() {
        List<String> list = new ArrayList<>();
        for (String verb : JOIN_VERBS) for (String noun : JOIN_NOUNS)
            list.add("&8[&a+&8] {prefix} &7{player} &7" + verb + " " + noun);
        return list;
    }
    private List<String> generateJoinNew() {
        List<String> list = new ArrayList<>();
        for (String e : new String[]{"&eвпервые здесь! &6🌟","&eновый игрок! &6🎉","&eсвежая кровь! &6🩸","&eновичок! &6🍼","&eновобранец! &6⚔","&eпервый раз! &6🎂","&eначало пути! &6🗺","&eдебют! &6🎬","&eпервый заход! &6🎯","&eсвежее мясо! &6🥩"})
            list.add("&8[&a+&8] {prefix} &7{player} " + e);
        return list;
    }
    private List<String> generateJoinDonator() {
        List<String> list = new ArrayList<>();
        for (String e : new String[]{"&6зашёл с сиянием","&6почтил присутствием","&6прибыл величественно","&6спустился с небес","&6озарил светом","&6открыл портал","&6прошёл сквозь врата","&6пришёл с дарами","&6вошёл как король","&6появился в ореоле"})
            list.add("&8[&a+&8] {prefix} &7{player} " + e);
        return list;
    }
    private List<String> generateJoinNewDonator() {
        return List.of("&8[&a+&8] {prefix} &7{player} &eвпервые и уже донатер! &6👑","&8[&a+&8] {prefix} &7{player} &eновый донатер! &6💎","&8[&a+&8] {prefix} &7{player} &eс порога с поддержкой! &6🔥");
    }
    private List<String> generateQuits() {
        List<String> list = new ArrayList<>();
        for (String verb : QUIT_VERBS)
            list.add("&8[&c-&8] {prefix} &7{player} &7" + verb);
        return list;
    }
    private List<String> generateQuitDonator() {
        return List.of("&8[&c-&8] {prefix} &7{player} &6ушёл по делам","&8[&c-&8] {prefix} &7{player} &6покинул нас","&8[&c-&8] {prefix} &7{player} &6ушёл в закат","&8[&c-&8] {prefix} &7{player} &6скрылся во тьме","&8[&c-&8] {prefix} &7{player} &6отбыл по-королевски","&8[&c-&8] {prefix} &7{player} &6ушёл с почестями","&8[&c-&8] {prefix} &7{player} &6покинул трон","&8[&c-&8] {prefix} &7{player} &6исчез в сиянии");
    }
}
