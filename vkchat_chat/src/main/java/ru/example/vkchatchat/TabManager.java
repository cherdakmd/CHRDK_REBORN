package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Встроенная таб-система: header/footer, nametags, префиксы групп
 */
public class TabManager implements Listener {
    private final VKChatChatPlugin plugin;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private int statsIndex = 0;
    private final String[] statsLines = new String[4];

    public TabManager(VKChatChatPlugin plugin) {
        this.plugin = plugin;
        setupTeams();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sendTab(p);
        }, 20L, 20L);
        // Ротация статистики каждые 5 секунд
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::updateStats, 60L, 100L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            assignTeam(p);
            sendTab(p);
        }, 10L);
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
        String g = getGroup(p);
        return switch (g) {
            case "overlord" -> 0;
            case "legend" -> 1;
            case "star" -> 2;
            case "flame" -> 3;
            case "spark" -> 4;
            default -> 5;
        };
    }

    private void sendTab(Player p) {
        String header = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("tab.header",
                        "&4&l⚔ &c&lCHRDK REBORN &4&l⚔\n&7Добро пожаловать, &f%player%"));
        String footer = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("tab.footer",
                        "&7Онлайн &c%online% &8| &7Репутация &c%vkchat_reputation%\n&7Статус: %luckperms-prefix%\n\n&4⚔ &c/donate info &4⚔"));

        header = header.replace("%player%", p.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        footer = footer.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%vkchat_reputation%", getReputation(p))
                .replace("%luckperms-prefix%", getPrefix(p))
                .replace("%stats%", getStats());

        sendTabPacket(p, header, footer);
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

    private void sendTabPacket(Player p, String header, String footer) {
        try {
            Object headerComponent = getChatComponent(header);
            Object footerComponent = getChatComponent(footer);

            Object packet = getNMSClass("PacketPlayOutPlayerListHeaderFooter")
                    .getDeclaredConstructor().newInstance();
            Field hf = packet.getClass().getDeclaredField("header");
            hf.setAccessible(true); hf.set(packet, headerComponent);
            Field ff = packet.getClass().getDeclaredField("footer");
            ff.setAccessible(true); ff.set(packet, footerComponent);

            Object connection = getConnection(p);
            connection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(connection, packet);
        } catch (Exception ignored) {}
    }

    private Object getChatComponent(String text) {
        try {
            return getNMSClass("ChatComponentText").getConstructor(String.class).newInstance(
                    ChatColor.translateAlternateColorCodes('&', text));
        } catch (Exception e) {
            return null;
        }
    }

    private Object getConnection(Player p) throws Exception {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Field f = handle.getClass().getDeclaredField("playerConnection");
        f.setAccessible(true);
        return f.get(handle);
    }

    private Class<?> getNMSClass(String name) {
        try {
            return Class.forName("net.minecraft.server." + getVersion() + "." + name);
        } catch (Exception e) {
            return null;
        }
    }

    private String getVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
    }

    private void updateStats() {
        statsLines[0] = "&c❤ " + getTopRep();
        statsLines[1] = "&e💰 " + getTopDonator();
        statsLines[2] = "&a⏱ " + getUptime();
        statsLines[3] = "&b🌍 " + Bukkit.getOfflinePlayers().length + " игроков всего";
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
            if (top != null && top.contains("\n")) {
                return "Топ реп: " + top.split("\n")[0].replaceAll("\\d+\\.\\s*", "");
            }
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
        long uptime = System.currentTimeMillis() - plugin.getServer().getWorlds().get(0).getFullTime();
        // Actually use real uptime
        long millis = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long days = millis / 86400000;
        long hours = (millis % 86400000) / 3600000;
        long mins = (millis % 3600000) / 60000;
        if (days > 0) return "Аптайм: " + days + "д " + hours + "ч";
        return "Аптайм: " + hours + "ч " + mins + "м";
    }
}
