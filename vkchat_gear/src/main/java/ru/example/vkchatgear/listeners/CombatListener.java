package ru.example.vkchatgear.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.List;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.ArrayList;
import java.util.Random;

public class CombatListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack item : e.getDrops()) {
            inventory.add(item);
        }
        
        List<ItemStack> downgradeCandidates = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                if (item.getItemMeta().getLore().toString().contains("Редкость:")) {
                    downgradeCandidates.add(item);
                }
            }
        }
        
        if (!downgradeCandidates.isEmpty()) {
            ItemStack toDowngrade = downgradeCandidates.get(random.nextInt(downgradeCandidates.size()));
            ItemMeta meta = toDowngrade.getItemMeta();
            List<String> lore = meta.getLore();
            boolean hasSeal = false;
            int sealIndex = -1;
            
            for (int i = 0; i < lore.size(); i++) {
                if (org.bukkit.ChatColor.stripColor(lore.get(i)).contains("Печать Души")) {
                    hasSeal = true;
                    sealIndex = i;
                    break;
                }
            }
            
            if (hasSeal) {
                lore.remove(sealIndex);
                meta.setLore(lore);
                toDowngrade.setItemMeta(meta);
                p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "🛡️ [Печать Души] Ваша Печать Души защитила предмет " + meta.getDisplayName() + " от потери грейда, но разрушилась!");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation(), 40, 0.5, 0.5, 0.5, 0.15);
            } else {
                plugin.getGearManager().downgradeGear(toDowngrade);
                p.sendMessage(org.bukkit.ChatColor.DARK_RED + "☠ При смерти ваше снаряжение пострадало... Один из предметов потерял свой грейд!");
            }
        }
    }

    private final VKChatGearPlugin plugin;
    private final Random random = new Random();

    public CombatListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;

        // Зависимость сложности мобов от репутации игрока ВК (Тьма наступает)
        if (e.getEntity() instanceof Player && e.getDamager() instanceof org.bukkit.entity.Monster) {
            Player victim = (Player) e.getEntity();
            try {
                int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(victim);
                if (vkId != -1) {
                    int rep = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getReputation(vkId);
                    if (rep > 100) {
                        double multiplier = 1.0 + (rep - 100) * 0.0005;
                        multiplier = Math.min(2.5, multiplier); // Ограничиваем урон максимум в 2.5 раза
                        
                        e.setDamage(e.getDamage() * multiplier);
                        
                        // Мрачные частицы при получении урона
                        victim.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 0), 1.5f));
                        
                        // Шанс дебаффа для богатых репутацией игроков
                        if (rep > 1000 && new java.util.Random().nextInt(100) < 15) {
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                            victim.sendMessage(org.bukkit.ChatColor.RED + "☠ Твоя высокая репутация привлекает Тьму! Монстр ошеломил тебя (Замедление II)!");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }


        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();
            double extraHealth = 0.0;
            double damageReduction = 0.0;
            
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor != null && armor.hasItemMeta()) {
                    int lvl = armor.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
                    extraHealth += (lvl * 0.5); // +0.5 ХП за каждый уровень шмотки
                    damageReduction += (lvl * 0.01); // 1% снижения урона за каждый уровень
                }
            }
            
            double multiplier = 1.0;
            // Дебафф сета Ясного Сокола: +10% получаемого урона (хрупкий класс)
            if (plugin.getGearManager().isWearingSet(victim, "sokol")) {
                multiplier = 1.10;
            }

            if (damageReduction > 0) {
                e.setDamage(e.getDamage() * (1.0 - Math.min(damageReduction, 0.35)) * multiplier); // Нерф: кап 35% резиста от заточки
                double rarityDefense = 0.0;
                for (ItemStack armor : victim.getInventory().getArmorContents()) {
                    rarityDefense += plugin.getGearManager().getRarityDefenseBonus(plugin.getGearManager().getRarityKey(armor));
                }
                if (rarityDefense > 0) {
                    e.setDamage(e.getDamage() * (1.0 - Math.min(0.25, rarityDefense)));
                }
                for (ItemStack armor : victim.getInventory().getArmorContents()) {
                    if (plugin.getGearManager().hasDefect(armor, "fragile")) {
                        e.setDamage(e.getDamage() * 1.10);
                        break;
                    }
                }
            } else if (multiplier != 1.0) {
                e.setDamage(e.getDamage() * multiplier);
            }
        }

        // Эффекты брони жертвы
        if (e.getEntity() instanceof Player && e.getDamager() instanceof LivingEntity) {
            Player victim = (Player) e.getEntity();
            LivingEntity attacker = (LivingEntity) e.getDamager();
            
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                    List<String> lore = armor.getItemMeta().getLore();
                    
                    if (hasEnchant(lore, "Уклонение") && random.nextInt(100) < 10) {
                        e.setCancelled(true);
                        victim.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                        victim.sendMessage(org.bukkit.ChatColor.WHITE + " Вы уклонились от атаки!");
                        return;
                    }
                    if (hasEnchant(lore, "Кровавые шипы") && random.nextInt(100) < 30) {
                        attacker.damage(e.getDamage() * 0.3);
                        attacker.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, attacker.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                    }
                    if (hasEnchant(lore, "Огненная аура") && random.nextInt(100) < 20) {
                        attacker.setFireTicks(80);
                    }
                    // Эгида (Пассивно при лоу ХП)
                    if (hasEnchant(lore, "Эгида")) {
                        if ((victim.getHealth() / victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()) <= 0.2) {
                            if (!victim.hasPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)) {
                                victim.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 4)); // Почти неуязвимость 5 сек
                                victim.sendMessage(org.bukkit.ChatColor.AQUA + " Эгида активирована! Вы защищены.");
                            }
                        }
                    }
                    
                    // Поглощение
                    if (hasEnchant(lore, "Поглощение") && random.nextInt(100) < 15) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
                    }
                    
                    if (hasEnchant(lore, "Магматический Шаг")) victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0));
                    if (hasEnchant(lore, "Эндер Щит") && random.nextInt(100) < 5) {
                        org.bukkit.Location loc = victim.getLocation().add(random.nextInt(10)-5, 0, random.nextInt(10)-5);
                        victim.teleport(loc);
                    }
                    if (hasEnchant(lore, "Поступь Ветра")) victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                    if (hasEnchant(lore, "Кожа Голема")) victim.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 0));
                    if (hasEnchant(lore, "Рефлексы Паука")) victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 1));
                    if (hasEnchant(lore, "Аура Исцеления") && random.nextInt(100) < 10) victim.setHealth(Math.min(victim.getHealth() + 2.0, victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                    if (hasEnchant(lore, "Подводная Жизнь")) victim.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0));

                    if (hasEnchant(lore, "Зеркало") && random.nextInt(100) < 15) {
                        if (e.getCause() == EntityDamageByEntityEvent.DamageCause.MAGIC || e.getCause() == EntityDamageByEntityEvent.DamageCause.PROJECTILE) {
                            attacker.damage(e.getDamage());
                            e.setCancelled(true);
                            victim.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                            return;
                        }
                    }

                    // Связь Душ (Шанс 10% вернуть 50% урона и исцелиться)
                    if (hasEnchant(lore, "Связь Душ") && random.nextInt(100) < 10) {
                        double dmg = e.getDamage() * 0.50;
                        attacker.damage(dmg, victim);
                        victim.setHealth(Math.min(victim.getHealth() + dmg, victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                        victim.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "🔮 [Связь Душ] Вы вернули " + String.format("%.1f", dmg) + " урона и исцелились!");
                        victim.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, victim.getLocation().add(0, 1.0, 0), 25, 0.3, 0.3, 0.3);
                        attacker.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.0, 0), 10);
                    }

                    String armorProc = getRarityProc(armor);
                    if (isProc(armorProc, "Астральный Барьер", "Щит Сварога", "Оберег") && random.nextInt(100) < 12) {
                        e.setDamage(e.getDamage() * 0.65);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 80, 1));
                        victim.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, victim.getLocation().add(0, 1.0, 0), 35, 0.5, 0.7, 0.5, 0.08);
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
                        victim.sendMessage(org.bukkit.ChatColor.AQUA + "✦ [Астральный Барьер] Удар частично рассеян.");
                    }
                    if (isProc(armorProc, "Развеивание", "Очищение") && random.nextInt(100) < 10) {
                        cleanseNegativeEffects(victim);
                        victim.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, victim.getLocation().add(0, 1.0, 0), 25, 0.4, 0.6, 0.4, 0.04);
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
                        victim.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "✦ [Развеивание] Негативные эффекты рассеяны.");
                    }
                }
            }

            // Грозовой Разряд (контратака молнией)
            if (plugin.getGearManager().isWearingSet(victim, "perun") && random.nextInt(100) < 15) {
                attacker.getWorld().strikeLightningEffect(attacker.getLocation());
                attacker.damage(5.0, victim);
                victim.sendMessage(org.bukkit.ChatColor.YELLOW + " Грозовой разряд ударил вашего обидчика!");
            }

            // --- НОВЫЕ АКТИВНЫЕ ЭФФЕКТЫ НАЦИОНАЛЬНЫХ СЕТОВ ---

            // 1. Темный Совет (Танкист) - Дымовая завеса при ХП < 20%
            if (plugin.getGearManager().isWearingSet(victim, "tankist")) {
                double hpPercent = victim.getHealth() / victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                if (hpPercent <= 0.20) {
                    long lastSmoke = 0;
                    if (victim.hasMetadata("tankist_smoke_cooldown")) {
                        lastSmoke = victim.getMetadata("tankist_smoke_cooldown").get(0).asLong();
                    }
                    if (System.currentTimeMillis() - lastSmoke >= 45000L) { // Кулдаун 45 секунд
                        victim.setMetadata("tankist_smoke_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
                        
                        // Эффекты для игрока
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 1));
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                        victim.sendMessage(org.bukkit.ChatColor.DARK_RED + "💨 [Спецагент] Сработала дымовая завеса! Вы получили невидимость и скорость!");
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.8f);
                        victim.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, victim.getLocation(), 80, 1.0, 0.5, 1.0, 0.1);
                        victim.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, victim.getLocation(), 80, 1.0, 0.5, 1.0, 0.1);

                        // Дебаффы для нападающего и врагов вокруг
                        for (org.bukkit.entity.Entity ent : victim.getNearbyEntities(5, 5, 5)) {
                            if (ent instanceof LivingEntity && ent != victim) {
                                LivingEntity le = (LivingEntity) ent;
                                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 2));
                            }
                        }
                    }
                }
            }

            // 2. Светлая Империя (Богатырь) - Богатырский щит бессмертия при ХП < 15%
            if (plugin.getGearManager().isWearingSet(victim, "bogatyr")) {
                double hpPercent = victim.getHealth() / victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                if (hpPercent <= 0.15) {
                    long lastShield = 0;
                    if (victim.hasMetadata("bogatyr_shield_cooldown")) {
                        lastShield = victim.getMetadata("bogatyr_shield_cooldown").get(0).asLong();
                    }
                    if (System.currentTimeMillis() - lastShield >= 120000L) { // Кулдаун 2 минуты
                        victim.setMetadata("bogatyr_shield_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
                        
                        e.setCancelled(true);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 4)); // Полная неуязвимость на 5 секунд
                        victim.sendMessage(org.bukkit.ChatColor.GOLD + "🛡️ [Богатырский Щит] Ваше здоровье критическое! Пробужден щит бессмертия на 5 секунд!");
                        victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.8f);
                        victim.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, victim.getLocation(), 100, 0.8, 1.0, 0.8, 0.2);
                        return;
                    }
                }
            }
            
            // Проверка Второго дыхания при смертельном ударе
            if (victim.getHealth() - e.getFinalDamage() <= 0) {
                for (int i = 0; i < victim.getInventory().getArmorContents().length; i++) {
                    ItemStack armor = victim.getInventory().getArmorContents()[i];
                    if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                        if (hasEnchant(armor.getItemMeta().getLore(), "Второе дыхание")) {
                            e.setCancelled(true);
                            victim.setHealth(victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() / 2);
                            victim.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, victim.getLocation(), 100, 1, 1, 1, 0.1);
                            
                            // Защитная механика для сетовой и легендарной экипировки (вместо пропажи - штраф заточки)
                            boolean isSetPiece = plugin.getGearManager().isLegalSetPiece(armor);
                            
                            if (isSetPiece) {
                                NamespacedKey lvlKey = new NamespacedKey(plugin, "upgrade_level");
                                int currentLvl = armor.getItemMeta().getPersistentDataContainer().getOrDefault(lvlKey, PersistentDataType.INTEGER, 0);
                                int newLvl = Math.max(0, currentLvl - 5);
                                plugin.getGearManager().updateGearUpgradeLevel(armor, newLvl);
                                
                                // Ломаем прочность в 1 единицу
                                org.bukkit.inventory.meta.Damageable dmgMeta = (org.bukkit.inventory.meta.Damageable) armor.getItemMeta();
                                dmgMeta.setDamage(armor.getType().getMaxDurability() - 1);
                                armor.setItemMeta((ItemMeta) dmgMeta);
                                
                                victim.sendMessage(org.bukkit.ChatColor.YELLOW + "🛡️ [Второе Дыхание] Сработало спасение! Твой ценный сет брони " + armor.getItemMeta().getDisplayName() + " не пропал, но потерял -5 уровней заточки и сломан до 1 прочности!");
                            } else {
                                victim.sendMessage(org.bukkit.ChatColor.YELLOW + " Сработало Второе дыхание! Обычная броня была уничтожена.");
                                ItemStack[] contents = victim.getInventory().getArmorContents();
                                contents[i] = null;
                                victim.getInventory().setArmorContents(contents);
                            }
                            return;
                        }
                    }
                }
            }
        }

        // Эффекты оружия атакующего
        if (e.getDamager() instanceof Player && e.getEntity() instanceof LivingEntity) {
            Player p = (Player) e.getDamager();
            LivingEntity target = (LivingEntity) e.getEntity();
            ItemStack weapon = p.getInventory().getItemInMainHand();



            if (weapon != null && weapon.hasItemMeta()) {
                ItemMeta meta = weapon.getItemMeta();
                
                if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) {
                    int upgradeLvl = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER);
                    e.setDamage(e.getDamage() + (upgradeLvl * 0.6));
                }
                if (plugin.getGearManager().hasDefect(weapon, "dull")) {
                    e.setDamage(e.getDamage() * 0.90);
                }
                double rarityDamageBonus = plugin.getGearManager().getRarityDamageBonus(plugin.getGearManager().getRarityKey(weapon));
                if (rarityDamageBonus > 0) {
                    e.setDamage(e.getDamage() * (1.0 + rarityDamageBonus));
                }

                String weaponProc = getRarityProc(weapon);
                if (isProc(weaponProc, "Грозовой Импульс", "Воля Грозаа") && random.nextInt(100) < 12) {
                    double procDamage = 4.0 + Math.min(8.0, e.getDamage() * 0.20);
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(procDamage, p);
                    for (org.bukkit.entity.Entity near : target.getNearbyEntities(3, 3, 3)) {
                        if (near instanceof LivingEntity && near != p && near != target) ((LivingEntity) near).damage(procDamage * 0.45, p);
                    }
                    p.sendMessage(org.bukkit.ChatColor.YELLOW + "✦ [Грозовой Импульс] Разряд прошёл по цели.");
                }
                if (isProc(weaponProc, "Багровый Резонанс", "Кровь Рода") && random.nextInt(100) < 14) {
                    double heal = Math.min(6.0, Math.max(1.0, e.getFinalDamage() * 0.22));
                    p.setHealth(Math.min(p.getHealth() + heal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                    p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.2, 0), 5, 0.4, 0.4, 0.4, 0.03);
                    p.sendMessage(org.bukkit.ChatColor.RED + "✦ [Багровый Резонанс] Восстановлено " + String.format("%.1f", heal) + " HP.");
                }
                if (isProc(weaponProc, "Похищение Жизни", "Вампиризм") && random.nextInt(100) < 16) {
                    double heal = Math.min(4.0, Math.max(1.0, e.getFinalDamage() * 0.18));
                    p.setHealth(Math.min(p.getHealth() + heal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                    target.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, target.getLocation().add(0, 1.0, 0), 12, 0.25, 0.35, 0.25, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 80), 1.2f));
                }
                if (isProc(weaponProc, "Пламенный Контур") && random.nextInt(100) < 12) {
                    target.setFireTicks(Math.max(target.getFireTicks(), 80));
                    e.setDamage(e.getDamage() * 1.10);
                    target.getWorld().spawnParticle(org.bukkit.Particle.FLAME, target.getLocation().add(0, 1.0, 0), 20, 0.35, 0.35, 0.35, 0.04);
                }

                if (meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    
                    if (hasEnchant(lore, "Вампиризм")) {
                        double heal = e.getFinalDamage() * 0.2;
                        p.setHealth(Math.min(p.getHealth() + heal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                        p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
                    }
                    if (hasEnchant(lore, "Отравление")) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    }
                    // Метеорит (Пассивно шанс 10%)
                    if (hasEnchant(lore, "Метеоритный Удар") && random.nextInt(100) < 10) {
                        target.getWorld().createExplosion(target.getLocation(), 2.0f, false, false, p);
                    }
                    if (hasEnchant(lore, "Грозовой Разряд") && random.nextInt(100) < 15) {
                        target.getWorld().strikeLightningEffect(target.getLocation());
                        target.damage(5.0, p);
                    }
                    if (hasEnchant(lore, "Окоченение")) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                    }
                    if (hasEnchant(lore, "Мрак") && random.nextInt(100) < 20) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    }
                    if (hasEnchant(lore, "Гниль")) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    }
                    if (hasEnchant(lore, "Подбрасывание") && random.nextInt(100) < 15) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 4));
                    }
                    if (hasEnchant(lore, "Бронебойность")) {
                        e.setDamage(EntityDamageByEntityEvent.DamageModifier.ARMOR, 0); // Игнор брони (частичный)
                        e.setDamage(e.getDamage() * 1.08);
                    }
                    if (hasEnchant(lore, "Берсерк")) {
                        double missingHp = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() - p.getHealth();
                        e.setDamage(e.getDamage() + (missingHp * 0.5)); // +0.5 урона за каждое отсутствующее ХП
                    }
                    if (hasEnchant(lore, "Разоружение") && random.nextInt(100) < 5) {
                        if (target instanceof Player) {
                            Player tPlayer = (Player) target;
                            ItemStack hand = tPlayer.getInventory().getItemInMainHand();
                            if (hand != null && hand.getType() != org.bukkit.Material.AIR) {
                                tPlayer.getWorld().dropItemNaturally(tPlayer.getLocation(), hand);
                                tPlayer.getInventory().setItemInMainHand(null);
                                tPlayer.sendMessage(org.bukkit.ChatColor.RED + " У вас выбили оружие из рук!");
                            }
                        }
                    }
                    if (hasEnchant(lore, "Жнец Душ") && random.nextInt(100) < 5) {
                        if (!(target instanceof org.bukkit.entity.Boss)) {
                            if ((target.getHealth() / target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()) <= 0.15) {
                                target.setHealth(0);
                                p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
                    }
                    
                    if (hasEnchant(lore, "Критический Удар") && random.nextInt(100) < 15) {
                        e.setDamage(e.getDamage() * 1.35);
                        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, target.getLocation(), 20);
                    }
                    if (hasEnchant(lore, "Взрыв Иссушения") && random.nextInt(100) < 10) {
                        for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                            if (ent instanceof LivingEntity && ent != p) {
                                ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                            }
                        }
                    }
                    if (hasEnchant(lore, "Ядовитое Облако") && random.nextInt(100) < 15) {
                        for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                            if (ent instanceof LivingEntity && ent != p) {
                                ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                            }
                        }
                    }
                    if (hasEnchant(lore, "Удар Грома") && random.nextInt(100) < 10) {
                        target.getWorld().strikeLightningEffect(target.getLocation());
                        for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                            if (ent instanceof LivingEntity && ent != p) {
                                ((LivingEntity) ent).damage(5.0);
                            }
                        }
                    }
                    if (hasEnchant(lore, "Аура Вампира")) {
                        double heal = e.getFinalDamage() * 0.3;
                        p.setHealth(Math.min(p.getHealth() + heal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                    }

                    if (hasEnchant(lore, "Казнь")) {
                        if ((target.getHealth() / target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()) <= 0.3) {
                            e.setDamage(e.getDamage() * 1.25);
                            p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                        }
                    }

                    if (hasEnchant(lore, "Похищение Жизни")) {
                        p.setHealth(Math.min(p.getHealth() + 1.0, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                    }
                    if (hasEnchant(lore, "Огненный Удар")) {
                        target.setFireTicks(80);
                    }
                    if (hasEnchant(lore, "Паралич") && random.nextInt(100) < 10) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 5));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                        p.sendMessage(org.bukkit.ChatColor.YELLOW + " Цель парализована!");
                    }

                    // 1. Метеоритный Дождь (Шанс 10% вызвать серию взрывов вокруг цели)
                    if (hasEnchant(lore, "Метеоритный Дождь") && random.nextInt(100) < 10) {
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "☄️ [Метеоритный Дождь] Огненная волна накрыла врагов вокруг!");
                        for (int k = 0; k < 3; k++) {
                            org.bukkit.Location loc = target.getLocation().clone().add(random.nextInt(6) - 3, 0, random.nextInt(6) - 3);
                            target.getWorld().createExplosion(loc, 1.5f, false, false, p);
                        }
                    }

                    // 2. Ледяное Касание (Шанс 15% заморозить на 3 сек)
                    if (hasEnchant(lore, "Ледяное Касание") && random.nextInt(100) < 15) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 9)); // Медлительность X
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        p.sendMessage(org.bukkit.ChatColor.BLUE + "❄️ [Ледяное Касание] Вы заморозили цель на 3 секунды!");
                        target.getWorld().spawnParticle(org.bukkit.Particle.SNOWBALL, target.getLocation().add(0, 1.0, 0), 30, 0.3, 0.5, 0.3, 0.1);
                    }

                    // 3. Распад (Шанс 5% на двойной урон и Иссушение III)
                    if (hasEnchant(lore, "Распад") && random.nextInt(100) < 5) {
                        e.setDamage(e.getDamage() * 1.45);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2)); // Иссушение III
                        p.sendMessage(org.bukkit.ChatColor.DARK_RED + "☠️ [Распад] Цель дезинтегрирована! Двойной урон и увядание III!");
                        target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, target.getLocation().add(0, 1.0, 0), 30, 0.4, 0.5, 0.4, 0.1);
                    }

                    // 4. Аура Вампиризма (AOE похищение ХП у врагов вокруг)
                    if (hasEnchant(lore, "Аура Вампиризма") && random.nextInt(100) < 15) {
                        double totalHeal = 0.0;
                        int count = 0;
                        for (org.bukkit.entity.Entity near : target.getNearbyEntities(4, 4, 4)) {
                            if (near instanceof LivingEntity && near != p) {
                                LivingEntity enemy = (LivingEntity) near;
                                enemy.damage(3.0, p);
                                totalHeal += 1.5;
                                count++;
                                enemy.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, enemy.getLocation().add(0, 1.0, 0), 10, 0.2, 0.2, 0.2, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                            }
                        }
                        if (count > 0) {
                            p.setHealth(Math.min(p.getHealth() + totalHeal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                            p.sendMessage(org.bukkit.ChatColor.DARK_RED + "✨ [Аура Вампиризма] Вы похитили ХП у " + count + " противников в радиусе, восстановив +" + String.format("%.1f", totalHeal) + " HP!");
                            
                            // Рисуем багровый круг вокруг игрока
                            org.bukkit.Location pLoc = p.getLocation();
                            for (int i = 0; i < 30; i++) {
                                double angle = i * (2 * Math.PI / 30);
                                double rx = pLoc.getX() + 4.0 * Math.cos(angle);
                                double rz = pLoc.getZ() + 4.0 * Math.sin(angle);
                                pLoc.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, new org.bukkit.Location(pLoc.getWorld(), rx, pLoc.getY() + 0.1, rz), 1, 0, 0, 0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 2.0f));
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_DEATH, 1.0f, 0.5f);
                        }
                    }
                }
            }

            // Чернобог (иссушающие удары)
            if (plugin.getGearManager().isWearingSet(p, "chernobog") && random.nextInt(100) < 20) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
            }

            // 1. Кощей (Темные Язычники) - Вампиризм в размере 15% от нанесенного урона
            if (plugin.getGearManager().isWearingSet(p, "koshchey")) {
                double heal = e.getFinalDamage() * 0.15;
                p.setHealth(Math.min(p.getHealth() + heal, p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2);
            }

            // 2. Ясный Сокол (Темная Империя) - Казнь при ХП < 25% (шанс 20%)
            if (plugin.getGearManager().isWearingSet(p, "sokol")) {
                double maxHp = target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                if (target.getHealth() / maxHp <= 0.25) {
                    if (random.nextInt(100) < 20) {
                        e.setDamage(Math.max(e.getDamage() * 2.0, 12.0)); // Нерф: мощный, но не гарантированный ваншот
                        p.sendMessage(org.bukkit.ChatColor.RED + "⚔️ [Опричная Казнь] Вы казнили раненого противника!");
                        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);
                        target.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                    }
                }
            }
        }
    }


    private String getRarityProc(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        ItemMeta meta = item.getItemMeta();
        String pdc = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rarity_proc"), PersistentDataType.STRING);
        if (pdc != null && !pdc.trim().isEmpty()) return org.bukkit.ChatColor.stripColor(pdc);
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String stripped = org.bukkit.ChatColor.stripColor(line);
                if (stripped.startsWith("Прок редкости:")) return stripped.substring("Прок редкости:".length()).trim();
            }
        }
        return "";
    }

    private boolean isProc(String proc, String... aliases) {
        if (proc == null) return false;
        String clean = org.bukkit.ChatColor.stripColor(proc).toLowerCase(java.util.Locale.ROOT);
        for (String a : aliases) {
            if (clean.contains(a.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private void cleanseNegativeEffects(Player p) {
        for (PotionEffectType type : new PotionEffectType[]{
                PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.BLINDNESS,
                PotionEffectType.CONFUSION, PotionEffectType.HUNGER, PotionEffectType.LEVITATION
        }) {
            p.removePotionEffect(type);
        }
        p.setFireTicks(0);
    }

    private boolean hasEnchant(List<String> lore, String name) {
        for (String line : lore) {
            if (org.bukkit.ChatColor.stripColor(line).contains(name)) return true;
        }
        return false;
    }

    @EventHandler
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            Player p = (Player) e.getEntity();

            // Проверка чар Полет Ветра
            boolean hasWindGlide = false;
            for (ItemStack armor : p.getInventory().getArmorContents()) {
                if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                    if (hasEnchant(armor.getItemMeta().getLore(), "Полет Ветра")) {
                        hasWindGlide = true;
                        break;
                    }
                }
            }
            if (hasWindGlide) {
                e.setCancelled(true);
                p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 20, 0.4, 0.2, 0.4, 0.05);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
                p.sendMessage(org.bukkit.ChatColor.AQUA + "🍃 [Полет Ветра] Сила ветра спасла вас от урона при падении!");
                return;
            }

            if (plugin.getGearManager().isWearingSet(p, "gagarin")) {
                e.setCancelled(true);
                
                p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 100, 2.0, 0.2, 2.0, 0.1);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                
                for (org.bukkit.entity.Entity ent : p.getNearbyEntities(5, 5, 5)) {
                    if (ent instanceof LivingEntity && ent != p) {
                        LivingEntity le = (LivingEntity) ent;
                        le.setVelocity(new org.bukkit.util.Vector(0, 0.8, 0));
                        le.damage(4.0, p);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0));
                    }
                }
                p.sendMessage(org.bukkit.ChatColor.AQUA + " 🌌 Гравитационный импульс отбросил всех врагов вокруг!");
            }
        }
    }
}
