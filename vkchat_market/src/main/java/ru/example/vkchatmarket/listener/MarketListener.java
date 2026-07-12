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
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchatmarket.service.MarketService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketListener implements Listener {
    private final VKChatMarketPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey catKey;
    private final NamespacedKey sellAllConfirmKey;

    private final Map<UUID, String> amountPrompt = new ConcurrentHashMap<>();

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

        // Продать всё — подтверждение
        if (meta.getPersistentDataContainer().has(sellAllConfirmKey, PersistentDataType.STRING)) {
            String catKey = meta.getPersistentDataContainer().get(sellAllConfirmKey, PersistentDataType.STRING);
            MarketCategory mc = MarketCategory.fromConfig(catKey);
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (vkId == -1 && !VKChatBridge.hasPass(p)) { p.sendMessage("§c❌ Привяжи ВК!"); return; }
            int earned = plugin.getMarketService().sellAll(p, mc, vkId);
            if (earned > 0) {
                p.sendMessage("§a✓ Всё продано! Выручка: §e" + earned + " реп.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.sendMessage("§7Нет предметов для продажи.");
            }
            MarketGui.openMainMenu(plugin, p);
            return;
        }

        String parsedCat = parseCategoryFromTitle(title);

        // Продать всё
        if (hasPdc(meta, "mkt_sellall")) {
            MarketCategory mc = MarketCategory.fromConfig(parsedCat);
            List<MarketEntry> list = mc != null ? plugin.getMarketService().getByCategory(mc) : plugin.getMarketService().getAll();
            int total = 0, count = 0;
            for (MarketEntry en : list) {
                int owned = plugin.getMarketService().countItems(p, en);
                if (owned > 0) { total += plugin.getMarketService().prices().getSellPrice(en, p) * owned; count += owned; }
            }
            if (count == 0) { p.sendMessage("§7Нет предметов для продажи."); return; }
            MarketGui.openSellAllConfirm(plugin, p, parsedCat, total, count);
            return;
        }

        // Поиск
        if (hasPdc(meta, "mkt_search")) {
            p.closeInventory();
            p.sendMessage("§e🔍 Введи название предмета в чат (или 'отмена'):");
            plugin.getSearchPrompt().put(p.getUniqueId(), "");
            return;
        }

        // Закрыть
        if (hasPdc(meta, "mkt_close")) {
            p.closeInventory();
            return;
        }

        // Кастомное количество
        if (hasPdc(meta, "mkt_amount")) {
            amountPrompt.put(p.getUniqueId(), parsedCat);
            p.closeInventory();
            p.sendMessage("§e✎ Введи количество для покупки/продажи в чат.");
            p.sendMessage("§7Затем используй ЛКМ/ПКМ на товаре в маркете.");
            return;
        }

        // Категория
        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) { MarketGui.openMainMenu(plugin, p); return; }
            MarketGui.openCategory(plugin, p, cat, 0);
            return;
        }

        // Торговля
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        MarketEntry entry = plugin.getMarketService().get(itemId);
        if (entry == null) return;

        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1 && !VKChatBridge.hasPass(p)) { p.sendMessage("§c❌ Привяжи ВК: /vklink"); return; }

        ClickType click = e.getClick();

        // Кастомное количество (предварительно нажали кнопку)
        if (click == ClickType.MIDDLE || click == ClickType.CREATIVE) {
            handleCustomAmount(p, entry, parsedCat);
            return;
        }

        if (click == ClickType.LEFT)         trade(p, "sell", entry, 1, parsedCat);
        else if (click == ClickType.SHIFT_LEFT)  trade(p, "sell", entry, 64, parsedCat);
        else if (click == ClickType.RIGHT)       trade(p, "buy", entry, 1, parsedCat);
        else if (click == ClickType.SHIFT_RIGHT) trade(p, "buy", entry, 16, parsedCat);
    }

    private void handleCustomAmount(Player p, MarketEntry entry, String catFromTitle) {
        String catStr = amountPrompt.remove(p.getUniqueId());
        if (catStr == null) {
            p.sendMessage("§c❌ Сначала нажми §e✎ Кол-во §cв нижнем ряду.");
            return;
        }
        p.sendMessage("§e✎ Введи количество для §f" + entry.displayName() + " §eв чат (или 'отмена'):");
        plugin.getCustomAmountPrompt().put(p.getUniqueId(), entry.id() + "|" + catFromTitle);
    }

    private void trade(Player p, String mode, MarketEntry entry, int amount, String catFromTitle) {
        MarketService svc = plugin.getMarketService();
        int vkId = VKChatBridge.getLinkedVkId(p);

        if ("buy".equals(mode)) {
            int price = svc.prices().getBuyPrice(entry, p);
            int total = price * amount;
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < total) { p.sendMessage("§c❌ Нужно " + total + " реп. (у тебя " + rep + ")"); return; }
            VKChatBridge.takeReputation(vkId, total);
            svc.giveItems(p, entry, amount);
            svc.prices().recordBuy(entry, amount);
            p.sendMessage("§a✓ Куплено " + amount + "x §f" + entry.displayName() + " §aза §e" + total + " реп.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else {
            int owned = svc.countItems(p, entry);
            int toSell = Math.min(amount, owned);
            if (toSell <= 0) { p.sendMessage("§c❌ Нет предметов!"); return; }
            int price = svc.prices().getSellPrice(entry, p);
            int total = price * toSell;
            svc.takeItems(p, entry, toSell);
            VKChatBridge.addEffectiveRep(p, total);
            svc.prices().recordSell(entry, toSell);
            p.sendMessage("§a✓ Продано " + toSell + "x §f" + entry.displayName() + " §aза §e" + total + " реп.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }

        reopen(p, catFromTitle);
    }

    private void reopen(Player p, String catKey) {
        String title = p.getOpenInventory().getTitle();
        if (title.contains("Поиск:")) {
            // При поиске — возвращаемся в меню
            Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openMainMenu(plugin, p));
        } else {
            int page = getPageFromTitle(title);
            Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openCategory(plugin, p, catKey, page));
        }
    }

    // ═══ ЧАТ-ВВОД ═══

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (plugin.getSearchPrompt().containsKey(id)) {
            e.setCancelled(true);
            plugin.getSearchPrompt().remove(id);
            String query = e.getMessage().trim();
            if (query.equalsIgnoreCase("отмена") || query.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openMainMenu(plugin, p));
                return;
            }
            final String q = query;
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<MarketEntry> results = plugin.getMarketService().search(q);
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

        if (plugin.getCustomAmountPrompt().containsKey(id)) {
            e.setCancelled(true);
            String data = plugin.getCustomAmountPrompt().remove(id);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("отмена") || msg.isEmpty()) {
                String cat = data.contains("|") ? data.split("\\|")[1] : "all";
                Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openCategory(plugin, p, cat, 0));
                return;
            }
            int amount;
            try { amount = Integer.parseInt(msg); } catch (NumberFormatException ex) {
                p.sendMessage("§c❌ Введи число!");
                plugin.getCustomAmountPrompt().put(id, data);
                return;
            }
            if (amount <= 0) { p.sendMessage("§c❌ > 0!"); return; }
            String[] parts = data.split("\\|");
            String itemId = parts[0];
            String catKey = parts.length > 1 ? parts[1] : "all";
            MarketEntry entry = plugin.getMarketService().get(itemId);
            if (entry == null) return;
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (vkId == -1 && !VKChatBridge.hasPass(p)) { p.sendMessage("§c❌ Привяжи ВК!"); return; }
            Bukkit.getScheduler().runTask(plugin, () -> trade(p, "sell", entry, amount, catKey));
        }
    }

    // ═══ ВСПОМОГАТЕЛЬНЫЕ ═══

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
                    plugin.getConfig().getString("categories." + cat.configKey() + ".name", ""));
            if (trimmed.equalsIgnoreCase(name)) return cat.configKey();
        }
        if (trimmed.equals("Все товары")) return "all";
        if (trimmed.contains("Поиск")) return "all";
        return "all";
    }

    private int getPageFromTitle(String title) {
        try {
            int idx = title.lastIndexOf(" §8");
            if (idx < 0) return 0;
            String pagePart = title.substring(idx + 3);
            if (pagePart.contains("/")) {
                return Integer.parseInt(pagePart.split("/")[0]) - 1;
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
