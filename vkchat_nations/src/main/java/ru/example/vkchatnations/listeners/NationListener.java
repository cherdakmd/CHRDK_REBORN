package ru.example.vkchatnations.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.attribute.AttributeInstance;

public class NationListener implements Listener {
    private final VKChatNationsPlugin plugin;

    public NationListener(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        // Пассивные мутации и баффы перенесены в MutationEffectScheduler
    }

    // ==========================================
    // ОБНАРУЖЕНИЕ ВХОДА/ВЫХОДА ИЗ ПРИВАТОВ ЧЕРЕЗ БЛОКИ
    // ==========================================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            Player p = e.getPlayer();
            ChunkClaim fromClaim = plugin.getNationManager().getClaimAt(e.getFrom());
            ChunkClaim toClaim = plugin.getNationManager().getClaimAt(e.getTo());
            
            checkClaimChange(p, fromClaim, toClaim);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        checkClaimChange(e.getPlayer(), plugin.getNationManager().getClaimAt(e.getFrom()), plugin.getNationManager().getClaimAt(e.getTo()));
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        plugin.getNationManager().removeAutoClaim(e.getPlayer());
    }

    private void checkClaimChange(Player p, ChunkClaim fromClaim, ChunkClaim toClaim) {
        String fromNation = fromClaim != null ? fromClaim.getNation() : null;
        String toNation = toClaim != null ? toClaim.getNation() : null;
        String playerNation = plugin.getNationManager().getPlayerNation(p);

        if (fromNation == null && toNation == null) {
            return;
        }

        if (fromNation != null && toNation != null) {
            if (fromNation.equals(toNation)) {
                return;
            }
            sendExitMessage(p, fromNation);
            sendEnterMessage(p, toNation, toNation.equals(playerNation));
        }
        else if (fromNation != null && toNation == null) {
            sendExitMessage(p, fromNation);
            sendActionbar(p, "&e&l⛺ Дикие земли (Свободная зона)");
        }
        else if (fromNation == null && toNation != null) {
            sendEnterMessage(p, toNation, toNation.equals(playerNation));
        }
    }

    private void sendActionbar(Player p, String msg) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    private void sendEnterMessage(Player p, String nation, boolean isYourChunk) {
        String format;
        String nationName = plugin.getNationManager().getNationNamePublic(nation);
        String nationPrefix = plugin.getNationManager().getNationPrefixPublic(nation);
        String coloredName = nationPrefix + nationName;
        int memberCount = plugin.getNationManager().getMemberCount(nation);

        if (isYourChunk) {
            sendActionbar(p, "&a&l➡ Вход: " + coloredName + " &7(Своя земля · " + memberCount + " жителей)");
            if (plugin.getConfig().getBoolean("messages.enter_your_chunk.enabled", true)) {
                format = plugin.getConfig().getString("messages.enter_your_chunk.format", "&8[&a+&8] &f{player} &7вернулся на свою территорию [&a{nation}&7]");
                String msg = format.replace("{player}", p.getName())
                        .replace("{nation}", coloredName);
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                return;
            }
        }

        sendActionbar(p, "&c&l➡ Вход: " + coloredName + " &7(" + memberCount + " жителей)");
        if (plugin.getConfig().getBoolean("messages.enter_nation.enabled", true)) {
            format = plugin.getConfig().getString("messages.enter_nation.format", "&8[&c!&8] &f{player} &7вошёл на территорию нации [&c{nation}&7] &8(" + memberCount + " жителей)");
            String msg = format.replace("{player}", p.getName())
                    .replace("{nation}", coloredName);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    private void sendExitMessage(Player p, String nation) {
        String nationName = plugin.getNationManager().getNationNamePublic(nation);
        String nationPrefix = plugin.getNationManager().getNationPrefixPublic(nation);
        String coloredName = nationPrefix + nationName;

        sendActionbar(p, "&e&l⬅ Выход: " + coloredName);
        
        if (!plugin.getConfig().getBoolean("messages.exit_nation.enabled", true)) return;

        String format = plugin.getConfig().getString("messages.exit_nation.format", "&8[&a-&8] &f{player} &7вышел с территории нации [&c{nation}&7]");
        String msg = format.replace("{player}", p.getName())
                .replace("{nation}", coloredName);

        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private boolean canBuild(Player p, org.bukkit.block.Block b) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        ChunkClaim claim = plugin.getNationManager().getClaimAt(b.getLocation());

        if (claim == null) return true;

        if (claim.getOwner().equals(p.getUniqueId())) return true;
        if (claim.getTrusted().contains(p.getUniqueId())) return true;

        if (claim.getDurability() <= 0) return true;

        p.sendMessage(ChatColor.RED + "Территория защищена!");
        return false;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItemInHand();

        // Разрешаем огонь от кремня владельцу привата (портал)
        if (item != null && item.getType() == Material.FLINT_AND_STEEL) {
            ChunkClaim fc = plugin.getNationManager().getClaimAt(e.getBlock().getLocation());
            if (fc == null || fc.getOwner().equals(p.getUniqueId()) || fc.getTrusted().contains(p.getUniqueId())) return;
        }

        if (item == null || !item.hasItemMeta()) {
            if (!canBuild(p, e.getBlock())) e.setCancelled(true);
            return;
        }

        NamespacedKey radiusKey = new NamespacedKey(plugin, "claim_block_radius");
        if (item.getItemMeta().getPersistentDataContainer().has(radiusKey, PersistentDataType.INTEGER)) {
            int radius = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.INTEGER);
            
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "⚠️ Сначала выберите Нацию! (/nation)");
                e.setCancelled(true);
                return;
            }

            org.bukkit.block.Block b = e.getBlock();
            String world = b.getWorld().getName();
            int x = b.getX();
            int z = b.getZ();

            // Проверка на пересечение с другими приватами
            if (plugin.getNationManager().checkOverlap(world, x, z, radius)) {
                p.sendMessage(ChatColor.RED + "❌ Здесь нельзя установить приват! Область пересекается с существующим приватом.");
                e.setCancelled(true);
                return;
            }

            // Успешно регистрируем приват!
            plugin.getNationManager().registerClaim(p, b, radius);
            return;
        }

        if (!canBuild(p, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        org.bukkit.block.Block b = e.getBlock();
        String world = b.getWorld().getName();
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();

        String key = world + ";" + x + ";" + y + ";" + z;
        ChunkClaim claim = plugin.getNationManager().getNationClaims().get(key);

        if (claim != null) {
            // Это центральный блок привата!
            if (claim.getOwner().equals(p.getUniqueId()) || p.hasPermission("vkchat.admin")) {
                plugin.getNationManager().getNationClaims().remove(key);
                plugin.getNationManager().saveAll();

                e.setDropItems(false); // Не дропаем обычный блок

                // Дропаем соответствующий блок привата
                ItemStack dropBlock;
                if (claim.getRadius() == 8) {
                    dropBlock = plugin.getNationManager().getSmallClaimBlockItem();
                } else if (claim.getRadius() == 16) {
                    dropBlock = plugin.getNationManager().getMediumClaimBlockItem();
                } else {
                    dropBlock = plugin.getNationManager().getLargeClaimBlockItem();
                }
                b.getWorld().dropItemNaturally(b.getLocation(), dropBlock);

                p.sendMessage(ChatColor.YELLOW + "⚠ Блок привата сломан. Защитная зона радиусом " + claim.getRadius() + " блоков удалена!");
                plugin.getNationManager().broadcastToNationWithPrefix(claim.getNation(),
                        ChatColor.YELLOW + "⚠ " + ChatColor.WHITE + p.getName() + ChatColor.YELLOW + " удалил приватный блок радиусом " + claim.getRadius() + "!");
                return;
            } else {
                p.sendMessage(ChatColor.RED + "❌ Вы не можете ломать чужой блок привата!");
                e.setCancelled(true);
                return;
            }
        }

        // Если это не центральный блок, проверяем стандартные права на постройку
        if (!canBuild(p, b)) {
            e.setCancelled(true);
            return;
        }

        // ⛏️ Стахановец (stakhanovite) - Двойной дроп руды под землей
        if (plugin.getNationManager().hasMutation(p, "stakhanovite") && p.getLocation().getBlockY() < 50) {
            Material type = b.getType();
            if (type.name().contains("IRON_ORE") || type.name().contains("GOLD_ORE") || type.name().contains("COAL") || type.name().contains("DIAMOND_ORE")) {
                if (ThreadLocalRandom.current().nextDouble() < 0.20) {
                    p.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(type, 1));
                    p.sendMessage(ChatColor.GOLD + "⛏️ [Стахановец] Двойной дроп руды!");
                    p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 10);
                }
            }
        }

        // ⛏️ Индустриальный Магнит (soviet_magnet)
        if (plugin.getNationManager().hasMutation(p, "soviet_magnet")) {
            Material type = b.getType();
            if (type.name().contains("ORE") || type.name().contains("COAL")) {
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    p.giveExp(e.getExpToDrop() > 0 ? e.getExpToDrop() : 1);
                    p.sendMessage(ChatColor.GOLD + "⚙️ [Индустриальный Магнит] Получено +15% бонусного опыта/ресурсов!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.block.Block b = e.getClickedBlock();
        if (b == null) return;

        Player p = e.getPlayer();
        ChunkClaim claim = plugin.getNationManager().getClaimAt(b.getLocation());
        if (claim == null) return;

        // Если это владелец, и кликнули по центральному блоку привата
        if (b.getX() == claim.getX() && b.getY() == claim.getY() && b.getZ() == claim.getZ()) {
            if (claim.getOwner().equals(p.getUniqueId())) {
                e.setCancelled(true);
                plugin.getGuiListener().openClaimFeedGui(p, claim);
                return;
            }
        }

        if (claim.getOwner().equals(p.getUniqueId())) return;
        if (claim.getTrusted().contains(p.getUniqueId())) return;

        if (p.hasPermission("vkchat.admin")) return;

        if (claim.getDurability() <= 0) return;

        Material type = b.getType();
        String name = type.name();
        
        if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER") ||
            name.contains("DOOR") || name.contains("GATE") || name.contains("LEVER") ||
            name.contains("BUTTON") || name.contains("PLATE") || name.contains("FURNACE") ||
            name.contains("HOPPER") || name.contains("DISPENSER") || name.contains("DROPPER") ||
            name.contains("ANVIL") || name.contains("REPEATER") || name.contains("COMPARATOR") ||
            type == Material.BREWING_STAND || type == Material.BEACON) {
            
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "❌ Это приватная собственность другого игрока!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        
        org.bukkit.inventory.InventoryHolder holder = e.getInventory().getHolder();
        org.bukkit.Location loc = null;
        
        if (holder instanceof org.bukkit.block.BlockState) {
            loc = ((org.bukkit.block.BlockState) holder).getLocation();
        } else if (holder instanceof org.bukkit.entity.Entity) {
            loc = ((org.bukkit.entity.Entity) holder).getLocation();
        } else if (holder instanceof org.bukkit.block.DoubleChest) {
            loc = ((org.bukkit.block.DoubleChest) holder).getLocation();
        }
        
        if (loc == null) return;
        
        ChunkClaim claim = plugin.getNationManager().getClaimAt(loc);
        if (claim == null) return;
        
        if (claim.getOwner().equals(p.getUniqueId())) return;
        if (claim.getTrusted().contains(p.getUniqueId())) return;
        
        if (p.hasPermission("vkchat.admin")) return;
        
        if (claim.getDurability() <= 0) return;
        
        e.setCancelled(true);
        p.sendMessage(ChatColor.RED + "❌ Это приватный инвентарь другого игрока!");
    }

    // Активные свойства национальных предметов перенесены в NationalItemActivator

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            Player victim = (Player) e.getEntity();
            Player attacker = (Player) e.getDamager();

            String vNation = plugin.getNationManager().getPlayerNation(victim);
            String aNation = plugin.getNationManager().getPlayerNation(attacker);

            if (vNation != null && aNation != null && vNation.equals(aNation)) {
                // Проверяем Кровавую Луну и Шлем Нации (Проклятие Безумия)
                boolean bloodMoonActive = false;
                try {
                    if (ru.example.vkchat.VKChatPlugin.getInstance() != null && 
                        ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager() != null) {
                        bloodMoonActive = ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager().isActive();
                    }
                } catch (Throwable ignored) {}

                if (bloodMoonActive) {
                    boolean hasNationalHelmet = false;
                    ItemStack helmet = attacker.getInventory().getHelmet();
                    if (helmet != null && helmet.hasItemMeta()) {
                        if (helmet.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "national_item"), org.bukkit.persistence.PersistentDataType.STRING)) {
                            hasNationalHelmet = true;
                        }
                    }

                    if (!hasNationalHelmet) {
                        // Обходим блокировку дружественного огня!
                        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                            attacker.sendMessage(ChatColor.RED + "☠️ [БЕЗУМИЕ КРОВЯНОЙ ЛУНЫ] Проклятие затмило ваш разум! Без защитного шлема нации вы ранили своего соотечественника!");
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                            attacker.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, attacker.getLocation().add(0, 1, 0), 10, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                        }
                        return; // Не отменяем событие, урон проходит!
                    }
                }

                e.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Нельзя бить соотечественников!");
                return;
            }
        }

        if (e.getDamager() instanceof Player && e.getEntity() instanceof LivingEntity) {
            Player p = (Player) e.getDamager();
            LivingEntity target = (LivingEntity) e.getEntity();
            ItemStack hand = p.getInventory().getItemInMainHand();

            if (hand != null && hand.hasItemMeta()) {
                String natId = hand.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING
                );
                if ("kgb_dagger".equals(natId)) {
                    if (ThreadLocalRandom.current().nextDouble() < 0.40) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
                        p.sendMessage(ChatColor.DARK_RED + "🗡️ [Смерш] Удар кинжала наложил увядание!");
                    }
                } else if ("imperial_saber".equals(natId)) {
                    if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
                        p.sendMessage(ChatColor.RED + "🗡️ [Кровотечение] Рана сабли кровоточит!");
                        target.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, target.getLocation().add(0, 1.0, 0), 10, 0.2, 0.2, 0.2, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                    }
                }
            }

            if (plugin.getNationManager().hasMutation(p, "wither_touch") && ThreadLocalRandom.current().nextDouble() < 0.15) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
                p.sendMessage(ChatColor.DARK_PURPLE + "💀 [Касание Нави] Цель иссушена!");
            }

            if (plugin.getNationManager().hasMutation(p, "blood_rage")) {
                AttributeInstance maxHpAttr3 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp3 = maxHpAttr3 != null ? maxHpAttr3.getValue() : 20.0;
                double hpPercent = p.getHealth() / maxHp3;
                if (hpPercent <= 0.30) {
                    e.setDamage(e.getDamage() * 1.30);
                }
            }

            if (plugin.getNationManager().hasMutation(p, "punisher_strike")) {
                int hits = 0;
                if (p.hasMetadata("punisher_hits")) {
                    hits = p.getMetadata("punisher_hits").get(0).asInt();
                }
                hits++;
                if (hits >= 5) {
                    hits = 0;
                    e.setDamage(e.getDamage() * 1.5);
                    p.sendMessage(ChatColor.RED + "⚔️ [Карательный Меч] КРИТИЧЕСКИЙ УДАР (x1.5)!");
                    target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.2, 0.5, 0.2, 0.1);
                }
                p.setMetadata("punisher_hits", new org.bukkit.metadata.FixedMetadataValue(plugin, hits));
            }

            if (plugin.getNationManager().hasMutation(p, "dark_assassin")) {
                long time = p.getWorld().getTime();
                boolean isNight = time > 13000 && time < 23000;
                int light = p.getLocation().getBlock().getLightLevel();
                if (isNight || light <= 7) {
                    e.setDamage(e.getDamage() * 1.20);
                }
            }

            // [НОВОЕ] Тактический Удар (trian_strike / tactical_strike) - тройной урон из невидимости
            if (plugin.getNationManager().hasMutation(p, "tactical_strike")) {
                if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    e.setDamage(e.getDamage() * 3.0);
                    p.sendMessage(ChatColor.GREEN + "💥 [Тактический Удар] Тройной урон из невидимости!");
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
        }

        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();

            ItemStack hand = victim.getInventory().getItemInMainHand();
            ItemStack offHand = victim.getInventory().getItemInOffHand();
            
            boolean isHoldingShield = false;
            if (hand != null && hand.getType() != Material.AIR && hand.hasItemMeta()) {
                ItemMeta meta = hand.getItemMeta();
                if (meta != null) {
                    String natItem = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING);
                    if ("imperial_shield".equals(natItem)) {
                        isHoldingShield = true;
                    }
                }
            }
            if (!isHoldingShield && offHand != null && offHand.getType() != Material.AIR && offHand.hasItemMeta()) {
                ItemMeta meta = offHand.getItemMeta();
                if (meta != null) {
                    String natItem = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING);
                    if ("imperial_shield".equals(natItem)) {
                        isHoldingShield = true;
                    }
                }
            }
            
            if (isHoldingShield && victim.isBlocking()) {
                e.setDamage(e.getDamage() * 0.50);
                victim.sendMessage(ChatColor.GREEN + "🛡️ [Щит Святогора] Богатырский щит поглотил 50% урона!");
                victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
            }

            if (plugin.getNationManager().hasMutation(victim, "bogatyr_resolve") && victim.isBlocking()) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 40, 1));
            }

            if (plugin.getNationManager().hasMutation(victim, "sacred_shield")) {
                AttributeInstance maxHpAttr4 = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp4 = maxHpAttr4 != null ? maxHpAttr4.getValue() : 20.0;
                double hpPercent = victim.getHealth() / maxHp4;
                if (hpPercent <= 0.15) {
                    long lastShield = 0;
                    if (victim.hasMetadata("sacred_shield_time")) {
                        lastShield = victim.getMetadata("sacred_shield_time").get(0).asLong();
                    }
                    if (System.currentTimeMillis() - lastShield >= 120000L) {
                        victim.setMetadata("sacred_shield_time", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 4));
                        victim.sendMessage(ChatColor.GREEN + "✨ [Оберег Руси] Сработал священный оберег! Вы неуязвимы на 3 секунды!");
                        victim.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, victim.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }

            // [НОВОЕ] Ужас Чернобога (pagan_fear) - шанс 10% иссушить нападающего
            if (plugin.getNationManager().hasMutation(victim, "pagan_fear") && e.getDamager() instanceof LivingEntity) {
                if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                    ((LivingEntity) e.getDamager()).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
                    victim.sendMessage(ChatColor.DARK_PURPLE + "💀 [Ужас Чернобога] Нападающий иссушен!");
                }
            }

            // [НОВОЕ] Царское Терпение (imperial_patience) -10% урона при полном здоровье
            if (plugin.getNationManager().hasMutation(victim, "imperial_patience")) {
                AttributeInstance maxHpAttr5 = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHpVal5 = maxHpAttr5 != null ? maxHpAttr5.getValue() : 20.0;
                if (victim.getHealth() >= maxHpVal5 - 0.5) {
                    e.setDamage(e.getDamage() * 0.90);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            ChunkClaim claim = plugin.getNationManager().getClaimAt(victim.getLocation());
            if (claim != null && claim.getOwner().equals(killer.getUniqueId())) {
                String vNation = plugin.getNationManager().getPlayerNation(victim);
                String kNation = plugin.getNationManager().getPlayerNation(killer);
                if (vNation != null && !vNation.equals(kNation)) {
                    claim.addDurability(50);
                    plugin.getNationManager().saveAll();
                    killer.sendMessage(ChatColor.GREEN + "Вы защитили свой приват от чужака! Прочность привата +50.");
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand != null && hand.hasItemMeta()) {
            String natId = hand.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING
            );
            if ("pagan_sickle".equals(natId)) {
                AttributeInstance maxHpAttr6 = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp6 = maxHpAttr6 != null ? maxHpAttr6.getValue() : 20.0;
                killer.setHealth(Math.min(killer.getHealth() + 1.0, maxHp6));
                killer.sendMessage(ChatColor.GREEN + "🌾 [Жатва] Ритуальный серп восстановил вам 1 HP!");
                killer.getWorld().spawnParticle(org.bukkit.Particle.HEART, killer.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2);
            }
        }

        if (plugin.getNationManager().hasMutation(killer, "vampiric_claws")) {
            AttributeInstance victimMaxHpAttr = e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = victimMaxHpAttr != null ? victimMaxHpAttr.getValue() : 20.0;
            double heal = maxHp * 0.10;
            AttributeInstance killerMaxHpAttr = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double killerMaxHp = killerMaxHpAttr != null ? killerMaxHpAttr.getValue() : 20.0;
            killer.setHealth(Math.min(killer.getHealth() + heal, killerMaxHp));
            killer.sendMessage(ChatColor.GREEN + "🩸 [Жертвенные Когти] Восстановлено +" + String.format("%.1f", heal) + " HP!");
            killer.getWorld().spawnParticle(org.bukkit.Particle.HEART, killer.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3);
        }

        // [НОВОЕ] Ярость Опричника (oprichnik_fury) - скорость II на 5 сек при убийстве
        if (plugin.getNationManager().hasMutation(killer, "oprichnik_fury")) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            killer.sendMessage(ChatColor.GREEN + "⚡ [Ярость Опричника] Скорость II на 5 секунд за убийство!");
        }
    }

    @EventHandler
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Monster) {
            // Пропускаем мобов обороны приватов (рейды/осады/диверсии) — они не блокируются
            if (e.getEntity().hasMetadata("defense_raid")
                    || e.getEntity().hasMetadata("defense_siege_boss")
                    || e.getEntity().hasMetadata("defense_siege_minion")
                    || e.getEntity().hasMetadata("defense_saboteur")) {
                return;
            }
            ChunkClaim claim = plugin.getNationManager().getClaimAt(e.getLocation());
            // Ур.3 «Покой»: запрет естественного спавна монстров (спавнеры работают).
            int noSpawnLevel = plugin.getConfig().getInt("claim.no-spawn-level", 3);
            if (claim != null && claim.getDurability() > 0 && claim.getLevel() >= noSpawnLevel && claim.isNoSpawnProtectionEnabled()) {
                if (e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInteractCrop(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            org.bukkit.block.Block b = e.getClickedBlock();
            if (b != null && b.getBlockData() instanceof org.bukkit.block.data.Ageable) {
                
                if (plugin.getNationManager().hasMutation(e.getPlayer(), "nature_regrowth")) {
                    if (e.getPlayer().getInventory().getItemInMainHand().getType() == org.bukkit.Material.AIR) {
                        org.bukkit.block.data.Ageable ageable = (org.bukkit.block.data.Ageable) b.getBlockData();
                        if (ageable.getAge() < ageable.getMaximumAge() && ThreadLocalRandom.current().nextDouble() < 0.45) {
                            ageable.setAge(ageable.getMaximumAge());
                            b.setBlockData(ageable);
                            b.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.05);
                            e.getPlayer().playSound(b.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        }
                    }
                }

                ChunkClaim claim = plugin.getNationManager().getClaimAt(b.getLocation());
                // Ур.5 «Цитадель»: мгновенный рост ферм владельцем (Shift+ПКМ).
                int farmingLevel = plugin.getConfig().getInt("claim.farming-level", 5);
                if (claim != null && claim.getDurability() > 0 && claim.getLevel() >= farmingLevel && claim.getOwner().equals(e.getPlayer().getUniqueId())) {
                    if (e.getPlayer().isSneaking() && ThreadLocalRandom.current().nextDouble() < 0.3) {
                        org.bukkit.block.data.Ageable ageable = (org.bukkit.block.data.Ageable) b.getBlockData();
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getMaximumAge());
                            b.setBlockData(ageable);
                            b.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 10);
                            e.getPlayer().playSound(b.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (plugin.getNationManager().hasMutation(p, "herbal_healing")) {
            Material type = e.getItem().getType();
            if (type == Material.APPLE || type == Material.SWEET_BERRIES) {
                AttributeInstance maxHpAttr7 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp7 = maxHpAttr7 != null ? maxHpAttr7.getValue() : 20.0;
                p.setHealth(Math.min(p.getHealth() + 4.0, maxHp7));
                p.sendMessage(ChatColor.GREEN + "🍎 [Целебные Травы] Восстановлено +4 HP!");
                p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.5, 0), 2, 0.2, 0.2, 0.2);
            }
        }
    }

    @EventHandler
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            Player p = (Player) e.getEntity();
            if (plugin.getNationManager().hasMutation(p, "gravity_leap")) {
                double dmg = e.getDamage();
                double newDmg = Math.max(0, dmg - 15.0);
                if (newDmg == 0) {
                    e.setCancelled(true);
                    p.sendMessage(ChatColor.AQUA + "🪶 [Опричный Прыжок] Вы совершили мягкую посадку без урона!");
                } else {
                    e.setDamage(newDmg);
                    p.sendMessage(ChatColor.AQUA + "🪶 [Опричный Прыжок] Урон от падения значительно снижен!");
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        ChunkClaim claim = plugin.getNationManager().getPlayerHomeClaim(p.getUniqueId());
        if (claim != null && claim.hasHome()) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(claim.getWorldName());
            if (w != null) {
                e.setRespawnLocation(new org.bukkit.Location(w, claim.getHomeX(), claim.getHomeY(), claim.getHomeZ()));
                p.sendMessage(ChatColor.GREEN + "🌟 [Оберег Нации] Вы успешно возродились в безопасности на точке вашего привата!");
            }
        }
    }

    // XP-бонус на территории нации (+25% опыта)
    @EventHandler
    public void onExpChange(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) return;
        ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
        if (claim != null && claim.getNation().equals(nation)) {
            int original = e.getAmount();
            e.setAmount(original + original / 4); // +25%
        }
    }

    @EventHandler
    public void onJoinDurabilityWarning(org.bukkit.event.player.PlayerJoinEvent e) {
        org.bukkit.entity.Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (ChunkClaim c : plugin.getNationManager().getNationClaims().values()) {
                if (c.getOwner().equals(p.getUniqueId()) && c.getDurability() < c.getMaxDurability() * 0.2) {
                    p.sendMessage(ChatColor.RED + "⚠ Внимание! Прочность привата '" + c.getName() + "' ниже 20% (" + c.getDurability() + "/" + c.getMaxDurability() + ")");
                    p.sendMessage(ChatColor.GRAY + "Покорми приват через меню (клик по блоку привата)");
                }
            }
        }, 60L);
    }

    @EventHandler
    public void onDonatorDiscount(org.bukkit.event.player.PlayerInteractEvent e) {
        // Скидка донатерам — встроена в GUI через проверку permission
    }

    // ═══ PARTICLE BORDER — показать границы привата при ПКМ с блоком привата в руке ═══
    @EventHandler
    public void onClaimBorderParticles(org.bukkit.event.player.PlayerInteractEvent e) {
        org.bukkit.entity.Player p = e.getPlayer();
        org.bukkit.inventory.ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        NamespacedKey radiusKey = new NamespacedKey(plugin, "claim_block_radius");
        if (!item.getItemMeta().getPersistentDataContainer().has(radiusKey, PersistentDataType.INTEGER)) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        int radius = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.INTEGER);
        // Ищем приват игрока в этой локации
        ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы должны быть внутри своего привата.");
            return;
        }
        // Показываем границы
        org.bukkit.World w = p.getWorld();
        int cx = claim.getX();
        int cz = claim.getZ();
        int minX = cx - claim.getRadius();
        int maxX = cx + claim.getRadius();
        int minZ = cz - claim.getRadius();
        int maxZ = cz + claim.getRadius();
        int y = p.getLocation().getBlockY();
        for (int x = minX; x <= maxX; x++) {
            w.spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y + 0.5, minZ + 0.5, 1, 0, 0, 0, 0);
            w.spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y + 0.5, maxZ + 0.5, 1, 0, 0, 0, 0);
        }
        for (int z = minZ; z <= maxZ; z++) {
            w.spawnParticle(org.bukkit.Particle.END_ROD, minX + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
            w.spawnParticle(org.bukkit.Particle.END_ROD, maxX + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
        }
        p.sendMessage(ChatColor.GREEN + "✦ Границы привата подсвечены.");
    }

    // ═══ ЛОГГИРОВАНИЕ ═══
    public static void logClaim(String action, Player player, String details) {
        String line = java.time.LocalDateTime.now() + " [" + action + "] " + player.getName() + " (" + player.getUniqueId() + "): " + details;
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("plugins/VKChatNations/claim-log.txt"),
                (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {}
    }
}
