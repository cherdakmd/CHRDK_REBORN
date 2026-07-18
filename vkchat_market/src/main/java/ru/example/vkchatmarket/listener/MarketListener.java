package ru.example.vkchatmarket.listener;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.gui.PlayerGuiState;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchatmarket.prompt.PlayerPromptService;
import ru.example.vkchatmarket.service.MarketService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketListener implements Listener {
    private final VKChatMarketPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey catKey;
    private final NamespacedKey sellAllConfirmKey;

    private final Map<UUID, Long> tradeCooldown = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 300;
    private static final int MAX_RECENT = 10;

    public MarketListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "mkt_item");
        this.catKey = new NamespacedKey(plugin, "mkt_cat");
        this.sellAllConfirmKey = new NamespacedKey(plugin, "mkt_sellall_confirm");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.contains("БИРЖА")) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack current = e.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;
        ItemMeta meta = current.getItemMeta();

        // Sell all confirm
        if (meta.getPersistentDataContainer().has(sellAllConfirmKey, PersistentDataType.STRING)) {
            String catKeyVal = meta.getPersistentDataContainer().get(sellAllConfirmKey, PersistentDataType.STRING);
            MarketCategory mc = MarketCategory.fromConfig(catKeyVal);
            if (!checkAuth(p)) return;
            int earned = plugin.getMarketService().sellAll(p, mc);
            if (earned > 0) {
                p.sendMessage("§a✓ Всё продано! Выручка: §e" + earned + " реп.");
                playSound(p, Sound.ENTITY_PLAYER_LEVELUP);
            } else {
                p.sendMessage("§7Нет предметов для продажи.");
            }
            MarketGui.openMainMenu(plugin, p);
            return;
        }

        // Bulk confirm
        if (hasPdc(meta, "mkt_bulk_confirm")) {
            handleBulkConfirm(p, title);
            return;
        }

        // Bulk cancel
        if (hasPdc(meta, "mkt_bulk_cancel")) {
            MarketGui.openMainMenu(plugin, p);
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Cart buy
        if (hasPdc(meta, "mkt_cart_buy")) {
            handleCartBuy(p);
            return;
        }

        // Sort buttons
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "mkt_sort"), PersistentDataType.STRING)) {
            String sortModeName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "mkt_sort"), PersistentDataType.STRING);
            try {
                PlayerGuiState.SortMode mode = PlayerGuiState.SortMode.valueOf(sortModeName);
                plugin.getGuiState().setSort(p.getUniqueId(), mode);
                var state = plugin.getGuiState().get(p.getUniqueId());
                MarketGui.openCategory(plugin, p, state.categoryKey(), state.page());
                playSound(p, Sound.UI_BUTTON_CLICK);
            } catch (Exception ignored) {}
            return;
        }

        // Prev page
        if (hasPdc(meta, "mkt_prev")) {
            var state = plugin.getGuiState().get(p.getUniqueId());
            int newPage = Math.max(0, state.page() - 1);
            plugin.getGuiState().set(p.getUniqueId(), state.categoryKey(), newPage, state.searchQuery());
            if (state.isSearch()) {
                List<MarketEntry> results = plugin.getMarketService().search(state.searchQuery());
                MarketGui.openSearchResults(plugin, p, results, state.searchQuery(), newPage);
            } else {
                MarketGui.openCategory(plugin, p, state.categoryKey(), newPage);
            }
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Next page
        if (hasPdc(meta, "mkt_next")) {
            var state = plugin.getGuiState().get(p.getUniqueId());
            int newPage = state.page() + 1;
            plugin.getGuiState().set(p.getUniqueId(), state.categoryKey(), newPage, state.searchQuery());
            if (state.isSearch()) {
                List<MarketEntry> results = plugin.getMarketService().search(state.searchQuery());
                MarketGui.openSearchResults(plugin, p, results, state.searchQuery(), newPage);
            } else {
                MarketGui.openCategory(plugin, p, state.categoryKey(), newPage);
            }
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        String parsedCat = parseCategoryFromTitle(title);

        // Sell all — show preview
        if (hasPdc(meta, "mkt_sellall")) {
            MarketGui.openSellAllPreview(plugin, p, parsedCat);
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Sell all from main menu
        if (hasPdc(meta, "mkt_sellall_all")) {
            MarketGui.openSellAllPreview(plugin, p, "all");
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Search
        if (hasPdc(meta, "mkt_search")) {
            p.closeInventory();
            p.sendMessage("§e🔍 Введи название предмета в чат (или 'отмена'):");
            p.sendMessage("§7Префиксы: §eore:§7, §efood:§7, §earmor:§7 и т.д.");
            plugin.getPromptService().startSearch(p.getUniqueId());
            return;
        }

        // Close
        if (hasPdc(meta, "mkt_close")) {
            p.closeInventory();
            return;
        }

        // Custom amount
        if (hasPdc(meta, "mkt_amount")) {
            plugin.getPromptService().startCustomAmount(p.getUniqueId(), "", parsedCat);
            p.closeInventory();
            p.sendMessage("§e✎ Введи количество для покупки/продажи в чат.");
            p.sendMessage("§7Затем используй ЛКМ/ПКМ на товаре в маркете.");
            return;
        }

        // Stats
        if (hasPdc(meta, "mkt_stats")) {
            showStats(p);
            return;
        }

        // Daily reward
        if (hasPdc(meta, "mkt_daily")) {
            handleDailyReward(p);
            return;
        }

        // Profile
        if (hasPdc(meta, "mkt_profile")) {
            showProfile(p);
            return;
        }

        // Cart (main menu)
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "mkt_cat"), PersistentDataType.STRING)) {
            String catVal = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "mkt_cat"), PersistentDataType.STRING);
            if ("mkt_cart".equals(catVal)) {
                MarketGui.openCartView(plugin, p);
                playSound(p, Sound.UI_BUTTON_CLICK);
                return;
            }
        }

        // Cart close
        if (hasPdc(meta, "mkt_cart_close")) {
            var state = plugin.getGuiState().get(p.getUniqueId());
            MarketGui.openCategory(plugin, p, state.categoryKey(), state.page());
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Category
        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) { MarketGui.openMainMenu(plugin, p); playSound(p, Sound.UI_BUTTON_CLICK); return; }
            if ("mkt_cart".equals(cat)) { MarketGui.openCartView(plugin, p); playSound(p, Sound.UI_BUTTON_CLICK); return; }
            MarketGui.openCategory(plugin, p, cat, 0);
            playSound(p, Sound.UI_BUTTON_CLICK);
            return;
        }

        // Trade
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        MarketEntry entry = plugin.getMarketService().get(itemId);
        if (entry == null) return;

        if (!checkAuth(p)) return;
        if (!checkCooldown(p)) return;

        ClickType click = e.getClick();

        // Custom amount via MIDDLE
        if (click == ClickType.MIDDLE || click == ClickType.CREATIVE) {
            handleCustomAmount(p, entry, parsedCat);
            return;
        }

        // Bulk confirm for large amounts
        boolean bulk = plugin.getGuiState().get(p.getUniqueId()).bulkMode();
        if (click == ClickType.SHIFT_LEFT) {
            int amt = bulk ? 128 : 64;
            if (amt > 32) { handleBulkTrade(p, "sell", entry, amt, parsedCat); return; }
            trade(p, "sell", entry, amt, parsedCat);
        } else if (click == ClickType.SHIFT_RIGHT) {
            int amt = bulk ? 128 : 16;
            if (amt > 32) { handleBulkTrade(p, "buy", entry, amt, parsedCat); return; }
            trade(p, "buy", entry, amt, parsedCat);
        } else if (click == ClickType.LEFT) {
            trade(p, "sell", entry, 1, parsedCat);
        } else if (click == ClickType.RIGHT) {
            trade(p, "buy", entry, 1, parsedCat);
        } else if (click == ClickType.DROP) {
            addToCart(p, entry);
        }
    }

    private void addToCart(Player p, MarketEntry entry) {
        plugin.getGuiState().addToCart(p.getUniqueId(), entry.id(), 1);
        p.sendMessage("§e🛒 Добавлено в корзину: §f" + entry.displayName());
        playSound(p, Sound.ENTITY_ITEM_PICKUP);
    }

    private void handleBulkTrade(Player p, String mode, MarketEntry entry, int amount, String catFromTitle) {
        MarketGui.openBulkConfirm(plugin, p, mode, entry, amount);
        plugin.getGuiState().set(p.getUniqueId(), catFromTitle, plugin.getGuiState().get(p.getUniqueId()).page(), plugin.getGuiState().get(p.getUniqueId()).searchQuery());
    }

    private void handleBulkConfirm(Player p, String title) {
        var state = plugin.getGuiState().get(p.getUniqueId());
        if (!checkAuth(p)) return;
        MarketGui.openCategory(plugin, p, state.categoryKey(), state.page());
    }

    private void handleCartBuy(Player p) {
        if (!checkAuth(p)) return;
        var cart = plugin.getGuiState().getCart(p.getUniqueId());
        if (cart.isEmpty()) { p.sendMessage("§7Корзина пуста."); return; }

        MarketService svc = plugin.getMarketService();
        int totalSpent = 0;
        int itemsBought = 0;
        for (var ce : cart.entrySet()) {
            MarketEntry entry = svc.get(ce.getKey());
            if (entry == null) continue;
            int amount = ce.getValue();
            int price = svc.prices().getBuyPrice(entry, p, amount);
            int total = price * amount;
            int vkId = VKChatBridge.getLinkedVkId(p);
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < total) {
                p.sendMessage("§c❌ Недостаточно реп. для §f" + entry.displayName() + " §c(нужно " + total + ")");
                continue;
            }
            VKChatBridge.takeReputation(vkId, total);
            svc.giveItems(p, entry, amount);
            svc.prices().recordBuy(entry, amount);
            totalSpent += total;
            itemsBought += amount;
            p.sendMessage("§a✓ Куплено §f" + amount + "x §f" + entry.displayName());
        }
        plugin.getGuiState().clearCart(p.getUniqueId());
        if (totalSpent > 0) {
            p.sendMessage("§6Итого потрачено: §e" + totalSpent + " реп. §7за §f" + itemsBought + " §7шт.");
            playSound(p, Sound.ENTITY_PLAYER_LEVELUP);
        }
        MarketGui.openMainMenu(plugin, p);
    }

    private void handleDailyReward(Player p) {
        if (!checkAuth(p)) return;
        long last = plugin.getGuiState().getLastDailyReward(p.getUniqueId());
        long cooldown = plugin.getSettingsConfig().getInt("settings.daily-reward-cooldown-hours", 24) * 3600000L;
        if (System.currentTimeMillis() - last < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - last)) / 60000;
            p.sendMessage("§c⏳ Бонус уже получен. Жди §e" + remaining + " §cмин.");
            playSound(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        int amount = plugin.getSettingsConfig().getInt("settings.daily-reward-amount", 50);
        VKChatBridge.addEffectiveRep(p, amount);
        plugin.getGuiState().setLastDailyReward(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage("§a✓ Дневной бонус: §e+" + amount + " реп.");
        playSound(p, Sound.ENTITY_PLAYER_LEVELUP);
        MarketGui.openMainMenu(plugin, p);
    }

    private void showStats(Player p) {
        var svc = plugin.getMarketService();
        var prices = svc.prices();
        p.sendMessage("§6§l═══ СТАТИСТИКА БИРЖИ ═══");
        p.sendMessage("§7Товаров: §f" + svc.getAll().size());
        p.sendMessage("§7Категорий: §f" + MarketCategory.values().length);
        p.sendMessage("§7Динамика: §f" + (plugin.getSettingsConfig().getBoolean("settings.dynamic-pricing", true) ? "вкл" : "выкл"));
        p.sendMessage("§7Событие: " + (prices.hasActiveEvent() ? "§c" + prices.getActiveEventName() : "§7нет"));
        p.sendMessage("§6§l═══════════════════");
        p.closeInventory();
    }

    private void showProfile(Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;
        String donorTag = plugin.getMarketService().prices().donorTag(p);
        int loyalty = plugin.getGuiState().getLoyaltyCount(p.getUniqueId());
        int cartSize = plugin.getGuiState().getCart(p.getUniqueId()).size();

        p.sendMessage("§6§l═══ ПРОФИЛЬ ТРЕЙДЕРА ═══");
        p.sendMessage("§7Баланс: §e" + rep + " реп." + (donorTag != null ? " §6⭐ " + donorTag : ""));
        p.sendMessage("§7Лояльность: §f" + loyalty + " §7сделок подряд");
        p.sendMessage("§7В корзине: §f" + cartSize + " §7товаров");
        p.sendMessage("§6§l═══════════════════");
        p.closeInventory();
    }

    private void handleCustomAmount(Player p, MarketEntry entry, String catFromTitle) {
        var prompt = plugin.getPromptService().get(p.getUniqueId());
        if (prompt.type() == PlayerPromptService.PromptType.NONE) {
            p.sendMessage("§c❌ Сначала нажми §e✎ Кол-во §cв нижнем ряду.");
            return;
        }
        p.sendMessage("§e✎ Введи количество для §f" + entry.displayName() + " §eв чат (или 'отмена'):");
        plugin.getPromptService().startCustomAmount(p.getUniqueId(), entry.id(), catFromTitle);
    }

    private void trade(Player p, String mode, MarketEntry entry, int amount, String catFromTitle) {
        MarketService svc = plugin.getMarketService();
        int vkId = VKChatBridge.getLinkedVkId(p);

        // Loyalty bonus
        var state = plugin.getGuiState();
        if ("sell".equals(mode)) {
            state.incrementLoyalty(p.getUniqueId());
        } else {
            state.resetLoyalty(p.getUniqueId());
        }

        if ("buy".equals(mode)) {
            int price = svc.prices().getBuyPrice(entry, p, amount);
            int total = price * amount;
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < total) {
                p.sendMessage("§c❌ Нужно " + total + " реп. (у тебя " + rep + ")");
                playSound(p, Sound.ENTITY_VILLAGER_NO);
                return;
            }
            VKChatBridge.takeReputation(vkId, total);
            svc.giveItems(p, entry, amount);
            svc.prices().recordBuy(entry, amount);
            p.sendMessage("§a✓ Куплено " + amount + "x §f" + entry.displayName() + " §aза §e" + total + " реп.");
            playSound(p, Sound.ENTITY_VILLAGER_YES);
            if (svc.prices().dynamics().isWhaleTrade(amount)) {
                svc.prices().dynamics().announceWhaleTrade(p.getName(), entry, amount, true, total);
            }
            svc.prices().dynamics().checkPriceAlerts(entry, price);
        } else {
            int owned = svc.countItems(p, entry);
            int toSell = Math.min(amount, owned);
            if (toSell <= 0) {
                p.sendMessage("§c❌ Нет предметов!");
                playSound(p, Sound.ENTITY_VILLAGER_NO);
                return;
            }
            int price = svc.prices().getSellPrice(entry, p, toSell);
            int total = price * toSell;
            svc.takeItems(p, entry, toSell);
            VKChatBridge.addEffectiveRep(p, total);
            svc.prices().recordSell(entry, toSell);
            p.sendMessage("§a✓ Продано " + toSell + "x §f" + entry.displayName() + " §aза §e" + total + " реп.");
            playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
            if (svc.prices().dynamics().isWhaleTrade(toSell)) {
                svc.prices().dynamics().announceWhaleTrade(p.getName(), entry, toSell, false, total);
            }
            svc.prices().dynamics().checkPriceAlerts(entry, price);
        }

        reopen(p, catFromTitle);
    }

    private void reopen(Player p, String catKey) {
        var state = plugin.getGuiState().get(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state.isSearch()) {
                List<MarketEntry> results = plugin.getMarketService().search(state.searchQuery());
                MarketGui.openSearchResults(plugin, p, results, state.searchQuery(), state.page());
            } else {
                MarketGui.openCategory(plugin, p, state.categoryKey(), state.page());
            }
        });
    }

    // === ЧАТ-ВВОД ===

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        var prompt = plugin.getPromptService().get(id);

        if (prompt.type() == PlayerPromptService.PromptType.SEARCH) {
            e.setCancelled(true);
            plugin.getPromptService().clear(id);
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("отмена") || query.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openMainMenu(plugin, p));
                return;
            }
            final String q = query;
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<MarketEntry> results;
                if (q.contains(":")) {
                    results = plugin.getMarketService().searchWithPrefix(q);
                } else {
                    results = plugin.getMarketService().search(q);
                }
                if (results.isEmpty()) {
                    p.sendMessage("§7Ничего не найдено: §f" + q);
                    MarketGui.openMainMenu(plugin, p);
                } else {
                    p.sendMessage("§aНайдено: §f" + results.size());
                    MarketGui.openSearchResults(plugin, p, results, q);
                }
            });
            return;
        }

        if (prompt.type() == PlayerPromptService.PromptType.CUSTOM_AMOUNT) {
            e.setCancelled(true);
            String data = plugin.getPromptService().getItemId(id) + "|" + plugin.getPromptService().getCategory(id);
            plugin.getPromptService().clear(id);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("отмена") || msg.isEmpty()) {
                String cat = data.contains("|") ? data.split("\\|")[1] : "all";
                Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openCategory(plugin, p, cat, 0));
                return;
            }

            String tradeMode = "buy";
            String amountStr = msg;
            if (msg.startsWith("-")) {
                tradeMode = "sell";
                amountStr = msg.substring(1);
            } else if (msg.startsWith("+")) {
                amountStr = msg.substring(1);
            }

            int amount;
            try { amount = Integer.parseInt(amountStr.trim()); } catch (NumberFormatException ex) {
                p.sendMessage("§c❌ Введи число! (§e-10 §c= продать 10, §e+10 §c= купить 10)");
                plugin.getPromptService().startCustomAmount(p.getUniqueId(), data.contains("|") ? data.split("\\|")[0] : "", data.contains("|") ? data.split("\\|")[1] : "all");
                return;
            }
            if (amount <= 0) { p.sendMessage("§c❌ > 0!"); return; }
            String[] parts = data.split("\\|");
            String itemId = parts[0];
            String catKey = parts.length > 1 ? parts[1] : "all";
            MarketEntry entry = plugin.getMarketService().get(itemId);
            if (entry == null) return;
            if (!checkAuth(p)) return;
            if (!checkCooldown(p)) return;
            final String mode = tradeMode;
            final int amt = amount;
            Bukkit.getScheduler().runTask(plugin, () -> trade(p, mode, entry, amt, catKey));
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ ===

    private boolean checkAuth(Player p) {
        if (!VKChatBridge.hasVkOrPass(p)) {
            p.sendMessage("§c❌ Привяжи ВК: /vklink");
            return false;
        }
        return true;
    }

    private boolean checkCooldown(Player p) {
        long now = System.currentTimeMillis();
        Long last = tradeCooldown.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        tradeCooldown.put(p.getUniqueId(), now);
        return true;
    }

    private void playSound(Player p, Sound sound) {
        p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    private boolean hasPdc(ItemMeta meta, String key) {
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, key), PersistentDataType.BYTE);
    }

    private String parseCategoryFromTitle(String title) {
        if (!title.contains("◂ §7")) return "all";
        String part = title.substring(title.indexOf("◂ §7") + 4);
        if (part.contains(" §8")) part = part.substring(0, part.indexOf(" §8"));
        String trimmed = org.bukkit.ChatColor.stripColor(part).trim();
        if (trimmed.equals("Меню") || trimmed.isEmpty()) return "all";
        for (MarketCategory cat : MarketCategory.values()) {
            String name = org.bukkit.ChatColor.stripColor(
                    plugin.getCategoriesConfig().getString("categories." + cat.configKey() + ".name", ""));
            if (trimmed.equalsIgnoreCase(name)) return cat.configKey();
        }
        if (trimmed.equals("Все товары")) return "all";
        if (trimmed.contains("Поиск")) return "all";
        if (trimmed.contains("Продажа всех")) return "all";
        if (trimmed.contains("Сортировка")) return "all";
        if (trimmed.contains("Корзина")) return "all";
        if (trimmed.contains("Подтверждение")) return "all";
        return "all";
    }
}
