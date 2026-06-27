package ru.example.vkchatteleport.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.example.vkchatteleport.VKChatTeleportPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {
    private final VKChatTeleportPlugin plugin;
    private final File homesFile;
    private FileConfiguration homesConfig;

    // Сетка домов: UUID -> Имя дома -> HomeLocation
    private final Map<UUID, Map<String, HomeLocation>> playerHomes = new ConcurrentHashMap<>();

    // Кулдауны игроков: Тип кулдауна -> UUID -> Время последнего использования (мс)
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    // Ожидающие TPA запросы: Кому (цель) -> От кого (отправитель)
    private final Map<UUID, UUID> tpaRequests = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tpaTimeoutTasks = new ConcurrentHashMap<>();

    // Текущие телепортации с задержкой: UUID -> WarmupTask
    private final Map<UUID, WarmupTask> activeWarmups = new ConcurrentHashMap<>();

    public TeleportManager(VKChatTeleportPlugin plugin) {
        this.plugin = plugin;
        this.homesFile = new File(plugin.getDataFolder(), "homes.yml");
        
        cooldowns.put("rtp", new ConcurrentHashMap<>());
        cooldowns.put("home", new ConcurrentHashMap<>());
        cooldowns.put("tpa", new ConcurrentHashMap<>());
        
        loadHomes();
    }

    // ==========================================
    // СИСТЕМА ДУШЕВНЫХ ДОМОВ (PERSISTENT HOMES)
    // ==========================================

    private void loadHomes() {
        playerHomes.clear();
        if (!homesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                homesFile.createNewFile();
            } catch (IOException ignored) {}
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
        if (homesConfig.contains("homes")) {
            for (String uuidStr : homesConfig.getConfigurationSection("homes").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, HomeLocation> homesMap = new ConcurrentHashMap<>();
                    if (homesConfig.getConfigurationSection("homes." + uuidStr) != null) {
                        for (String homeName : homesConfig.getConfigurationSection("homes." + uuidStr).getKeys(false)) {
                            String path = "homes." + uuidStr + "." + homeName;
                            String world = homesConfig.getString(path + ".world");
                            double x = homesConfig.getDouble(path + ".x");
                            double y = homesConfig.getDouble(path + ".y");
                            double z = homesConfig.getDouble(path + ".z");
                            float yaw = (float) homesConfig.getDouble(path + ".yaw");
                            float pitch = (float) homesConfig.getDouble(path + ".pitch");

                            homesMap.put(homeName.toLowerCase(), new HomeLocation(world, x, y, z, yaw, pitch));
                        }
                    }
                    playerHomes.put(uuid, homesMap);
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка загрузки домов для UUID " + uuidStr + ": " + e.getMessage());
                }
            }
        }
    }

    private void saveHomes() {
        homesConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, HomeLocation>> entry : playerHomes.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, HomeLocation> homeEntry : entry.getValue().entrySet()) {
                String path = "homes." + uuidStr + "." + homeEntry.getKey();
                HomeLocation loc = homeEntry.getValue();
                homesConfig.set(path + ".world", loc.worldName);
                homesConfig.set(path + ".x", loc.x);
                homesConfig.set(path + ".y", loc.y);
                homesConfig.set(path + ".z", loc.z);
                homesConfig.set(path + ".yaw", loc.yaw);
                homesConfig.set(path + ".pitch", loc.pitch);
            }
        }
        try {
            homesConfig.save(homesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить файл homes.yml: " + e.getMessage());
        }
    }

    public Map<String, HomeLocation> getHomes(UUID uuid) {
        return playerHomes.getOrDefault(uuid, Collections.emptyMap());
    }

    public HomeLocation getHome(UUID uuid, String name) {
        Map<String, HomeLocation> homes = playerHomes.get(uuid);
        if (homes == null) return null;
        return homes.get(name.toLowerCase());
    }

    public void setHome(UUID uuid, String name, Location loc) {
        Map<String, HomeLocation> homes = playerHomes.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        homes.put(name.toLowerCase(), new HomeLocation(
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch()
        ));
        saveHomes();
    }

    public boolean deleteHome(UUID uuid, String name) {
        Map<String, HomeLocation> homes = playerHomes.get(uuid);
        if (homes == null) return false;
        if (homes.remove(name.toLowerCase()) != null) {
            if (homes.isEmpty()) {
                playerHomes.remove(uuid);
            }
            saveHomes();
            return true;
        }
        return false;
    }

    public int getHomeCount(UUID uuid) {
        Map<String, HomeLocation> homes = playerHomes.get(uuid);
        return homes != null ? homes.size() : 0;
    }

    // ==========================================
    // КУЛДАУНЫ
    // ==========================================

    public long getCooldownRemaining(String type, UUID uuid, int cooldownSeconds) {
        Map<UUID, Long> map = cooldowns.get(type);
        if (map == null || !map.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - map.get(uuid);
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return remaining > 0 ? remaining / 1000L : 0;
    }

    public void setCooldown(String type, UUID uuid) {
        Map<UUID, Long> map = cooldowns.get(type);
        if (map != null) {
            map.put(uuid, System.currentTimeMillis());
        }
    }

    // ==========================================
    // TPA ЗАПРОСЫ
    // ==========================================

    public boolean sendTpaRequest(Player sender, Player target) {
        // Проверяем, есть ли уже запрос от этого отправителя к кому-то, или просто перезаписываем
        tpaRequests.put(target.getUniqueId(), sender.getUniqueId());

        // Снимаем старый таймаут таск если был
        BukkitTask oldTask = tpaTimeoutTasks.remove(target.getUniqueId());
        if (oldTask != null) oldTask.cancel();

        // Запуск таймаута на 60 секунд (1200 тиков)
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tpaRequests.remove(target.getUniqueId(), sender.getUniqueId())) {
                tpaTimeoutTasks.remove(target.getUniqueId());
                if (sender.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "⏳ Запрос на телепортацию к " + target.getName() + " истек.");
                }
            }
        }, 1200L);

        tpaTimeoutTasks.put(target.getUniqueId(), task);
        return true;
    }

    public UUID getTpaRequest(UUID targetId) {
        return tpaRequests.get(targetId);
    }

    public void clearTpaRequest(UUID targetId) {
        tpaRequests.remove(targetId);
        BukkitTask task = tpaTimeoutTasks.remove(targetId);
        if (task != null) task.cancel();
    }

    // ==========================================
    // ТЕЛЕПОРТАЦИЯ С ЗАДЕРЖКОЙ (WARMUP SYSTEM)
    // ==========================================

    public void startTeleportWarmup(Player player, Location target, String successMessage, int cost, String cooldownType, Runnable onComplete) {
        UUID uuid = player.getUniqueId();
        cancelActiveWarmup(uuid, false); // Отменяем старый вармап без сообщения о движении

        int delay = plugin.getConfig().getInt("teleportation.warmup.delay", 3);
        if (delay <= 0) {
            // Мгновенный телепорт
            executeTeleport(player, target, successMessage, cost, cooldownType, onComplete);
            return;
        }

        WarmupTask task = new WarmupTask(player, target, delay, successMessage, cost, cooldownType, onComplete);
        activeWarmups.put(uuid, task);
        task.run();
    }

    public boolean isTeleporting(UUID uuid) {
        return activeWarmups.containsKey(uuid);
    }

    public void cancelActiveWarmup(UUID uuid, boolean notify) {
        WarmupTask task = activeWarmups.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "❌ Телепортация отменена! Вы сдвинулись или получили урон.");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                }
            }
        }
    }

    private void executeTeleport(Player player, Location target, String successMessage, int cost, String cooldownType, Runnable onComplete) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(player);
        if (vkId == -1) {
            player.sendMessage(ChatColor.RED + "Для телепортации нужно привязать ВКонтакте (/vklink)!");
            return;
        }

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (currentRep < cost) {
            player.sendMessage(ChatColor.RED + "Тебе не хватает репутации ВКонтакте! Нужно: " + cost);
            return;
        }

        // Снимаем репутацию
        if (cost > 0) {
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        }

        // Телепортируем
        player.teleport(target);
        player.sendMessage(successMessage);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        // Спавн частиц на финише
        spawnFinishParticles(target);

        // Ставим кулдаун
        if (cooldownType != null) {
            setCooldown(cooldownType, player.getUniqueId());
        }

        // Запускаем кастомный коллбек если есть
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void spawnFinishParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        for (int i = 0; i < 30; i++) {
            double angle = i * (2 * Math.PI / 30);
            double x = loc.getX() + 0.8 * Math.cos(angle);
            double z = loc.getZ() + 0.8 * Math.sin(angle);
            world.spawnParticle(Particle.PORTAL, new Location(world, x, loc.getY() + 0.5, z), 1, 0, 0, 0, 0.1);
        }
    }

    // Внутренний класс для таски прогресса вармапа
    private class WarmupTask {
        private final Player player;
        private final Location target;
        private final int totalSeconds;
        private final String successMessage;
        private final int cost;
        private final String cooldownType;
        private final Runnable onComplete;
        
        private int secondsPassed = 0;
        private BukkitTask schedulerTask;

        public WarmupTask(Player player, Location target, int delay, String successMessage, int cost, String cooldownType, Runnable onComplete) {
            this.player = player;
            this.target = target;
            this.totalSeconds = delay;
            this.successMessage = successMessage;
            this.cost = cost;
            this.cooldownType = cooldownType;
            this.onComplete = onComplete;
        }

        public void run() {
            player.sendMessage(ChatColor.YELLOW + "⏳ Телепортация начнется через " + ChatColor.GOLD + totalSeconds + ChatColor.YELLOW + " сек. Не двигайтесь и не получайте урон!");
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.5f);

            schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) {
                    cancel();
                    activeWarmups.remove(player.getUniqueId());
                    return;
                }

                secondsPassed++;

                // Спавним спиральные частицы вокруг игрока каждую секунду
                spawnWarmupParticles(player.getLocation());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.5f + (secondsPassed * 0.3f));

                if (secondsPassed >= totalSeconds) {
                    cancel();
                    activeWarmups.remove(player.getUniqueId());
                    executeTeleport(player, target, successMessage, cost, cooldownType, onComplete);
                } else {
                    int secondsLeft = totalSeconds - secondsPassed;
                    player.sendMessage(ChatColor.YELLOW + "⏳ До телепортации осталось " + ChatColor.GOLD + secondsLeft + ChatColor.YELLOW + " сек...");
                }
            }, 20L, 20L);
        }

        public void cancel() {
            if (schedulerTask != null) {
                schedulerTask.cancel();
            }
        }

        private void spawnWarmupParticles(Location loc) {
            World world = loc.getWorld();
            if (world == null) return;
            for (double y = 0; y <= 2.0; y += 0.15) {
                double angle = y * Math.PI * 3 + (secondsPassed * 0.5); // Вращающаяся спираль
                double x = loc.getX() + 0.6 * Math.cos(angle);
                double z = loc.getZ() + 0.6 * Math.sin(angle);
                
                // Чередуем синее пламя и розовые искры!
                if (secondsPassed % 2 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, new Location(world, x, loc.getY() + y, z), 1, 0, 0, 0, 0.01);
                } else {
                    world.spawnParticle(Particle.REDSTONE, new Location(world, x, loc.getY() + y, z), 2, 0, 0, 0, 0.01, new Particle.DustOptions(org.bukkit.Color.FUCHSIA, 1.2f));
                }
                world.spawnParticle(Particle.SMOKE_NORMAL, loc.clone().add(0, 0.1, 0), 1, 0.3, 0.1, 0.3, 0.02);
            }
        }
    }

    // Вспомогательный класс-структура координат
    public static class HomeLocation {
        public final String worldName;
        public final double x, y, z;
        public final float yaw, pitch;

        public HomeLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }
}
