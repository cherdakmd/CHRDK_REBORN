package ru.example.vkchatnations.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

import java.util.Iterator;

/**
 * ClaimDefenseListener — система обороны приватов.
 *
 * Реализует три блока защиты, привязанных к уровням прокачки:
 *   • Уровень 2 (Антивзрыв)     — защита блоков/сущностей/рамок от всех взрывов.
 *   • Уровень 4 (Огнеупорность) — запрет поджога, растекания огня и лавы.
 *   • Уровень 5 (Цитадель)      — запрет PvP на территории.
 *
 * Центральный блок привата ВСЕГДА защищён от взрывов (любой уровень),
 * чтобы приват нельзя было «снести» одним динамитом.
 *
 * Пороговые уровни настраиваются в config.yml (секция claim).
 */
public class ClaimDefenseListener implements Listener {
    private final VKChatNationsPlugin plugin;

    public ClaimDefenseListener(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Пороговые уровни защиты (с дефолтами) ----
    private int explosionLevel() { return plugin.getConfig().getInt("claim.explosion-protect-level", 2); }
    private int noSpawnLevel()   { return plugin.getConfig().getInt("claim.no-spawn-level", 3); }
    private int fireLevel()      { return plugin.getConfig().getInt("claim.fire-protect-level", 4); }
    private int pvpLevel()       { return plugin.getConfig().getInt("claim.pvp-protect-level", 5); }

    /** Приват активен и его уровень ≥ требуемого. */
    private boolean isProtected(ChunkClaim claim, int minLevel) {
        return claim != null && claim.getDurability() > 0 && claim.getLevel() >= minLevel;
    }

    /** Является ли блок центральным блоком привата (всегда взрывозащищён). */
    private boolean isCentralClaimBlock(Block b) {
        String key = b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
        return plugin.getNationManager().getNationClaims().containsKey(key);
    }

    // ==========================================================
    //  УРОВЕНЬ 2 — ЗАЩИТА ОТ ВЗРЫВОВ
    // ==========================================================

    /** 1. Взрывы сущностей (TNT, криперы, эндер-кристаллы, гасты, воронки). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (isCentralClaimBlock(b)
                    || isProtected(plugin.getNationManager().getClaimAt(b.getLocation()), explosionLevel())) {
                it.remove();
            }
        }
    }

    /** 2. Взрывы блоков (кровати, якоря возрождения в Аду и т.п.). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (isCentralClaimBlock(b)
                    || isProtected(plugin.getNationManager().getClaimAt(b.getLocation()), explosionLevel())) {
                it.remove();
            }
        }
    }

    /** 3. Урон от взрывов игрокам и существам на защищённой территории. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        Location loc = e.getEntity().getLocation();
        if (isProtected(plugin.getNationManager().getClaimAt(loc), explosionLevel())) {
            e.setCancelled(true);
        }
    }

    /** 4. Рамки, картины и стенды для брони не падают от взрывов. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) return;
        if (isProtected(plugin.getNationManager().getClaimAt(e.getEntity().getLocation()), explosionLevel())) {
            e.setCancelled(true);
        }
    }

    // ==========================================================
    //  УРОВЕНЬ 4 — ЗАЩИТА ОТ ОГНЯ И ЛАВЫ
    // ==========================================================

    /** 5. Запрет поджога блоков (кремень, молния, lava→fire, fireball). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (isProtected(plugin.getNationManager().getClaimAt(e.getBlock().getLocation()), fireLevel())) {
            e.setCancelled(true);
        }
    }

    /** 6. Запрет растекания огня по защищённой территории. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE) {
            if (isProtected(plugin.getNationManager().getClaimAt(e.getBlock().getLocation()), fireLevel())) {
                e.setCancelled(true);
            }
        }
    }

    /** 7. Запрет затекания лавы и огня на защищённую территорию. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Material src = e.getBlock().getType();
        if (src == Material.LAVA || src == Material.FIRE) {
            if (isProtected(plugin.getNationManager().getClaimAt(e.getToBlock().getLocation()), fireLevel())) {
                e.setCancelled(true);
            }
        }
    }

    // ==========================================================
    //  УРОВЕНЬ 5 — ЗАПРЕТ PvP (ЦИТАДЕЛЬ)
    // ==========================================================

    /** 8. На территории Цитадели игроки не могут наносить урон друг другу. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;

        Player victim = (Player) e.getEntity();
        ChunkClaim claim = plugin.getNationManager().getClaimAt(victim.getLocation());
        if (isProtected(claim, pvpLevel())) {
            // Кровавая Луна игнорирует мирный режим нации, но Цитадель всё равно держит оборону.
            e.setCancelled(true);
            e.getDamager().sendMessage(org.bukkit.ChatColor.RED + "Запрещено PvP на территории Цитадели (приват 5 уровня)!");
        }
    }
}
