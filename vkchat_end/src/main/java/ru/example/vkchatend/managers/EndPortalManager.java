package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [1] Система создания порталов в Энд
 * [2] Механика яиц дракона
 * [3] Усиление кристаллов Энда
 */
public class EndPortalManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey portalKey;
    private final NamespacedKey eggKey;
    private final NamespacedKey crystalKey;

    // Активные порталы
    private final Map<String, PortalData> activePortals = new ConcurrentHashMap<>();

    // Яйца дракона
    private final Map<UUID, DragonEggData> dragonEggs = new ConcurrentHashMap<>();

    // Усиленные кристаллы
    private final Map<UUID, EnhancedCrystal> enhancedCrystals = new ConcurrentHashMap<>();

    private static class PortalData {
        Location location;
        String creator;
        long creationTime;
        int usesLeft;

        PortalData(Location location, String creator, int maxUses) {
            this.location = location;
            this.creator = creator;
            this.creationTime = System.currentTimeMillis();
            this.usesLeft = maxUses;
        }
    }

    private static class DragonEggData {
        Location location;
        int hatchingProgress;
        long lastUpdate;

        DragonEggData(Location location) {
            this.location = location;
            this.hatchingProgress = 0;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private static class EnhancedCrystal {
        int level;
        double powerMultiplier;
        long enhancementTime;

        EnhancedCrystal(int level) {
            this.level = level;
            this.powerMultiplier = 1.0 + (level * 0.25);
            this.enhancementTime = System.currentTimeMillis();
        }
    }

    public EndPortalManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.portalKey = new NamespacedKey(plugin, "end_portal");
        this.eggKey = new NamespacedKey(plugin, "dragon_egg");
        this.crystalKey = new NamespacedKey(plugin, "enhanced_crystal");
        startHatchingTask();
    }

    /**
     * Запуск задачи вылупления яиц
     */
    private void startHatchingTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (DragonEggData egg : dragonEggs.values()) {
                if (egg.hatchingProgress < 100) {
                    egg.hatchingProgress += 1;
                    egg.lastUpdate = System.currentTimeMillis();

                    if (egg.hatchingProgress >= 100) {
                        hatchDragonEgg(egg);
                    }
                }
            }
        }, 1200L, 1200L); // Каждую минуту
    }

    /**
     * Создать портал в Энд
     */
    public boolean createPortal(Player p, Location loc) {
        int cost = plugin.getConfig().getInt("end.portal.creation-cost", 2000);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return false;
        }

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        // Создание портала
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
        int maxUses = plugin.getConfig().getInt("end.portal.max-uses", 20);
        activePortals.put(key, new PortalData(loc, p.getName(), maxUses));

        // Визуальные эффекты
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2 / 12;
            double x = loc.getX() + 2 * Math.cos(angle);
            double z = loc.getZ() + 2 * Math.sin(angle);
            loc.getWorld().spawnParticle(Particle.PORTAL, new Location(loc.getWorld(), x, loc.getY() + 1, z), 10, 0.1, 0.1, 0.1, 0.05);
        }

        p.sendMessage(ChatColor.DARK_PURPLE + "✦ Портал в Энд создан! Стоимость: " + cost + " реп.");
        p.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);

        return true;
    }

    /**
     * Улучшить яйцо дракона
     */
    public boolean enhanceDragonEgg(Player p, Location eggLoc) {
        int cost = plugin.getConfig().getInt("end.dragon-egg.enhance-cost", 5000);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        UUID eggId = UUID.nameUUIDFromBytes(eggLoc.toString().getBytes());
        dragonEggs.put(eggId, new DragonEggData(eggLoc));

        p.sendMessage(ChatColor.GOLD + "✦ Яйцо дракона начинает вылупление! Процесс займёт ~100 минут.");
        p.playSound(eggLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);

        return true;
    }

    /**
     * Усилить кристалл Энда
     */
    public boolean enhanceCrystal(Player p, Location crystalLoc) {
        int level = getCrystalLevel(crystalLoc);
        if (level >= 5) {
            p.sendMessage(ChatColor.RED + "Кристалл уже максимального уровня!");
            return false;
        }

        int cost = plugin.getConfig().getInt("end.crystal.enhance-cost", 1000) * (level + 1);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        UUID crystalId = UUID.nameUUIDFromBytes(crystalLoc.toString().getBytes());
        enhancedCrystals.put(crystalId, new EnhancedCrystal(level + 1));

        p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Кристалл усилен до уровня " + (level + 1) + "! Мощность: x" + String.format("%.2f", 1.0 + ((level + 1) * 0.25)));
        p.playSound(crystalLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f);

        return true;
    }

    /**
     * Вылупление яйца дракона
     */
    private void hatchDragonEgg(DragonEggData egg) {
        Location loc = egg.location;
        World world = loc.getWorld();
        if (world == null) return;

        // Спавн мини-дракона
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 5, 0.5, 0.5, 0.5);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);

        // Награда всем игрокам в Энде
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 100 * 100) {
                try {
                    int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                    if (vkId != -1) {
                        VKChatPlugin.getInstance().getApi().addReputation(vkId, 500);
                    }
                } catch (Exception ignored) {}
                plugin.getEndManager().addEndReputation(p, 250);
                p.sendMessage(ChatColor.GOLD + "✦ Яйцо дракона вылупилось! +500 реп. ВК, +250 реп. Энда");
            }
        }
    }

    /**
     * Получить уровень кристалла
     */
    public int getCrystalLevel(Location loc) {
        UUID crystalId = UUID.nameUUIDFromBytes(loc.toString().getBytes());
        EnhancedCrystal crystal = enhancedCrystals.get(crystalId);
        return crystal != null ? crystal.level : 0;
    }

    /**
     * Получить множитель мощности кристалла
     */
    public double getCrystalPowerMultiplier(Location loc) {
        UUID crystalId = UUID.nameUUIDFromBytes(loc.toString().getBytes());
        EnhancedCrystal crystal = enhancedCrystals.get(crystalId);
        return crystal != null ? crystal.powerMultiplier : 1.0;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        // Проверка на создание портала
        ItemStack item = e.getItemInHand();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(portalKey, PersistentDataType.INTEGER)) {
            createPortal(p, e.getBlock().getLocation());
            e.setCancelled(true);
        }
    }

    public int getActivePortalCount() {
        return activePortals.size();
    }
}
