package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final VKChatPlugin plugin;
    private final Map<UUID, String> originalMessages = new ConcurrentHashMap<>();

    public ChatListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatLowest(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();

        // 1. Проверка Мута (Блокировка отправки при муте)
        if (plugin.getChatManager().isMuted(p.getUniqueId())) {
            e.setCancelled(true);
            long rem = plugin.getChatManager().getMuteRemaining(p.getUniqueId()) / 1000 / 60;
            p.sendMessage(plugin.getConfigManager().formatColor("&cВы замучены! Осталось: " + Math.max(1, rem) + " мин."));
            return;
        }

        // 2. Система игнорирования (черный список игроков)
        e.getRecipients().removeIf(r -> plugin.getChatManager().isIgnored(r.getUniqueId(), p.getUniqueId()));

        // Захватываем оригинальное сообщение перед тем, как его изменят другие плагины (например, EssentialsX)
        originalMessages.put(p.getUniqueId(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMonitor(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String original = originalMessages.remove(p.getUniqueId());

        if (e.isCancelled()) return;

        // Определяем символ глобального чата из конфига
        String globalSymbol = plugin.getConfig().getString("chat.global-symbol", "!");
        if (globalSymbol == null || globalSymbol.trim().isEmpty()) {
            globalSymbol = "!";
        }

        boolean isGlobal = false;
        if (original != null) {
            String cleanOriginal = ChatColor.stripColor(original).trim();
            if (cleanOriginal.startsWith(globalSymbol) || cleanOriginal.startsWith("!")) {
                isGlobal = true;
            }
        }

        // Если это глобальное сообщение, отправляем его в ВК
        if (isGlobal) {
            // Берем уже отформатированное / измененное плагинами (например, EssentialsX) финальное сообщение
            String finalMsg = e.getMessage();

            // Если в финальном сообщении все еще остался символ "!", вырезаем его для ВК
            if (finalMsg.startsWith(globalSymbol)) {
                finalMsg = finalMsg.substring(globalSymbol.length()).trim();
            } else if (finalMsg.startsWith("!")) {
                finalMsg = finalMsg.substring(1).trim();
            }

            final String strippedMsg = ChatColor.stripColor(finalMsg);

            // Получаем префикс нации чисто для ВК трансляции
            String nation = "";
            try {
                org.bukkit.plugin.Plugin nationsPlugin = Bukkit.getPluginManager().getPlugin("VKChatNations");
                if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                    Object nationMgr = nationsPlugin.getClass().getMethod("getNationManager").invoke(nationsPlugin);
                    String c = (String) nationMgr.getClass().getMethod("getPlayerNation", org.bukkit.entity.Player.class).invoke(nationMgr, p);
                    if (c != null) {
                        String prefix = nationsPlugin.getConfig().getString("nations." + c + ".prefix", "[" + c + "]");
                        if (prefix.contains("[") || prefix.contains("]")) {
                            nation = ChatColor.translateAlternateColorCodes('&', prefix + " ");
                        } else {
                            nation = ChatColor.translateAlternateColorCodes('&', "&8[" + prefix + "&8] ");
                        }
                    }
                }
            } catch (Exception ignored) {}

            final String finalNation = ChatColor.stripColor(nation).trim();
            final String finalFormattedMsg = strippedMsg;
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int mainChat = plugin.getConfig().getInt("vk.peer-id", 2000000001);
                String prefixText = finalNation.isEmpty() ? "" : finalNation + " ";
                plugin.getVkManager().sendMessage(mainChat, "[Игра] " + prefixText + p.getName() + ": " + finalFormattedMsg);
            });
        }
    }
}
