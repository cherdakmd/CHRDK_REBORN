package ru.example.vkchatgear.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatgear.VKChatGearPlugin;

public class MechanicsListener implements Listener {

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        
        // Полный запрет Шелкового Касания для предотвращения дюпов руды
        if (tool != null && tool.hasItemMeta() && tool.getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            tool.removeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
            p.sendMessage(org.bukkit.ChatColor.RED + "⚠️ Шёлковое касание полностью запрещено на сервере для предотвращения дюпов руды!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        }
        
        if (tool != null && tool.hasItemMeta() && plugin.getGearManager().isGear(tool.getType())) {
            String name = tool.getType().name();
            if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
                ItemMeta meta = tool.getItemMeta();
                if (meta == null || !meta.hasLore()) return;

                java.util.List<String> lore = meta.getLore();

                // Ore Magnet & Auto-smelt & Telekinesis
                boolean oreMagnet = false;
                for (String l : lore) { if (org.bukkit.ChatColor.stripColor(l).contains("Магнит Руд")) oreMagnet = true; }
                boolean autoSmelt = false;
                for (String l : lore) { if (org.bukkit.ChatColor.stripColor(l).contains("Авто-плавка")) autoSmelt = true; }
                boolean hasTelekinesis = false;
                for (String l : lore) { if (org.bukkit.ChatColor.stripColor(l).contains("Телекинез")) hasTelekinesis = true; }

                boolean dropsHandledBySmelt = false;

                // Авто-плавка работает только если нет Магнита Руд (иначе дюп слитков)
                if (autoSmelt && !oreMagnet) {
                    ItemStack smelted = getSmeltedFromOre(e.getBlock().getType());
                    if (smelted != null) {
                        e.setDropItems(false);
                        dropsHandledBySmelt = true;
                        if (hasTelekinesis) {
                            p.getInventory().addItem(smelted).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                        } else {
                            p.getWorld().dropItemNaturally(e.getBlock().getLocation(), smelted);
                        }
                        p.giveExp(1);
                    }
                }

                // Магнит Руд: руда падает как обычно + дополнительно выпадает слиток
                if (oreMagnet) {
                    ItemStack ingot = getIngotFromOre(e.getBlock().getType());
                    if (ingot != null) {
                        if (hasTelekinesis) {
                            p.getInventory().addItem(ingot).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                        } else {
                            p.getWorld().dropItemNaturally(e.getBlock().getLocation(), ingot);
                        }
                        p.giveExp(1);
                    }
                }
                
                // Timber
                boolean timber = false;
                for (String l : lore) { if (org.bukkit.ChatColor.stripColor(l).contains("Дровосек")) timber = true; }
                if (timber && e.getBlock().getType().name().endsWith("_LOG")) {
                    org.bukkit.block.Block b = e.getBlock();
                    for (int i = 1; i < 6; i++) {
                        org.bukkit.block.Block up = b.getRelative(0, i, 0);
                        if (up.getType() == b.getType()) up.breakNaturally(tool);
                        else break;
                    }
                }

                // Телекинез (Сразу в инвентарь)
                if (hasTelekinesis && !dropsHandledBySmelt) {
                    e.setDropItems(false);
                    for (ItemStack drop : e.getBlock().getDrops(tool)) {
                        p.getInventory().addItem(drop).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                    }
                    int exp = e.getExpToDrop();
                    if (exp > 0) {
                        p.giveExp(exp);
                        e.setExpToDrop(0);
                    }
                    p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, e.getBlock().getLocation(), 5, 0.5, 0.5, 0.5);
                }
                
                // Шанс сохранить прочность (1% за уровень заточки)
                int lvl = meta.getPersistentDataContainer().getOrDefault(new org.bukkit.NamespacedKey(plugin, "upgrade_level"), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                if (lvl > 0 && Math.random() * 100 < lvl && meta instanceof org.bukkit.inventory.meta.Damageable) {
                    org.bukkit.inventory.meta.Damageable dmgMeta = (org.bukkit.inventory.meta.Damageable) meta;
                    if (dmgMeta.getDamage() > 0) {
                        dmgMeta.setDamage(dmgMeta.getDamage() - 1);
                    }
                }
                
                tool.setItemMeta(meta);
            }
        }
    }

    private final VKChatGearPlugin plugin;

    public MechanicsListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        // Спавн красивых шлейфов частиц для новых легендарных и национальных сетов
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
            if (plugin.getGearManager().isWearingSet(p, "perun")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, p.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "chernobog")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.TOWN_AURA, p.getLocation().add(0, 0.1, 0), 4, 0.3, 0.1, 0.3, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "gagarin")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 0.1, 0), 2, 0.2, 0.1, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "udarnik")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, p.getLocation().add(0, 0.1, 0), 3, 0.2, 0.1, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "tankist")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, p.getLocation().add(0, 0.1, 0), 2, 0.2, 0.1, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "volhv")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 0.2, 0), 2, 0.3, 0.2, 0.3, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "koshchey")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, p.getLocation().add(0, 0.2, 0), 2, 0.2, 0.2, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "bogatyr")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 0.3, 0), 2, 0.2, 0.2, 0.2, 0.02);
            } else if (plugin.getGearManager().isWearingSet(p, "sokol")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, p.getLocation().add(0, 0.2, 0), 5, 0.2, 0.2, 0.2, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 1.0f));
            } else if (plugin.getGearManager().isWearingSet(p, "bone_armor")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 0.1, 0), 3, 0.2, 0.1, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "shadow_blade")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 0.3, 0), 2, 0.2, 0.3, 0.2, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "ember_crown")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.FLAME, p.getLocation().add(0, 0.2, 0), 2, 0.15, 0.1, 0.15, 0.01);
            } else if (plugin.getGearManager().isWearingSet(p, "plague_mist")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_MOB, p.getLocation().add(0, 0.2, 0), 3, 0.3, 0.2, 0.3, 0.01, org.bukkit.Color.fromRGB(80, 200, 80));
            } else if (plugin.getGearManager().isWearingSet(p, "starforged")) {
                p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0.01);
            }
        }

        // ВАЖНО: бонус max-health от заточки брони отключён.
        // Раньше этот блок на PlayerMoveEvent периодически менял GENERIC_MAX_HEALTH,
        // а vkchat_artifacts тоже пересчитывал max-health каждые 2 секунды. В итоге у игроков
        // постоянно появлялись и пропадали дополнительные сердца. За здоровье теперь отвечают
        // стабильные эффекты/артефакты/сеты, а заточка Gear даёт урон и снижение входящего урона.
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!plugin.getConfig().getBoolean("settings.prevent-vanilla-repair", true)) return;
        
        ItemStack i1 = e.getInventory().getItem(0);
        ItemStack i2 = e.getInventory().getItem(1);
        
        if (i1 != null && i1.hasItemMeta() && i1.getItemMeta().hasLore() && i1.getItemMeta().getLore().toString().contains("Редкость")) {
            // Если пытаются починить предметом или скрестить - запрещаем
            if (i2 != null) {
                e.setResult(null);
            }
        }
    }

    private ItemStack getIngotFromOre(org.bukkit.Material blockType) {
        String name = blockType.name();
        if (name.contains("IRON_ORE")) return new ItemStack(org.bukkit.Material.IRON_INGOT);
        if (name.contains("GOLD_ORE")) return new ItemStack(org.bukkit.Material.GOLD_INGOT);
        if (name.contains("COPPER_ORE")) {
            try {
                return new ItemStack(org.bukkit.Material.valueOf("COPPER_INGOT"));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private ItemStack getSmeltedFromOre(org.bukkit.Material blockType) {
        String name = blockType.name();
        if (name.contains("IRON_ORE")) return new ItemStack(org.bukkit.Material.IRON_INGOT);
        if (name.contains("GOLD_ORE")) return new ItemStack(org.bukkit.Material.GOLD_INGOT);
        if (name.contains("COPPER_ORE")) {
            try {
                return new ItemStack(org.bukkit.Material.valueOf("COPPER_INGOT"));
            } catch (Exception ignored) {}
        }
        return null;
    }
}
