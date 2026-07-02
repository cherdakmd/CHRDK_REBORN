package ru.example.vkchatartifacts.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.items.ArtifactFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public class ConsumablesListener implements Listener {
    private final VKChatArtifactsPlugin plugin;
    private final NamespacedKey consumableKey;
    private final NamespacedKey curseKey;
    private final NamespacedKey isArtifactKey;
    private final NamespacedKey buffKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey mythicKey;
    private final NamespacedKey expireKey;

    public static final Map<UUID, Long> ENCHANTMENT_SCROLL_BOOST = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ConsumablesListener(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
        this.consumableKey = new NamespacedKey(plugin, "is_consumable");
        this.curseKey = new NamespacedKey(plugin, "curse_type");
        this.isArtifactKey = new NamespacedKey(plugin, "is_artifact");
        this.buffKey = new NamespacedKey(plugin, "buff_type");
        this.levelKey = new NamespacedKey(plugin, "buff_level");
        this.mythicKey = new NamespacedKey(plugin, "is_mythic");
        this.expireKey = new NamespacedKey(plugin, "expire_time");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(consumableKey, PersistentDataType.STRING)) return;
        
        String type = meta.getPersistentDataContainer().get(consumableKey, PersistentDataType.STRING);
        e.setCancelled(true);

        int cdSecs = plugin.getConfig().getInt("consumables.cooldown", 30);
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(p.getUniqueId())) {
            long lastUsed = cooldowns.get(p.getUniqueId());
            if (now - lastUsed < cdSecs * 1000L) {
                p.sendMessage(ChatColor.RED + "Подождите " + ((cdSecs * 1000L - (now - lastUsed)) / 1000L) + " сек. перед следующим использованием свитка!");
                return;
            }
        }

        if ("CLEANSE".equals(type)) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() == Material.AIR || !offhand.hasItemMeta()) {
                p.sendMessage(ChatColor.RED + "Положите артефакт в левую руку (Offhand), чтобы очистить его!");
                return;
            }

            ItemMeta offMeta = offhand.getItemMeta();
            if (!offMeta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "В левой руке не артефакт!");
                return;
            }

            String curse = offMeta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
            if (curse == null || curse.equals("NONE")) {
                p.sendMessage(ChatColor.YELLOW + "Этот артефакт не проклят!");
                return;
            }

            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);

            int chance = plugin.getConfig().getInt("consumables.cleanse.success-chance", 50);
            int breakChance = plugin.getConfig().getInt("consumables.cleanse.break-chance", 30);
            
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < chance) {
                offMeta.getPersistentDataContainer().set(curseKey, PersistentDataType.STRING, "NONE");
                
                java.util.List<String> lore = offMeta.getLore();
                if (lore != null) {
                    lore.removeIf(line -> line.contains("☠ Проклятие"));
                    lore.add(ChatColor.AQUA + "✨ Очищено магией света");
                    offMeta.setLore(lore);
                }
                offhand.setItemMeta(offMeta);
                
                p.sendMessage(ChatColor.GREEN + "✨ Проклятие успешно снято!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                sendVkChat("✨ Чудо! Игрок " + p.getName() + " успешно очистил артефакт от проклятия!");
            } else if (roll < chance + breakChance) {
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                p.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, p.getLocation().add(0, 1, 0), 1);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                
                p.sendMessage(ChatColor.DARK_RED + " Артефакт не выдержал магии и рассыпался в пыль!");
                sendVkChat(" Катастрофа! Игрок " + p.getName() + " попытался очистить артефакт, но предмет взорвался!");
            } else {
                p.sendMessage(ChatColor.RED + "❌ Очищение не удалось, свиток потрачен впустую.");
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            }
        } 
        else if ("ESCAPE".equals(type)) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + " Сфера активирована! Не двигайтесь 3 секунды...");
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.1);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
            
            Location startLoc = p.getLocation();
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getLocation().distance(startLoc) < 1.0) {
                    p.performCommand("home");
                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.1);
                } else if (p.isOnline()) {
                    p.sendMessage(ChatColor.RED + "Телепортация прервана, так как вы пошевелились.");
                }
            }, 60L);
        }
        else if ("ENCHANTMENT_SCROLL".equals(type)) {
            ENCHANTMENT_SCROLL_BOOST.put(p.getUniqueId(), System.currentTimeMillis() + 600000L);
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.2);
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
            p.sendMessage(ChatColor.AQUA + "✨ Свиток Чар Усиления активирован! Все баффы артефактов усилены на 50% (10 мин).");
        }
        else if ("REPAIR_KIT".equals(type)) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() == Material.AIR || !offhand.hasItemMeta()) {
                p.sendMessage(ChatColor.RED + "Положите хрупкий артефакт в левую руку (Offhand)!");
                return;
            }
            ItemMeta offMeta = offhand.getItemMeta();
            if (!offMeta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "В левой руке не артефакт!");
                return;
            }
            if (!offMeta.getPersistentDataContainer().has(expireKey, PersistentDataType.LONG)) {
                p.sendMessage(ChatColor.RED + "Этот артефакт не хрупкий, набор не нужен!");
                return;
            }
            offMeta.getPersistentDataContainer().set(expireKey, PersistentDataType.LONG, System.currentTimeMillis() + 86400000L);
            offhand.setItemMeta(offMeta);
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);
            p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.1);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            p.sendMessage(ChatColor.GREEN + "✨ Ремонтный набор восстановил время жизни артефакта на 24 часа!");
        }
        else if ("EXCHANGE_RUNE".equals(type)) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() == Material.AIR || !offhand.hasItemMeta()) {
                p.sendMessage(ChatColor.RED + "Положите артефакт в левую руку (Offhand)!");
                return;
            }
            ItemMeta offMeta = offhand.getItemMeta();
            if (!offMeta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "В левой руке не артефакт!");
                return;
            }
            String newBuff = ArtifactFactory.BUFFS[ThreadLocalRandom.current().nextInt(ArtifactFactory.BUFFS.length)];
            offMeta.getPersistentDataContainer().set(buffKey, PersistentDataType.STRING, newBuff);
            java.util.List<String> lore = offMeta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.contains("➕"));
                String buffLore = "";
                int lvl = offMeta.getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER) ? offMeta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER) : 1;
                switch (newBuff) {
                    case "HEALTH": buffLore = ChatColor.GREEN + "➕ Максимальное Здоровье +" + (lvl * 2); break;
                    case "DAMAGE": buffLore = ChatColor.GREEN + "➕ Урон в ближнем бою +" + lvl; break;
                    case "SPEED": buffLore = ChatColor.GREEN + "➕ Скорость передвижения +" + (lvl * 10) + "%"; break;
                    case "REGENERATION": buffLore = ChatColor.GREEN + "➕ Пассивная Регенерация " + lvl + " ур."; break;
                    case "VAMPIRISM": buffLore = ChatColor.GREEN + "➕ Вампиризм " + (lvl * 10) + "%"; break;
                    case "THORNS": buffLore = ChatColor.GREEN + "➕ Отражение урона " + lvl + " ур."; break;
                    case "FIRE_RESISTANCE": buffLore = ChatColor.GREEN + "➕ Иммунитет к огню"; break;
                    case "LEVITATION": buffLore = ChatColor.GREEN + "➕ Иммунитет к урону от падения"; break;
                    case "CRITICAL": buffLore = ChatColor.GREEN + "➕ Шанс крита " + (lvl * 5) + "%"; break;
                    case "ABSORPTION": buffLore = ChatColor.GREEN + "➕ Абсорбция " + lvl + " ур."; break;
                    case "NIGHT_VISION": buffLore = ChatColor.GREEN + "➕ Ночное зрение"; break;
                    case "HASTE": buffLore = ChatColor.GREEN + "➕ Спешка " + lvl + " ур."; break;
                    case "WATER_BREATHING": buffLore = ChatColor.GREEN + "➕ Дыхание под водой"; break;
                    case "JUMP_BOOST": buffLore = ChatColor.GREEN + "➕ Мощный прыжок " + lvl + " ур."; break;
                    case "LUCK": buffLore = ChatColor.GREEN + "➕ Удача " + lvl + " ур."; break;
                    case "WITHER_TOUCH": buffLore = ChatColor.GREEN + "➕ Касание Иссушителя " + lvl + " ур."; break;
                    case "POISON_STRIKE": buffLore = ChatColor.GREEN + "➕ Ядовитый Удар " + lvl + " ур."; break;
                    case "FREEZE_AURA": buffLore = ChatColor.GREEN + "➕ Ледяная Аура " + lvl + " ур."; break;
                    case "LIGHTNING_STRIKE": buffLore = ChatColor.GREEN + "➕ Удар Молнии " + lvl + " ур."; break;
                    case "GHOST_WALK": buffLore = ChatColor.GREEN + "➕ Призрачный Шаг (Невидимость)"; break;
                    case "TRUE_STRIKE": buffLore = ChatColor.GREEN + "➕ Истинный Удар (Пробивание брони)"; break;
                    case "STEEL_SKIN": buffLore = ChatColor.GREEN + "➕ Стальная Кожа (Броня +" + lvl + ")"; break;
                    case "AQUATIC_SPEED": buffLore = ChatColor.GREEN + "➕ Скорость под водой " + lvl + " ур."; break;
                    case "FIRE_WALKER": buffLore = ChatColor.GREEN + "➕ Огненный Шаг (Хождение по лаве)"; break;
                    case "XP_BOOST": buffLore = ChatColor.GREEN + "➕ Бонус к опыту +" + (lvl * 15) + "%"; break;
                    case "DOUBLE_JUMP": buffLore = ChatColor.GREEN + "➕ Двойной прыжок"; break;
                    case "DODGE_CHANCE": buffLore = ChatColor.GREEN + "➕ Шанс уклонения " + (lvl * 5) + "%"; break;
                    case "KNOCKBACK_RESIST": buffLore = ChatColor.GREEN + "➕ Сопротивление отбрасыванию " + (lvl * 30) + "%"; break;
                    case "MAX_HEALTH_BOOST": buffLore = ChatColor.GREEN + "➕ Колоссальное здоровье +" + (lvl * 10); break;
                    case "HERO_OF_VILLAGE": buffLore = ChatColor.GREEN + "➕ Герой Деревни " + lvl + " ур."; break;
                    case "STRENGTH_BOOST": buffLore = ChatColor.GREEN + "➕ Сила " + lvl + " ур."; break;
                    case "RESISTANCE": buffLore = ChatColor.GREEN + "➕ Сопротивление " + lvl + " ур."; break;
                    case "SATURATION": buffLore = ChatColor.GREEN + "➕ Вечная сытость"; break;
                    case "LUCK_OF_THE_SEA": buffLore = ChatColor.GREEN + "➕ Морская удача " + lvl + " ур."; break;
                    case "SOUL_DRAIN": buffLore = ChatColor.GREEN + "➕ Вытягивание души (лечит при убийстве) +" + lvl; break;
                    case "FROST_BITE": buffLore = ChatColor.GREEN + "➕ Морозный укус " + lvl + " ур."; break;
                    case "MANA_SHIELD": buffLore = ChatColor.GREEN + "➕ Мана-щит (поглощает " + (lvl * 10) + "% урона)"; break;
                    case "TELEKINESIS": buffLore = ChatColor.GREEN + "➕ Телекинез (подбор предметов на расст.)"; break;
                    case "ENDER_SHIFT": buffLore = ChatColor.GREEN + "➕ Эндер-сдвиг (телепорт ПКМ в воздухе)"; break;
                    case "BERSERKER": buffLore = ChatColor.GREEN + "➕ Ярость " + lvl + " ур. (урон растет с потерей ХП)"; break;
                    case "ARCANE_BURST": buffLore = ChatColor.GREEN + "➕ Магический взрыв " + lvl + " ур."; break;
                    case "SHADOW_STEP": buffLore = ChatColor.GREEN + "➕ Теневой шаг (ускорение после уклонения)"; break;
                    case "LIFESTEAL_AURA": buffLore = ChatColor.GREEN + "➕ Аура вампиризма " + lvl + " ур."; break;
                    case "IRON_WILL": buffLore = ChatColor.GREEN + "➕ Железная воля " + lvl + " ур."; break;
                    case "TRAP_SENSE": buffLore = ChatColor.GREEN + "➕ Чувство ловушки " + lvl + " ур."; break;
                    case "TREASURE_HUNTER": buffLore = ChatColor.GREEN + "➕ Охотник за сокровищами +" + (lvl * 10) + "%"; break;
                    case "FLAME_TONGUE": buffLore = ChatColor.GREEN + "➕ Пылающий язык " + lvl + " ур."; break;
                    case "WIND_WALKER": buffLore = ChatColor.GREEN + "➕ Шагающий по ветру " + lvl + " ур."; break;
                    case "ECHO_STRIKE": buffLore = ChatColor.GREEN + "➕ Удар-эхо " + lvl + " ур. (шанс двойного удара)"; break;
                    default: buffLore = ChatColor.GREEN + "➕ " + newBuff + " " + lvl + " ур."; break;
                }
                lore.add(2, buffLore);
                offMeta.setLore(lore);
            }
            offhand.setItemMeta(offMeta);
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.2);
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            p.sendMessage(ChatColor.DARK_PURPLE + "✨ Руна Обмена перекатила бафф артефакта на: " + newBuff + "!");
        }
        else if ("FORTIFICATION_TOTEM".equals(type)) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() == Material.AIR || !offhand.hasItemMeta()) {
                p.sendMessage(ChatColor.RED + "Положите артефакт в левую руку (Offhand)!");
                return;
            }
            ItemMeta offMeta = offhand.getItemMeta();
            if (!offMeta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "В левой руке не артефакт!");
                return;
            }
            if (offMeta.getPersistentDataContainer().has(mythicKey, PersistentDataType.INTEGER) && offMeta.getPersistentDataContainer().get(mythicKey, PersistentDataType.INTEGER) == 1) {
                p.sendMessage(ChatColor.YELLOW + "Этот артефакт уже привязан к душе!");
                return;
            }
            offMeta.getPersistentDataContainer().set(mythicKey, PersistentDataType.INTEGER, 1);
            java.util.List<String> lore = offMeta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.contains("Привязана") || line.contains("не выпадает"));
                lore.add(ChatColor.AQUA + "✨ Привязана к душе (Не выпадает при смерти).");
                offMeta.setLore(lore);
            }
            offhand.setItemMeta(offMeta);
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);
            p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.3);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            p.sendMessage(ChatColor.GOLD + "✨ Тотем Укрепления привязал артефакт к твоей душе!");
        }
        else if ("SYNTHESIS".equals(type)) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            ItemStack offHand = p.getInventory().getItemInOffHand();

            if (mainHand == null || mainHand.getType() == Material.AIR || !mainHand.hasItemMeta() ||
                !mainHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "Держите артефакт в основной руке!");
                return;
            }
            if (offHand == null || offHand.getType() == Material.AIR || !offHand.hasItemMeta() ||
                !offHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "Положите второй артефакт в левую руку (Offhand)!");
                return;
            }

            ItemMeta mainMeta = mainHand.getItemMeta();
            ItemMeta offMeta = offHand.getItemMeta();

            String mainBuff = mainMeta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            String offBuff = offMeta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            int mainLevel = mainMeta.getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER) ? mainMeta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER) : 1;
            int offLevel = offMeta.getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER) ? offMeta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER) : 1;

            if (mainBuff == null || offBuff == null) {
                p.sendMessage(ChatColor.RED + "Ошибка: не удалось определить баффы артефактов!");
                return;
            }

            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);

            if (mainBuff.equals(offBuff)) {
                int newLevel = Math.min(mainLevel + 1, 5);
                mainMeta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, newLevel);

                java.util.List<String> lore = mainMeta.getLore();
                if (lore != null) {
                    for (int i = 0; i < lore.size(); i++) {
                        String line = lore.get(i);
                        if (line.contains("➕")) {
                            lore.set(i, ChatColor.GREEN + "➕ " + getBuffDescription(mainBuff, newLevel));
                            break;
                        }
                    }
                    mainMeta.setLore(lore);
                }

                mainHand.setItemMeta(mainMeta);
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

                p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.2);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
                p.sendMessage(ChatColor.GREEN + "✨ Свиток Синтеза усилил артефакт! Уровень: " + mainLevel + " → " + newLevel);
            } else {
                java.util.List<String> lore = mainMeta.getLore();
                if (lore != null) {
                    boolean found = false;
                    for (int i = 0; i < lore.size(); i++) {
                        if (lore.get(i).contains("➕") && !found) {
                            found = true;
                            continue;
                        }
                    }
                    if (lore.size() > 2) {
                        lore.add(2, ChatColor.GREEN + "➕ " + getBuffDescription(offBuff, offLevel));
                    } else {
                        lore.add(ChatColor.GREEN + "➕ " + getBuffDescription(offBuff, offLevel));
                    }
                    mainMeta.setLore(lore);
                }

                mainHand.setItemMeta(mainMeta);
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

                p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.2);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
                p.sendMessage(ChatColor.GREEN + "✨ Свиток Синтеза объединил баффы! Новый бафф: " + offBuff);
            }
        }
        else if ("DECAY_ANTIPODE".equals(type)) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() == Material.AIR || !offhand.hasItemMeta()) {
                p.sendMessage(ChatColor.RED + "Положите артефакт в левую руку (Offhand)!");
                return;
            }
            ItemMeta offMeta = offhand.getItemMeta();
            if (!offMeta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                p.sendMessage(ChatColor.RED + "В левой руке не артефакт!");
                return;
            }
            String curse = offMeta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
            if (curse == null || curse.equals("NONE")) {
                p.sendMessage(ChatColor.YELLOW + "Этот артефакт не проклят!");
                return;
            }
            offMeta.getPersistentDataContainer().set(curseKey, PersistentDataType.STRING, "NONE");
            java.util.List<String> lore = offMeta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.contains("☠ Проклятие"));
                lore.add(ChatColor.GREEN + "✨ Антидот нейтрализовал проклятие!");
                offMeta.setLore(lore);
            }
            offhand.setItemMeta(offMeta);
            item.setAmount(item.getAmount() - 1);
            cooldowns.put(p.getUniqueId(), now);
            p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            p.sendMessage(ChatColor.GREEN + "✨ Антидот Разложения успешно снял проклятие!");
            sendVkChat("✨ Игрок " + p.getName() + " снял проклятие с артефакта с помощью Антидота Разложения!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLethalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            ItemStack offHand = p.getInventory().getItemInOffHand();
            
            ItemStack totem = null;
            if (isReviveTotem(mainHand)) totem = mainHand;
            else if (isReviveTotem(offHand)) totem = offHand;
            
            if (totem != null) {
                e.setCancelled(true);
                totem.setAmount(totem.getAmount() - 1);
                
                AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                p.setHealth(maxHpAttr != null ? maxHpAttr.getValue() : 20.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 1));
                
                p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 200, 0.5, 1, 0.5, 0.5);
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                
                p.sendMessage(ChatColor.RED + " Тотем Крови спас твою жизнь, восстановив все силы!");
            }
        }
    }

    private boolean isReviveTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(consumableKey, PersistentDataType.STRING) &&
               "REVIVE".equals(meta.getPersistentDataContainer().get(consumableKey, PersistentDataType.STRING));
    }

    private void sendVkChat(String msg) {
        try {
            Object corePlugin = Bukkit.getPluginManager().getPlugin("VKChat");
            if (corePlugin != null) {
                Method getApiMethod = corePlugin.getClass().getMethod("getApi");
                Object vkApi = getApiMethod.invoke(corePlugin);
                Method m = vkApi.getClass().getMethod("sendToMainChat", String.class);
                m.invoke(vkApi, msg);
            }
        } catch (Exception ignored) { }
    }

    private String getBuffDescription(String buff, int level) {
        return ru.example.vkchatartifacts.items.ArtifactFactory.getBuffDescription(buff, level);
    }
}
