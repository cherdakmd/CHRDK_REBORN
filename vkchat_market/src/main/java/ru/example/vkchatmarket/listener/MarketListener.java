package ru.example.vkchatmarket.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    // Ожидание ввода количества через табличку
    private final Map<UUID, String> amountPrompt = new ConcurrentHashMap<>();

    public MarketListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "mkt_item");
        this.catKey = new NamespacedKey(plugin, "mkt_cat");
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
        if (hasPdc(meta, "mkt_sellall_confirm")) {
            String catKey = getPdcString(meta, "mkt_sellall_confirm");
            if (catKey != null && catKey.startsWith("§")) catKey = catKey.substring(1);
            MarketCategory mc = MarketCategory.fromConfig(catKey);
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (vkId == -1 && !VKChatBridge.hasPass(p)) { p.sendMessage("§c❌ Привяжи ВК!"); return; }
            int earned = plugin.getMarketService().sellAll(p, mc, vkId);
            if (earned > 0) {
                p.sendMessage("§a✓ Всё продано! Выручка: §e" + earned + " реп.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.sendMessage("§7Нет предметов для продажи в этой категории.");
            }
            MarketGui.openCategory(plugin, p, catKey != null ? catKey : "all", 0);
            return;
        }

        // Продать всё
        if (hasPdc(meta, "mkt_sellall")) {
            String catKey = getCategoryFromTitle(title);
            if ("menu".equals(catKey)) catKey = "all";
            MarketCategory mc = MarketCategory.fromConfig(catKey);
            List<MarketEntry> list = mc != null ? plugin.getMarketService().getByCategory(mc) : plugin.getMarketService().getAll();
            int total = 0, count = 0;
            for (MarketEntry en : list) {
                int owned = plugin.getMarketService().countItems(p, en);
                if (owned > 0) { total += plugin.getMarketService().prices().getSellPrice(en, p) * owned; count += owned; }
            }
            if (count == 0) { p.sendMessage("§7Нет предметов для продажи."); return; }
            MarketGui.openSellAllConfirm(plugin, p, catKey, total, count);
            return;
        }

        // Поиск
        if (hasPdc(meta, "mkt_search")) {
            p.closeInventory();
            p.sendMessage("§e🔍 Введи название предмета в чат (или 'отмена' для выхода):");
            plugin.getSearchPrompt().put(p.getUniqueId(), "");
            return;
        }

        // Кастомное количество
        if (hasPdc(meta, "mkt_amount")) {
            String catFromTitle = getCategoryFromTitle(title);
            amountPrompt.put(p.getUniqueId(), catFromTitle);
            p.closeInventory();
            p.sendMessage("§e✎ Кликни по табличке и напиши количество на ПЕРВОЙ строке.");
            p.sendMessage("§7Затем кликни предмет в маркете СРЕДНЕЙ кнопкой мыши.");
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
        String catFromTitle = getCategoryFromTitle(title);

        // Средняя кнопка = кастомное количество
        if (click == ClickType.MIDDLE || click == ClickType.CREATIVE) {
            handleCustomAmount(p, entry, catFromTitle);
            return;
        }

        if (click == ClickType.LEFT)         trade(p, "sell", entry, 1, catFromTitle);
        else if (click == ClickType.SHIFT_LEFT)  trade(p, "sell", entry, 64, catFromTitle);
        else if (click == ClickType.RIGHT)       trade(p, "buy", entry, 1, catFromTitle);
        else if (click == ClickType.SHIFT_RIGHT) trade(p, "buy", entry, 16, catFromTitle);
    }

    private void handleCustomAmount(Player p, MarketEntry entry, String catFromTitle) {
        String catStr = amountPrompt.remove(p.getUniqueId());
        if (catStr == null) {
            p.sendMessage("§c❌ Сначала нажми §e✎ Кол-во §cв нижнем ряду, затем среднюю кнопку на товаре.");
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
            p.sendMessage("§a✓ Куплено " + amount + "x " + entry.displayName() + " §aза §e" + total + " реп.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else {
            int owned = svc.countItems(p, entry);
            int toSell = Math.min(amount, owned);
            if (toSell <= 0) { p.sendMessage("§c❌ Нет предметов!"); return; }
            int price = svc.prices().getSellPrice(entry, p);
            int total = price * toSell;
            svc.takeItems(p, entry, toSell);
            VKChatBridge.addPoints(vkId, total);
            svc.prices().recordSell(entry, toSell);
            p.sendMessage("§a✓ Продано " + toSell + "x " + entry.displayName() + " §aза §e" + total + " реп.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }

        int page = getPageFromTitle(p.getOpenInventory().getTitle());
        MarketGui.openCategory(plugin, p, catFromTitle, page);
    }

    // ═══ ЧАТ-ВВОД (поиск и кастомное количество) ═══

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // Поиск
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
                    p.sendMessage("§7Ничего не найдено по запросу: §f" + q);
                    MarketGui.openMainMenu(plugin, p);
                } else {
                    p.sendMessage("§aНайдено: §f" + results.size() + " §aтоваров.");
                    MarketGui.openSearchResults(plugin, p, results, q);
                }
            });
            return;
        }

        // Кастомное количество
        if (plugin.getCustomAmountPrompt().containsKey(id)) {
            e.setCancelled(true);
            String data = plugin.getCustomAmountPrompt().remove(id);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("отмена") || msg.isEmpty()) {
                String cat = data.contains("|") ? data.split("\\|")[1] : "all";
                final String c = cat;
                Bukkit.getScheduler().runTask(plugin, () -> MarketGui.openCategory(plugin, p, c, 0));
                return;
            }
            int amount;
            try { amount = Integer.parseInt(msg); } catch (NumberFormatException ex) {
                p.sendMessage("§c❌ Введи число!");
                plugin.getCustomAmountPrompt().put(id, data);
                return;
            }
            if (amount <= 0) { p.sendMessage("§c❌ Число должно быть > 0!"); return; }
            String[] parts = data.split("\\|");
            String itemId = parts[0];
            String catFromTitle = parts.length > 1 ? parts[1] : "all";
            MarketEntry entry = plugin.getMarketService().get(itemId);
            if (entry == null) { p.sendMessage("§c❌ Товар не найден!"); return; }
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (vkId == -1 && !VKChatBridge.hasPass(p)) { p.sendMessage("§c❌ Привяжи ВК!"); return; }

            // Определяем покупка или продажа по контексту
            // По умолчанию — продажа (чаще используется)
            final String fcat = catFromTitle;
            final int famount = amount;
            Bukkit.getScheduler().runTask(plugin, () -> trade(p, "sell", entry, famount, fcat));
        }
    }

    // ═══ ВСПОМОГАТЕЛЬНЫЕ ═══

    private boolean hasPdc(ItemMeta meta, String key) {
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, key), PersistentDataType.BYTE);
    }

    private String getPdcString(ItemMeta meta, String rawKey) {
        // sellall_confirm key is composite: mkt_sellall_confirm§ores
        for (NamespacedKey nk : meta.getPersistentDataContainer().getKeys()) {
            if (nk.getKey().startsWith("mkt_sellall_confirm")) {
                String full = nk.getKey();
                if (full.length() > "mkt_sellall_confirm".length()) {
                    return full.substring("mkt_sellall_confirm".length());
                }
            }
        }
        return "all";
    }

    private String getCategoryFromTitle(String title) {
        if (!title.contains("◂ §7")) return "all";
        String part = title.substring(title.indexOf("◂ §7") + 5);
        if (part.contains(" §8")) part = part.substring(0, part.indexOf(" §8"));
        if (part.contains("Поиск:")) return "search";
        String trimmed = part.trim();
        for (MarketCategory cat : MarketCategory.values()) {
            String name = plugin.getConfig().getString("categories." + cat.configKey() + ".name", "");
            if (trimmed.startsWith(name) || trimmed.equals(name)) return cat.configKey();
        }
        if (trimmed.equals("Все товары")) return "all";
        if (trimmed.startsWith("Меню")) return "menu";
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
