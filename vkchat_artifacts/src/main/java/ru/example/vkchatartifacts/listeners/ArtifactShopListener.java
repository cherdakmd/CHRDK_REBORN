package ru.example.vkchatartifacts.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.items.ArtifactFactory;
import ru.example.vkchatartifacts.items.ConsumableFactory;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;

public class ArtifactShopListener implements Listener {
    private final VKChatArtifactsPlugin plugin;
    public static final String SHOP_TITLE = "§8▸ §6§lАРТЕФАКТЫ §8◂ §7Магазин";

    public ArtifactShopListener(VKChatArtifactsPlugin plugin) { this.plugin = plugin; }

    public static void openShop(VKChatArtifactsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, SHOP_TITLE);

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        // ═══ ШАПКА ═══
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatPlugin.getInstance().getApi().getReputation(vkId) : 0;
        inv.setItem(4, item(Material.GOLD_BLOCK,
                "§6§l✨ Рынок Древних Артефактов",
                "§7Магические предметы и свитки",
                "",
                "§e💰 Баланс: §f" + rep + " реп.",
                "",
                "§7§oАртефакты дают постоянные баффы",
                "§7§oпока лежат в инвентаре"));

        // ═══ АРТЕФАКТЫ (верхний ряд) ═══
        inv.setItem(10, shopItem(plugin, Material.HEART_OF_THE_SEA,
                "§e🏺 Случайный Древний Артефакт",
                "§7Случайный бафф до III уровня",
                "§7+ одно случайное проклятие",
                "§6Цена: §e3750 реп.",
                "normal", 3750));

        inv.setItem(13, shopItem(plugin, Material.NETHER_STAR,
                "§d✨ Мифическая Реликвия",
                "§7Гарантированный бафф V уровня",
                "§7Без проклятий! Не выпадает при смерти!",
                "§6Цена: §e11250 реп.",
                "relic", 11250));

        // ═══ РАСХОДНИКИ (средний ряд) ═══
        inv.setItem(21, shopItem(plugin, Material.PAPER,
                "§a📜 Свиток Очищения",
                "§7Попытка снять проклятие с артефакта",
                "§6Цена: §e1500 реп.",
                "cleanse", 1500));

        inv.setItem(22, shopItem(plugin, Material.ENDER_EYE,
                "§b🔮 Сфера Побега",
                "§7Экстренный телепорт домой",
                "§6Цена: §e1200 реп.",
                "escape", 1200));

        inv.setItem(23, shopItem(plugin, Material.TOTEM_OF_UNDYING,
                "§c🧛 Тотем Крови",
                "§7Полное восстановление ХП при активации",
                "§6Цена: §e3000 реп.",
                "revive", 3000));

        // ═══ СВИТКИ И ИНСТРУМЕНТЫ (нижний ряд) ═══
        inv.setItem(29, shopItem(plugin, Material.BOOK,
                "§3📖 Свиток Чар Усиления",
                "§7+50% к баффам артефактов на 10 мин",
                "§6Цена: §e4500 реп.",
                "enchant_scroll", 4500));

        inv.setItem(30, shopItem(plugin, Material.ANVIL,
                "§7🔨 Ремонтный Набор",
                "§7+24 часа жизни хрупкому артефакту",
                "§6Цена: §e2250 реп.",
                "repair_kit", 2250));

        inv.setItem(31, shopItem(plugin, Material.ENCHANTED_BOOK,
                "§5🔮 Руна Обмена",
                "§7Меняет тип баффа на случайный",
                "§6Цена: §e3000 реп.",
                "exchange_rune", 3000));

        inv.setItem(32, shopItem(plugin, Material.BEACON,
                "§6🛡 Тотем Укрепления",
                "§7Привязка к душе (не выпадает при смерти)",
                "§6Цена: §e7500 реп.",
                "fort_totem", 7500));

        inv.setItem(33, shopItem(plugin, Material.MILK_BUCKET,
                "§a🧪 Антидот Разложения",
                "§7Снимает ЛЮБОЕ проклятие со 100% шансом",
                "§6Цена: §e3750 реп.",
                "decay_antipode", 3750));

        // ═══ НИЖНИЙ РЯД ═══
        inv.setItem(45, infoItem(Material.BOOK,
                "§7ℹ Как работают артефакты",
                "§7Держи артефакт в ИНВЕНТАРЕ",
                "§7(не в сундуке!) для активации баффов.",
                "§7Проклятия накладывают дебаффы."));

        inv.setItem(49, infoItem(Material.BARRIER, "§c✕ Закрыть"));

        inv.setItem(53, infoItem(Material.KNOWLEDGE_BOOK,
                "§e💡 Совет",
                "§7Мифическая Реликвия не имеет",
                "§7проклятий и не теряется при смерти."));

        p.openInventory(inv);
    }

    private static ItemStack shopItem(VKChatArtifactsPlugin plugin, Material mat, String name, String desc1, String price, String type, int cost) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(desc1);
        lore.add("");
        lore.add(price);
        lore.add("");
        lore.add("§e▶ Нажми для покупки");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, cost);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shopItem(VKChatArtifactsPlugin plugin, Material mat, String name, String desc1, String desc2, String price, String type, int cost) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(desc1);
        lore.add(desc2);
        lore.add("");
        lore.add(price);
        lore.add("");
        lore.add("§e▶ Нажми для покупки");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, cost);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(java.util.Arrays.asList(lore));
        i.setItemMeta(m);
        return i;
    }

    private static ItemStack infoItem(Material mat, String name, String... lore) {
        return item(mat, name, lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(SHOP_TITLE)) return;
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey typeKey = new NamespacedKey(plugin, "buy_artifact_type");
        NamespacedKey costKey = new NamespacedKey(plugin, "buy_artifact_cost");

        if (item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING)) {
            String type = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
            int cost = item.getItemMeta().getPersistentDataContainer().get(costKey, PersistentDataType.INTEGER);

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) { p.sendMessage(ChatColor.RED + "❌ Привяжи ВК! (/vklink)"); return; }

            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) { p.sendMessage(ChatColor.RED + "❌ Нужно " + cost + " реп. (у тебя " + rep + ")"); return; }

            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

            ItemStack itemToGive = switch (type) {
                case "normal" -> ArtifactFactory.generateArtifact(plugin, false);
                case "relic" -> ArtifactFactory.generateArtifact(plugin, true);
                case "cleanse" -> ConsumableFactory.generateCleanseScroll(plugin);
                case "escape" -> ConsumableFactory.generateEscapeScroll(plugin);
                case "revive" -> ConsumableFactory.generateReviveScroll(plugin);
                case "enchant_scroll" -> ConsumableFactory.generateEnchantmentScroll(plugin);
                case "repair_kit" -> ConsumableFactory.generateRepairKit(plugin);
                case "exchange_rune" -> ConsumableFactory.generateExchangeRune(plugin);
                case "fort_totem" -> ConsumableFactory.generateFortificationTotem(plugin);
                case "decay_antipode" -> ConsumableFactory.generateDecayAntipode(plugin);
                default -> null;
            };

            if (itemToGive != null) {
                p.getInventory().addItem(itemToGive).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(ChatColor.GREEN + "✓ Куплено за " + ChatColor.GOLD + cost + " реп. ВК!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
        }
    }
}
