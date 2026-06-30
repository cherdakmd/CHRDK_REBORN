package ru.example.vkchatend.listeners;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;
import ru.example.vkchatend.managers.EndManager;
import ru.example.vkchatend.managers.EndCorruptionManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * События в Энде
 */
public class EndListener implements Listener {
    private final VKChatEndPlugin plugin;

    public EndListener(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * При входе в Энд — приветствие
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        int level = plugin.getEndManager().getEndLevel(p);
        p.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Добро пожаловать в Энд!");
        p.sendMessage(ChatColor.GRAY + "Уровень Энда: " + ChatColor.WHITE + level);
        p.sendMessage(ChatColor.GRAY + "Доступные зоны: " + ChatColor.WHITE + getAvailableZones(p));
        p.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");
    }

    /**
     * Убийство мобов в Энде — репутация
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        EntityType type = e.getEntity().getType();
        int rep = 0;

        switch (type) {
            case ENDERMAN:
                rep = 3;
                break;
            case ENDERMITE:
                rep = 5;
                break;
            case SHULKER:
                rep = 10;
                break;
            case PHANTOM:
                rep = 8;
                break;
            case EVOKER:
            case VINDICATOR:
                rep = 15;
                break;
            default:
                rep = 1;
        }

        if (rep > 0) {
            try {
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
                }
            } catch (Exception ignored) {}

            plugin.getEndManager().addEndReputation(p, rep / 2);
        }

        // Шанс на эндер-артефакт
        if (ThreadLocalRandom.current().nextInt(100) < 2) {
            String[] rarities = {"common", "rare", "epic", "legendary"};
            String rarity = rarities[ThreadLocalRandom.current().nextInt(rarities.length)];
            org.bukkit.inventory.ItemStack artifact = plugin.getEndArtifactManager().createRandomArtifact(rarity);
            if (artifact != null) {
                e.getDrops().add(artifact);
                p.sendMessage(ChatColor.DARK_PURPLE + "✦ Эндер-артефакт дропнул!");
            }
        }
    }

    /**
     * Движение в Энде — обнаружение зон
     */
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        // Проверка коррупции
        EndCorruptionManager.CorruptionLevel corruption = plugin.getEndCorruptionManager().getCorruptionAt(e.getTo());
        if (corruption != plugin.getEndCorruptionManager().getCorruptionAt(e.getFrom())) {
            if (corruption.level > 0) {
                p.sendMessage(corruption.color + "☠ Зона коррупции: " + corruption.displayName);
            }
        }
    }

    /**
     * Получить доступные зоны
     */
    private String getAvailableZones(Player p) {
        int level = plugin.getEndManager().getEndLevel(p);
        int count = 0;
        for (EndManager.EndZone zone : EndManager.EndZone.values()) {
            if (level >= zone.requiredLevel) count++;
        }
        return count + "/" + EndManager.EndZone.values().length;
    }
}
