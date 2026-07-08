package ru.example.vkchatmobs.drop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatmobs.util.BloodMoonHelper;
import ru.example.vkchatmobs.util.VKChatBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MobDropFactory — выделенная фабрика лута и репутации за убийство мобов.
 *
 * FIX #2:  Вынесено ~250 строк из MobListener.onMobDeath().
 * IMPROVE #2: Конфиг-управляемые таблицы дропа с масштабированием по рангу.
 */
public class MobDropFactory {

    private final VKChatMobsPlugin plugin;

    // --- Фарм-лимит репутации ---
    private final Map<UUID, Integer> farmedRepToday = new ConcurrentHashMap<>();
    private final Map<UUID, Long> farmResetTimes = new ConcurrentHashMap<>();

    public MobDropFactory(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════
    //  Репутация
    // ═══════════════════════════════════════════

    /**
     * Начислить репутацию ВК за убийство моба.
     * Поддерживает проходку (pass holders) через addEffectiveRep.
     *
     * @return начисленное количество репутации (0 если лимит/ошибка)
     */
    public int awardReputation(Player killer, int rank, boolean isMiniBoss, boolean isSuperBoss) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("VKChat")) {
                // Базовая репутация
                int baseRep = plugin.getConfig().getInt("loot.rep.base", 2);
                int finalRep = baseRep + (rank - 1) * plugin.getConfig().getInt("loot.rep.per-rank", 2);

                if (isSuperBoss) {
                    finalRep += plugin.getConfig().getInt("loot.rep.super-boss-bonus", 50);
                } else if (isMiniBoss) {
                    finalRep += plugin.getConfig().getInt("loot.rep.mini-boss-bonus", 15);
                }

                // Кровавая Луна — удвоение
                if (BloodMoonHelper.isBloodMoonActive()) {
                    finalRep *= 2;
                }

                // --- Динамический лимит фарма ---
                int totalJobLevels = getJobLevels(killer);
                UUID pUuid = killer.getUniqueId();
                long now = System.currentTimeMillis();

                if (now - farmResetTimes.getOrDefault(pUuid, 0L) >= 3600000L) {
                    farmResetTimes.put(pUuid, now);
                    farmedRepToday.put(pUuid, 0);
                }

                int maxHourRep = plugin.getConfig().getInt("loot.rep.max-base", 300)
                        + totalJobLevels * plugin.getConfig().getInt("loot.rep.per-job-level", 3);
                int current = farmedRepToday.getOrDefault(pUuid, 0);

                if (current >= maxHourRep) {
                    killer.sendMessage(ChatColor.RED + "⚠️ Лимит фарма! На основе ваших профессий лимит составляет "
                            + maxHourRep + " реп/час. Вы набили максимум. Отдохните!");
                    return 0;
                }

                farmedRepToday.merge(pUuid, finalRep, Integer::sum);

                // Используем addEffectiveRep для поддержки проходки
                boolean awarded = VKChatBridge.addEffectiveRep(killer, finalRep);
                if (!awarded) {
                    // Fallback: старый путь через vkId
                    int vkId = VKChatBridge.getLinkedVkId(killer);
                    if (vkId != -1) {
                        VKChatBridge.addPoints(vkId, finalRep);
                    }
                }

                // Сообщение
                String message = ChatColor.GOLD + "🔺 +" + finalRep + " репутации ВК за убийство "
                        + (isSuperBoss ? "Мирового Босса" : (isMiniBoss ? "Мини-Босса" : "монстра"))
                        + "!";
                if (BloodMoonHelper.isBloodMoonActive()) {
                    message += ChatColor.RED + " 🌙 (Бонус Кровавой Луны!)";
                }
                killer.sendMessage(message);
                return finalRep;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    // ═══════════════════════════════════════════
    //  Кристаллы / Свитки
    // ═══════════════════════════════════════════

    /**
     * Попытаться дропнуть кристалл/свиток с моба ранга >= 9.
     * Шанс из конфига: loot.crystal-scroll-chance (default 5).
     */
    public void rollCrystalScrollDrop(LivingEntity mob, Player killer, int rank) {
        if (rank < 9 || killer == null) return;

        int chance = plugin.getConfig().getInt("loot.crystal-scroll-chance", 5);
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;

        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null || !gearPlugin.isEnabled()) return;

        ItemStack dropToGive = rollCrystalOrScroll(gearPlugin, rank);
        if (dropToGive != null) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), dropToGive);
            killer.sendMessage(ChatColor.LIGHT_PURPLE + "✨ ОСОБЫЙ ДРОП! С сильного элитного монстра выпал: "
                    + dropToGive.getItemMeta().getDisplayName() + ChatColor.LIGHT_PURPLE + "!");
        }
    }

    /**
     * Выбрать случайный кристалл или свиток.
     * Рекомендуется делегировать в RuneRegistry из VKChatGear,
     * но fallback — локальное создание.
     */
    private ItemStack rollCrystalOrScroll(org.bukkit.plugin.Plugin gearPlugin, int rank) {
        int roll = ThreadLocalRandom.current().nextInt(6);

        if (roll == 0) {
            return createScroll(gearPlugin);
        } else if (roll == 1) {
            return createCrystal(gearPlugin, Material.DIAMOND, "rare", "Редкий [XI-XV]", "§9💎 Кристалл Заточки: Редкий [XI-XV]", 900);
        } else if (roll == 2) {
            return createCrystal(gearPlugin, Material.EMERALD, "common", "Обычный [I-X]", "§a💎 Кристалл Заточки: Обычный [I-X]", 400);
        } else if (roll == 3) {
            return createCrystal(gearPlugin, Material.NETHER_STAR, "legendary", "Легендарный [XVI-XX]", "§5💎 Кристалл Заточки: Легендарный [XVI-XX]", 1500);
        } else if (roll == 4 && rank >= 10) {
            return createCrystal(gearPlugin, Material.HEART_OF_THE_SEA, "ancient", "Древний [XXI-XXV]", "§c💎 Кристалл Заточки: Древний [XXI-XXV]", 2500);
        }
        return null;
    }

    private ItemStack createCrystal(org.bukkit.plugin.Plugin gearPlugin, Material mat, String tier, String tierName, String displayName, int price) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Позволяет затачивать снаряжение.");
        if (tier.equals("ancient")) {
            lore.add("§cВысочайшая ступень мастерства.");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, tier);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, tierName);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, price);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createScroll(org.bukkit.plugin.Plugin gearPlugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lСвиток Сохранения");
        List<String> lore = new ArrayList<>();
        lore.add("§7Защищает предмет от отката");
        lore.add("§7уровня заточки при неудаче!");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "safety_scroll_price"), PersistentDataType.INTEGER, 1500);
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════
    //  Жетоны / Осколки
    // ═══════════════════════════════════════════

    /**
     * Выдать жетоны и осколки за супер-боссов / мини-боссов.
     */
    public void awardTokensAndShards(LivingEntity mob, Player killer, boolean isSuperBoss, boolean isMiniBoss, String bossType) {
        if (killer == null) return;

        if (isSuperBoss) {
            // 100% Осколок Артефакта + 2-3 Жетона Рун
            mob.getWorld().dropItemNaturally(mob.getLocation(), MobListener.getArtifactShard());
            ItemStack rt = MobListener.getRuneToken();
            rt.setAmount(2 + ThreadLocalRandom.current().nextInt(2));
            mob.getWorld().dropItemNaturally(mob.getLocation(), rt);

            // Спецдроп void_walker из конфига
            handleBossSpecificDrops(mob, killer, bossType);

            killer.sendMessage("§d✨ ПРЕДОПРЕДЕЛЕННЫЙ ЛУТ! С поверженного Мирового Супер-Босса выпали древние жетоны сокровищ!");
        } else if (isMiniBoss) {
            int runeChance = Math.min(
                    plugin.getConfig().getInt("loot.rune-token.max-chance", 50),
                    plugin.getConfig().getInt("loot.rune-token.base-chance", 5) + 3 * getRankFromMob(mob)
            );
            if (ThreadLocalRandom.current().nextInt(100) < runeChance) {
                mob.getWorld().dropItemNaturally(mob.getLocation(), MobListener.getRuneToken());
                killer.sendMessage("§6✨ НАХОДКА! С мини-босса выпал Древний Жетон Рун!");
            }
        }
    }

    /**
     * Обработать специфичные для типа босса дропы (void_walker и т.д.).
     * Конфиг: loot.boss-drops.<bossType>.rare
     */
    private void handleBossSpecificDrops(LivingEntity mob, Player killer, String bossType) {
        if (bossType == null) return;

        // Читаем из конфига редкие дропы для конкретного босса
        String path = "loot.boss-drops." + bossType + ".rare";
        if (!plugin.getConfig().contains(path)) {
            // Fallback: хардкод void_walker (обратная совместимость)
            if (bossType.equals("void_walker")) {
                rollVoidWalkerDrops(mob, killer);
            }
            return;
        }

        List<String> rareDrops = plugin.getConfig().getStringList(path);
        for (String dropStr : rareDrops) {
            String[] parts = dropStr.split(";");
            if (parts.length >= 4) {
                try {
                    Material mat = Material.valueOf(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    double dropChance = Double.parseDouble(parts[3]);
                    if (ThreadLocalRandom.current().nextInt(100) < (int) dropChance) {
                        int amount = ThreadLocalRandom.current().nextInt(max - min + 1) + min;
                        mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(mat, amount));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void rollVoidWalkerDrops(LivingEntity mob, Player killer) {
        if (ThreadLocalRandom.current().nextInt(100) < 1) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(Material.ELYTRA));
            killer.sendMessage("§b✨ ЛЕГЕНДАРНЫЙ ДРОП! С Странника Бездны выпали КРЫЛЬЯ!");
        }
        if (ThreadLocalRandom.current().nextInt(100) < 3) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(Material.TOTEM_OF_UNDYING));
            killer.sendMessage("§e✨ РЕДКИЙ ДРОП! С Странника Бездны выпал ТОТЕМ БЕССМЕРТИЯ!");
        }
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(Material.SHULKER_BOX));
            killer.sendMessage("§d✨ РЕДКИЙ ДРОП! С Странника Бездны выпала ШАЛКЕР-КОРОБКА!");
        }
    }

    // ═══════════════════════════════════════════
    //  Экстра-лут и мини-босс лут
    // ═══════════════════════════════════════════

    /**
     * Попробовать экстра-лут за элитных мобов.
     */
    public void rollExtraLoot(LivingEntity mob, double multiplier) {
        if (!plugin.getConfig().getBoolean("loot.extra-rewards.enabled", true)) return;
        double minMult = plugin.getConfig().getDouble("loot.extra-rewards.min-multiplier", 3.0);
        if (multiplier < minMult) return;

        int chance = plugin.getConfig().getInt("loot.extra-rewards.chance", 15);
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;

        List<String> items = plugin.getConfig().getStringList("loot.extra-rewards.items");
        if (items.isEmpty()) return;

        String randomItem = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        dropConfigItem(mob, randomItem);
    }

    /**
     * Выдать гарантированный лут за мини-босса из конфига.
     */
    public void dropMiniBossLoot(LivingEntity mob) {
        List<String> guaranteedLoot = plugin.getConfig().getStringList("mini_bosses.guaranteed-loot");
        if (guaranteedLoot != null) {
            for (String itemStr : guaranteedLoot) {
                dropConfigItem(mob, itemStr);
            }
        }
    }

    private void dropConfigItem(LivingEntity mob, String itemStr) {
        String[] parts = itemStr.split(";");
        if (parts.length == 3) {
            try {
                Material mat = Material.valueOf(parts[0]);
                int min = Integer.parseInt(parts[1]);
                int max = Integer.parseInt(parts[2]);
                int amount = ThreadLocalRandom.current().nextInt(max - min + 1) + min;
                mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(mat, amount));
            } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════
    //  Утилиты
    // ═══════════════════════════════════════════

    private int getJobLevels(Player p) {
        int total = 0;
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                for (String job : Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith")) {
                    total += (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class)
                            .invoke(dataManager, p.getUniqueId(), job);
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    private int getRankFromMob(LivingEntity mob) {
        try {
            NamespacedKey rankKey = new NamespacedKey(plugin, "mob_rank");
            return mob.getPersistentDataContainer().getOrDefault(rankKey, PersistentDataType.INTEGER, 1);
        } catch (Exception e) { return 1; }
    }

    /**
     * Очистить устаревшие записи фарм-лимитов.
     */
    public void cleanup(long now) {
        farmedRepToday.entrySet().removeIf(e ->
                now - farmResetTimes.getOrDefault(e.getKey(), 0L) > 7200000);
    }
}
