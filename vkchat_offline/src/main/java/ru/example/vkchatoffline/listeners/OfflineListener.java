package ru.example.vkchatoffline.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import java.util.*;

public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;
    public OfflineListener(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.isCancelled()) return;
        String cmd = e.getCommand().toLowerCase();
        int peer = e.getPeerId();
        int sender = e.getSenderVkId();
        String[] args = e.getArgs();

        // Команда смены
        if (cmd.equals("!смена")) {
            plugin.getShiftManager().handleCommand(peer, sender, null, args);
            e.setCancelled(true);
            return;
        }

        // Команды походов
        if (cmd.equals("!поход") || cmd.equals("!походы") || cmd.equals("!пойти") || 
            cmd.equals("!выбор") || cmd.equals("!статус") || cmd.equals("!герой") ||
            cmd.equals("!навыки") || cmd.equals("!кампания") || cmd.equals("!характеристики") ||
            cmd.equals("!статы") || cmd.equals("!бой") || cmd.equals("!продолжить")) {
            plugin.getAdventureManager().handleCommand(sender, cmd, args);
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (e.isCancelled()) return;
        // Обрабатываем только ЛС (peer == sender)
        if (e.getPeer() != e.getSenderId()) return;

        String msg = e.getMessage() == null ? "" : e.getMessage().trim().toLowerCase();
        int sender = e.getSenderId();

        // Обработка кнопок походов
        if (msg.equals("походы") || msg.equals("поход")) {
            plugin.getAdventureManager().handleCommand(sender, "!поход", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("герой")) {
            plugin.getAdventureManager().handleCommand(sender, "!герой", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("навыки")) {
            plugin.getAdventureManager().handleCommand(sender, "!навыки", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("кампания")) {
            plugin.getAdventureManager().handleCommand(sender, "!кампания", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("характеристики") || msg.equals("статы")) {
            plugin.getAdventureManager().handleCommand(sender, "!характеристики", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("бой")) {
            plugin.getAdventureManager().handleCommand(sender, "!бой", new String[]{});
            e.setCancelled(true);
        } else if (msg.equals("продолжить")) {
            plugin.getAdventureManager().handleCommand(sender, "!продолжить", new String[]{});
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1 && plugin.getRewardManager().hasPendingRewards(vkId)) {
                List<ItemStack> rewards = plugin.getRewardManager().getPendingRewards(vkId);
                for (ItemStack item : rewards) p.getInventory().addItem(item);
                p.sendMessage("§a🎁 Награды из похода получены!");
            }
        } catch (Exception ignored) {}
    }
}
