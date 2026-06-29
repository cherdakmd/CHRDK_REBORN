package ru.example.vkchatartifacts.bosses;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.items.ArtifactFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.reflect.Method;

public class BossManager extends BukkitRunnable implements Listener {
    private final VKChatArtifactsPlugin plugin;
    private final Map<java.util.UUID, Long> skillCooldowns = new HashMap<>();
    private static final long SKILL_COOLDOWN_MS = 5000L;
    
    private LivingEntity activeBoss = null;
    private String activeBossId = null;

    public BossManager(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearBosses() {
        if (activeBoss != null && !activeBoss.isDead()) {
            activeBoss.remove();
        }
        skillCooldowns.clear();
    }

    @Override
    public void run() {
        if (activeBoss != null && !activeBoss.isDead()) return; 
        
        ConfigurationSection list = plugin.getConfig().getConfigurationSection("bosses.list");
        if (list == null) return;
        
        List<String> keys = new ArrayList<>(list.getKeys(false));
        if (keys.isEmpty()) return;
        
        activeBossId = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        ConfigurationSection bossConfig = list.getConfigurationSection(activeBossId);

        World w = Bukkit.getWorld("world");
        if (w == null) return;

        int radius = plugin.getConfig().getInt("bosses.radius", 5000);
        int x = ThreadLocalRandom.current().nextInt(radius * 2) - radius;
        int z = ThreadLocalRandom.current().nextInt(radius * 2) - radius;
        int y = w.getHighestBlockYAt(x, z) + 1;
        Location spawnLoc = new Location(w, x, y, z);
        String name = ChatColor.translateAlternateColorCodes('&', bossConfig.getString("name", "Босс"));

        long preAnnounce = plugin.getConfig().getLong("bosses.announce-before", 900); // 15 минут
        
        String preMsgMC = "&5&m=================================================\n" +
                          " &d&lМИРОВОЕ СОБЫТИЕ!\n" +
                          " &fДревнее зло пробуждается...\n" +
                          " &f" + name + " &fпоявится через &a" + (preAnnounce/60) + " минут&f!\n" +
                          " &fКоординаты: X: &c" + x + " &fZ: &c" + z + "\n" +
                          "&5&m=================================================";
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', preMsgMC));

        String preMsgVK = " ДРЕВНЕЕ ЗЛО ПРОБУЖДАЕТСЯ!\n" + ChatColor.stripColor(name) + " появится на координатах X: " + x + ", Z: " + z + " ровно через " + (preAnnounce/60) + " минут!\nСобирайте союзников, за его голову дадут мощный Артефакт!";
        sendVkChat(preMsgVK);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String typeStr = bossConfig.getString("type", "ZOMBIE");
            EntityType type = EntityType.valueOf(typeStr);

            Entity entity = w.spawnEntity(spawnLoc, type);
            if (entity instanceof LivingEntity) {
                activeBoss = (LivingEntity) entity;
                
                activeBoss.setCustomName(name);
                activeBoss.setCustomNameVisible(true);
                activeBoss.setRemoveWhenFarAway(false);
                
                AttributeInstance hp = activeBoss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) {
                    double health = Math.min(1024.0, bossConfig.getDouble("health", 1000.0));
                    hp.setBaseValue(health);
                    activeBoss.setHealth(health);
                }
                
                AttributeInstance dmg = activeBoss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(bossConfig.getDouble("damage", 15.0));

                AttributeInstance speed = activeBoss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(bossConfig.getDouble("speed", 0.3));

                if (!bossConfig.getBoolean("aggressive", true)) {
                    if (activeBoss instanceof Mob) {
                        ((Mob) activeBoss).setTarget(null);
                    }
                }
            }

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&d&l⚡ БОСС ПОЯВИЛСЯ! Сражение началось!"));
            sendVkChat("⚡ БОСС ПОЯВИЛСЯ! " + ChatColor.stripColor(name) + " вошел в этот мир. Кто же заберет артефакт?");
            
        }, preAnnounce * 20L);
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent e) {
        if (activeBoss == null || e.getEntity() != activeBoss) return;
        if (!(e.getDamager() instanceof Player)) return;

        Player p = (Player) e.getDamager();
        ConfigurationSection bossConfig = plugin.getConfig().getConfigurationSection("bosses.list." + activeBossId);
        if (bossConfig == null) return;

        List<String> skills = bossConfig.getStringList("skills");
        if (skills.isEmpty()) return;

        java.util.UUID bossUuid = activeBoss.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastSkill = skillCooldowns.get(bossUuid);
        if (lastSkill != null && (now - lastSkill) < SKILL_COOLDOWN_MS) return;

        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            skillCooldowns.put(bossUuid, now);
            String skill = skills.get(ThreadLocalRandom.current().nextInt(skills.size()));
            
            switch (skill) {
                case "MINIONS":
                    activeBoss.getWorld().spawnEntity(activeBoss.getLocation().add(2, 0, 0), EntityType.SKELETON);
                    activeBoss.getWorld().spawnEntity(activeBoss.getLocation().add(-2, 0, 0), EntityType.SKELETON);
                    activeBoss.getWorld().spawnEntity(activeBoss.getLocation().add(0, 0, 2), EntityType.SKELETON);
                    p.sendMessage(ChatColor.RED + "Босс призывает миньонов!");
                    break;
                case "LIGHTNING":
                    activeBoss.getWorld().strikeLightning(p.getLocation());
                    break;
                case "PULL":
                    p.teleport(activeBoss.getLocation());
                    p.sendMessage(ChatColor.DARK_RED + "Босс притянул тебя к себе!");
                    break;
                case "EARTHQUAKE":
                    for (Entity ent : activeBoss.getNearbyEntities(10, 10, 10)) {
                        if (ent instanceof Player) {
                            ent.setVelocity(ent.getVelocity().setY(1.5));
                        }
                    }
                    break;
                case "POISON_CLOUD":
                    for (Entity ent : activeBoss.getNearbyEntities(5, 5, 5)) {
                        if (ent instanceof Player) {
                            ((Player) ent).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                        }
                    }
                    break;
                case "WEB_TRAP":
                    p.getLocation().getBlock().setType(org.bukkit.Material.COBWEB);
                    p.sendMessage(ChatColor.DARK_GRAY + "Ты застрял в паутине!");
                    break;
                case "TELEPORT":
                    Location loc = activeBoss.getLocation().add(ThreadLocalRandom.current().nextInt(10) - 5, 0, ThreadLocalRandom.current().nextInt(10) - 5);
                    activeBoss.teleport(loc);
                    break;
                case "BLINDNESS":
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                    break;
                case "WITHER_SKULL":
                    org.bukkit.entity.WitherSkull skull = activeBoss.launchProjectile(org.bukkit.entity.WitherSkull.class);
                    skull.setVelocity(p.getLocation().toVector().subtract(activeBoss.getLocation().toVector()).normalize().multiply(1.5));
                    break;
                case "FREEZE":
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 4));
                    p.sendMessage(ChatColor.AQUA + "Ты заморожен!");
                    break;
                case "FIRE_RING":
                    for (int i = 0; i < 360; i += 30) {
                        double rad = Math.toRadians(i);
                        Location flame = activeBoss.getLocation().add(Math.cos(rad) * 3, 0, Math.sin(rad) * 3);
                        flame.getBlock().setType(org.bukkit.Material.FIRE);
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (activeBoss != null && e.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) {
            ConfigurationSection bossConfig = plugin.getConfig().getConfigurationSection("bosses.list." + activeBossId);
            
            // Если секции drop нет в конфиге, ставим шанс 100%
            int dropChance = bossConfig.contains("drop.artifact-chance") ? bossConfig.getInt("drop.artifact-chance") : 100;
            double mythicChanceConfig = plugin.getConfig().getDouble("artifacts.mythic-chance", 5.0);
            
            // Гарантированный лут (расходники)
            e.getDrops().add(ru.example.vkchatartifacts.items.ConsumableFactory.generateCleanseScroll(plugin));
            e.getDrops().add(ru.example.vkchatartifacts.items.ConsumableFactory.generateReviveScroll(plugin));

            // Синтез Свиток шанс 30%
            if (ThreadLocalRandom.current().nextInt(100) < 30) {
                org.bukkit.inventory.ItemStack synth = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
                org.bukkit.inventory.meta.ItemMeta smeta = synth.getItemMeta();
                smeta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&d&l Свиток Синтеза"));
                java.util.List<String> slore = new java.util.ArrayList<>();
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Древний магический пергамент."));
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Позволяет объединить чары двух Легендарных"));
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7или Эпических предметов одного типа."));
                slore.add("");
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e▶ Положите предмет-основу в левую руку"));
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e▶ Положите предмет-жертву в инвентарь (первый слот)"));
                slore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e▶ Возьмите свиток в правую руку и нажмите ПКМ"));
                smeta.setLore(slore);
                synth.setItemMeta(smeta);
                e.getDrops().add(synth);
            }


            
            if (ThreadLocalRandom.current().nextInt(100) < dropChance) {
                boolean isMythic = ThreadLocalRandom.current().nextDouble() * 100 <= mythicChanceConfig;
                org.bukkit.inventory.ItemStack artifact = ArtifactFactory.generateArtifact(plugin, isMythic);
                e.getDrops().add(artifact);
                
                if (isMythic) {
                    Player killer = e.getEntity().getKiller();
                    String pName = killer != null ? killer.getName() : "Неизвестный Герой";
                    
                    String mcMsg = plugin.getConfig().getString("artifacts.messages.mythic-announce-mc").replace("{player}", pName);
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', mcMsg));
                    
                    String vkMsg = plugin.getConfig().getString("artifacts.messages.mythic-announce-vk").replace("{player}", pName);
                    sendVkChat(vkMsg);
                }
            }
            
            activeBoss = null;
            activeBossId = null;
            skillCooldowns.clear();
        }
    }
    
    private void sendVkChat(String msg) {
        try {
            Object corePlugin = Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null) {
                Method getApiMethod = corePlugin.getClass().getMethod("getApi");
                Object vkApi = getApiMethod.invoke(corePlugin);
                Method m = vkApi.getClass().getMethod("sendToMainChat", String.class);
                m.invoke(vkApi, msg);
            }
        } catch (Exception ignored) { }
    }
}
