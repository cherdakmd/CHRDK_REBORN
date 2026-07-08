package ru.example.vkchatnations.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

/**
 * MutationEffectScheduler — периодический планировщик пассивных мутаций наций.
 *
 * Извлечено из NationListener (конструктор, ~150 строк):
 * - 11 пассивных мутаций (Артельный Труд, Единение с Лесом, Противоядие и т.д.)
 * - Фестиваль нации (Спешка + Удача)
 * - Тактические Очки КГБ (Ночное зрение)
 * - Баффы внутри приватов нации
 *
 * Запускается каждые 2 секунды (40 тиков).
 */
public class MutationEffectScheduler {

    private final VKChatNationsPlugin plugin;

    public MutationEffectScheduler(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запустить периодический таск мутаций.
     */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) continue;

            tickFestival(p, nation);
            tickNationalHelmet(p);
            tickClaimBuffs(p, nation);
            tickPassiveMutations(p, nation);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ФЕСТИВАЛЬ НАЦИИ
    // ═══════════════════════════════════════════════════════════════

    private void tickFestival(Player p, String nation) {
        long endTime = plugin.getNationManager().getFestivalEndTime(nation);
        if (endTime <= 0) return;

        if (System.currentTimeMillis() < endTime) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, 0, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0, true, false, false));
            p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 2.1, 0), 2, 0.1, 0.1, 0.1, 0.01);
        } else {
            plugin.getNationManager().setFestivalEndTime(nation, 0L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ТАКТИЧЕСКИЕ ОЧКИ КГБ
    // ═══════════════════════════════════════════════════════════════

    private void tickNationalHelmet(Player p) {
        org.bukkit.inventory.ItemStack helmet = p.getInventory().getHelmet();
        if (helmet == null || !helmet.hasItemMeta()) return;

        String nationalId = helmet.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "national_item"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
        if ("kgb_glasses".equals(nationalId)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, false));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // БАФФЫ ВНУТРИ ПРИВАТОВ НАЦИИ
    // ═══════════════════════════════════════════════════════════════

    private void tickClaimBuffs(Player p, String nation) {
        ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
        if (claim == null || !nation.equals(claim.getNation())) return;

        java.util.List<String> buffs = plugin.getConfig().getStringList("nations." + nation + ".bonuses");
        for (String b : buffs) {
            String[] parts = b.split(";");
            if (parts.length == 2) {
                try {
                    PotionEffectType type = PotionEffectType.getByName(parts[0]);
                    int lvl = Integer.parseInt(parts[1]);
                    if (type != null) {
                        p.addPotionEffect(new PotionEffect(type, 100, lvl - 1, true, false, false));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ПАССИВНЫЕ МУТАЦИИ
    // ═══════════════════════════════════════════════════════════════

    private void tickPassiveMutations(Player p, String nation) {
        var mgr = plugin.getNationManager();

        // 1. Артельный Труд (collective_labor)
        if (mgr.hasMutation(p, "collective_labor")) {
            int nearbyCitizens = 0;
            for (org.bukkit.entity.Entity ent : p.getNearbyEntities(15, 15, 15)) {
                if (ent instanceof Player other && nation.equals(mgr.getPlayerNation(other))) {
                    nearbyCitizens++;
                }
            }
            if (nearbyCitizens > 0) {
                int amp = Math.min(2, nearbyCitizens - 1);
                p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, amp, true, false));
            }
        }

        // 2. Единение с Лесом (forest_communion)
        if (mgr.hasMutation(p, "forest_communion")) {
            String biome = p.getLocation().getBlock().getBiome().name();
            if (biome.contains("FOREST") || biome.contains("TAIGA") || biome.contains("JUNGLE")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false));
            }
        }

        // 3. Противоядие КГБ (poison_immunity)
        if (mgr.hasMutation(p, "poison_immunity")) {
            p.removePotionEffect(PotionEffectType.POISON);
        }

        // 4. Аура Страха (terror_aura)
        if (mgr.hasMutation(p, "terror_aura")) {
            for (org.bukkit.entity.Entity ent : p.getNearbyEntities(8, 8, 8)) {
                if (ent instanceof org.bukkit.entity.Monster) {
                    ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, true, false));
                    ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false));
                }
            }
        }

        // 5. Здоровье Рабочего (proletarian_health)
        if (mgr.hasMutation(p, "proletarian_health")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, true, false, false));
        }

        // 6. Радар КГБ (kgb_radar)
        if (mgr.hasMutation(p, "kgb_radar")) {
            for (org.bukkit.entity.Entity ent : p.getNearbyEntities(12, 12, 12)) {
                if (ent instanceof LivingEntity le && ent != p) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false));
                }
            }
        }

        // 7. Милость Ярилы (pagan_luck)
        if (mgr.hasMutation(p, "pagan_luck")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0, true, false, false));
        }

        // 8. Дыхание Водяного (pagan_breath)
        if (mgr.hasMutation(p, "pagan_breath")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, true, false, false));
        }

        // 9. Теневой Шаг (shadow_speed)
        if (mgr.hasMutation(p, "shadow_speed")) {
            long time = p.getWorld().getTime();
            if (time > 13000 && time < 23000) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false, false));
            }
        }

        // 10. Милость Монарха (hero_of_villages)
        if (mgr.hasMutation(p, "hero_of_villages")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, 0, true, false, false));
        }

        // 11. Стальная Воля (iron_skin)
        if (mgr.hasMutation(p, "iron_skin")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, true, false, false));
        }
    }
}
