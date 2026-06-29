package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;
import ru.example.vkchatevents.util.ClaimProtection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class InvasionManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final NamespacedKey voidKey;

    private boolean invasionActive = false;
    private Location riftLocation;
    private int riftHp;
    private int maxRiftHp;
    private ArmorStand holo;
    private BukkitRunnable waveTask;
    private List<Entity> spawnedMobs = new ArrayList<>();

    public InvasionManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        this.voidKey = new NamespacedKey(plugin, "void_mob");

        int interval = plugin.getConfig().getInt("invasions.interval", 7200);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                tryStartInvasion();
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);
    }

    
    public boolean isActive() {
        return invasionActive;
    }
    
    public Location getActiveLocation() {
        return riftLocation;
    }

    public void tryStartInvasion() {
        if (invasionActive) return;

        int minOnline = plugin.getConfig().getInt("invasions.min-online", 2);
        if (Bukkit.getOnlinePlayers().size() < minOnline) return;

        World world = Bukkit.getWorlds().get(0);
        int radius = plugin.getConfig().getInt("invasions.spawn-radius", 1000);
        riftLocation = ClaimProtection.findSafeWildernessLocation(world, radius, plugin.getConfig().getInt("invasions.protected-radius", 32), 80);
        if (riftLocation == null) return;
        int x = riftLocation.getBlockX();
        int z = riftLocation.getBlockZ();
        int y = riftLocation.getBlockY();

        Block block = riftLocation.getBlock();
        block.setType(Material.CRYING_OBSIDIAN);

        maxRiftHp = plugin.getConfig().getInt("invasions.rift-hp", 100);
        riftHp = maxRiftHp;

        spawnHologram();

        invasionActive = true;

        String msg = "Внимание! Разлом из Бездны открылся на координатах: X:" + x + " Z:" + z + ". Уничтожьте его, пока не стало слишком поздно!";
        
        // Broadcast in Minecraft
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.DARK_PURPLE + "[Бездна] " + ChatColor.LIGHT_PURPLE + msg);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            
            // JSON Hover + Click logic inside Minecraft chat
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + p.getName() + " [\"\",{\"text\":\"[" + ChatColor.LIGHT_PURPLE + "Телепорт к Разлому" + ChatColor.WHITE + "]\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/vk tp " + x + " " + y + " " + z + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Нажмите для телепортации\"}}]");
        }

        startWaves();
    }

    private void spawnHologram() {
        if (holo != null) holo.remove();
        holo = (ArmorStand) riftLocation.getWorld().spawnEntity(riftLocation.clone().add(0.5, 1.5, 0.5), EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setGravity(false);
        holo.setCustomNameVisible(true);
        updateHologram();
    }

    private void updateHologram() {
        if (holo != null) {
            holo.setCustomName(ChatColor.DARK_PURPLE + "Разлом Бездны: " + ChatColor.LIGHT_PURPLE + riftHp + "/" + maxRiftHp + " \u2764");
        }
    }

    private void startWaves() {
        int waveInterval = plugin.getConfig().getInt("invasions.wave-interval", 30);
        int mobsPerWave = plugin.getConfig().getInt("invasions.mobs-per-wave", 5);

        waveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!invasionActive) {
                    this.cancel();
                    return;
                }
                
                // Spawn particle effects
                riftLocation.getWorld().spawnParticle(Particle.PORTAL, riftLocation.clone().add(0.5, 1.0, 0.5), 100, 1.0, 1.0, 1.0, 0.1);
                riftLocation.getWorld().playSound(riftLocation, Sound.BLOCK_BEACON_AMBIENT, 2.0f, 0.5f);

                // Spawn mobs
                for (int i = 0; i < mobsPerWave; i++) {
                    spawnVoidMob();
                }
            }
        };
        waveTask.runTaskTimer(plugin, 20L, waveInterval * 20L);
    }

    private void spawnVoidMob() {
        Location spawnLoc = riftLocation.clone().add(ThreadLocalRandom.current().nextInt(10) - 5, 1, ThreadLocalRandom.current().nextInt(10) - 5);
        int highestY = spawnLoc.getWorld().getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ());
        spawnLoc.setY(highestY + 1);

        if (ClaimProtection.isLocationClaimed(spawnLoc)) return;

        int type = ThreadLocalRandom.current().nextInt(3);
        LivingEntity mob;
        if (type == 0) {
            mob = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            mob.setCustomName(ChatColor.DARK_PURPLE + "Гниль Бездны");
            ((Zombie) mob).setBaby(false);
        } else if (type == 1) {
            mob = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
            mob.setCustomName(ChatColor.DARK_PURPLE + "Стрелок Бездны");
        } else {
            mob = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMAN);
            mob.setCustomName(ChatColor.DARK_PURPLE + "Тень Бездны");
        }

        mob.setCustomNameVisible(true);
        mob.getPersistentDataContainer().set(voidKey, PersistentDataType.BYTE, (byte) 1);
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999, 0));
        
        spawnedMobs.add(mob);
    }

    @EventHandler
    public void onRiftHit(PlayerInteractEvent e) {
        if (!invasionActive) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        
        Block b = e.getClickedBlock();
        if (b != null && b.getLocation().equals(riftLocation)) {
            e.setCancelled(true);
            
            riftHp--;
            updateHologram();
            
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            b.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, b.getLocation().add(0.5, 1.0, 0.5), 10);

            if (riftHp <= 0) {
                closeRift(e.getPlayer());
            }
        }
    }

    private void closeRift(Player killer) {
        invasionActive = false;
        
        riftLocation.getBlock().setType(Material.AIR);
        if (holo != null) holo.remove();
        if (waveTask != null) waveTask.cancel();

        // Kill remaining mobs
        for (Entity mob : spawnedMobs) {
            if (mob.isValid()) {
                mob.remove();
            }
        }
        spawnedMobs.clear();

        riftLocation.getWorld().playSound(riftLocation, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        riftLocation.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, riftLocation, 1);

        String msg = "Игрок " + killer.getName() + " успешно уничтожил Разлом Бездны и получил ценные награды!";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.GREEN + "[Бездна] " + msg);
        }

        if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatPlugin.getInstance().getApi().sendToMainChat("\u2728 " + msg);
        }

        dropLoot();
    }

    private void dropLoot() {
        List<String> lootStrings = plugin.getConfig().getStringList("invasions.loot");
        for (String ls : lootStrings) {
            try {
                String[] parts = ls.split(";");
                Material mat = Material.valueOf(parts[0].toUpperCase());
                int min = Integer.parseInt(parts[1]);
                int max = Integer.parseInt(parts[2]);
                int amount = min + ThreadLocalRandom.current().nextInt(max - min + 1);
                
                if (amount > 0) {
                    riftLocation.getWorld().dropItemNaturally(riftLocation, new ItemStack(mat, amount));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка парсинга лута Бездны: " + ls);
            }
        }
    }
}
