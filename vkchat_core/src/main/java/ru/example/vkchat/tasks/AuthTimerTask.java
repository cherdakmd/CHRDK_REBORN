package ru.example.vkchat.tasks;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

public class AuthTimerTask extends BukkitRunnable {
    private final VKChatPlugin plugin;

    public AuthTimerTask(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int timeLimit = plugin.getConfig().getInt("auth.link-time-limit", 60);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getAuthManager().isFullyAuthorized(p)) {
                long joinTime = plugin.getAuthManager().getJoinTime(p);
                long passed = (System.currentTimeMillis() - joinTime) / 1000;
                long left = timeLimit - passed;
                
                if (left <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.kickPlayer(plugin.getConfigManager().getMessage("link_timeout")));
                    continue;
                }
                
                String stateMsg = "";
                String titleMsg = "";
                String subTitleMsg = "";
                
                if (plugin.getAuthManager().isWaiting2fa(p)) {
                    stateMsg = "&cПодтверди вход в ВК!";
                    titleMsg = "&c&lБЕЗОПАСНОСТЬ";
                    subTitleMsg = "&fМы отправили код тебе в &bВК";
                } else if (!plugin.getAuthManager().isLinked(p)) {
                    stateMsg = "&cПривяжи ВК!";
                    titleMsg = "&c&lПРИВЯЖИ ВК";
                    subTitleMsg = "&fНапиши &b/vklink &fв чат!";
                } else if (!plugin.getAuthManager().isRegistered(p)) {
                    stateMsg = "&cЗарегистрируйся!";
                    titleMsg = "&c&lРЕГИСТРАЦИЯ";
                    subTitleMsg = "&fНапиши &e/register пароль &fв чат!";
                } else {
                    stateMsg = "&cВойди в аккаунт!";
                    titleMsg = "&c&lВХОД В АККАУНТ";
                    subTitleMsg = "&fНапиши &a/login пароль &fв чат!";
                }

                String msg = ChatColor.translateAlternateColorCodes('&', stateMsg + " &eОсталось: &a" + left + " &eсек.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                
                p.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', titleMsg),
                        ChatColor.translateAlternateColorCodes('&', subTitleMsg),
                        0, 30, 0
                );
            }
        }
    }
}