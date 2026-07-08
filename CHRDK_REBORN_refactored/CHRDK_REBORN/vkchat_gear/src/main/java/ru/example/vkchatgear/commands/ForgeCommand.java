package ru.example.vkchatgear.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatgear.VKChatGearPlugin;

import org.bukkit.command.TabCompleter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class ForgeCommand implements CommandExecutor, Listener, TabCompleter {
    private final VKChatGearPlugin plugin;
    private final Random random = new Random();

    private final String HUB_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §7Меню";
    private final String FUSION_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §dСлияние";
    private final String REFORGE_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §cПерековка";
    private final String CLEANSE_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §bОчищение";
    private final String REPAIR_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §aРемонт";
    private final String SCROLLS_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §eСвитки";
    private final String RUNE_CLEANSING_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §5Руны";

    private static final int[] FUSION_SLOTS = {20, 22, 24};
    private static final int CENTER_SLOT = 22;
    private static final int CONFIRM_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private final Map<UUID, PendingOp> pending = new java.util.concurrent.ConcurrentHashMap<>();

    private static class PendingOp {
        String action;
        long created = System.currentTimeMillis();
        int repCost;
        Material materialCost;
        int materialAmount;
        int chance;
        String targetRarity;
        boolean guaranteed;
        boolean protection;
        boolean antiDefect;
        boolean discountScroll;
        String summary;
    }

    public ForgeCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        openHub((Player) sender);
        return true;
    }

    public void openMenu(Player p) { openHub(p); }

    private void openHub(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, HUB_TITLE);

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        inv.setItem(4, item(Material.ANVIL, "§4§l⚒ МИСТИЧЕСКАЯ КУЗНЯ",
                "§7Древний горн работает с MMO-предметами",
                "§7и репутацией ВК. Все операции требуют",
                "§7предпросмотр и подтверждение."));

        inv.setItem(20, item(Material.NETHER_STAR, "§d⭐ Слияние редкости",
                "§7Три предмета одной редкости → один выше",
                "§7Центр — улучшается, бока — катализаторы",
                "", "§e▶ Открыть"));
        inv.setItem(22, item(Material.NETHERITE_INGOT, "§c🔥 Перековка",
                "§7Полный переброс свойств предмета",
                "§7Может добавить дефект",
                "", "§e▶ Открыть"));
        inv.setItem(24, item(Material.GRINDSTONE, "§b🕯 Очищение",
                "§7Снимает дефекты с предмета",
                "§7Цена зависит от глубины дефекта",
                "", "§e▶ Открыть"));

        inv.setItem(29, item(Material.IRON_INGOT, "§a🔧 Ремонт",
                "§7Восстановление прочности MMO-шмота",
                "§7Цена: репутация + материал предмета",
                "", "§e▶ Открыть"));
        inv.setItem(31, item(Material.PAPER, "§e📜 Свитки кузни",
                "§7Шанс, защита, анти-дефект, скидка",
                "§7Используются автоматически из инвентаря",
                "", "§e▶ Открыть"));
        inv.setItem(33, item(Material.PURPUR_BLOCK, "§5💀 Очищение рун",
                "§7Снимает все кастомные чары с предмета",
                "§7Цена: " + plugin.getConfig().getInt("rune-cleansing.cost", 500) + " реп + " +
                        plugin.getConfig().getInt("rune-cleansing.material-amount", 1) + "x " +
                        plugin.getConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK"),
                "", "§e▶ Открыть"));

        inv.setItem(49, item(Material.BOOK, "§e📖 Правила",
                "§7• Предпросмотр всегда обязателен",
                "§7• Цена = редкость + сила предмета",
                "§7• Кузнец (Jobs) даёт бонусы"));
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    private void openFusion(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, FUSION_TITLE);
        fill(inv, Material.RED_STAINED_GLASS_PANE);
        for (int s : FUSION_SLOTS) inv.setItem(s, null);
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "⭐ Слияние редкости",
                ChatColor.GRAY + "Положи любые 3 MMO-предмета одной редкости.",
                ChatColor.GRAY + "Слот 22 — цель, 20 и 24 — катализаторы.",
                ChatColor.RED + "Первый клик покажет предпросмотр. Второй — подтверждение."));
        inv.setItem(29, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(31, marker(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.GOLD + "Цель"));
        inv.setItem(33, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния",
                ChatColor.GRAY + "Покажет шанс, цену, ресурсы и свитки."));
        nav(inv);
        p.openInventory(inv);
    }

    private void openReforge(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, REFORGE_TITLE);
        fill(inv, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.NETHERITE_INGOT, ChatColor.RED + "🔥 Перековка предмета",
                ChatColor.GRAY + "Положи предмет в центральный слот.",
                ChatColor.GRAY + "Перековка может усилить чары/свойство,",
                ChatColor.GRAY + "но может оставить дефект."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openCleanse(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, CLEANSE_TITLE);
        fill(inv, Material.CYAN_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.GRINDSTONE, ChatColor.AQUA + "🕯 Очищение дефектов",
                ChatColor.GRAY + "Положи дефектный предмет в центральный слот.",
                ChatColor.GRAY + "Очищение требует репутацию и ресурсы."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openRepair(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, REPAIR_TITLE);
        fill(inv, Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.IRON_INGOT, ChatColor.GREEN + "🔧 Ремонт MMO-предмета",
                ChatColor.GRAY + "Положи повреждённый предмет в центральный слот.",
                ChatColor.GRAY + "Ремонт восстанавливает прочность до 100%.",
                ChatColor.GRAY + "Цена: репутация ВК + материал предмета.",
                ChatColor.RED + "Обычная наковальня для MMO Gear отключена."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ремонта"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openScrolls(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, SCROLLS_TITLE);
        fill(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(4, item(Material.PAPER, ChatColor.GOLD + "📜 Свитки горна",
                ChatColor.GRAY + "Свитки используются автоматически из инвентаря",
                ChatColor.GRAY + "при следующей подходящей операции кузни."));
        addScrollShopItem(inv, 19, "chance_25", Material.PAPER, "&dСвиток Точного Слияния", "+25% к шансу слияния");
        addScrollShopItem(inv, 20, "chance_50", Material.MAP, "&5Свиток Сильного Слияния", "+50% к шансу слияния");
        addScrollShopItem(inv, 21, "perfect", Material.NETHER_STAR, "&6&lСвиток Идеального Слияния", "100% успех слияния");
        addScrollShopItem(inv, 23, "protect_all", Material.TOTEM_OF_UNDYING, "&bСвиток Полной Защиты", "При провале сохраняет цель и катализаторы");
        addScrollShopItem(inv, 24, "anti_defect", Material.HONEYCOMB, "&aСвиток Чистой Стали", "Защита от дефекта при перековке");
        addScrollShopItem(inv, 25, "discount", Material.EMERALD, "&2Свиток Скидки", "-25% репутационной цены операции");
        nav(inv);
        p.openInventory(inv);
    }

    private void openRuneCleansing(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, RUNE_CLEANSING_TITLE);
        fill(inv, Material.MAGENTA_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.PURPUR_BLOCK, ChatColor.DARK_PURPLE + "💀 Очищение рун",
                ChatColor.GRAY + "Положи предмет с кастомными чарами в центральный слот.",
                ChatColor.GRAY + "Удаляет ВСЕ кастомные чары с предмета.",
                ChatColor.YELLOW + "Стоимость: " + plugin.getConfig().getInt("rune-cleansing.cost", 500) + " реп. ВК",
                ChatColor.YELLOW + "Ресурс: " + plugin.getConfig().getInt("rune-cleansing.material-amount", 1) + "x " +
                        plugin.getConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK")));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения рун"));
        nav(inv);
        p.openInventory(inv);
    }

    private void addScrollShopItem(Inventory inv, int slot, String type, Material mat, String name, String desc) {
        int price = plugin.getConfig().getInt("forge2.scrolls." + type + ".price", defaultScrollPrice(type));
        ItemStack it = item(mat, ChatColor.translateAlternateColorCodes('&', name),
                ChatColor.GRAY + desc,
                "",
                ChatColor.YELLOW + "Цена: " + price + " реп. ВК",
                ChatColor.DARK_GRAY + "Клик — купить");
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_shop_price"), PersistentDataType.INTEGER, price);
        it.setItemMeta(meta);
        inv.setItem(slot, it);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (!isForgeTitle(title)) return;
        Player p = (Player) e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();

        if (raw >= top.getSize()) {
            if ((title.equals(FUSION_TITLE) || title.equals(REFORGE_TITLE) || title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE)) && e.isShiftClick() && e.getCurrentItem() != null && plugin.getGearManager().isGear(e.getCurrentItem().getType())) {
                int free = title.equals(FUSION_TITLE) ? firstFreeFusionSlot(top) : (isEmpty(top.getItem(CENTER_SLOT)) ? CENTER_SLOT : -1);
                if (free != -1) {
                    e.setCancelled(true);
                    top.setItem(free, e.getCurrentItem().clone());
                    e.getCurrentItem().setAmount(0);
                    pending.remove(p.getUniqueId());
                    resetConfirmButton(title, top);
                }
            }
            e.setCancelled(true);
            return;
        }

        if (isWorkSlot(title, raw)) {
            pending.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> resetConfirmButton(title, top));
            return;
        }

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(HUB_TITLE)) {
            if (raw == 20) openFusion(p);
            else if (raw == 22) openReforge(p);
            else if (raw == 24) openCleanse(p);
            else if (raw == 29) openRepair(p);
            else if (raw == 31) openScrolls(p);
            else if (raw == 33) openRuneCleansing(p);
            else if (raw == CLOSE_SLOT) p.closeInventory();
            return;
        }

        if (raw == BACK_SLOT) { returnInputs(p, top); openHub(p); return; }
        if (raw == CLOSE_SLOT) { returnInputs(p, top); p.closeInventory(); return; }

        if (title.equals(SCROLLS_TITLE)) {
            buyScroll(p, clicked);
            return;
        }

        if (raw == CONFIRM_SLOT) {
            if (title.equals(FUSION_TITLE)) handleFusionButton(p, top);
            else if (title.equals(REFORGE_TITLE)) handleReforgeButton(p, top);
            else if (title.equals(CLEANSE_TITLE)) handleCleanseButton(p, top);
            else if (title.equals(REPAIR_TITLE)) handleRepairButton(p, top);
            else if (title.equals(RUNE_CLEANSING_TITLE)) handleRuneCleansingButton(p, top);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!isForgeTitle(e.getView().getTitle())) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        
        // Очистка GUI свитков — не выдавать предметы из магазина
        if (e.getView().getTitle().equals(SCROLLS_TITLE)) {
            for (int i = 0; i < e.getInventory().getSize(); i++) {
                ItemStack item = e.getInventory().getItem(i);
                if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                        .has(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING)) {
                    e.getInventory().setItem(i, null);
                }
            }
            return;
        }
        
        if (e.getView().getTitle().equals(HUB_TITLE)) return;
        pending.remove(p.getUniqueId());
        returnInputs(p, e.getInventory());
    }

    private void handleFusionButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("fusion") && System.currentTimeMillis() - op.created < 30000L) {
            executeFusion(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(FUSION_TITLE, inv);
            return;
        }
        op = previewFusion(p, inv);
        if (op != null) {
            pending.put(p.getUniqueId(), op);
            inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить слияние",
                    op.summary.split("\n")));
        }
    }

    private PendingOp previewFusion(Player p, Inventory inv) {
        ItemStack left = inv.getItem(20), center = inv.getItem(22), right = inv.getItem(24);
        if (!isValidFusionItem(left) || !isValidFusionItem(center) || !isValidFusionItem(right)) {
            p.sendMessage(ChatColor.RED + "Положи 3 MMO-предмета одной редкости в слоты 20/22/24.");
            return null;
        }
        String rarity = plugin.getGearManager().getRarityKey(center);
        if (!rarity.equals(plugin.getGearManager().getRarityKey(left)) || !rarity.equals(plugin.getGearManager().getRarityKey(right))) {
            p.sendMessage(ChatColor.RED + "Все 3 предмета должны быть одной редкости.");
            return null;
        }
        String next = nextRarity(rarity);
        if (next == null) { p.sendMessage(ChatColor.RED + "Легендарную редкость выше повысить нельзя."); return null; }

        PendingOp op = new PendingOp();
        op.action = "fusion";
        op.targetRarity = next;
        int power = itemPower(center) + itemPower(left) / 2 + itemPower(right) / 2;
        int baseCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.rarity-upgrade-cost", 500);
        // Ancient fusion requires 5000 rep minimum
        if (next.equals("ancient")) {
            baseCost = Math.max(baseCost, 5000);
        }
        op.repCost = Math.max(1, baseCost + power * plugin.getConfig().getInt("forge2.cost.power-rep-multiplier", 18) + rarityIndex(next) * plugin.getConfig().getInt("forge2.cost.rarity-rep-step", 350));
        op.materialCost = materialCostFor(next);
        op.materialAmount = materialAmountFor(next) + Math.max(0, power / 12);
        op.chance = plugin.getConfig().getInt("hardcore-forging.rarity-upgrade-chance." + next, defaultUpgradeChance(next));
        int bs = plugin.getGearManager().getBlacksmithLevel(p);
        int jobBonus = Math.min(plugin.getConfig().getInt("forge2.blacksmith.max-chance-bonus", 10), bs / 5);
        op.chance += jobBonus;
        op.guaranteed = hasScroll(p, "perfect");
        if (!op.guaranteed) {
            if (hasScroll(p, "chance_50")) op.chance += 50;
            else if (hasScroll(p, "chance_25")) op.chance += 25;
        }
        op.protection = hasScroll(p, "protect_all");
        op.discountScroll = hasScroll(p, "discount");
        if (op.discountScroll) op.repCost = (int)Math.round(op.repCost * 0.75);
        op.chance = Math.min(100, op.chance);
        op.summary = ChatColor.GOLD + "Слияние: " + rarityDisplay(rarity) + ChatColor.GRAY + " → " + rarityDisplay(next) + "\n" +
                ChatColor.YELLOW + "Шанс: " + (op.guaranteed ? "100% (свиток)" : op.chance + "%") + "\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.RED + "Провал: катализаторы сгорят" + (op.protection ? " (оберег спасёт всё)" : "") + "\n" +
                ChatColor.GRAY + "Кликни ещё раз по зелёной кнопке.";
        p.sendMessage(ChatColor.GOLD + "⚒ Предпросмотр слияния готов. Проверь кнопку подтверждения.");
        return op;
    }

    private void executeFusion(Player p, Inventory inv, PendingOp op) {
        ItemStack left = inv.getItem(20), center = inv.getItem(22), right = inv.getItem(24);
        if (!isValidFusionItem(left) || !isValidFusionItem(center) || !isValidFusionItem(right)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "слияние редкости")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        consumeRelevantScrolls(p, op);
        boolean success = op.guaranteed || random.nextInt(100) < op.chance;
        log(p, "FUSION_START", op.summary.replace("§", "&"));
        if (success) {
            ItemStack result = center.clone();
            applyRarity(result, op.targetRarity);
            maybeUpgradeEnchant(result);
            applyRarityProc(result, op.targetRarity);
            markLegacyIfNeeded(result);
            inv.setItem(20, null); inv.setItem(22, result); inv.setItem(24, null);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 70, 0.7, 0.7, 0.7, 0.2);
            announceIfNeeded(p, result, op.targetRarity);
            p.sendMessage(ChatColor.GOLD + "⭐ Успех! Редкость повышена до " + rarityDisplay(op.targetRarity));
            log(p, "FUSION_SUCCESS", itemName(result));
        } else {
            if (op.protection) {
                inv.setItem(20, left); inv.setItem(22, center); inv.setItem(24, right);
                p.sendMessage(ChatColor.AQUA + "🛡 Свиток Полной Защиты сохранил все предметы. Списаны только цена и ресурсы.");
                log(p, "FUSION_FAIL_PROTECTED", itemName(center));
            } else {
                inv.setItem(20, null); inv.setItem(22, center); inv.setItem(24, null);
                p.sendMessage(ChatColor.RED + "❌ Провал. Центральный предмет сохранён, катализаторы сгорели.");
                log(p, "FUSION_FAIL", itemName(center));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
        }
    }

    private void handleReforgeButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("reforge") && System.currentTimeMillis() - op.created < 30000L) {
            executeReforge(p, inv, op); pending.remove(p.getUniqueId()); resetConfirmButton(REFORGE_TITLE, inv); return;
        }
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        op = new PendingOp(); op.action = "reforge";
        int power = itemPower(item);
        op.repCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.reforge-cost", 650) + power * plugin.getConfig().getInt("forge2.cost.reforge-power-rep", 12);
        op.materialCost = Material.DIAMOND; op.materialAmount = 4 + power / 15;
        op.antiDefect = hasScroll(p, "anti_defect"); op.discountScroll = hasScroll(p, "discount");
        if (op.discountScroll) op.repCost = (int)Math.round(op.repCost * 0.75);
        op.summary = ChatColor.RED + "Перековка предмета\n" + ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" + ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x DIAMOND\n" + ChatColor.GRAY + "Может улучшить 1 чар и перебросить свойство.\n" + ChatColor.RED + "Риск дефекта" + (op.antiDefect ? " снят свитком" : " сохраняется") + "\n" + ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить перековку", op.summary.split("\n")));
    }

    private void executeReforge(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "перековка")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) { p.sendMessage(ChatColor.RED + "Не хватает алмазов."); return; }
        consumeRelevantScrolls(p, op);
        ItemStack result = item.clone();
        maybeUpgradeEnchant(result);
        applyRarityProc(result, plugin.getGearManager().getRarityKey(result));
        if (!op.antiDefect && random.nextInt(100) < plugin.getConfig().getInt("forge2.defects.chance-on-reforge", 22)) {
            plugin.getGearManager().applyRandomDefect(result);
            p.sendMessage(ChatColor.YELLOW + "⚠ Перековка оставила дефект.");
        }
        inv.setItem(CENTER_SLOT, result);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        p.sendMessage(ChatColor.GREEN + "🔥 Предмет перекован в реликтовой кузне.");
        log(p, "REFORGE", itemName(result));
    }

    private void handleCleanseButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("cleanse") && System.currentTimeMillis() - op.created < 30000L) {
            executeCleanse(p, inv, op); pending.remove(p.getUniqueId()); resetConfirmButton(CLEANSE_TITLE, inv); return;
        }
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        op = new PendingOp(); op.action = "cleanse";
        int defects = countDefects(item);
        op.repCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.cleanse-cost", 350) + Math.max(0, defects - 1) * 250;
        op.materialCost = Material.LAPIS_LAZULI; op.materialAmount = 16 + Math.max(0, defects - 1) * 16;
        op.summary = ChatColor.AQUA + "Очищение дефектов\n" + ChatColor.YELLOW + "Дефектов найдено: " + defects + "\n" + ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" + ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x LAPIS_LAZULI\n" + ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить очищение", op.summary.split("\n")));
    }

    private void executeCleanse(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "очищение")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) { p.sendMessage(ChatColor.RED + "Не хватает лазурита."); return; }
        boolean changed = plugin.getGearManager().cleanseDefects(item);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
        p.sendMessage(changed ? ChatColor.AQUA + "🕯 Дефекты очищены." : ChatColor.YELLOW + "Дефектов не было, но диагностика и обряд оплачены.");
        log(p, "CLEANSE", itemName(item) + " changed=" + changed);
    }

    private void handleRepairButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("repair") && System.currentTimeMillis() - op.created < 30000L) {
            executeRepair(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(REPAIR_TITLE, inv);
            return;
        }
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable)) {
            p.sendMessage(ChatColor.YELLOW + "Этот предмет не имеет прочности и не нуждается в ремонте.");
            return;
        }
        org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        int max = item.getType().getMaxDurability();
        int damage = dmg.getDamage();
        if (max <= 0 || damage <= 0) {
            p.sendMessage(ChatColor.GREEN + "Предмет уже полностью целый.");
            return;
        }
        PendingOp preview = new PendingOp();
        preview.action = "repair";
        int percent = (int)Math.ceil((damage / (double)Math.max(1, max)) * 100.0);
        int power = itemPower(item);
        int base = plugin.getGearManager().getDiscountedCost(p, "forge2.repair.base-rep-cost", 120);
        int perPercent = plugin.getConfig().getInt("forge2.repair.rep-per-damage-percent", 8);
        int powerCost = plugin.getConfig().getInt("forge2.repair.power-rep-multiplier", 8);
        preview.repCost = Math.max(1, base + percent * perPercent + power * powerCost);
        if (plugin.getGearManager().hasDefect(item, "fragile")) preview.repCost = (int)Math.round(preview.repCost * plugin.getConfig().getDouble("forge2.repair.fragile-cost-multiplier", 1.35));
        preview.materialCost = repairMaterialFor(item.getType());
        preview.materialAmount = Math.max(1, plugin.getConfig().getInt("forge2.repair.base-material-amount", 2) + percent / plugin.getConfig().getInt("forge2.repair.percent-per-extra-material", 20));
        preview.discountScroll = hasScroll(p, "discount");
        if (preview.discountScroll) preview.repCost = (int)Math.round(preview.repCost * 0.75);
        preview.summary = ChatColor.GREEN + "Ремонт MMO-предмета\n" +
                ChatColor.GRAY + "Поломка: " + ChatColor.YELLOW + damage + "/" + max + " (" + percent + "%)\n" +
                ChatColor.YELLOW + "Цена: " + preview.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + preview.materialAmount + "x " + preview.materialCost.name() + "\n" +
                ChatColor.GRAY + "Результат: прочность будет восстановлена до 100%.\n" +
                (preview.discountScroll ? ChatColor.AQUA + "Свиток Скидки: -25% цены репутации\n" : "") +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), preview);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить ремонт", preview.summary.split("\n")));
        p.sendMessage(ChatColor.GREEN + "🔧 Предпросмотр ремонта готов. Проверь зелёную кнопку.");
    }

    private void executeRepair(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable)) return;
        org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        if (dmg.getDamage() <= 0) { p.sendMessage(ChatColor.GREEN + "Предмет уже полностью целый."); return; }
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "ремонт MMO-предмета")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса для ремонта: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        if (op.discountScroll) consumeScroll(p, "discount");
        dmg.setDamage(0);
        item.setItemMeta((ItemMeta) dmg);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.25f);
        p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.08);
        p.sendMessage(ChatColor.GREEN + "🔧 Предмет полностью отремонтирован за " + op.repCost + " реп. ВК и " + op.materialAmount + "x " + op.materialCost.name() + ".");
        log(p, "REPAIR", itemName(item) + " cost=" + op.repCost + " material=" + op.materialCost + "x" + op.materialAmount);
    }

    private void handleRuneCleansingButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("rune_cleansing") && System.currentTimeMillis() - op.created < 30000L) {
            executeRuneCleansing(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(RUNE_CLEANSING_TITLE, inv);
            return;
        }
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        int enchantCount = plugin.getGearManager().countCustomRuneLines(item);
        if (enchantCount == 0) {
            p.sendMessage(ChatColor.YELLOW + "На этом предмете нет кастомных чар для удаления.");
            return;
        }
        op = new PendingOp();
        op.action = "rune_cleansing";
        op.repCost = plugin.getConfig().getInt("rune-cleansing.cost", 500);
        String matName = plugin.getConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK");
        op.materialCost = Material.valueOf(matName);
        op.materialAmount = plugin.getConfig().getInt("rune-cleansing.material-amount", 1);

        List<String> enchantNames = new ArrayList<>();
        List<String> lore = item.getItemMeta().hasLore() ? item.getItemMeta().getLore() : new ArrayList<>();
        List<String> allCustom = plugin.getGearManager().getAvailableCustomEnchants(item.getType());
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String key : allCustom) {
                String rawName = plugin.getConfig().getString("custom_enchants." + key + ".name", "");
                String cfg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName)).toLowerCase();
                if (!cfg.isEmpty() && stripped.contains(cfg.split(" ")[0].toLowerCase())) {
                    enchantNames.add(ChatColor.translateAlternateColorCodes('&', rawName));
                    break;
                }
            }
        }

        op.summary = ChatColor.DARK_PURPLE + "Очищение рун\n" +
                ChatColor.YELLOW + "Найдено чар: " + enchantCount + "\n" +
                ChatColor.GRAY + "Удаляемые чары:\n" +
                String.join("\n", enchantNames) + "\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.RED + "⚠ Все кастомные чары будут удалены!\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить очищение рун", op.summary.split("\n")));
        p.sendMessage(ChatColor.DARK_PURPLE + "💀 Предпросмотр очищения рун готов. Проверь зелёную кнопку.");
    }

    private void executeRuneCleansing(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "очищение рун")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        removeCustomEnchants(item);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 0.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        p.sendMessage(ChatColor.DARK_PURPLE + "💀 Все кастомные чары были удалены с предмета.");
        log(p, "RUNE_CLEANSING", itemName(item));
    }

    private void removeCustomEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return;
        List<String> lore = meta.getLore();
        List<String> allCustom = plugin.getGearManager().getAvailableCustomEnchants(item.getType());
        List<String> toRemove = new ArrayList<>();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String key : allCustom) {
                String rawName = plugin.getConfig().getString("custom_enchants." + key + ".name", "");
                String cfg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName)).toLowerCase();
                if (!cfg.isEmpty() && stripped.contains(cfg.split(" ")[0].toLowerCase())) {
                    toRemove.add(line);
                    break;
                }
            }
        }
        lore.removeAll(toRemove);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private Material repairMaterialFor(Material type) {
        String n = type.name();
        if (n.contains("NETHERITE")) return Material.NETHERITE_SCRAP;
        if (n.contains("DIAMOND")) return Material.DIAMOND;
        if (n.contains("GOLD") || n.contains("GOLDEN")) return Material.GOLD_INGOT;
        if (n.contains("IRON") || n.contains("CHAINMAIL")) return Material.IRON_INGOT;
        if (n.contains("LEATHER")) return Material.LEATHER;
        if (n.contains("STONE")) return Material.COBBLESTONE;
        if (n.contains("WOOD") || n.contains("BOW") || n.contains("CROSSBOW")) return Material.OAK_PLANKS;
        return Material.EMERALD;
    }

    private void buyScroll(Player p, ItemStack clicked) {
        if (!clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String type = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING);
        Integer price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_shop_price"), PersistentDataType.INTEGER);
        if (type == null || price == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, price, "покупка свитка кузни")) return;
        ItemStack scroll = createScroll(type);
        p.getInventory().addItem(scroll).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
        p.sendMessage(ChatColor.GREEN + "Куплен свиток: " + scroll.getItemMeta().getDisplayName() + ChatColor.GREEN + " за " + price + " реп.");
        log(p, "BUY_SCROLL", type + " price=" + price);
    }

    private ItemStack createScroll(String type) {
        Material mat = type.equals("perfect") ? Material.NETHER_STAR : type.equals("protect_all") ? Material.TOTEM_OF_UNDYING : Material.PAPER;
        String name;
        String lore;
        switch (type) {
            case "chance_25": name = "§dСвиток Точного Слияния"; lore = "§7+25% к следующему слиянию редкости."; break;
            case "chance_50": name = "§5Свиток Сильного Слияния"; lore = "§7+50% к следующему слиянию редкости."; break;
            case "perfect": name = "§6§lСвиток Идеального Слияния"; lore = "§7Следующее слияние редкости будет §a100%§7 успешным."; break;
            case "protect_all": name = "§bСвиток Полной Защиты"; lore = "§7При провале слияния сохраняет цель и катализаторы."; break;
            case "anti_defect": name = "§aСвиток Чистой Стали"; lore = "§7Защищает от дефекта при следующей перековке."; break;
            case "discount": name = "§2Свиток Скидки"; lore = "§7-25% репутационной цены следующей операции кузни."; break;
            default: name = "§7Свиток кузни"; lore = "§7Свиток.";
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore, "§8Расходуется автоматически в /forge."));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_type"), PersistentDataType.STRING, type);
        if (type.equals("perfect")) meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean hasScroll(Player p, String type) { return findScrollSlot(p, type) != -1; }
    private int findScrollSlot(Player p, String type) {
        ItemStack[] items = p.getInventory().getContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || !it.hasItemMeta()) continue;
            ItemMeta meta = it.getItemMeta();
            String t = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_type"), PersistentDataType.STRING);
            if (type.equals(t)) return i;
            if (type.equals("perfect") && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER)) return i;
        }
        return -1;
    }

    private void consumeRelevantScrolls(Player p, PendingOp op) {
        if (op.discountScroll) consumeScroll(p, "discount");
        if (op.action.equals("fusion")) {
            if (op.guaranteed) consumeScroll(p, "perfect");
            else if (hasScroll(p, "chance_50")) consumeScroll(p, "chance_50");
            else if (hasScroll(p, "chance_25")) consumeScroll(p, "chance_25");
            if (op.protection) consumeScroll(p, "protect_all");
        }
        if (op.action.equals("reforge") && op.antiDefect) consumeScroll(p, "anti_defect");
    }

    private boolean consumeScroll(Player p, String type) {
        int slot = findScrollSlot(p, type);
        if (slot == -1) return false;
        ItemStack it = p.getInventory().getItem(slot);
        if (it.getAmount() <= 1) p.getInventory().setItem(slot, null); else it.setAmount(it.getAmount() - 1);
        return true;
    }

    private ItemStack getCenterGear(Player p, Inventory inv) {
        ItemStack item = inv.getItem(CENTER_SLOT);
        if (isEmpty(item)) { p.sendMessage(ChatColor.RED + "Положи предмет в центральный слот."); return null; }
        if (!plugin.getGearManager().isGear(item.getType())) { p.sendMessage(ChatColor.RED + "Это не оружие/броня/инструмент."); return null; }
        if (!isValidFusionItem(item)) p.sendMessage(ChatColor.YELLOW + "⚠ Legacy-предмет будет помечен как мигрированный после операции.");
        return item;
    }

    private boolean isValidFusionItem(ItemStack item) {
        if (isEmpty(item) || !plugin.getGearManager().isGear(item.getType()) || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) return true;
        // Legacy-разрешение: старые предметы с лором редкости принимаются, но после операции помечаются.
        if (meta.hasLore()) {
            for (String line : meta.getLore()) if (ChatColor.stripColor(line).startsWith("Редкость:")) return true;
        }
        return false;
    }

    private void markLegacyIfNeeded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_legacy_migrated"), PersistentDataType.INTEGER, 1);
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Заточка: +0");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private int itemPower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        int power = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
        power += item.getEnchantments().size() * 2;
        power += plugin.getGearManager().countCustomRuneLines(item) * 3;
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING)) power += 10;
        String n = item.getType().name();
        if (n.contains("NETHERITE")) power += 12; else if (n.contains("DIAMOND")) power += 8; else if (n.contains("IRON")) power += 4;
        return power;
    }

    private boolean takeMaterial(Player p, Material mat, int amount) {
        if (amount <= 0) return true;
        if (!p.getInventory().containsAtLeast(new ItemStack(mat), amount)) return false;
        p.getInventory().removeItem(new ItemStack(mat, amount));
        return true;
    }

    private Material materialCostFor(String targetRarity) {
        String raw = plugin.getConfig().getString("forge2.resources." + targetRarity + ".material", null);
        if (raw != null) try { return Material.valueOf(raw); } catch (Exception ignored) {}
        if (targetRarity.equals("ancient")) return Material.NETHERITE_INGOT;
        if (targetRarity.equals("legendary")) return Material.NETHERITE_SCRAP;
        if (targetRarity.equals("epic")) return Material.DIAMOND;
        if (targetRarity.equals("rare")) return Material.GOLD_INGOT;
        return Material.IRON_INGOT;
    }

    private int materialAmountFor(String targetRarity) {
        int cfg = plugin.getConfig().getInt("forge2.resources." + targetRarity + ".amount", -1);
        if (cfg > 0) return cfg;
        if (targetRarity.equals("ancient")) return 2;
        if (targetRarity.equals("legendary")) return 4;
        if (targetRarity.equals("epic")) return 8;
        if (targetRarity.equals("rare")) return 16;
        return 16;
    }

    private void maybeUpgradeEnchant(ItemStack item) {
        if (item == null || item.getEnchantments().isEmpty()) return;
        List<Enchantment> list = new ArrayList<>(item.getEnchantments().keySet());
        Collections.shuffle(list);
        for (Enchantment e : list) {
            int lvl = item.getEnchantmentLevel(e);
            if (lvl < e.getMaxLevel() && e.canEnchantItem(item)) {
                item.addUnsafeEnchantment(e, lvl + 1);
                return;
            }
        }
    }

    private void applyRarityProc(ItemStack item, String rarity) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<String> pool = plugin.getConfig().getStringList("forge2.rarity-procs." + rarity);
        if (pool.isEmpty()) pool = defaultProcPool(rarity);
        if (pool.isEmpty()) return;
        String proc = rebrandProc(pool.get(random.nextInt(pool.size())));
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.removeIf(l -> ChatColor.stripColor(l).startsWith("Прок редкости:"));
        lore.add(ChatColor.DARK_PURPLE + "Прок редкости: " + ChatColor.translateAlternateColorCodes('&', proc));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rarity_proc"), PersistentDataType.STRING, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', proc)));
        item.setItemMeta(meta);
    }

    private String rebrandProc(String proc) {
        if (proc == null) return "";
        return proc.replace("Воля Перуна", "Грозовой Импульс")
                .replace("Кровь Рода", "Багровый Резонанс")
                .replace("Щит Сварога", "Астральный Барьер")
                .replace("Пламя Ярило", "Пламенный Контур")
                .replace("Очищение", "Развеивание")
                .replace("Вампиризм", "Похищение Жизни");
    }

    private List<String> defaultProcPool(String rarity) {
        if (rarity.equals("ancient")) return Arrays.asList("&5Древнее Проклятие", "&5Бездна Хаоса", "&5Вечная Тьма", "&5Космический Удар");
        if (rarity.equals("legendary")) return Arrays.asList("&6Грозовой Импульс", "&6Багровый Резонанс", "&6Астральный Барьер", "&6Пламенный Контур");
        if (rarity.equals("epic")) return Arrays.asList("&5Критический жар", "&5Оберег", "&5Развеивание", "&5Похищение Жизни");
        if (rarity.equals("rare")) return Arrays.asList("&9Искра удачи", "&9Стальная кожа", "&9Резкий удар");
        if (rarity.equals("uncommon")) return Arrays.asList("&aМалая искра", "&aЛёгкая сталь");
        return Collections.emptyList();
    }

    private int countDefects(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        int count = 0;
        for (String key : defectKeys()) if (plugin.getGearManager().hasDefect(item, key)) count++;
        if (count == 0 && item.getItemMeta().hasLore()) for (String l : item.getItemMeta().getLore()) if (ChatColor.stripColor(l).startsWith("Дефект:")) count++;
        return count;
    }

    private List<String> defectKeys() {
        if (plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list") == null) return Arrays.asList("fragile", "heavy", "dull");
        return new ArrayList<>(plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list").getKeys(false));
    }

    private void announceIfNeeded(Player p, ItemStack result, String rarity) {
        if (!(rarity.equals("epic") || rarity.equals("legendary") || rarity.equals("ancient"))) return;
        String msg = "⚒ Реликтовый горн вспыхнул! " + p.getName() + " возвысил предмет до " + ChatColor.stripColor(rarityDisplay(rarity)) + ": " + ChatColor.stripColor(itemName(result));
        if (rarity.equals("ancient")) Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + msg);
        else if (rarity.equals("legendary")) Bukkit.broadcastMessage(ChatColor.GOLD + msg);
        else Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + msg);
        try { VKChatPlugin.getInstance().getApi().sendToMainChat(msg); } catch (Exception ignored) {}
    }

    private void log(Player p, String action, String details) {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            File file = new File(plugin.getDataFolder(), "forge-log.log");
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(stamp + " | " + action + " | " + p.getName() + " | " + details.replace('\n', ' ') + "\n");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось записать forge-log.log: " + e.getMessage());
        }
    }

    private void resetConfirmButton(String title, Inventory inv) {
        if (title.equals(FUSION_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния", ChatColor.GRAY + "Покажет шанс, цену, ресурсы и свитки."));
        else if (title.equals(REFORGE_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        else if (title.equals(CLEANSE_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения"));
        else if (title.equals(REPAIR_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ремонта"));
        else if (title.equals(RUNE_CLEANSING_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения рун"));
    }

    private boolean isForgeTitle(String title) { return title.equals(HUB_TITLE) || title.equals(FUSION_TITLE) || title.equals(REFORGE_TITLE) || title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE) || title.equals(SCROLLS_TITLE) || title.equals(RUNE_CLEANSING_TITLE); }
    private boolean isWorkSlot(String title, int slot) { return title.equals(FUSION_TITLE) ? (slot == 20 || slot == 22 || slot == 24) : ((title.equals(REFORGE_TITLE) || title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE) || title.equals(RUNE_CLEANSING_TITLE)) && slot == CENTER_SLOT); }
    private boolean isEmpty(ItemStack item) { return item == null || item.getType() == Material.AIR; }
    private int firstFreeFusionSlot(Inventory inv) { for (int s : FUSION_SLOTS) if (isEmpty(inv.getItem(s))) return s; return -1; }

    private void returnInputs(Player p, Inventory inv) {
        for (int slot : FUSION_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (isEmpty(item)) continue;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && " ".equals(item.getItemMeta().getDisplayName())) continue;
            inv.setItem(slot, null);
            p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    private void nav(Inventory inv) {
        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.YELLOW + "← Назад"));
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Закрыть"));
    }

    private int defaultScrollPrice(String type) {
        switch (type) {
            case "chance_25": return 2500;
            case "chance_50": return 5500;
            case "perfect": return 12000;
            case "protect_all": return 9000;
            case "anti_defect": return 3500;
            case "discount": return 4000;
            default: return 1000;
        }
    }

    private int defaultUpgradeChance(String targetRarity) { switch (targetRarity) { case "uncommon": return 85; case "rare": return 65; case "epic": return 40; case "legendary": return 20; case "ancient": return 8; default: return 50; } }
    private String nextRarity(String rarity) { switch (rarity) { case "common": return "uncommon"; case "uncommon": return "rare"; case "rare": return "epic"; case "epic": return "legendary"; case "legendary": return "ancient"; default: return null; } }
    private int rarityIndex(String key) { switch (key) { case "uncommon": return 1; case "rare": return 2; case "epic": return 3; case "legendary": return 4; case "ancient": return 5; default: return 0; } }

    private void applyRarity(ItemStack item, String rarityKey) {
        ItemMeta meta = item.getItemMeta();
        String rarityName = rarityDisplay(rarityKey);
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            if (ChatColor.stripColor(lore.get(i)).startsWith("Редкость:")) {
                lore.set(i, ChatColor.GRAY + "Редкость: " + rarityName);
                replaced = true;
                break;
            }
        }
        if (!replaced) lore.add(0, ChatColor.GRAY + "Редкость: " + rarityName);
        String prop = plugin.getGearManager().getRarityPropertyLine(rarityKey);
        if (prop != null) {
            lore.removeIf(l -> ChatColor.stripColor(l).startsWith("Свойство редкости:"));
            lore.add(Math.min(1, lore.size()), prop);
        }
        boolean alreadyMarked = false;
        for (String l : lore) {
            if (ChatColor.stripColor(l).contains("Возвышено в реликтовой кузне")) { alreadyMarked = true; break; }
        }
        if (!alreadyMarked) lore.add(ChatColor.DARK_PURPLE + "⭐ Возвышено в реликтовой кузне");
        meta.setLore(lore);
        String pure = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : item.getType().name();
        pure = pure.replaceFirst("^\\[[^]]+\\]\\s*", "");
        meta.setDisplayName(rarityName + " " + ChatColor.WHITE + pure);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_rarity"), PersistentDataType.STRING, rarityKey);
        item.setItemMeta(meta);
    }

    private String rarityDisplay(String key) { return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("rarities." + key + ".name", key)); }
    private String itemName(ItemStack item) { if (item == null) return "null"; return item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name(); }

    private ItemStack marker(Material mat, String name) { return item(mat, name, ChatColor.GRAY + "Рабочие слоты выше/рядом пустые."); }
    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv, Material mat) { ItemStack filler = item(mat, " "); for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
