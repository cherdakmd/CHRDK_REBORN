package ru.example.vkchatmarket.data;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.gui.MarketItemFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MarketTransactionService — бизнес-логика торговых операций рынка.
 *
 * Извлечено из MarketGuiListener для разделения GUI и бизнес-логики:
 * - Продажа предметов (sell / sellAll)
 * - Покупка предметов (buy / buyLimited)
 * - Инвентарные операции (подсчёт, удаление предметов)
 * - Проверки (VK-привязка, кулдаун, баланс, сток)
 */
public final class MarketTransactionService {

    private MarketTransactionService() {}

    // ═══════════════════════════════════════════════════════════════
    // ПРОДАЖА
    // ═══════════════════════════════════════════════════════════════

    /**
     * Продать предметы игрока на рынок.
     *
     * @param plugin  экземпляр плагина
     * @param p       игрок
     * @param itemId  идентификатор товара
     * @param limit   -1 = все, >0 = максимум N штук (64 для shift-продажи)
     */
    public static void sellItems(VKChatMarketPlugin plugin, Player p, String itemId, int limit) {
        int vkId = requireVkLinked(p);
        if (vkId == -1) return;

        Material m = getMarketMaterial(plugin, itemId);
        boolean isCustom = !plugin.getConfig().getString("items." + itemId + ".enchant", "").isEmpty();

        // Подсчитать количество
        int count = countSellableItems(p, m, itemId, isCustom, limit);
        if (count == 0) { p.sendMessage("§cНет предметов для продажи!"); return; }

        // Кулдаун
        if (!plugin.getMarketManager().canTrade(itemId, p)) { p.sendMessage("§cПодождите..."); return; }

        // Продажа через MarketManager
        double donorMult = MarketItemFactory.donorSellMultiplier(p);
        int rep = plugin.getMarketManager().sellItems(itemId, count, donorMult);
        if (rep <= 0) { p.sendMessage("§cРынок переполнен!"); return; }
        plugin.getMarketManager().markTrade(itemId, p);

        // Удалить предметы из инвентаря
        removeItemsFromInventory(p, m, count, itemId, isCustom);

        // Начислить репутацию
        VKChatBridge.addPoints(vkId, rep);

        // Если это зачарованная книга — зарегистрировать кастомную
        if (m == Material.ENCHANTED_BOOK) {
            registerSoldCustomBooks(plugin, p, m);
        }

        // Обратная связь
        p.sendMessage("§a§l💰 Продано §f" + count + " шт. §a→ §e" + rep + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.sendTitle("§a§l+ " + rep + " реп.", "§fПродано " + count + " шт. " + itemId, 5, 20, 5);
        plugin.getMarketManager().logTransaction(p.getName(), itemId, count, "SELL",
                plugin.getMarketManager().getCurrentPrice(itemId), rep);
        plugin.getMarketFun().recordQuestProgress(p, itemId, count, "sell");
    }

    /**
     * Продать все продаваемые предметы из инвентаря (через GUI с подтверждением).
     */
    public static void sellAllSellable(VKChatMarketPlugin plugin, Player p) {
        Map<String, Integer> toSell = collectSellable(plugin, p);
        if (toSell.isEmpty()) { p.sendMessage("§cНет предметов для продажи."); return; }

        // Проверка кулдауна для первого предмета
        String firstItem = toSell.keySet().iterator().next();
        if (!plugin.getMarketManager().canTrade(firstItem, p)) {
            p.sendMessage("§cПодождите между продажами!");
            return;
        }

        int vkId = requireVkLinked(p);
        if (vkId == -1) return;

        int totalCount = 0;
        int totalRep = 0;

        for (Map.Entry<String, Integer> entry : toSell.entrySet()) {
            String itemId = entry.getKey();
            Material m; try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = entry.getValue();

            removeItemsFromInventory(p, m, count, itemId, false);

            double donorMult = MarketItemFactory.donorSellMultiplier(p);
            int rep = Math.max(1, (int) Math.round(
                    plugin.getMarketManager().calculateBulkSellPrice(itemId, count) * donorMult));
            totalRep += rep;
            totalCount += count;
            plugin.getMarketManager().sellItems(itemId, count, donorMult);
            plugin.getMarketManager().markTrade(itemId, p);
        }

        VKChatBridge.addPoints(vkId, totalRep);
        p.sendMessage("§a§l💰 Продано: §f" + totalCount + " шт. §a→ §e" + totalRep + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    /**
     * Продать все из команды /market sellall (без GUI).
     */
    public static void sellAllFromCommand(VKChatMarketPlugin plugin, Player p) {
        int vkId = requireVkLinked(p);
        if (vkId == -1) return;

        Map<String, Integer> toSell = collectSellable(plugin, p);
        if (toSell.isEmpty()) { p.sendMessage("§cНет предметов для продажи."); return; }

        int totalCount = 0;
        int totalRep = 0;

        for (Map.Entry<String, Integer> entry : toSell.entrySet()) {
            String itemId = entry.getKey();
            Material m; try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = entry.getValue();

            removeItemsFromInventory(p, m, count, itemId, false);

            double donorMult = MarketItemFactory.donorSellMultiplier(p);
            int rep = Math.max(1, (int) Math.round(
                    plugin.getMarketManager().calculateBulkSellPrice(itemId, count) * donorMult));
            totalRep += rep;
            totalCount += count;
            plugin.getMarketManager().sellItems(itemId, count, donorMult);
        }

        VKChatBridge.addPoints(vkId, totalRep);
        p.sendMessage("§a§l💰 Продано: §f" + totalCount + " шт. §a→ §e" + totalRep + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════
    // ПОКУПКА
    // ═══════════════════════════════════════════════════════════════

    /**
     * Купить предметы у рынка.
     *
     * @param plugin  экземпляр плагина
     * @param p       игрок
     * @param itemId  идентификатор товара
     * @param amount  желаемое количество
     */
    public static void buyItems(VKChatMarketPlugin plugin, Player p, String itemId, int amount) {
        int vkId = requireVkLinked(p);
        if (vkId == -1) return;

        if (!plugin.getMarketManager().canTrade(itemId, p)) { p.sendMessage("§cПодождите..."); return; }

        Material m = getMarketMaterial(plugin, itemId);
        boolean isBook = m == Material.ENCHANTED_BOOK;

        // Определяем реальное количество с учётом стока
        int actual = amount;
        if (!isBook) {
            int stock = plugin.getMarketManager().getStock(itemId);
            int minStock = plugin.getConfig().getInt("items." + itemId + ".min-stock", -200);
            int canBuy = Math.max(0, stock - minStock);
            if (canBuy <= 0) { p.sendMessage("§cТовар закончился! Дефицит!"); return; }
            actual = Math.min(amount, canBuy);
        }

        // Проверяем место в инвентаре ДО списания
        ItemStack sample = MarketItemFactory.createCustomItem(plugin, itemId);
        int free = countInventorySpace(p, sample);
        if (free < actual) { p.sendMessage("§cИнвентарь полон!"); return; }

        // Рассчитываем стоимость
        double donorMult = MarketItemFactory.donorBuyMultiplier(p);
        int cost = isBook
                ? (int) (plugin.getConfig().getDouble("items." + itemId + ".base-price", 200) * donorMult)
                : plugin.getMarketManager().buyItems(itemId, actual, donorMult);
        if (cost <= 0) { p.sendMessage("§cОшибка цены!"); return; }

        // Проверяем баланс
        int rep = VKChatBridge.getReputation(vkId);
        if (rep < cost) { p.sendMessage("§cНужно " + cost + " реп. (у тебя " + rep + ")"); return; }

        // Выдаём предметы
        for (int i = 0; i < actual; i++) {
            ItemStack toGive = MarketItemFactory.createCustomItem(plugin, itemId);
            if (isBook && !toGive.getItemMeta().hasEnchants()) {
                toGive = plugin.getMarketManager().takeCustomBook();
                if (toGive == null) {
                    toGive = MarketItemFactory.createRandomEnchantedBook();
                }
            }
            p.getInventory().addItem(toGive);
        }

        // Списываем репутацию
        VKChatBridge.takeReputation(vkId, cost);
        plugin.getMarketManager().markTrade(itemId, p);

        p.sendMessage("§6§l💰 Куплено §f" + actual + " шт. §6→ §e" + cost + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        p.sendTitle("§6§l- " + cost + " реп.", "§fКуплено " + actual + " шт. " + itemId, 5, 20, 5);
        plugin.getMarketManager().logTransaction(p.getName(), itemId, actual, "BUY",
                plugin.getMarketManager().getBuyPrice(itemId), cost);
        plugin.getMarketFun().recordQuestProgress(p, itemId, actual, "buy");
    }

    /**
     * Купить лимитированный предмет.
     */
    public static void buyLimitedItem(VKChatMarketPlugin plugin, Player p, String itemId) {
        int vkId = requireVkLinked(p);
        if (vkId == -1) return;

        int price = (int) Math.max(1, Math.round(
                plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000)
                        * MarketItemFactory.donorBuyMultiplier(p)));
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);

        // Проверяем дневной лимит
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        NamespacedKey limitKey = new NamespacedKey(plugin, "limited_" + today + "_" + itemId.toLowerCase());
        int boughtToday = p.getPersistentDataContainer().getOrDefault(limitKey, PersistentDataType.INTEGER, 0);
        if (boughtToday >= limit) {
            p.sendMessage("§cЛимит на сегодня: " + boughtToday + "/" + limit);
            return;
        }

        // Проверяем баланс
        int currentRep = VKChatBridge.getReputation(vkId);
        if (currentRep < price) {
            p.sendMessage("§cНужно " + price + " реп. (у тебя " + currentRep + ")");
            return;
        }

        // Выдаём предмет
        if (!p.getInventory().addItem(MarketItemFactory.createCustomItem(plugin, itemId)).isEmpty()) {
            p.sendMessage("§cИнвентарь полон!");
            return;
        }

        // Списываем
        p.getPersistentDataContainer().set(limitKey, PersistentDataType.INTEGER, boughtToday + 1);
        VKChatBridge.takeReputation(vkId, price);
        p.sendMessage("§d§l💎 Куплено: §f" + itemId + " §dза §e" + price + " реп. §7("
                + (boughtToday + 1) + "/" + limit + ")");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    // СБОР ПРОДАВАЕМЫХ ПРЕДМЕТОВ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Собрать список продаваемых предметов из инвентаря.
     * Возвращает Map: itemId → количество.
     */
    public static Map<String, Integer> collectSellable(VKChatMarketPlugin plugin, Player p) {
        Map<String, Integer> toSell = new HashMap<>();
        for (String itemId : MarketCategoryResolver.getConfiguredItems(plugin, "all")) {
            Material m;
            try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = 0;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == m
                        && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                    count += item.getAmount();
                }
            }
            if (count > 0) toSell.put(itemId, count);
        }
        return toSell;
    }

    /**
     * Рассчитать общую стоимость продаваемых предметов (для GUI-предпросмотра).
     */
    public static int calculateSellAllTotal(VKChatMarketPlugin plugin, Map<String, Integer> sellable) {
        int total = 0;
        for (Map.Entry<String, Integer> e : sellable.entrySet()) {
            total += Math.max(1, (int) Math.round(
                    plugin.getMarketManager().calculateBulkSellPrice(e.getKey(), e.getValue())));
        }
        return total;
    }

    // ═══════════════════════════════════════════════════════════════
    // ИНВЕНТАРНЫЕ ОПЕРАЦИИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Удалить предметы из инвентаря игрока.
     *
     * @param p        игрок
     * @param m        тип материала
     * @param count    количество для удаления
     * @param itemId   идентификатор товара (для кастомных предметов)
     * @param isCustom признак кастомного предмета (с зачарованием)
     */
    private static void removeItemsFromInventory(Player p, Material m, int count,
                                                  String itemId, boolean isCustom) {
        int toRemove = count;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || item.getType() != m) continue;

            // Фильтрация: только подходящие предметы
            if (isCustom) {
                if (!customItemMatches(itemId, item)) continue;
            } else {
                // Обычные предметы: пропускаем с лором (не продаются)
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) continue;
            }

            if (item.getAmount() <= toRemove) {
                toRemove -= item.getAmount();
                p.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - toRemove);
                toRemove = 0;
            }
            if (toRemove == 0) break;
        }
    }

    /**
     * Подсчитать количество продаваемых предметов в инвентаре.
     */
    private static int countSellableItems(Player p, Material m, String itemId,
                                           boolean isCustom, int limit) {
        int count = 0;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || item.getType() != m) continue;

            if (isCustom) {
                if (!customItemMatches(itemId, item)) continue;
            } else {
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) continue;
            }

            int can = limit < 0 ? item.getAmount() : Math.min(item.getAmount(), Math.max(0, limit - count));
            count += can;
            if (limit > 0 && count >= limit) break;
        }
        return count;
    }

    /**
     * Подсчитать свободное место в инвентаре для данного предмета.
     */
    private static int countInventorySpace(Player p, ItemStack sample) {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = p.getInventory().getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                free += sample.getMaxStackSize();
            } else if (slot.isSimilar(sample)) {
                free += sample.getMaxStackSize() - slot.getAmount();
            }
        }
        return free;
    }

    // ═══════════════════════════════════════════════════════════════
    // ПРЕДМЕТНЫЕ УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Получить Material для товара из конфигурации.
     */
    public static Material getMarketMaterial(VKChatMarketPlugin plugin, String itemId) {
        String matName = plugin.getConfig().getString("items." + itemId + ".material", "");
        if (!matName.isEmpty()) {
            try { return Material.valueOf(matName); } catch (Exception ignored) {}
        }
        try { return Material.valueOf(itemId); } catch (Exception e) { return Material.BARRIER; }
    }

    /**
     * Проверить, соответствует ли ItemStack кастомному товару (с зачарованием).
     */
    public static boolean customItemMatches(String itemId, ItemStack stack) {
        // Делегируем MarketItemFactory, но сохраняем fallback для совместимости
        if (stack == null || !stack.hasItemMeta()) return false;

        // Проверяем через PersistentDataContainer (если предмет был создан через фабрику)
        if (stack.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatMarket"), "market_item"),
                PersistentDataType.STRING)) {
            String stored = stack.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(
                            org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatMarket"), "market_item"),
                    PersistentDataType.STRING);
            return itemId.equals(stored);
        }

        // Fallback: проверка по материалу и зачарованию
        return false; // Не-market предметы с чарами не матчим
    }

    /**
     * Зарегистрировать проданные кастомные зачарованные книги.
     */
    private static void registerSoldCustomBooks(VKChatMarketPlugin plugin, Player p, Material m) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (stack != null && stack.getType() == m && stack.hasItemMeta()
                    && !stack.getItemMeta().getEnchants().isEmpty()) {
                plugin.getMarketManager().addCustomBook(stack.clone());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ПРОВЕРКИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Проверить привязку ВК. Возвращает vkId или -1 с отправкой сообщения.
     */
    private static int requireVkLinked(Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1 && !VKChatBridge.hasPass(p)) {
            p.sendMessage("§cПривяжи ВК (/vklink) для торговли!");
            return -1;
        }
        return vkId;
    }
}
