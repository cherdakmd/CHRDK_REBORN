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

        // Все команды походов
        plugin.getAdventureManager().handleCommand(sender, cmd, args);
        e.setCancelled(true);
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (e.isCancelled()) return;
        if (e.getPeer() != e.getSenderId()) return;

        String msg = e.getMessage() == null ? "" : e.getMessage().trim().toLowerCase();
        int sender = e.getSenderId();

        // ═══ МАРШРУТЫ ═══
        if (msg.contains("🌲") || msg.equals("лес")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "лес"});
            e.setCancelled(true);
        } else if (msg.contains("⛏") || msg.contains("шахт")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "шахты"});
            e.setCancelled(true);
        } else if (msg.contains("🏛") || msg.contains("руин")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "руины"});
            e.setCancelled(true);
        } else if (msg.contains("🌿") || msg.contains("болот")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "болота"});
            e.setCancelled(true);
        } else if (msg.contains("🏰") || msg.contains("замок")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "замок"});
            e.setCancelled(true);
        } else if (msg.contains("🔥") || msg.contains("незер")) {
            plugin.getAdventureManager().handleCommand(sender, "!пойти", new String[]{"!пойти", "незер"});
            e.setCancelled(true);
        }

        // ═══ НАВИГАЦИЯ ═══
        else if (msg.contains("герой") || msg.contains("👤")) {
            plugin.getAdventureManager().handleCommand(sender, "!герой", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("тайник") || msg.contains("🎒")) {
            plugin.getAdventureManager().handleCommand(sender, "!тайник", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("навыки") || msg.contains("🌳")) {
            plugin.getAdventureManager().handleCommand(sender, "!навыки", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("кампания") || msg.contains("📖")) {
            plugin.getAdventureManager().handleCommand(sender, "!кампания", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("характеристики") || msg.contains("статы") || msg.contains("📊")) {
            plugin.getAdventureManager().handleCommand(sender, "!характеристики", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("статус")) {
            plugin.getAdventureManager().handleCommand(sender, "!статус", new String[]{});
            e.setCancelled(true);
        }

        // ═══ ВЫБОР ДЕЙСТВИЯ ═══
        else if (msg.contains("рискнуть")) {
            plugin.getAdventureManager().handleCommand(sender, "!выбор", new String[]{"!выбор", "1"});
            e.setCancelled(true);
        } else if (msg.contains("осторожно") || msg.contains("🛡")) {
            plugin.getAdventureManager().handleCommand(sender, "!выбор", new String[]{"!выбор", "2"});
            e.setCancelled(true);
        } else if (msg.contains("исследовать") || msg.contains("🔍")) {
            plugin.getAdventureManager().handleCommand(sender, "!выбор", new String[]{"!выбор", "3"});
            e.setCancelled(true);
        } else if (msg.contains("отступить") || msg.contains("🏃")) {
            plugin.getAdventureManager().handleCommand(sender, "!выбор", new String[]{"!выбор", "4"});
            e.setCancelled(true);
        }

        // ═══ БОЙ ═══
        else if (msg.contains("атака") || msg.contains("⚔")) {
            plugin.getAdventureManager().handleCombatAction(sender, ru.example.vkchatoffline.combat.CombatManager.CombatAction.ATTACK);
            e.setCancelled(true);
        } else if (msg.contains("защита")) {
            plugin.getAdventureManager().handleCombatAction(sender, ru.example.vkchatoffline.combat.CombatManager.CombatAction.DEFEND);
            e.setCancelled(true);
        } else if (msg.contains("способность") || msg.contains("🔥")) {
            plugin.getAdventureManager().handleCombatAction(sender, ru.example.vkchatoffline.combat.CombatManager.CombatAction.SKILL);
            e.setCancelled(true);
        } else if (msg.contains("зелье") || msg.contains("🧪")) {
            plugin.getAdventureManager().handleCombatAction(sender, ru.example.vkchatoffline.combat.CombatManager.CombatAction.ITEM);
            e.setCancelled(true);
        } else if (msg.contains("побег")) {
            plugin.getAdventureManager().handleCombatAction(sender, ru.example.vkchatoffline.combat.CombatManager.CombatAction.FLEE);
            e.setCancelled(true);
        }

        // ═══ ПРОДОЛЖЕНИЕ ═══
        else if (msg.contains("продолжить") || msg.contains("▶")) {
            plugin.getAdventureManager().handleCommand(sender, "!продолжить", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("забрать") || msg.contains("🎉")) {
            plugin.getAdventureManager().handleCommand(sender, "!забрать", new String[]{});
            e.setCancelled(true);
        } else if (msg.contains("лечиться") || msg.contains("🏥")) {
            plugin.getAdventureManager().handleCommand(sender, "!лечиться", new String[]{});
            e.setCancelled(true);
        }

        // ═══ ПОХОДЫ ═══
        else if (msg.contains("поход") || msg.contains("⛺")) {
            plugin.getAdventureManager().handleCommand(sender, "!поход", new String[]{});
            e.setCancelled(true);
        }

        // ═══ ДРУГОЕ ═══
        else if (msg.contains("смена") || msg.contains("⛏")) {
            plugin.getShiftManager().handleCommand(0, sender, null, new String[]{"!смена"});
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
