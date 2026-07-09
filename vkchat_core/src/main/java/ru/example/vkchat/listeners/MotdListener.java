package ru.example.vkchat.listeners;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.MotdProviderRegistry;

public class MotdListener implements Listener {
    private final VKChatPlugin plugin;

    public MotdListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }

        String line1 = plugin.getConfig().getString("motd.line-1", "&6&l✦ CHRDK REBORN ✦ &8▸ &fMMO-Выживание 1.16.5");
        String defaultLine2 = plugin.getConfig().getString("motd.line-2-default", "&eАртефакты &8• &aГир &8• &dНации &8• &bРаботы &8• &cБоссы &8• &6Ивенты &8• &fВК БОТ");

        // Получаем активное событие от зарегистрированных провайдеров (без reflection)
        String activeLine = MotdProviderRegistry.getActiveMotdLine();
        String line2 = activeLine != null ? activeLine : defaultLine2;

        String motd = ChatColor.translateAlternateColorCodes('&', line1 + "\n" + line2);
        e.setMotd(motd);
    }
}
