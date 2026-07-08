package ru.example.vkchatgear.combat;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Контекст боевого события — передаётся во все боевые эффекты.
 * Содержит все необходимые данные и утилитные методы,
 * чтобы каждый эффект был самодостаточным.
 */
public class CombatContext {

    private final EntityDamageByEntityEvent event;
    private final VKChatGearPlugin plugin;

    // Ссылки на разделяемые кулдаун-мапы (хранятся в CombatEffectRegistry)
    private final Map<String, Long> cooldowns;
    private final Map<UUID, Long> messageCooldowns;

    // Кешированные ссылки (lazy)
    private Player attacker;
    private Player victim;
    private LivingEntity attackerEntity;
    private LivingEntity victimEntity;

    // Счётчик проков зачарований оружия за текущий удар
    private int procs = 0;

    public static final int MAX_PROCS_PER_HIT = 3;
    public static final long MESSAGE_COOLDOWN_MS = 2000;

    public CombatContext(EntityDamageByEntityEvent event, VKChatGearPlugin plugin,
                         Map<String, Long> cooldowns, Map<UUID, Long> messageCooldowns) {
        this.event = event;
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.messageCooldowns = messageCooldowns;
    }

    // ═══════════════════════════════════════
    // Event accessors
    // ═══════════════════════════════════════

    public EntityDamageByEntityEvent getEvent() { return event; }
    public VKChatGearPlugin getPlugin() { return plugin; }

    public boolean isAttackerPlayer() { return event.getDamager() instanceof Player; }
    public boolean isVictimPlayer() { return event.getEntity() instanceof Player; }
    public boolean isAttackerLiving() { return event.getDamager() instanceof LivingEntity; }
    public boolean isVictimLiving() { return event.getEntity() instanceof LivingEntity; }
    public boolean isDamagerMonster() { return event.getDamager() instanceof org.bukkit.entity.Monster; }

    public Player getAttacker() {
        if (attacker == null && isAttackerPlayer()) attacker = (Player) event.getDamager();
        return attacker;
    }

    public Player getVictim() {
        if (victim == null && isVictimPlayer()) victim = (Player) event.getEntity();
        return victim;
    }

    public LivingEntity getAttackerEntity() {
        if (attackerEntity == null && isAttackerLiving()) attackerEntity = (LivingEntity) event.getDamager();
        return attackerEntity;
    }

    public LivingEntity getVictimEntity() {
        if (victimEntity == null && isVictimLiving()) victimEntity = (LivingEntity) event.getEntity();
        return victimEntity;
    }

    // ═══════════════════════════════════════
    // Proc counter (offensive enchants)
    // ═══════════════════════════════════════

    public int getProcs() { return procs; }
    public boolean canProc() { return procs < MAX_PROCS_PER_HIT; }
    public void addProc() { procs++; }

    // ═══════════════════════════════════════
    // Cooldown management
    // ═══════════════════════════════════════

    /**
     * Проверить и установить кулдаун.
     * @param key Уникальный ключ (например "meteor:UUID")
     * @param cooldownMs Длительность кулдауна в мс
     * @return true если кулдаун прошёл и установлен
     */
    public boolean checkCooldown(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMs) return false;
        cooldowns.put(key, now);
        return true;
    }

    /** Player metadata-based cooldown */
    public void setMetaCooldown(Player p, String key) {
        p.setMetadata(key, new FixedMetadataValue(plugin, System.currentTimeMillis()));
    }

    public long getMetaCooldown(Player p, String key) {
        if (p.hasMetadata(key)) return p.getMetadata(key).get(0).asLong();
        return 0L;
    }

    public boolean isMetaCooldownReady(Player p, String key, long cooldownMs) {
        return System.currentTimeMillis() - getMetaCooldown(p, key) >= cooldownMs;
    }

    // ═══════════════════════════════════════
    // Message utilities
    // ═══════════════════════════════════════

    public void sendMessage(Player p, String msg) {
        long now = System.currentTimeMillis();
        UUID uid = p.getUniqueId();
        Long last = messageCooldowns.get(uid);
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        messageCooldowns.put(uid, now);
        p.sendMessage(msg);
    }

    // ═══════════════════════════════════════
    // Combat utilities
    // ═══════════════════════════════════════

    public boolean rollChance(int percent) {
        return ThreadLocalRandom.current().nextInt(100) < percent;
    }

    /** Исцелить игрока, не превышая макс. ХП */
    public void heal(Player p, double amount) {
        double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        p.setHealth(Math.min(p.getHealth() + amount, maxHp));
    }

    /** Исцелить LivingEntity, не превышая макс. ХП */
    public void heal(LivingEntity e, double amount) {
        double maxHp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        e.setHealth(Math.min(e.getHealth() + amount, maxHp));
    }

    public void addPotion(LivingEntity e, PotionEffectType type, int ticks, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, ticks, amplifier));
    }

    public void addPotion(LivingEntity e, PotionEffectType type, int ticks, int amplifier, boolean ambient, boolean particles) {
        e.addPotionEffect(new PotionEffect(type, ticks, amplifier, ambient, particles));
    }

    public double getMaxHp(LivingEntity e) {
        return e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
    }

    public double getHpPercent(LivingEntity e) {
        return e.getHealth() / getMaxHp(e);
    }

    public boolean isWearingSet(Player p, String setId) {
        return plugin.getGearManager().isWearingSet(p, setId);
    }

    // ═══════════════════════════════════════
    // Lore / Rarity utilities
    // ═══════════════════════════════════════

    public static boolean hasEnchant(List<String> lore, String name) {
        for (String line : lore) {
            if (ChatColor.stripColor(line).contains(name)) return true;
        }
        return false;
    }

    public String getRarityProc(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        ItemMeta meta = item.getItemMeta();
        String pdc = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "rarity_proc"), PersistentDataType.STRING);
        if (pdc != null && !pdc.trim().isEmpty()) return ChatColor.stripColor(pdc);
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String stripped = ChatColor.stripColor(line);
                if (stripped.startsWith("Прок редкости:"))
                    return stripped.substring("Прок редкости:".length()).trim();
            }
        }
        return "";
    }

    public static boolean isProc(String proc, String... aliases) {
        if (proc == null) return false;
        String clean = ChatColor.stripColor(proc).toLowerCase(java.util.Locale.ROOT);
        for (String a : aliases) {
            if (clean.contains(a.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, "upgrade_level"),
                        PersistentDataType.INTEGER, 0);
    }

    public boolean hasDefect(ItemStack item, String defectId) {
        return plugin.getGearManager().hasDefect(item, defectId);
    }

    // ═══════════════════════════════════════
    // Cleanse utility
    // ═══════════════════════════════════════

    public static void cleanseNegativeEffects(Player p) {
        for (PotionEffectType type : new PotionEffectType[]{
                PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.BLINDNESS,
                PotionEffectType.CONFUSION, PotionEffectType.HUNGER, PotionEffectType.LEVITATION
        }) {
            p.removePotionEffect(type);
        }
        p.setFireTicks(0);
    }

    // ═══════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════

    public void cleanupCooldowns(long now) {
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000);
        messageCooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000);
    }
}
