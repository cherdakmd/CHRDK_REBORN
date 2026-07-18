package ru.example.vkchatmobs.bestiary;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.example.vkchatmobs.VKChatMobsPlugin;

public class BestiaryListener implements Listener {

    private final VKChatMobsPlugin plugin;

    public BestiaryListener(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        if (!plugin.getBestiaryManager().isEnabled()) return;
        plugin.getBestiaryManager().recordKill(killer, entity.getType());
    }
}
