package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система вторжений Энда в обычный мир
 */
public class EndInvasionManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey invasionMobKey;

    // Активные вторжения
    private final Map<String, InvasionData> activeInvasions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageTracker = new ConcurrentHashMap<>();

    // Типы вторжений
    public enum InvasionType {
        ENDER_RAIDS("Эндер-рейды", "Группы эндерменов атакуют деревни", 30, ChatColor.DARK_PURPLE),
        VOID_PORTAL("Портал Бездны", "Портал спавнит мобов Энда", 60, ChatColor.DARK_GRAY),
        SHULKER_DROP("Десант шалкеров", "Шалкеры падают с неба", 45, ChatColor.GOLD),
        DRAGON_ATTACK("Атака Дракона", "Дракон атакует мир", 90, ChatColor.RED),
        CORRUPTION_SPREAD("Распространение коррупции", "Коррупция заражает обычный мир", 120, ChatColor.DARK_RED);

        public final String displayName;
        public final String description;
        public final int durationMinutes;
        public final ChatColor color;

        InvasionType(String displayName, String description, int durationMinutes, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.durationMinutes = durationMinutes;
            this.color = color;
        }
    }

    private static class InvasionData {
        InvasionType type;
        Location center;
        int radius;
        long startTime;
        long endTime;
        int wave;
        BossBar bossBar;

        InvasionData(InvasionType type, Location center, int radius) {
            this.type = type;
            this.center = center;
            this.radius = radius;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (type.durationMinutes * 60000L);
            this.wave = 0;
        }

        boolean isActive() {
            return System.currentTimeMillis() < endTime;
        }
    }

    public EndInvasionManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.invasionMobKey = new NamespacedKey(plugin, "invasion_mob");
        startInvasionTask();
    }

    /**
     * Задача проверки вторжений
     */
    private void startInvasionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkInvasions();
                maybeStartInvasion();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Каждую минуту
    }

    /**
     * Проверка активных вторжений
     */
    private void checkInvasions() {
        Iterator<Map.Entry<String, InvasionData>> it = activeInvasions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, InvasionData> entry = it.next();
            InvasionData invasion = entry.getValue();

            if (!invasion.isActive()) {
                endInvasion(invasion);
                it.remove();
                continue;
            }

            // Спавн волн
            long elapsed = System.currentTimeMillis() - invasion.startTime;
            int newWave = (int) (elapsed / 120000); // Каждые 2 минуты — новая волна
            if (newWave > invasion.wave) {
                invasion.wave = newWave;
                spawnInvasionWave(invasion);
            }

            // Обновление BossBar
            if (invasion.bossBar != null) {
                long remaining = invasion.endTime - System.currentTimeMillis();
                long total = invasion.endTime - invasion.startTime;
                double progress = (double) remaining / total;
                invasion.bossBar.setProgress(Math.max(0, Math.min(1, progress)));
                invasion.bossBar.setTitle(invasion.type.color + "⚔ " + invasion.type.displayName + " — Волна " + invasion.wave);
            }
        }
    }

    /**
     * Попытаться начать вторжение
     */
    private void maybeStartInvasion() {
        if (activeInvasions.size() >= plugin.getConfig().getInt("end.invasions.max-active", 2)) return;

        double chance = plugin.getConfig().getDouble("end.invasions.spawn-chance", 0.01);
        if (new Random().nextDouble() >= chance) return;

        World normalWorld = Bukkit.getWorlds().get(0);
        if (normalWorld == null) return;

        // Найти игрока
        List<Player> players = new ArrayList<>();
        for (Player p : normalWorld.getPlayers()) {
            if (p.getLocation().getY() > 60) { // Над землей
                players.add(p);
            }
        }
        if (players.isEmpty()) return;

        Player target = players.get(new Random().nextInt(players.size()));
        Location center = target.getLocation().add(
                new Random().nextInt(200) - 100,
                0,
                new Random().nextInt(200) - 100
        );
        center.setY(normalWorld.getHighestBlockYAt(center) + 1);

        // Выбрать тип вторжения
        InvasionType[] types = InvasionType.values();
        InvasionType type = types[new Random().nextInt(types.length)];

        startInvasion(center, type);
    }

    /**
     * Начать вторжение
     */
    public void startInvasion(Location center, InvasionType type) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        int radius = plugin.getConfig().getInt("end.invasions.radius", 50);

        InvasionData invasion = new InvasionData(type, center, radius);
        activeInvasions.put(key, invasion);

        // Создать BossBar
        invasion.bossBar = Bukkit.createBossBar(
                type.color + "⚔ " + type.displayName,
                BarColor.PURPLE,
                BarStyle.SEGMENTED_10
        );
        invasion.bossBar.setVisible(true);

        // Уведомление
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");
        Bukkit.broadcastMessage(type.color + "⚔ ВТОРЖЕНИЕ ЭНДА: " + type.displayName);
        Bukkit.broadcastMessage(ChatColor.GRAY + type.description);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Координаты: " + center.getBlockX() + ", " + center.getBlockZ());
        Bukkit.broadcastMessage(ChatColor.GRAY + "Радиус: " + radius + " блоков");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Длительность: " + type.durationMinutes + " минут");
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");

        // Первая волна
        spawnInvasionWave(invasion);
    }

    /**
     * Спавн волны вторжения
     */
    private void spawnInvasionWave(InvasionData invasion) {
        World world = invasion.center.getWorld();
        if (world == null) return;

        int mobsPerWave = 5 + (invasion.wave * 3);
        String waveMsg;

        switch (invasion.type) {
            case ENDER_RAIDS:
                waveMsg = "Эндермены";
                for (int i = 0; i < mobsPerWave; i++) {
                    spawnInvasionMob(world, invasion.center, invasion.radius, EntityType.ENDERMAN, invasion.wave);
                }
                break;

            case VOID_PORTAL:
                waveMsg = "Мобы Бездны";
                for (int i = 0; i < mobsPerWave; i++) {
                    EntityType type = new Random().nextBoolean() ? EntityType.ENDERMITE : EntityType.SHULKER;
                    spawnInvasionMob(world, invasion.center, invasion.radius, type, invasion.wave);
                }
                break;

            case SHULKER_DROP:
                waveMsg = "Шалкеры";
                for (int i = 0; i < mobsPerWave; i++) {
                    Location spawnLoc = invasion.center.clone().add(
                            new Random().nextInt(invasion.radius * 2) - invasion.radius,
                            20 + new Random().nextInt(10),
                            new Random().nextInt(invasion.radius * 2) - invasion.radius
                    );
                    Shulker shulker = (Shulker) world.spawnEntity(spawnLoc, EntityType.SHULKER);
                    shulker.getPersistentDataContainer().set(invasionMobKey, PersistentDataType.INTEGER, invasion.wave);
                    applyInvasionEffects(shulker, invasion.wave);
                }
                break;

            case DRAGON_ATTACK:
                waveMsg = "Драконы";
                for (int i = 0; i < Math.min(3, invasion.wave); i++) {
                    Location spawnLoc = invasion.center.clone().add(
                            new Random().nextInt(30) - 15,
                            20,
                            new Random().nextInt(30) - 15
                    );
                    EnderDragon dragon = (EnderDragon) world.spawnEntity(spawnLoc, EntityType.ENDER_DRAGON);
                    dragon.getPersistentDataContainer().set(invasionMobKey, PersistentDataType.INTEGER, invasion.wave);
                    applyInvasionEffects(dragon, invasion.wave);
                }
                break;

            case CORRUPTION_SPREAD:
                waveMsg = "Коррупция";
                // Распространение блоков коррупции
                for (int i = 0; i < mobsPerWave * 2; i++) {
                    int x = invasion.center.getBlockX() + new Random().nextInt(invasion.radius * 2) - invasion.radius;
                    int z = invasion.center.getBlockZ() + new Random().nextInt(invasion.radius * 2) - invasion.radius;
                    int y = world.getHighestBlockYAt(x, z);
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.GRASS_BLOCK || block.getType() == Material.DIRT) {
                        block.setType(Material.END_STONE);
                    }
                }
                // Спавн мобов
                for (int i = 0; i < mobsPerWave; i++) {
                    spawnInvasionMob(world, invasion.center, invasion.radius, EntityType.ENDERMITE, invasion.wave);
                }
                break;

            default:
                waveMsg = "Мобы";
        }

        Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave + ": " + waveMsg + "!");
    }

    /**
     * Спавн моба вторжения
     */
    private void spawnInvasionMob(World world, Location center, int radius, EntityType type, int wave) {
        Location spawnLoc = center.clone().add(
                new Random().nextInt(radius * 2) - radius,
                0,
                new Random().nextInt(radius * 2) - radius
        );
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

        Entity entity = world.spawnEntity(spawnLoc, type);
        if (entity instanceof LivingEntity) {
            LivingEntity mob = (LivingEntity) entity;
            mob.getPersistentDataContainer().set(invasionMobKey, PersistentDataType.INTEGER, wave);
            applyInvasionEffects(mob, wave);
        }
    }

    /**
     * Применить эффекты вторжения
     */
    private void applyInvasionEffects(LivingEntity mob, int wave) {
        double multiplier = 1.0 + (wave * 0.25);

        // Усиление здоровья
        if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            double baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseHealth * multiplier);
            mob.setHealth(mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }

        // Усиление урона
        if (mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseDamage = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * multiplier);
        }

        // Эффекты
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, Math.min(2, wave / 3)));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, Math.min(2, wave / 4)));

        // Имя
        mob.setCustomName(ChatColor.DARK_PURPLE + "☠ Эндер-захватчик [" + wave + "]");
        mob.setCustomNameVisible(true);
        mob.setGlowing(true);
    }

    /**
     * Завершение вторжения
     */
    private void endInvasion(InvasionData invasion) {
        if (invasion.bossBar != null) {
            invasion.bossBar.removeAll();
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GREEN + "⚔ ВТОРЖЕНИЕ ОКОНЧЕНО: " + invasion.type.displayName);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Волн пережито: " + invasion.wave);
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════════");

        // Награды всем игрокам в зоне
        World world = invasion.center.getWorld();
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(invasion.center) <= invasion.radius) {
                    int reward = 100 + (invasion.wave * 50);
                    try {
                        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                        if (vkId != -1) {
                            VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
                        }
                    } catch (Exception ignored) {}
                    plugin.getEndManager().addEndReputation(p, reward / 2);
                    p.sendMessage(ChatColor.GOLD + "⚔ Вторжение отражено! +" + reward + " реп.");
                }
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!mob.getPersistentDataContainer().has(invasionMobKey, PersistentDataType.INTEGER)) return;

        Player killer = mob.getKiller();
        if (killer == null) return;

        int wave = mob.getPersistentDataContainer().getOrDefault(invasionMobKey, PersistentDataType.INTEGER, 1);
        int rep = 10 + (wave * 5);

        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(killer);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(killer, rep / 2);
        killer.sendMessage(ChatColor.DARK_PURPLE + "☠ Эндер-захватчик повержен! +" + rep + " реп.");
    }

    /**
     * Получить количество активных вторжений
     */
    public int getActiveInvasionCount() {
        return activeInvasions.size();
    }

    /**
     * Получить информацию о вторжениях
     */
    public String getInvasionsInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ⚔ Вторжения Энда ═══\n\n");

        for (InvasionType type : InvasionType.values()) {
            sb.append(type.color).append("• ").append(type.displayName);
            sb.append(ChatColor.GRAY).append(" — ").append(type.description);
            sb.append(ChatColor.DARK_GRAY).append(" (").append(type.durationMinutes).append(" мин)\n");
        }

        sb.append("\n").append(ChatColor.GRAY).append("Активных вторжений: ").append(activeInvasions.size());

        return sb.toString();
    }
}
