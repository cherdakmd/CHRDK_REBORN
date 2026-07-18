package ru.example.vkchatgear.artifacts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ArtifactsCommand — /artifacts (или /артефакты) — GUI управления артефактами.
 *
 * Хаб:
 * - Ваш инвентарь артефактов (до 5)
 * - Кнопка "Надеть в offhand"
 * - Кнопка "Убрать из offhand"
 * - Кнопка "Статистика"
 * - Кнопка "Магазин артефактов" (покупка за реп)
 */
public class ArtifactsCommand implements CommandExecutor, Listener {
    private final VKChatGearPlugin plugin;

    private static final String HUB_TITLE = "§8▸ §5§lАРТЕФАКТЫ §8◂ §7Управление";
    private static final String INVENTORY_TITLE = "§8▸ §5§lАРТЕФАКТЫ §8◂ §7Ваш инвентарь";
    private static final String SHOP_TITLE = "§8▸ §5§lАРТЕФАКТЫ §8◂ §6Магазин";

    // Хаб слоты
    private static final int SLOT_INVENTORY = 20;
    private static final int SLOT_EQUIP = 22;
    private static final int SLOTUNEQUIP = 24;
    private static final int SLOT_STATS = 31;
    private static final int SLOT_SHOP = 33;
    private static final int SLOT_CLOSE = 53;

    private static final Set<Integer> BORDER = new HashSet<>();
    static {
        for (int i = 0; i < 9; i++) BORDER.add(i);
        for (int i = 45; i < 54; i++) BORDER.add(i);
        for (int i = 9; i < 45; i++) {
            if (i % 9 == 0 || i % 9 == 8) BORDER.add(i);
        }
    }

    public ArtifactsCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("vkchat.admin")) {
            plugin.reloadConfig();
            if (plugin.getArtifactRegistry() != null) plugin.getArtifactRegistry().reload();
            sender.sendMessage("§a§l⚙ §aКонфиг артефактов перезагружен!");
            return true;
        }
        if (!(sender instanceof Player)) return true;
        openHub((Player) sender);
        return true;
    }

    // ═══════════════════════════════════════════
    // ХАБ
    // ═══════════════════════════════════════════

    private void openHub(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, HUB_TITLE);
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);

        ArtifactsManager mgr = plugin.getArtifactsManager();
        int count = mgr != null ? mgr.getActiveArtifactCount(p) : 0;

        inv.setItem(4, item(Material.NETHER_STAR, "§5§lМИСТИЧЕСКИЕ АРТЕФАКТЫ",
                "§7Артефакты — древние предметы силы.",
                "§7Надеваются в дополнительную руку.",
                "§7Максимум: §e" + count + " / 5",
                "", "§7Используйте кнопки ниже:"));

        inv.setItem(SLOT_INVENTORY, item(Material.ENDER_CHEST, "§d📦 Ваш инвентарь",
                "§7Просмотреть все ваши артефакты",
                "§7Артефакты: §e" + count + " / 5",
                "", "§e▶ Открыть"));

        ItemStack offhand = p.getInventory().getItemInOffHand();
        boolean hasEquipped = offhand != null && ArtifactsManager.isArtifact(offhand);
        inv.setItem(SLOT_EQUIP, hasEquipped
                ? item(Material.LIME_CONCRETE, "§a✓ Артефакт экипирован",
                        "§7" + ChatColor.stripColor(offhand.getItemMeta().getDisplayName()))
                : item(Material.GRAY_STAINED_GLASS_PANE, "§7Нет артефакта в offhand",
                        "§7Наденьте артефакт в доп. руку",
                        "§7или выберите из инвентаря"));

        inv.setItem(SLOTUNEQUIP, hasEquipped
                ? item(Material.RED_CONCRETE, "§c✕ Снять артефакт",
                        "§7Убрать артефакт из доп. руки",
                        "§7в основной инвентарь")
                : item(Material.GRAY_STAINED_GLASS_PANE, "§7Нечего снимать"));

        inv.setItem(SLOT_STATS, item(Material.BOOK, "§e📊 Статистика",
                "§7Пассивные бонусы от артефактов",
                "§7Бонусы применяются автоматически"));

        inv.setItem(SLOT_SHOP, item(Material.EMERALD_BLOCK, "§6🛒 Магазин артефактов",
                "§7Купить случайный артефакт",
                "§7за репутацию ВКонтакте",
                "", "§e▶ Открыть"));

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // ИНВЕНТАРЬ АРТЕФАКТОВ
    // ═══════════════════════════════════════════

    private void openInventory(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, INVENTORY_TITLE);
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);

        ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr == null) { p.closeInventory(); return; }

        List<ItemStack> artifacts = mgr.getPlayerArtifacts(p);

        // Отображаем артефакты в слотах 10-44
        int slot = 10;
        for (ItemStack art : artifacts) {
            while (BORDER.contains(slot) && slot < 45) slot++;
            if (slot >= 45) break;
            inv.setItem(slot, art);
            slot++;
        }

        // Подсказка
        if (artifacts.isEmpty()) {
            inv.setItem(31, item(Material.BARRIER, "§7Пока нет артефактов",
                    "§7Купите в магазине или получите из боя"));
        }

        // Навигация
        inv.setItem(45, item(Material.ARROW, "§e← Назад"));
        inv.setItem(53, item(Material.BARRIER, "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // МАГАЗИН
    // ═══════════════════════════════════════════

    private void openShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, SHOP_TITLE);
        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(4, item(Material.EMERALD_BLOCK, "§6🛒 Магазин Артефактов",
                "§7Покупайте случайные артефакты за репутацию",
                "§7Шанс редкости: Древний 1%, Легенд 5%, Эпик 12%, Редкий 27%, Обычный 55%"));

        // Покупка по тирам
        addShopItem(inv, 20, "common", "§fОбычный", 2000, Material.COAL_BLOCK, "§7Шанс: 55%");
        addShopItem(inv, 21, "rare", "§9Редкий", 5000, Material.IRON_BLOCK, "§7Шанс: 27%");
        addShopItem(inv, 22, "epic", "§5Эпический", 12000, Material.DIAMOND_BLOCK, "§7Шанс: 12%");
        addShopItem(inv, 23, "legendary", "§6Легендарный", 25000, Material.GOLD_BLOCK, "§7Шанс: 5%");
        addShopItem(inv, 24, "ancient", "§5Древний", 50000, Material.NETHERITE_BLOCK, "§7Шанс: 1%");

        // Рандомный за базовую цену
        inv.setItem(31, item(Material.NETHER_STAR, "§d🎲 Случайный артефакт",
                "§7Получите артефакт случайной редкости",
                "§7Цена: §e8000 реп. ВК",
                "", "§e▶ Купить"));

        inv.setItem(45, item(Material.ARROW, "§e← Назад"));
        inv.setItem(53, item(Material.BARRIER, "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    private void addShopItem(Inventory inv, int slot, String rarity, String displayName,
                              int price, Material mat, String chanceLine) {
        inv.setItem(slot, item(mat, displayName + " артефакт",
                chanceLine,
                "§7Получите артефакт этой редкости",
                "§eЦена: §b" + price + " реп. ВК",
                "§8Нажмите, чтобы купить"));
        // PDC
        ItemStack it = inv.getItem(slot);
        if (it == null) return;
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "art_shop_rarity"), PersistentDataType.STRING, rarity);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "art_shop_price"), PersistentDataType.INTEGER, price);
        it.setItemMeta(meta);
    }

    // ═══════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ
    // ═══════════════════════════════════════════

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(HUB_TITLE) && !title.equals(INVENTORY_TITLE) && !title.equals(SHOP_TITLE)) return;
        // При закрытии GUI артефактов — применяем бонусы
        if (e.getPlayer() instanceof Player) {
            ArtifactsManager mgr = plugin.getArtifactsManager();
            if (mgr != null) mgr.applyArtifactBonuses((Player) e.getPlayer());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (!title.equals(HUB_TITLE) && !title.equals(INVENTORY_TITLE) && !title.equals(SHOP_TITLE)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        Inventory top = e.getView().getTopInventory();
        if (raw >= top.getSize()) return;
        ItemStack clicked = e.getCurrentItem();

        // === ХАБ ===
        if (title.equals(HUB_TITLE)) {
            if (raw == SLOT_INVENTORY) openInventory(p);
            else if (raw == SLOT_EQUIP) handleEquipFromInventory(p);
            else if (raw == SLOTUNEQUIP) handleUnequip(p);
            else if (raw == SLOT_STATS) handleStats(p);
            else if (raw == SLOT_SHOP) openShop(p);
            else if (raw == SLOT_CLOSE) p.closeInventory();
            return;
        }

        // === ИНВЕНТАРЬ ===
        if (title.equals(INVENTORY_TITLE)) {
            if (raw == 45) openHub(p);
            else if (raw == 53) p.closeInventory();
            // Клик по артефакту — показать детали
            else if (clicked != null && ArtifactsManager.isArtifact(clicked)) {
                handleArtifactClick(p, clicked);
            }
            return;
        }

        // === МАГАЗИН ===
        if (title.equals(SHOP_TITLE)) {
            if (raw == 45) openHub(p);
            else if (raw == 53) p.closeInventory();
            else if (raw == 31) buyRandomArtifact(p, 8000);
            else if (clicked != null && clicked.hasItemMeta()) {
                ItemMeta meta = clicked.getItemMeta();
                String rarity = meta.getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "art_shop_rarity"), PersistentDataType.STRING);
                Integer price = meta.getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "art_shop_price"), PersistentDataType.INTEGER);
                if (rarity != null && price != null) {
                    buyArtifactByRarity(p, rarity, price);
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    // ДЕЙСТВИЯ
    // ═══════════════════════════════════════════

    private void handleEquipFromInventory(Player p) {
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (offhand != null && ArtifactsManager.isArtifact(offhand)) {
            p.sendMessage(ChatColor.YELLOW + "У вас уже есть артефакт в доп. руке. Сначала снимите его.");
            return;
        }
        // Ищем первый артефакт в инвентаре
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && ArtifactsManager.isArtifact(item)) {
                p.getInventory().setItemInOffHand(item);
                p.getInventory().setItem(i, null);
                p.sendMessage(ChatColor.GREEN + "Артефакт экипирован в доп. руку!");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
                ArtifactsManager mgr = plugin.getArtifactsManager();
                if (mgr != null) mgr.applyArtifactBonuses(p);
                openHub(p);
                return;
            }
        }
        p.sendMessage(ChatColor.YELLOW + "Нет артефактов для экипировки.");
    }

    private void handleUnequip(Player p) {
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (offhand == null || !ArtifactsManager.isArtifact(offhand)) {
            p.sendMessage(ChatColor.YELLOW + "Нет артефакта в доп. руке.");
            return;
        }
        p.getInventory().addItem(offhand).values().forEach(left ->
                p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.getInventory().setItemInOffHand(null);
        p.sendMessage(ChatColor.GREEN + "Артефакт снят из доп. руки.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
        ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr != null) mgr.applyArtifactBonuses(p);
        openHub(p);
    }

    private void handleStats(Player p) {
        ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr == null) return;
        List<ItemStack> arts = mgr.getPlayerArtifacts(p);
        if (arts.isEmpty()) {
            p.sendMessage(ChatColor.YELLOW + "У вас нет артефактов.");
            return;
        }
        p.sendMessage("§8▸ §5§lВаши артефакты §8◂");
        for (ItemStack art : arts) {
            ArtifactComponent def = mgr.getArtifactDef(art);
            if (def != null) {
                p.sendMessage("§5" + def.getName() + " §7(" + def.getRarity() + ")");
                for (Map.Entry<String, Double> entry : def.getStats().entrySet()) {
                    p.sendMessage("  §a+ " + entry.getKey() + ": " + String.format("%.2f", entry.getValue()));
                }
            }
        }
    }

    private void handleArtifactClick(Player p, ItemStack item) {
        ArtifactComponent def = plugin.getArtifactsManager() != null
                ? plugin.getArtifactsManager().getArtifactDef(item) : null;
        if (def == null) return;
        p.sendMessage("§5" + def.getName() + " §7— §" + rarityChar(def.getRarity()) + def.getRarity());
        p.sendMessage("§7" + def.getDescription());
        for (Map.Entry<String, Double> entry : def.getStats().entrySet()) {
            p.sendMessage("§a+ " + entry.getKey() + ": " + String.format("%.2f", entry.getValue()));
        }
    }

    private void buyRandomArtifact(Player p, int price) {
        if (!takeRep(p, price)) return;
        ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr == null) return;
        ArtifactComponent rolled = mgr.rollArtifact();
        if (rolled == null) {
            p.sendMessage(ChatColor.RED + "Артефакты не настроены в конфиге!");
            giveBack(p, price);
            return;
        }
        giveArtifact(p, mgr, rolled, price);
    }

    private void buyArtifactByRarity(Player p, String rarity, int price) {
        if (!takeRep(p, price)) return;
        ArtifactRegistry registry = plugin.getArtifactRegistry();
        if (registry == null) return;
        List<ArtifactComponent> pool = registry.getByRarity(rarity);
        if (pool.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Нет артефактов редкости " + rarity + "!");
            giveBack(p, price);
            return;
        }
        ArtifactComponent rolled = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        giveArtifact(p, plugin.getArtifactsManager(), rolled, price);
    }

    private void giveArtifact(Player p, ArtifactsManager mgr, ArtifactComponent def, int price) {
        ItemStack artifact = mgr.createArtifact(def);
        p.getInventory().addItem(artifact).values().forEach(left ->
                p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendMessage(ChatColor.GOLD + "Вы получили артефакт: " + def.getName() + "!");
        plugin.getLogger().info("[Artifacts] " + p.getName() + " купил артефакт: " + def.getId() + " (" + def.getRarity() + ")");
        openShop(p);
    }

    // ═══════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════

    private boolean takeRep(Player p, int cost) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        boolean hasPass = VKChatBridge.hasPass(p);
        if (vkId == -1 && !hasPass) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            p.closeInventory();
            return false;
        }
        if (vkId != -1) {
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + cost + ", у вас: " + rep);
                return false;
            }
            VKChatBridge.takeReputation(vkId, cost);
        } else if (hasPass) {
            int localRep = VKChatBridge.getLocalReputation(p);
            if (localRep < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + cost + ", у вас: " + localRep);
                return false;
            }
            VKChatBridge.takeLocalReputation(p, cost);
        }
        return true;
    }

    private void giveBack(Player p, int amount) {
        p.sendMessage(ChatColor.YELLOW + "Ошибка при выдаче артефакта. Обратитесь к администратору для возврата " + amount + " реп.");
        plugin.getLogger().warning("[Artifacts] Refund needed for " + p.getName() + ": " + amount + " rep (artifact creation failed)");
    }

    private static void fillBorder(Inventory inv, Material mat) {
        ItemStack border = glass(mat, " ");
        for (int i = 0; i < 54; i++) {
            if (BORDER.contains(i)) inv.setItem(i, border);
        }
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glass(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    private static char rarityChar(String rarity) {
        switch (rarity) {
            case "ancient": return '5';
            case "legendary": return '6';
            case "epic": return '9';
            case "rare": return 'b';
            case "uncommon": return 'a';
            default: return 'f';
        }
    }

    private static String rarityColor(String rarity) {
        switch (rarity) {
            case "ancient": return "§5";
            case "legendary": return "§6";
            case "epic": return "§9";
            case "rare": return "§b";
            default: return "§f";
        }
    }
}
