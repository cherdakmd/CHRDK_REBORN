package ru.example.vkchatgear.runes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.command.TabCompleter;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuneCommand implements CommandExecutor, TabCompleter {
    private final VKChatGearPlugin plugin;

    public RuneCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §d§lРУНЫ §8◂ §7Биржа");

        // Заполнение фиолетовым стеклом (рамка для красоты)
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i % 9 == 0 || i % 9 == 8 || i >= 45) {
                inv.setItem(i, border);
            }
        }

        // Книга Информации о динамических ценах (в слот 9)
        ItemStack infoBook = new ItemStack(Material.BOOK);
        ItemMeta bookMeta = infoBook.getItemMeta();
        bookMeta.setDisplayName("§b§l📈 Как работают динамические цены?");
        List<String> bookLore = new ArrayList<>();
        bookLore.add("§7Добро пожаловать на биржу рун!");
        bookLore.add("§7Цены на все руны колеблются в реальном времени:");
        bookLore.add("§e• При покупке руны: §7её цена возрастает на §c+5% §7(высокий спрос).");
        bookLore.add("§e• Цены на остальные руны: §7медленно падают на §a-1% §7за каждую покупку.");
        bookLore.add("§e• Скидки могут достигать: §dдо -50% §7от базовой стоимости!");
        bookLore.add("§e• Максимальный предел цены: §cдо +200% §7от базы при ажиотаже.");
        bookLore.add("");
        bookLore.add("§6Следите за рынком и покупайте товары со скидками!");
        bookMeta.setLore(bookLore);
        infoBook.setItemMeta(bookMeta);
        inv.setItem(9, infoBook);

        // Ряд 1: Атакующие руны (Оружие)
        addRune(inv, 10, "Вампиризм", "vampirism");
        addRune(inv, 11, "Казнь", "execute");
        addRune(inv, 12, "Метеоритный Удар", "meteor");
        addRune(inv, 13, "Жнец Душ", "soul_reaper");
        addRune(inv, 14, "Критический Удар", "critical_strike");
        addRune(inv, 15, "Распад", "disintegration");
        addRune(inv, 16, "Удар Грома", "thunder_strike");

        // Ряд 2: Защитные руны (Броня)
        addRune(inv, 19, "Уклонение", "dodge");
        addRune(inv, 20, "Эгида", "shield");
        addRune(inv, 21, "Второе Дыхание", "second_wind");
        addRune(inv, 22, "Кожа Голема", "golem_skin");
        addRune(inv, 23, "Зеркало", "reflect_magic");
        addRune(inv, 24, "Поглощение", "absorption");
        addRune(inv, 25, "Связь Душ", "soul_bond");

        // Ряд 3: Полезные и особые руны
        addRune(inv, 28, "Аура Спешки", "haste_aura");
        addRune(inv, 29, "Печать Души", "rarity_seal");
        addRune(inv, 30, "Полет Ветра", "wind_glide");
        addRune(inv, 31, "Магнит Руд", "ore_magnet");
        addRune(inv, 32, "Аура Вампиризма", "vampire_aoe");
        addRune(inv, 33, "Ледяное Касание", "frozen_touch");
        addRune(inv, 34, "Ядовитое Облако", "poison_cloud");

        // Ряд 4: Кристаллы Заточки и Свиток Сохранения
        addCrystal(inv, 37, "Обычный [I-X]", "common", "crystal_common");
        addCrystal(inv, 38, "Редкий [XI-XV]", "rare", "crystal_rare");
        addCrystal(inv, 39, "Легендарный [XVI-XX]", "legendary", "crystal_legendary");
        addCrystal(inv, 40, "Древний [XXI-XXV]", "ancient", "crystal_ancient");
        addSafetyScroll(inv, 41, "Свиток Сохранения", "safety_scroll");
        
        // Новые ультимативные руны
        addRune(inv, 42, "Рефлексы Паука", "spider_reflexes");
        addRune(inv, 43, "Магматический Шаг", "magma_walker");
        addRune(inv, 44, "Метеоритный Дождь", "meteor_shower");
        addFusionScroll(inv, 45);

        // ═══ 35 УНИКАЛЬНЫХ ИМЕНОВАННЫХ РУН ═══
        // Атакующие руны (слот 45-49)
        addRune(inv, 45, "🔥 Руна Пламени", "flame_rune");
        addRune(inv, 46, "❄️ Руна Мороза", "frost_rune");
        addRune(inv, 47, "⚡ Руна Молнии", "lightning_rune");
        addRune(inv, 48, "☠️ Руна Яда", "poison_rune");
        addRune(inv, 49, "🩸 Руна Крови", "blood_rune");

        // Ряд 5: Атакующие руны 2 (слот 50-53)
        addRune(inv, 50, "🌑 Руна Тени", "shadow_rune");
        addRune(inv, 51, "✨ Руна Святости", "holy_rune");
        addRune(inv, 52, "🌀 Руна Пустоты", "void_rune");
        addRune(inv, 53, "💀 Руна Хаоса", "chaos_rune");

        // Ряд 6: Защитные руны (слот 54-60) — новый инвентарь
        // Примечание: нужно расширить инвентарь до 6 рядов (54 слота)
        // Пока добавим в существующие слоты

        p.openInventory(inv);
        return true;
    }

    private boolean isArmorRune(String id) {
        return id.equals("dodge") || id.equals("fire_aura") || id.equals("reflect_magic") || 
               id.equals("shield") || id.equals("second_wind") || id.equals("absorption") || 
               id.equals("haste_aura") || id.equals("golem_skin") || id.equals("soul_bond") || 
               id.equals("spider_reflexes") || id.equals("magma_walker");
    }

    private void addFusionScroll(Inventory inv, int slot) {
        int price = plugin.getConfig().getInt("hardcore-forging.rarity-fusion-scroll.price", 10000);
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lСвиток Идеального Слияния");
        List<String> lore = new ArrayList<>();
        lore.add("§7Делает следующее слияние редкости");
        lore.add("§7в §e/forge §7100% успешным.");
        lore.add("");
        lore.add("§cОчень дорогой предмет для апа редкости.");
        lore.add("§7Держите в инвентаре при слиянии.");
        lore.add("");
        lore.add("§eЦена: §b" + price + " реп. ВК");
        lore.add("§8Нажмите, чтобы купить");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "fusion_scroll_price"), PersistentDataType.INTEGER, price);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void addSafetyScroll(Inventory inv, int slot, String name, String priceId) {
        int price = plugin.getRuneMarketManager().getPrice(priceId);
        
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§l" + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Защищает предмет от отката");
        lore.add("§7уровня заточки при неудаче!");
        lore.add("");
        lore.add("§e• Как использовать:");
        lore.add("§e  Просто держите этот свиток в инвентаре");
        lore.add("§e  в момент проведения заточки кристаллом.");
        lore.add("§e  Свиток автоматически спасет предмет");
        lore.add("§e  и будет израсходован при неудаче.");
        lore.add("");
        lore.add("§eЦена: §b" + price + " реп. ВК");
        lore.add("§8Нажмите, чтобы купить");
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "safety_scroll_price"), PersistentDataType.INTEGER, price);
        item.setItemMeta(meta);
        
        inv.setItem(slot, item);
    }

    private void addCrystal(Inventory inv, int slot, String name, String tier, String priceId) {
        Material mat = Material.EMERALD;
        String color = "§a";
        if (tier.equals("rare")) {
            mat = Material.DIAMOND;
            color = "§9";
        } else if (tier.equals("legendary")) {
            mat = Material.PRISMARINE_SHARD;
            color = "§6§l";
        }
        
        int price = plugin.getRuneMarketManager().getPrice(priceId);
        String eventSuffix = "";
        
        if (System.currentTimeMillis() < plugin.getActiveMagicEventExpireTime()) {
            String evt = plugin.getActiveMagicEventName();
            double mult = plugin.getActiveMagicEventMultiplier();
            
            if (evt.equals("Магический Коллапс")) {
                price = (int) (price * mult);
                eventSuffix = " §c(Коллапс: +80%)";
            } else if (evt.equals("Двойная Заточка")) {
                price = (int) (price * mult);
                eventSuffix = " §a(Скидка: -50%)";
            }
        }
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color + "💎 Кристалл Заточки: " + name);
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Позволяет затачивать снаряжение.");
        lore.add("");
        int from = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".from",
            tier.equals("common") ? 0 : tier.equals("rare") ? 10 : tier.equals("legendary") ? 15 : 20);
        int to = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".to",
            tier.equals("common") ? 10 : tier.equals("rare") ? 15 : tier.equals("legendary") ? 20 : 25);
        int success = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".success",
            tier.equals("common") ? 90 : tier.equals("rare") ? 60 : tier.equals("legendary") ? 35 : 25);
        lore.add("§e• Диапазон заточки: §f+" + from + " ➔ +" + to);
        lore.add("§e• Шанс успеха: §a" + success + "%");
        if (tier.equals("common")) {
            lore.add("§7• Безопасный стартовый кристалл до +" + to);
            lore.add("§c• При провале: редко снижает заточку на -1");
        } else if (tier.equals("rare")) {
            lore.add("§c• При провале: может снизить заточку на -1, но не ниже +" + from);
        } else if (tier.equals("legendary")) {
            lore.add("§c• При провале: может снизить заточку на -1/-2, но не ниже +" + from);
            lore.add("§4• Есть небольшой шанс уничтожения без Свитка Сохранения");
        } else if (tier.equals("ancient")) {
            lore.add("§4• При провале: может снизить заточку на -1/-3, но не ниже +" + from);
            lore.add("§4• Высокий шанс уничтожения без Свитка Сохранения!");
            lore.add("§5• Древний кристалл для эндгейм-заточки");
        }
        lore.add("");
        lore.add("§7Перетащите этот кристалл на предмет");
        lore.add("§7в инвентаре для его заточки!");
        lore.add("");
        lore.add("§eЦена: §b" + price + " реп. ВК" + eventSuffix);
        lore.add("§8Нажмите, чтобы купить");
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING, tier);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_price"), PersistentDataType.INTEGER, price);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        
        inv.setItem(slot, item);
    }

    private void addRune(Inventory inv, int slot, String name, String id) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Руна: " + name);
        
        int price = plugin.getRuneMarketManager().getPrice(id);
        String eventSuffix = "";
        
        if (System.currentTimeMillis() < plugin.getActiveMagicEventExpireTime()) {
            String evt = plugin.getActiveMagicEventName();
            double mult = plugin.getActiveMagicEventMultiplier();
            
            if (evt.equals("Магический Коллапс")) {
                price = (int) (price * mult);
                eventSuffix = " §c(Коллапс: +80%)";
            } else if (evt.equals("Неделя Защиты") && isArmorRune(id)) {
                price = (int) (price * mult);
                eventSuffix = " §a(Скидка: -40%)";
            } else if (evt.equals("Неделя Атаки") && !isArmorRune(id)) {
                price = (int) (price * mult);
                eventSuffix = " §a(Скидка: -40%)";
            }
        }
        
        List<String> lore = new ArrayList<>();
        String desc = plugin.getConfig().getString("custom_enchants." + id + ".name", null);
        if (desc != null) {
            lore.add(ChatColor.translateAlternateColorCodes('&', desc));
            lore.add("");
        }
        lore.add(ChatColor.GRAY + "Перетащите эту руну на");
        lore.add(ChatColor.GRAY + "ваше снаряжение в инвентаре,");
        lore.add(ChatColor.GRAY + "чтобы наложить чары!");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Цена: " + ChatColor.AQUA + price + " реп. ВК" + eventSuffix);
        lore.add(ChatColor.DARK_GRAY + "Нажмите, чтобы купить");
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_id"), PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_price"), PersistentDataType.INTEGER, price);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        
        inv.setItem(slot, item);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
