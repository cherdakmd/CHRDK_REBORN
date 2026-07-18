package ru.example.vkchat.game;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitRunnable;

import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureHuntManager extends BukkitRunnable implements Listener {

    private final VKChatPlugin plugin;
    private final Random random = ThreadLocalRandom.current();

    private boolean enabled;
    private long spawnIntervalTicks;
    private long despawnMillis;
    private int radius;
    private int compassCost;
    private List<LootEntry> lootTable;

    private Location treasureLocation;
    private long treasureSpawnTime;
    private boolean treasureActive;
    private final Map<Integer, Location> compassCooldowns = new HashMap<>();

    // Nation plugin hook (reflection)
    private Object nationsPlugin;
    private boolean nationsHooked = false;

    public TreasureHuntManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        reload();
        tryHookNations();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("treasure.enabled", true);
        int intervalMin = plugin.getConfig().getInt("treasure.spawn-interval-minutes", 120);
        this.spawnIntervalTicks = intervalMin * 60 * 20L;
        int despawnMin = plugin.getConfig().getInt("treasure.despawn-minutes", 60);
        this.despawnMillis = despawnMin * 60 * 1000L;
        this.radius = plugin.getConfig().getInt("treasure.radius-from-spawn", 500);
        this.compassCost = plugin.getConfig().getInt("treasure.compass-cost", 200);
        loadLootTable();
    }

    @SuppressWarnings("unchecked")
    private void loadLootTable() {
        lootTable = new ArrayList<>();
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("treasure.loot");
        for (Map<?, ?> entry : raw) {
            String material = (String) entry.get("material");
            int chance = entry.containsKey("chance") ? ((Number) entry.get("chance")).intValue() : 100;
            int min = entry.containsKey("min") ? ((Number) entry.get("min")).intValue() : 1;
            int max = entry.containsKey("max") ? ((Number) entry.get("max")).intValue() : 1;
            lootTable.add(new LootEntry(material, chance, min, max));
        }
    }

    private void tryHookNations() {
        try {
            org.bukkit.plugin.Plugin plug = Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (plug != null) {
                nationsPlugin = plug;
                nationsHooked = true;
                plugin.getLogger().info("[TreasureHunt] VKChatNations hooked!");
            }
        } catch (Exception e) {
            nationsHooked = false;
        }
    }

    // ====== COMMANDS ======

    public boolean executeCommand(Player player, String[] args) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Система сокровищ отключена.");
            return true;
        }
        if (args.length == 0) return false;

        if (args[0].equalsIgnoreCase("start")) {
            if (!player.hasPermission("vkchat.admin")) {
                player.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            startTreasure();
            return true;
        }

        if (args[0].equalsIgnoreCase("compass")) {
            giveCompass(player);
            return true;
        }

        return false;
    }

    // ====== TREASURE SPAWN ======

    public void startTreasure() {
        if (!enabled) return;
        if (treasureActive) {
            plugin.getLogger().info("[TreasureHunt] Treasure already active, skipping spawn.");
            return;
        }

        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();

        for (int attempts = 0; attempts < 50; attempts++) {
            int x = spawn.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = spawn.getBlockZ() + random.nextInt(radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z);

            Location loc = new Location(world, x, y, z);
            Block block = loc.getBlock();

            if (block.getType() != Material.AIR && !block.isLiquid()) continue;
            if (!loc.clone().add(0, 1, 0).getBlock().getType().isAir()) continue;

            loc.getBlock().setType(Material.CHEST);
            treasureLocation = loc;
            treasureSpawnTime = System.currentTimeMillis();
            treasureActive = true;

            fillChest(loc);

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6&l\u2605 Сундук с сокровищами появился на карте! (/treasure compass)"));

            plugin.getLogger().info("[TreasureHunt] Treasure spawned at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());

            // Notify nation if claimed
            if (nationsHooked) notifyNation(loc);

            return;
        }

        plugin.getLogger().warning("[TreasureHunt] Could not find valid location for treasure!");
    }

    private void fillChest(Location loc) {
        Chest chest = (Chest) loc.getBlock().getState();
        chest.getInventory().clear();

        for (int slot = 0; slot < Math.min(chest.getInventory().getSize(), 9); slot++) {
            if (random.nextInt(100) < 40) {
                ItemStack item = rollLoot();
                if (item != null) {
                    chest.getInventory().setItem(slot, item);
                }
            }
        }
    }

    private ItemStack rollLoot() {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (LootEntry entry : lootTable) {
            cumulative += entry.chance;
            if (roll < cumulative) {
                int amount = entry.min + random.nextInt(entry.max - entry.min + 1);
                Material mat = Material.getMaterial(entry.material);
                if (mat != null) {
                    return new ItemStack(mat, amount);
                }
            }
        }
        return null;
    }

    // ====== COMPASS ======

    private void giveCompass(Player player) {
        if (!treasureActive || treasureLocation == null) {
            player.sendMessage(ChatColor.RED + "Сейчас нет активного сокровища на карте.");
            return;
        }

        int vkId = plugin.getAuthManager().getLinkedVkId(player);
        if (vkId == -1) {
            player.sendMessage(ChatColor.RED + "Твой аккаунт не привязан к ВК!");
            return;
        }

        if (plugin.getReputationManager().getPoints(vkId) < compassCost) {
            player.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + compassCost);
            return;
        }

        plugin.getReputationManager().deductPoints(vkId, compassCost);

        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (meta != null) {
            meta.setLodestone(treasureLocation);
            meta.setLodestoneTracked(false);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&l\u2605 Компас сокровищ"));
            compass.setItemMeta(meta);
        }

        player.getInventory().addItem(compass);
        player.sendMessage(ChatColor.GREEN + "Компас сокровищ приобретён! Следуй за ним.");
    }

    // ====== NATION HOOK ======

    private void notifyNation(Location loc) {
        try {
            Object nation = nationsPlugin.getClass().getMethod("getClaimAt", Location.class).invoke(nationsPlugin, loc);
            if (nation != null) {
                String nationName = (String) nation.getClass().getMethod("getName").invoke(nation);
                String notifyMsg = ChatColor.translateAlternateColorCodes('&',
                        "&6&l\u2605 В землях нации " + nationName + " появилось сокровище!");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isPlayerInNation(p, nation)) {
                        p.sendMessage(notifyMsg);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TreasureHunt] Failed to notify nation: " + e.getMessage());
        }
    }

    private boolean isPlayerInNation(Player player, Object nation) {
        try {
            Object member = nation.getClass().getMethod("getMember", UUID.class).invoke(nation, player.getUniqueId());
            return member != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAtWarWithNation(Player player, Location loc) {
        if (!nationsHooked) return false;
        try {
            Object nation = nationsPlugin.getClass().getMethod("getClaimAt", Location.class).invoke(nationsPlugin, loc);
            if (nation == null) return false;
            return (boolean) nationsPlugin.getClass().getMethod("isAtWar", Player.class, Object.class).invoke(nationsPlugin, player, nation);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canOpenInClaim(Player player, Location loc) {
        if (!nationsHooked) return true;
        try {
            Object nation = nationsPlugin.getClass().getMethod("getClaimAt", Location.class).invoke(nationsPlugin, loc);
            if (nation == null) return true;
            return !isAtWarWithNation(player, loc);
        } catch (Exception e) {
            return true;
        }
    }

    // ====== LISTENERS ======

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (!treasureActive || treasureLocation == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        if (!block.getLocation().equals(treasureLocation)) return;

        Player player = event.getPlayer();

        // Anti-farm: if player placed a block on the chest location, invalidate
        if (!isNaturalChest(block)) {
            player.sendMessage(ChatColor.RED + "Этот сундук не является сокровищем!");
            return;
        }

        // Nation war check
        if (!canOpenInClaim(player, treasureLocation)) {
            player.sendMessage(ChatColor.RED + "Ваша нация в состоянии войны с владельцами этой земли! Нельзя открыть.");
            event.setCancelled(true);
            return;
        }

        // Give loot directly to player (the chest already has items from fillChest)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&e" + player.getName() + " &6нашёл сокровище!"));

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&e" + player.getName() + " &6нашёл сокровище!"));

        // Clear and despawn
        block.setType(Material.AIR);
        treasureActive = false;
        treasureLocation = null;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!treasureActive || treasureLocation == null) return;
        Block placed = event.getBlockPlaced();
        if (placed.getLocation().equals(treasureLocation)) {
            placed.setType(Material.AIR);
            treasureActive = false;
            treasureLocation = null;
            Bukkit.broadcastMessage(ChatColor.RED + "Сундук с сокровищем был уничтожен!");
        }
    }

    // ====== ANTI-FARM / CHEST VALIDATION ======

    private boolean isNaturalChest(Block block) {
        if (block.getType() != Material.CHEST) return false;
        if (treasureLocation == null) return false;
        return block.getLocation().equals(treasureLocation);
    }

    // ====== AUTO SPAWN TASK ======

    @Override
    public void run() {
        if (!enabled) return;

        // Despawn check
        if (treasureActive && treasureLocation != null) {
            if (System.currentTimeMillis() - treasureSpawnTime >= despawnMillis) {
                treasureLocation.getBlock().setType(Material.AIR);
                treasureLocation = null;
                treasureActive = false;
                Bukkit.broadcastMessage(ChatColor.RED + "Сундук с сокровищем исчез!");
                plugin.getLogger().info("[TreasureHunt] Treasure despawned (timeout).");
            }
            return;
        }

        startTreasure();
    }

    public void startAutoTask() {
        runTaskTimer(plugin, spawnIntervalTicks, spawnIntervalTicks);
    }

    public void onDisable() {
        if (treasureActive && treasureLocation != null) {
            treasureLocation.getBlock().setType(Material.AIR);
            treasureActive = false;
            treasureLocation = null;
        }
    }

    public boolean isTreasureActive() {
        return treasureActive;
    }

    public Location getTreasureLocation() {
        return treasureLocation;
    }

    // ====== LOOT ENTRY ======

    private static class LootEntry {
        final String material;
        final int chance;
        final int min;
        final int max;

        LootEntry(String material, int chance, int min, int max) {
            this.material = material;
            this.chance = chance;
            this.min = min;
            this.max = max;
        }
    }
}
