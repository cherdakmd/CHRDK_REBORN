package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatevents.VKChatEventsPlugin;
import ru.example.vkchatevents.util.ClaimProtection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AirdropManager implements Listener {
    private final VKChatEventsPlugin plugin;
    
    private Location activeAirdrop = null;
    private String activeTier = null;
    private Entity activeBoss = null;
    private boolean bossDefeated = false;
    private ArmorStand hologram = null;
    
    // Захват
    private Player capturingPlayer = null;
    private int captureSecondsLeft = 0;
    private long lastCombatTime = 0;
    private int beaconTaskId = -1;
    private int reminderTaskId = -1;
    private int captureTaskId = -1;
    private int radiationTaskId = -1;
    private int combatActionCount = 0;

    public AirdropManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        startTimer();
    }

    private void startTimer() {
        long interval = plugin.getConfig().getLong("airdrops.interval", 3600) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnAirdrop, interval, interval);
    }

    private void trySpawnAirdrop() {
        if (activeAirdrop != null) return;
        
        int minOnline = plugin.getConfig().getInt("airdrops.min-online", 3);
        if (Bukkit.getOnlinePlayers().size() < minOnline) return;

        World w = Bukkit.getWorlds().get(0);
        int protectedRadius = plugin.getConfig().getInt("airdrops.protected-radius", plugin.getConfig().getInt("radiation.radius", 20));
        Location loc = ClaimProtection.findSafeWildernessLocation(w, 3000, protectedRadius, 80);
        if (loc == null) return;

        spawnAirdropAt(loc);
    }

    public void spawnAirdropAt(Location loc) {
        if (activeAirdrop != null) return; // Уже есть один
        int protectedRadius = plugin.getConfig().getInt("airdrops.protected-radius", Math.max(plugin.getConfig().getInt("airdrops.crater-radius", 3) + 4, plugin.getConfig().getInt("radiation.radius", 20)));
        if (plugin.getConfig().getBoolean("airdrops.prevent-nation-claims", true) && ClaimProtection.isAreaClaimed(loc, protectedRadius)) {
            plugin.getLogger().info("Аирдроп отменён: выбранная зона пересекается с приватной территорией.");
            return;
        }
        this.activeAirdrop = loc;
        
        List<String> tiers = new ArrayList<>(plugin.getConfig().getConfigurationSection("airdrops.tiers").getKeys(false));
        this.activeTier = tiers.get(ThreadLocalRandom.current().nextInt(tiers.size()));
        String tName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("airdrops.tiers." + activeTier + ".name"));

        int r = plugin.getConfig().getInt("airdrops.crater-radius", 3);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx*dx + dz*dz <= r*r) {
                    Location bLoc = loc.clone().add(dx, -1, dz);
                    if (!ClaimProtection.isLocationClaimed(bLoc) && bLoc.getBlock().getType() != Material.BEDROCK) {
                        bLoc.getBlock().setType(Material.COARSE_DIRT);
                        bLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, bLoc.clone().add(0, 1, 0), 1);
                    }
                }
            }
        }
        
        Block b = loc.getBlock();
        b.setType(Material.PURPLE_SHULKER_BOX);
        bossDefeated = false;
        
        String bossTypeStr = plugin.getConfig().getString("airdrops.tiers." + activeTier + ".boss");
        try {
            EntityType bossType = EntityType.valueOf(bossTypeStr);
            activeBoss = loc.getWorld().spawnEntity(loc.clone().add(0, 2, 0), bossType);
            activeBoss.setCustomName(ChatColor.RED + "Охранник Аирдропа");
            activeBoss.setCustomNameVisible(true);
            activeBoss.getPersistentDataContainer().set(new NamespacedKey(plugin, "airdrop_boss"), PersistentDataType.INTEGER, 1);
            if (activeBoss instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) activeBoss;
                le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(150.0);
                le.setHealth(150.0);
            }
        } catch (Exception ignored) {}

        List<String> mobs = plugin.getConfig().getStringList("airdrops.tiers." + activeTier + ".mobs");
        for (String m : mobs) {
            String[] p = m.split(";");
            try {
                EntityType type = EntityType.valueOf(p[0]);
                int count = Integer.parseInt(p[1]);
                for(int i=0; i<count; i++) {
                    loc.getWorld().spawnEntity(loc.clone().add(ThreadLocalRandom.current().nextInt(6)-3, 1, ThreadLocalRandom.current().nextInt(6)-3), type);
                }
            } catch (Exception ignored) {}
        }

        // VK Integration: Map / Riddle
        String bc;
        int fuzz = plugin.getConfig().getInt("airdrops.coord-fuzziness", 200);
        int minX = loc.getBlockX() - fuzz;
        int maxX = loc.getBlockX() + fuzz;
        int minZ = loc.getBlockZ() - fuzz;
        int maxZ = loc.getBlockZ() + fuzz;
        
        if (ThreadLocalRandom.current().nextBoolean()) {
            bc = " С небес рухнул " + tName + "!\n" +
                 " Загадка: Ищите его в квадрате X: " + minX + " до " + maxX + ", Z: " + minZ + " до " + maxZ + "!\n" +
                 "Биом похож на " + loc.getBlock().getBiome().name() + "\n" +
                 " Чтобы получить ТОЧНЫЕ координаты, купи их в ВК командой !аирдроп";
        } else {
            // "Карта"
            bc = " С небес рухнул " + tName + "!\n" +
                 " КАРТА ПАДЕНИЯ (Текстовая схема):\n" +
                 "[-" + maxZ + "].......(X).......[" + maxX + "]\n" +
                 " Чтобы получить ТОЧНЫЕ координаты, купи их в ВК командой !аирдроп";
        }
        
        Bukkit.broadcastMessage(ChatColor.AQUA + ChatColor.stripColor(bc.replace("\\n", "\n")));
        VKChatPlugin.getInstance().getApi().sendToMainChat(bc);

        // Луч маяка
        beaconTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (activeAirdrop != null) {
                for (int i = 0; i < 20; i++) {
                    activeAirdrop.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, activeAirdrop.clone().add(0.5, i * 2, 0.5), 10, 0, 0, 0, 0);
                }
            } else {
                Bukkit.getScheduler().cancelTask(beaconTaskId);
            }
        }, 0L, 20L);

        // Радиация
        if (plugin.getConfig().getBoolean("radiation.enabled", true)) {
            radiationTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (activeAirdrop == null) {
                    Bukkit.getScheduler().cancelTask(radiationTaskId);
                    return;
                }
                double radRadius = plugin.getConfig().getDouble("radiation.radius", 20.0);
                double dmg = plugin.getConfig().getDouble("radiation.damage", 2.0);
                String safePot = plugin.getConfig().getString("radiation.safe_potion", "FIRE_RESISTANCE");
                PotionEffectType safeType = PotionEffectType.getByName(safePot);
                
                for (Player p : activeAirdrop.getWorld().getPlayers()) {
                    if (p.getLocation().distance(activeAirdrop) <= radRadius && !ClaimProtection.isLocationClaimed(p.getLocation())) {
                        if (safeType == null || !p.hasPotionEffect(safeType)) {
                            p.damage(dmg);
                            p.sendMessage(ChatColor.DARK_GREEN + "☢ Вы находитесь в зоне радиации Аирдропа! Выпейте зелье защиты!");
                        }
                    }
                }
            }, 40L, 40L);
        }

        int seconds = plugin.getConfig().getInt("airdrops.reminder-interval", 300);
        long ticks = seconds * 20L;

        reminderTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (activeAirdrop != null && capturingPlayer == null) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "[Напоминание] " + tName + ChatColor.GRAY + " всё еще ждет своих героев!");
                VKChatPlugin.getInstance().getApi().sendToMainChat("⏳ Напоминание: " + ChatColor.stripColor(tName) + " всё еще не залутан! Забирайте скорее.");
            } else if (activeAirdrop == null) {
                Bukkit.getScheduler().cancelTask(reminderTaskId);
            }
        }, ticks, ticks);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (activeAirdrop != null && activeBoss != null && e.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) {
            bossDefeated = true;
            Bukkit.broadcastMessage(ChatColor.YELLOW + " Охранник Аирдропа убит! Сундук разблокирован для захвата!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && activeAirdrop != null) {
            Block b = e.getClickedBlock();
            if (b != null && b.getType().name().contains("SHULKER_BOX") && b.getLocation().equals(activeAirdrop)) {
                e.setCancelled(true);
                
                if (!bossDefeated) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Сначала убейте Охранника, чтобы получить доступ к сундуку!");
                    return;
                }
                
                if (activeTier.equals("fake")) {
                    // Ловушка!
                    b.setType(Material.AIR);
                    b.getWorld().createExplosion(b.getLocation(), 10.0f, true, true);
                    Bukkit.broadcastMessage(ChatColor.RED + " Игрок " + e.getPlayer().getName() + " попался в ловушку Подозрительного Груза! Это была бомба!");
                    VKChatPlugin.getInstance().getApi().sendToMainChat(" Игрок " + e.getPlayer().getName() + " вскрыл Подозрительный Груз, но это оказалась бомба!");
                    clearAirdropState();
                    return;
                }
                
                if (capturingPlayer == null) {
                    startCapture(e.getPlayer());
                } else if (!capturingPlayer.equals(e.getPlayer())) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Аирдроп уже взламывает " + capturingPlayer.getName() + "! Убейте или отгоните его!");
                }
            }
        }
        
        // Крафт Ракеты
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
            if (item != null && item.getType() == Material.FIREWORK_ROCKET && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Сигнальная Ракета")) {
                if (activeAirdrop != null) {
                    e.getPlayer().sendMessage(ChatColor.RED + "Аирдроп уже на карте! Вы не можете вызвать новый.");
                    e.setCancelled(true);
                    return;
                }
                
                item.setAmount(item.getAmount() - 1);
                Bukkit.broadcastMessage(ChatColor.GOLD + " Игрок " + e.getPlayer().getName() + " запустил Сигнальную Ракету!");
                spawnAirdropAt(e.getPlayer().getLocation().add(0, 0, 50)); // Падает рядом с игроком
            }
        }
    }

    private void startCapture(Player p) {
        capturingPlayer = p;
        captureSecondsLeft = plugin.getConfig().getInt("airdrops.capture-time", 30);
        lastCombatTime = 0;
        
        String tName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("airdrops.tiers." + activeTier + ".name"));
        String bc = " ВНИМАНИЕ! Игрок " + p.getName() + " начал взлом " + ChatColor.stripColor(tName) + "! У вас есть " + captureSecondsLeft + " секунд, чтобы его остановить!";
        Bukkit.broadcastMessage(ChatColor.RED + bc);
        VKChatPlugin.getInstance().getApi().sendToMainChat(bc);
        
        // Голограмма
        hologram = (ArmorStand) activeAirdrop.getWorld().spawnEntity(activeAirdrop.clone().add(0.5, 2, 0.5), EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomNameVisible(true);

        captureTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (capturingPlayer == null || activeAirdrop == null) {
                cancelCapture();
                return;
            }

            if (capturingPlayer.getLocation().distance(activeAirdrop) > 5) {
                capturingPlayer.sendMessage(ChatColor.RED + "Вы слишком далеко ушли от Аирдропа! Взлом отменен.");
                cancelCapture();
                return;
            }

            if (System.currentTimeMillis() - lastCombatTime < 5000) {
                hologram.setCustomName(ChatColor.RED + "ВЗЛОМ ПРИОСТАНОВЛЕН (БОЙ)");
                return; // Пауза
            }

            captureSecondsLeft--;
            hologram.setCustomName(ChatColor.GREEN + "ВЗЛОМ: " + captureSecondsLeft + " СЕК");

            if (captureSecondsLeft <= 0) {
                finishCapture();
            }
        }, 20L, 20L);
    }

    private void cancelCapture() {
        capturingPlayer = null;
        if (captureTaskId != -1) Bukkit.getScheduler().cancelTask(captureTaskId);
        if (hologram != null) { hologram.remove(); hologram = null; }
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Взлом Аирдропа был прерван! Сундук снова свободен.");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (activeAirdrop != null && e.getEntity().getLocation().distance(activeAirdrop) <= 15) {
            combatActionCount++;
        }
        if (capturingPlayer != null && e.getEntity().equals(capturingPlayer)) {
            lastCombatTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (capturingPlayer != null && e.getEntity().equals(capturingPlayer)) {
            cancelCapture();
        }
    }

    private void finishCapture() {
        if (captureTaskId != -1) Bukkit.getScheduler().cancelTask(captureTaskId);
        if (hologram != null) { hologram.remove(); hologram = null; }
        
        String tName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("airdrops.tiers." + activeTier + ".name"));
        
        if (activeTier.equals("cursed")) {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "☠ Игрок " + capturingPlayer.getName() + " вскрыл Проклятый Груз и выпустил зло!");
            for (Entity ent : activeAirdrop.getWorld().getNearbyEntities(activeAirdrop, 15, 15, 15)) {
                if (ent instanceof Player) {
                    ((Player) ent).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
                    ((Player) ent).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                }
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + " Игрок " + capturingPlayer.getName() + " успешно взломал " + tName + "!");
            VKChatPlugin.getInstance().getApi().sendToMainChat(" Игрок " + capturingPlayer.getName() + " успешно взломал и забрал " + ChatColor.stripColor(tName) + "!");
        }
        
        List<String> items = plugin.getConfig().getStringList("airdrops.tiers." + activeTier + ".items");
        
        double multiplier = 1.0 + (combatActionCount * 0.05);
        multiplier = Math.min(3.0, multiplier);
        
        for (String i : items) {
            String[] p = i.split(";");
            try {
                Material mat = Material.valueOf(p[0]);
                int min = Integer.parseInt(p[1]);
                int max = Integer.parseInt(p[2]);
                int amt = ThreadLocalRandom.current().nextInt(max - min + 1) + min;
                
                amt = (int) (amt * multiplier);
                
                if(amt > 0) activeAirdrop.getWorld().dropItemNaturally(activeAirdrop.clone().add(0, 1, 0), new ItemStack(mat, amt));
            } catch (Exception ignored) {}
        }
        
        if (multiplier > 1.0) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "⚔️ [Аирдроп] Из-за ожесточенного боя в зоне взлома (активность: " + combatActionCount + "), количество выпавшего лута выросло в " + String.format("%.1f", multiplier) + " раза!");
        }
        
        activeAirdrop.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, activeAirdrop.clone().add(0, 1, 0), 200, 1, 1, 1, 0.2);
        
        clearAirdropState();
    }
    
    private void clearAirdropState() {
        if (activeAirdrop != null) {
            activeAirdrop.getBlock().setType(Material.AIR);
        }
        if (beaconTaskId != -1) Bukkit.getScheduler().cancelTask(beaconTaskId);
        if (reminderTaskId != -1) Bukkit.getScheduler().cancelTask(reminderTaskId);
        if (radiationTaskId != -1) Bukkit.getScheduler().cancelTask(radiationTaskId);
        activeAirdrop = null;
        activeTier = null;
        capturingPlayer = null;
        combatActionCount = 0;
    }

    public boolean isActive() {
        return activeAirdrop != null;
    }
    
    public Location getActiveLocation() {
        return activeAirdrop;
    }
    
    public String getActiveTierName() {
        if (activeTier == null) return "Груз";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("airdrops.tiers." + activeTier + ".name", "Груз"));
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.getCommand().equals("!аирдроп") || e.getCommand().equals("!airdrop")) {
            e.setCancelled(true);
            if (activeAirdrop == null) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeerId(), "❌ Сейчас на сервере нет активных Аирдропов.");
                return;
            }
            
            int cost = plugin.getConfig().getInt("airdrops.exact-coords-cost", 50);
            int vkId = e.getSenderVkId();
            
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < cost) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeerId(), "❌ Для покупки точных координат нужно " + cost + " репутации ВК!");
                return;
            }
            
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            
            String tName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("airdrops.tiers." + activeTier + ".name")));
            String msg = " ТОЧНЫЕ КООРДИНАТЫ " + tName + ":\n" +
                         " X: " + activeAirdrop.getBlockX() + "\n" +
                         " Y: " + activeAirdrop.getBlockY() + "\n" +
                         " Z: " + activeAirdrop.getBlockZ() + "\n\n" +
                         "С вашего счета списано " + cost + " репутации. Удачи в бою!";
            VKChatPlugin.getInstance().getApi().sendMessage(e.getPeerId(), msg);
        }
    }
}
