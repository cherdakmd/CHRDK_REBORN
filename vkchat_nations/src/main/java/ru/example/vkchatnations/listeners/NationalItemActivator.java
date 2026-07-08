package ru.example.vkchatnations.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.example.vkchatnations.VKChatNationsPlugin;

/**
 * NationalItemActivator — обработчик активных свойств национальных предметов (ПКМ).
 *
 * Извлечено из NationListener (~300 строк):
 * - 11 национальных предметов с активными свойствами
 * - Система кулдаунов (metadata-based)
 * - Утилита consumeOneItem
 */
public class NationalItemActivator implements Listener {

    private final VKChatNationsPlugin plugin;

    public NationalItemActivator(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════
    // ПКМ — АКТИВНЫЕ СВОЙСТВА НАЦИОНАЛЬНЫХ ПРЕДМЕТОВ
    // ═══════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        String nationalId = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING
        );
        if (nationalId == null) return;

        long now = System.currentTimeMillis();

        switch (nationalId) {
            case "soviet_manifesto" -> activateManifesto(p, now);
            case "soviet_crystal"   -> activateCrystal(p, now);
            case "kgb_serum"        -> activateSerum(p, item, now);
            case "pagan_staff"      -> activateStaff(p, now);
            case "pagan_amulet"     -> activateAmulet(p, now);
            case "pagan_brew"       -> activateBrew(p, item, now);
            case "pagan_idol"       -> activateIdol(p, now);
            case "pagan_infusion"   -> activateInfusion(p, item, now);
            case "imperial_scepter" -> activateScepter(p, now);
            case "imperial_bread"   -> activateBread(p, item);
            case "imperial_cup"     -> activateCup(p, item, now);
            default -> { return; }
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

        String nationalId = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING
        );
        if (!"imperial_shackles".equals(nationalId)) return;

        e.setCancelled(true);
        if (!(e.getRightClicked() instanceof LivingEntity victim)) return;

        long now = System.currentTimeMillis();
        long last = getCooldown(p, "imperial_shackles");
        if (now - last < 45000L) {
            p.sendMessage(ChatColor.RED + "⏳ Кандалы перезаряжаются! Осталось: " + ((45000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "imperial_shackles", now);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4));
        p.sendMessage(ChatColor.DARK_RED + "⛓️ Вы заковали " + victim.getName() + " в Кандалы Тайного Сыска на 3 секунды!");
        if (victim instanceof Player target) {
            target.sendMessage(ChatColor.RED + "⛓️ Вас заковали в кандалы опричника! Вы обездвижены!");
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 25, 0.3, 0.5, 0.3, 0.1);
    }

    // ═══════════════════════════════════════════════════════════════
    // РЕАЛИЗАЦИИ СПОСОБНОСТЕЙ
    // ═══════════════════════════════════════════════════════════════

    private void activateManifesto(Player p, long now) {
        long last = getCooldown(p, "soviet_manifesto");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Манифест перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "soviet_manifesto", now);
        p.sendMessage(ChatColor.RED + "📕 «Пролетарии всех стран, соединяйтесь!» Вы провозгласили волю равенства!");
        p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.5, 0), 20, 1.0, 0.5, 1.0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
        String pNation = plugin.getNationManager().getPlayerNation(p);
        for (Player ally : Bukkit.getOnlinePlayers()) {
            if (pNation != null && pNation.equals(plugin.getNationManager().getPlayerNation(ally))
                    && ally.getWorld().equals(p.getWorld()) && p.getLocation().distance(ally.getLocation()) <= 8) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 0));
                ally.sendMessage(ChatColor.GREEN + "✊ Вы вдохновлены Манифестом Труда соотечественника! Сила I на 30 секунд!");
            }
        }
    }

    private void activateCrystal(Player p, long now) {
        long last = getCooldown(p, "soviet_crystal");
        if (now - last < 120000L) {
            p.sendMessage(ChatColor.RED + "⏳ Кристалл перезаряжается! Осталось: " + ((120000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "soviet_crystal", now);
        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 1200, 1));
        p.sendMessage(ChatColor.GOLD + "⚙️ Сила Кристалла напитала ваши руки! Спешка II на 1 минуту!");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, p.getLocation(), 20, 0.5, 1.0, 0.5);
    }

    private void activateSerum(Player p, ItemStack item, long now) {
        long last = getCooldown(p, "kgb_serum");
        if (now - last < 120000L) {
            p.sendMessage(ChatColor.RED + "⏳ Сыворотка перезаряжается! Осталось: " + ((120000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "kgb_serum", now);
        consumeOneItem(p, item);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        p.sendMessage(ChatColor.DARK_RED + "🧪 Вы ввели Сыворотку Скрытности! Невидимость и Скорость II на 30 секунд.");
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, p.getLocation(), 30, 0.5, 1.0, 0.5);
    }

    private void activateStaff(Player p, long now) {
        long last = getCooldown(p, "pagan_staff");
        if (now - last < 30000L) {
            p.sendMessage(ChatColor.RED + "⏳ Посох перезаряжается! Осталось: " + ((30000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "pagan_staff", now);
        AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
        p.setHealth(Math.min(p.getHealth() + 4.0, max));
        p.sendMessage(ChatColor.GREEN + "🌿 Дыхание леса исцелило вас на 4 HP (2 сердца)!");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3);
    }

    private void activateAmulet(Player p, long now) {
        long last = getCooldown(p, "pagan_amulet");
        if (now - last < 15000L) {
            p.sendMessage(ChatColor.RED + "⏳ Оберег перезаряжается! Осталось: " + ((15000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "pagan_amulet", now);
        Vector velocity = p.getLocation().getDirection().multiply(1.3).setY(0.6);
        p.setVelocity(velocity);
        p.sendMessage(ChatColor.AQUA + "🪶 Оберег Стрибога уносит вас вперед по воздуху!");
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
    }

    private void activateBrew(Player p, ItemStack item, long now) {
        long last = getCooldown(p, "pagan_brew");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Отвар перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "pagan_brew", now);
        consumeOneItem(p, item);
        AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
        p.setHealth(Math.min(p.getHealth() + 10.0, max));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1));
        p.sendMessage(ChatColor.GREEN + "🏺 Вы выпили Отвар Лешего! Восстановлено 10 HP и получена Регенерация II.");
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1.0, 0), 20, 0.5, 0.5, 0.5);
    }

    private void activateIdol(Player p, long now) {
        long last = getCooldown(p, "pagan_idol");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Идол перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "pagan_idol", now);
        p.sendMessage(ChatColor.DARK_PURPLE + "💀 Мрак Чернобога вырывается на свободу!");
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SKELETON_DEATH, 1.2f, 0.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, p.getLocation(), 50, 2.0, 0.5, 2.0, 0.05);
        String pNation = plugin.getNationManager().getPlayerNation(p);
        for (org.bukkit.entity.Entity ent : p.getNearbyEntities(6, 6, 6)) {
            if (ent instanceof LivingEntity le && ent != p) {
                if (ent instanceof Player other) {
                    if (pNation != null && pNation.equals(plugin.getNationManager().getPlayerNation(other))) continue;
                }
                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
            }
        }
    }

    private void activateInfusion(Player p, ItemStack item, long now) {
        long last = getCooldown(p, "pagan_infusion");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Настойка перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "pagan_infusion", now);
        consumeOneItem(p, item);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        p.sendMessage(ChatColor.DARK_RED + "🩸 Вы выпили Настойку Кровавого Безумия! Дарована Сила II на 15 сек ценой увядания.");
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.8f);
        p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 1.0, 0), 20, 0.3, 0.3, 0.3);
    }

    private void activateScepter(Player p, long now) {
        long last = getCooldown(p, "imperial_scepter");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Скипетр перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "imperial_scepter", now);
        p.sendMessage(ChatColor.GOLD + "👑 Во имя Царя! Царский Скипетр накладывает опеку монарха!");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation(), 30, 1.0, 1.0, 1.0);
        String pNation = plugin.getNationManager().getPlayerNation(p);
        for (Player ally : Bukkit.getOnlinePlayers()) {
            if (pNation != null && pNation.equals(plugin.getNationManager().getPlayerNation(ally))
                    && ally.getWorld().equals(p.getWorld()) && p.getLocation().distance(ally.getLocation()) <= 8) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 1));
                ally.sendMessage(ChatColor.GREEN + "🛡️ Вы защищены щитом Монарха соотечественника! Сопротивление II на 10 секунд!");
            }
        }
    }

    private void activateBread(Player p, ItemStack item) {
        consumeOneItem(p, item);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.sendMessage(ChatColor.GREEN + "🍞 Вы вкусили Царский Каравай! Чувство сытости полностью восстановлено.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
    }

    private void activateCup(Player p, ItemStack item, long now) {
        long last = getCooldown(p, "imperial_cup");
        if (now - last < 60000L) {
            p.sendMessage(ChatColor.RED + "⏳ Кубок перезаряжается! Осталось: " + ((60000L - (now - last)) / 1000L) + " сек.");
            return;
        }
        setCooldown(p, "imperial_cup", now);
        consumeOneItem(p, item);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0));
        p.sendMessage(ChatColor.DARK_RED + "🍷 Вы осушили Кубок Грозного! Пробужден Царский Гнев (Сила III) на 10 секунд!");
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 1.0, 0), 25, 0.4, 0.4, 0.4);
    }

    // ═══════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════

    private void consumeOneItem(Player p, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
    }

    public static long getCooldown(Player p, String key) {
        String metadataKey = "cooldown_" + key;
        if (p.hasMetadata(metadataKey)) {
            return p.getMetadata(metadataKey).get(0).asLong();
        }
        return 0L;
    }

    public static void setCooldown(Player p, String key, long time) {
        String metadataKey = "cooldown_" + key;
        p.setMetadata(metadataKey, new org.bukkit.metadata.FixedMetadataValue(
                ru.example.vkchatnations.VKChatNationsPlugin.getInstance(), time));
    }
}
