package ru.example.vkchatnations.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatnations.VKChatNationsPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * NationShopGui — GUI магазина нации и создание национальной брони.
 *
 * Извлечено из NationGuiListener:
 * - Магазин нации (открытие + обработка кликов)
 * - Создание брони сетов (ударник, танкист, волхв и т.д.)
 * - Национальные предметы (кирка, кинжал, посох и т.д.)
 * - Выдача стартового сета при вступлении
 */
public class NationShopGui {

    private final VKChatNationsPlugin plugin;

    public NationShopGui(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════
    // МАГАЗИН НАЦИИ
    // ═══════════════════════════════════════════════════════════════

    public void openNationShop(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §e§lНАЦИЯ §8◂ §7Магазин");

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        String setKey = getNationSetKey(nation);
        String setName = getSetName(setKey);
        String setBonus = getSetBonus(setKey);

        inv.setItem(10, createShopArmorItem(Material.IRON_HELMET, "Шлем " + setName, setKey, setBonus, "helmet", 150));
        inv.setItem(11, createShopArmorItem(Material.IRON_CHESTPLATE, "Нагрудник " + setName, setKey, setBonus, "chestplate", 150));
        inv.setItem(12, createShopArmorItem(Material.IRON_LEGGINGS, "Поножи " + setName, setKey, setBonus, "leggings", 150));
        inv.setItem(13, createShopArmorItem(Material.IRON_BOOTS, "Сапоги " + setName, setKey, setBonus, "boots", 150));

        addNationalShopItems(inv, nation, 14, 15, 16);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Назад");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
    }

    /**
     * Обработать клик в магазине нации.
     * @return true если клик обработан
     */
    public boolean handleClick(Player p, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return false;

        if (clicked.getType() == Material.BARRIER) {
            // Вернуться в главное меню
            return true;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
            p.sendMessage(ChatColor.RED + "❌ Для покупок привяжите ВКонтакте! (/vklink)");
            return true;
        }

        int cost = 0;
        if (clicked.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING)) {
            cost = 150;
        } else if (clicked.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING)) {
            cost = 250;
        }

        if (cost == 0) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп. (У вас: " + rep + ").");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return true;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        String nation = plugin.getNationManager().getPlayerNation(p);
        String setKey = getNationSetKey(nation);

        ItemStack itemToGive = null;

        if (clicked.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING)) {
            String type = clicked.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING);
            Material mat = Material.IRON_HELMET;
            String pieceName = "Шлем";
            if (type.contains("chestplate")) { mat = Material.IRON_CHESTPLATE; pieceName = "Нагрудник"; }
            else if (type.contains("leggings")) { mat = Material.IRON_LEGGINGS; pieceName = "Поножи"; }
            else if (type.contains("boots")) { mat = Material.IRON_BOOTS; pieceName = "Сапоги"; }

            itemToGive = createArmorPiece(mat, pieceName + " " + getSetName(setKey), setKey, getSetBonus(setKey), gearPlugin);
        } else {
            itemToGive = clicked.clone();
            ItemMeta meta = itemToGive.getItemMeta();
            List<String> lore = meta.getLore();
            if (lore.size() >= 3) {
                lore.remove(lore.size() - 1);
                lore.remove(lore.size() - 1);
                lore.remove(lore.size() - 1);
            }
            meta.setLore(lore);
            itemToGive.setItemMeta(meta);
        }

        if (itemToGive != null) {
            if (!p.getInventory().addItem(itemToGive).isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(), itemToGive);
            }
            p.sendMessage(ChatColor.GREEN + "✓ Вы успешно приобрели " + clicked.getItemMeta().getDisplayName() + ChatColor.GREEN + " за " + ChatColor.GOLD + cost + " реп. ВК!");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // НАЦИОНАЛЬНЫЕ ПРЕДМЕТЫ
    // ═══════════════════════════════════════════════════════════════

    private void addNationalShopItems(Inventory inv, String nation, int s1, int s2, int s3) {
        if (nation.equals("soviet_light")) {
            inv.setItem(s1, createNationalItem(Material.IRON_PICKAXE, "☭ Кирка Пятилетки", "soviet_pickaxe", 250,
                    "&eПассивный эффект: &aИндустриализация",
                    "&7Позволяет ломать блоки руды",
                    "&7на 15% быстрее обычного."));
            inv.setItem(s2, createNationalItem(Material.BOOK, "📕 Манифест Пролетариата", "soviet_manifesto", 250,
                    "&eАктивное свойство (ПКМ): &aГолос Труда",
                    "&7Дарует эффект Силы I всем союзникам",
                    "&7в радиусе 8 блоков на 30 сек. Кд 1 мин."));
            inv.setItem(s3, createNationalItem(Material.QUARTZ, "⚙️ Индустриальный Кристалл", "soviet_crystal", 250,
                    "&eАктивное свойство (ПКМ): &aУдарный План",
                    "&7Дарует эффект Спешки II на 1 минуту.",
                    "&7Перезарядка: 2 минуты."));
        } else if (nation.equals("soviet_dark")) {
            inv.setItem(s1, createNationalItem(Material.LEATHER_HELMET, "🕶️ Тактические Очки КГБ", "kgb_glasses", 250,
                    "&eПассивный эффект: &aТепловизор",
                    "&7Дарует постоянное Ночное Зрение",
                    "&7при ношении данного шлема."));
            inv.setItem(s2, createNationalItem(Material.IRON_SWORD, "🗡️ Кинжал Смерша", "kgb_dagger", 250,
                    "&eПассивный эффект: &aТихая Ликвидация",
                    "&7Удары по противникам со спины",
                    "&7накладывают Иссушение I на 5 сек."));
            inv.setItem(s3, createNationalItem(Material.GLASS_BOTTLE, "🧪 Сыворотка Скрытности", "kgb_serum", 250,
                    "&eАктивное свойство (ПКМ): &aМаскировка",
                    "&7Дарует Невидимость I и Скорость II",
                    "&7на 30 секунд. Кд 2 минуты."));
        } else if (nation.equals("pagan_light")) {
            inv.setItem(s1, createNationalItem(Material.WOODEN_HOE, "🌿 Древесный Посох Волхвов", "pagan_staff", 250,
                    "&eАктивное свойство (ПКМ): &aДыхание Леса",
                    "&7Мгновенно исцеляет вас на 4 HP.",
                    "&7Перезарядка: 30 секунд."));
            inv.setItem(s2, createNationalItem(Material.FEATHER, "🪶 Оберег ветра Стрибога", "pagan_amulet", 250,
                    "&eАктивное свойство (ПКМ): &aПорыв Ветра",
                    "&7Резко запускает вас вперед и вверх",
                    "&7(двойной прыжок). Кд 15 секунд."));
            inv.setItem(s3, createNationalItem(Material.HONEY_BOTTLE, "🏺 Целебный Отвар Лешего", "pagan_brew", 250,
                    "&eАктивное свойство (ПКМ): &aЖивая Вода",
                    "&7Восполняет 10 HP и дает Регенерацию II",
                    "&7на 15 секунд. Кулдаун: 1 минута."));
        } else if (nation.equals("pagan_dark")) {
            inv.setItem(s1, createNationalItem(Material.WITHER_SKELETON_SKULL, "💀 Оскверненный Идол Нави", "pagan_idol", 250,
                    "&eАктивное свойство (ПКМ): &aГнев Нави",
                    "&7Накладывает Иссушение II на всех врагов",
                    "&7в радиусе 6 блоков на 6с. Кд 1 мин."));
            inv.setItem(s2, createNationalItem(Material.IRON_HOE, "🌾 Ритуальный Серп Жатвы", "pagan_sickle", 250,
                    "&eПассивный эффект: &aЖатва Душ",
                    "&7Каждое убийство монстра восполняет",
                    "&7вам 1 HP здоровья."));
            inv.setItem(s3, createNationalItem(Material.DRAGON_BREATH, "🩸 Кровь Жертвенного Алтаря", "pagan_infusion", 250,
                    "&eАктивное свойство (ПКМ): &aКульт Ярости",
                    "&7Дает Силу II на 15 сек, но накладывает",
                    "&7Иссушение I на 5 сек. Кд 1 мин."));
        } else if (nation.equals("imperial_light")) {
            inv.setItem(s1, createNationalItem(Material.BLAZE_ROD, "👑 Золотой Царский Скипетр", "imperial_scepter", 250,
                    "&eАктивное свойство (ПКМ): &aВоля Монарха",
                    "&7Дарует Сопротивление II союзникам",
                    "&7в радиусе 8 блоков на 10с. Кд 1 мин."));
            inv.setItem(s2, createNationalItem(Material.SHIELD, "🛡️ Богатырский Щит Святогора", "imperial_shield", 250,
                    "&eПассивный эффект: &aСтена Руси",
                    "&7Блокирует 50% входящего урона",
                    "&7при активной защите щитом."));
            inv.setItem(s3, createNationalItem(Material.BREAD, "🍞 Царский Каравай Насыщения", "imperial_bread", 250,
                    "&eПассивный эффект: &aЦарская Сытость",
                    "&7Полное насыщение и сатурация на 2 мин."));
        } else if (nation.equals("imperial_dark")) {
            inv.setItem(s1, createNationalItem(Material.IRON_SWORD, "🗡️ Карающая Сабля Опричника", "imperial_saber", 250,
                    "&eПассивный эффект: &aСекущий Удар",
                    "&7Каждый удар имеет 25% шанс вызвать",
                    "&7кровотечение (эффект яда) на 4 сек."));
            inv.setItem(s2, createNationalItem(Material.IRON_NUGGET, "⛓️ Кандалы Тайного Сыска", "imperial_shackles", 250,
                    "&eАктивное свойство (ПКМ): &aВзять под стражу",
                    "&7Накладывает Замедление V на цель в руке",
                    "&7на 3 секунды. Кулдаун 45 сек."));
            inv.setItem(s3, createNationalItem(Material.HONEY_BOTTLE, "🍷 Золотой Кубок Грозного", "imperial_cup", 250,
                    "&eАктивное свойство (ПКМ): &aЦарский Гнев",
                    "&7Дарует Силу III на 10 секунд, но накладывает",
                    "&7Медлительность I на 5 секунд. Кд 1 мин."));
        }
    }

    private ItemStack createNationalItem(Material mat, String name, String itemId, int cost, String... desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Категория: " + ChatColor.YELLOW + "Национальный Предмет");
        lore.add("");
        for (String d : desc) lore.add(ChatColor.translateAlternateColorCodes('&', d));
        lore.add("");
        lore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + cost + " реп. ВК");
        lore.add(ChatColor.YELLOW + "▶ Кликните, чтобы приобрести ◀");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createShopArmorItem(Material mat, String name, String setKey, String setBonus, String type, int cost) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Редкость: " + ChatColor.LIGHT_PURPLE + "[ЭПИЧЕСКИЙ]");
        lore.add("");
        lore.add(ChatColor.GOLD + "Часть сета: " + ChatColor.YELLOW + setKey);
        lore.add(ChatColor.translateAlternateColorCodes('&', setBonus));
        lore.add("");
        lore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + cost + " реп. ВК");
        lore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        meta.setLore(lore);

        NamespacedKey itemKey = new NamespacedKey(plugin, "shop_item_type");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "armor_" + type);
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    // НАЦИОНАЛЬНЫЕ СЕТЫ БРОНИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Выдать стартовый сет брони при вступлении в нацию.
     */
    public void giveNationArmorSet(Player p, String setKey) {
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null) return;

        String setName = getSetName(setKey);
        String setBonus = getSetBonus(setKey);

        ItemStack helmet = createArmorPiece(Material.IRON_HELMET, "Шлем Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack chest  = createArmorPiece(Material.IRON_CHESTPLATE, "Нагрудник Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack legs   = createArmorPiece(Material.IRON_LEGGINGS, "Поножи Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack boots  = createArmorPiece(Material.IRON_BOOTS, "Сапоги Нации (" + setName + ")", setKey, setBonus, gearPlugin);

        for (ItemStack item : new ItemStack[]{helmet, chest, legs, boots}) {
            if (!p.getInventory().addItem(item).isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }
    }

    private ItemStack createArmorPiece(Material mat, String displayName, String setKey, String setBonus, org.bukkit.plugin.Plugin gearPlugin) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + displayName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Редкость: " + ChatColor.LIGHT_PURPLE + "[ЭПИЧЕСКИЙ]");
        lore.add(ChatColor.GRAY + "Выдан при вступлении в Нацию.");
        lore.add("");
        lore.add(ChatColor.GOLD + "Часть сета: " + ChatColor.YELLOW + setKey);
        lore.add(ChatColor.translateAlternateColorCodes('&', setBonus));
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(gearPlugin, "gear_set");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, setKey);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "gear_set_origin"), PersistentDataType.STRING, "nation");

        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    // УТИЛИТЫ СЕТОВ
    // ═══════════════════════════════════════════════════════════════

    public static String getNationSetKey(String nationId) {
        return switch (nationId) {
            case "soviet_light"   -> "udarnik";
            case "soviet_dark"    -> "tankist";
            case "pagan_light"    -> "volhv";
            case "pagan_dark"     -> "koshchey";
            case "imperial_light" -> "bogatyr";
            case "imperial_dark"  -> "sokol";
            default -> "udarnik";
        };
    }

    public static String getSetName(String setKey) {
        return switch (setKey) {
            case "udarnik"  -> "Ударник Труда";
            case "tankist"  -> "Танкист";
            case "volhv"    -> "Волхв";
            case "koshchey" -> "Бессмертный";
            case "bogatyr"  -> "Богатырь";
            case "sokol"    -> "Ясный Сокол";
            default -> "Ударник";
        };
    }

    public static String getSetBonus(String setKey) {
        return switch (setKey) {
            case "udarnik"  -> "&c[Бонус Сета: Спешка III, дебафф: Слабость I]";
            case "tankist"  -> "&c[Бонус Сета: Сопротивление I, Невидимость I, дебафф: Медлительность II]";
            case "volhv"    -> "&a[Бонус Сета: Дыхание под водой, Регенерация II днем под солнцем]";
            case "koshchey" -> "&a[Бонус Сета: Сила I, дебафф: Медлительность I]";
            case "bogatyr"  -> "&a[Бонус Сета: Сопротивление I, Сила I, дебафф: Утомление I]";
            case "sokol"    -> "&a[Бонус Сета: Скорость II, Прыгучесть II]";
            default -> "";
        };
    }
}
