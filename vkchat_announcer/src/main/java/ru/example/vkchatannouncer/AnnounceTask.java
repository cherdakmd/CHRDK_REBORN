package ru.example.vkchatannouncer;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnnounceTask extends BukkitRunnable {
    private final VKChatAnnouncerPlugin plugin;
    private final List<String> messages;
    private int currentIndex = 0;

    public AnnounceTask(VKChatAnnouncerPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getConfig().getStringList("messages");
    }

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty() || messages.isEmpty()) return;

        // Шанс запустить викторину вместо автосообщения настраивается в config.yml
        if (ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("settings.quiz-chance", 20)) {
            QuizListener.askQuestion();
            return;
        }

        String msgLine;
        if (plugin.getConfig().getBoolean("settings.random-order", true)) {
            msgLine = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        } else {
            msgLine = messages.get(currentIndex);
            currentIndex = (currentIndex + 1) % messages.size();
        }

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.prefix", "&e &8| &f"));
        
        if (msgLine.equals("&f%stats%")) {
            sendStats(prefix);
        } else {
            sendInteractiveMessage(prefix + ChatColor.translateAlternateColorCodes('&', msgLine));
        }
    }

    private void sendInteractiveMessage(String text) {
        TextComponent base = new TextComponent("");

        // Simple URL parsing to make links clickable
        Pattern urlPattern = Pattern.compile("(https?://[\\w./]+)");
        Matcher matcher = urlPattern.matcher(text);
        
        int lastEnd = 0;
        while (matcher.find()) {
            String url = matcher.group(1);
            if (matcher.start() > lastEnd) {
                base.addExtra(new TextComponent(text.substring(lastEnd, matcher.start())));
            }
            TextComponent link = new TextComponent(url);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            base.addExtra(link);
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            base.addExtra(new TextComponent(text.substring(lastEnd)));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.spigot().sendMessage(base);
            p.sendMessage("");
        }
    }

    private void sendStats(String prefix) {
        List<String> statsLines = plugin.getConfig().getStringList("formats.stats");
        if (statsLines.isEmpty()) return;

        int online = Bukkit.getOnlinePlayers().size();
        int todayJoins = VKChatPlugin.getInstance().getStatsManager().getTodayJoins();
        
        // Parse the top rep
        String topRepStr = VKChatPlugin.getInstance().getReputationManager().getTopReputation();
        String top1 = "Никого";
        if (topRepStr != null) {
            String[] split = topRepStr.split("\n");
            if (split.length > 0) {
                top1 = ChatColor.stripColor(split[0]).replace("1. ", "");
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            for (String line : statsLines) {
                String formatted = line.replace("%online%", String.valueOf(online))
                                       .replace("%today_joins%", String.valueOf(todayJoins))
                                       .replace("%top_rep%", top1);
                p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', formatted));
            }
            p.sendMessage("");
        }
    }
}
