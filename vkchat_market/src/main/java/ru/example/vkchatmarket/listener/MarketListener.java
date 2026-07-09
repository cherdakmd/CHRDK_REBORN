package ru.example.vkchatmarket.listener;

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
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchatmarket.service.MarketService;

public class MarketListener implements Listener {
    private final VKChatMarketPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey catKey;
    private final NamespacedKey navKey;

    public MarketListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "mkt_item");
        this.catKey = new NamespacedKey(plugin, "mkt_cat");
        this.navKey = new NamespacedKey(plugin, "mkt_nav");
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

        // Категория
        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) MarketGui.openMainMenu(plugin, p);
            else MarketGui.openCategory(plugin, p, cat, 0);
            return;
        }

        // Навигация
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            int page = meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER);
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            MarketGui.openCategory(plugin, p, cat != null ? cat : "all", page);
            return;
        }

        // Торговля
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        MarketService svc = plugin.getMarketService();
        MarketEntry entry = svc.get(itemId);
        if (entry == null) return;

        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1 && !VKChatBridge.hasPass(p)) {
            p.sendMessage("§c❌ Привяжи ВК: /vklink");
            return;
        }

        ClickType click = e.getClick();
        String catFromTitle = getCategoryFromTitle(title);

        if (click == ClickType.LEFT) {
            sell(p, svc, entry, 1);
        } else if (click == ClickType.SHIFT_LEFT) {
            sell(p, svc, entry, 64);
        } else if (click == ClickType.RIGHT) {
            buy(p, svc, entry, 1);
        } else if (click == ClickType.SHIFT_RIGHT) {
            buy(p, svc, entry, 16);
        } else {
            return;
        }

        int page = getPageFromTitle(title);
        MarketGui.openCategory(plugin, p, catFromTitle, page);
    }

    private void buy(Player p, MarketService svc, MarketEntry entry, int amount) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        int price = svc.prices().getBuyPrice(entry);
        int total = price * amount;
        int rep = VKChatBridge.getReputation(vkId);

        if (rep < total) {
            p.sendMessage("§c❌ Нужно " + total + " реп. (у тебя " + rep + ")");
            return;
        }
        VKChatBridge.takeReputation(vkId, total);
        svc.giveItems(p, entry, amount);
        p.sendMessage("§a✓ Куплено " + amount + "x " + entry.displayName() + " §aза §e" + total + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void sell(Player p, MarketService svc, MarketEntry entry, int amount) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        int owned = svc.countItems(p, entry);
        int toSell = Math.min(amount, owned);
        if (toSell <= 0) {
            p.sendMessage("§c❌ Нет предметов для продажи!");
            return;
        }
        int price = svc.prices().getSellPrice(entry);
        int total = price * toSell;
        svc.takeItems(p, entry, toSell);
        VKChatBridge.addPoints(vkId, total);
        p.sendMessage("§a✓ Продано " + toSell + "x " + entry.displayName() + " §aза §e" + total + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private String getCategoryFromTitle(String title) {
        if (!title.contains("◂ §7")) return "all";
        String part = title.substring(title.indexOf("◂ §7") + 5);
        if (part.contains(" §8")) part = part.substring(0, part.indexOf(" §8"));
        return switch (part.trim()) {
            case "Руды/слитки" -> "ores";
            case "Еда и ферма" -> "food";
            case "Дерево" -> "wood";
            case "Стройматериалы" -> "blocks";
            case "Лут мобов" -> "mobs";
            case "Декор" -> "decor";
            case "Все товары" -> "all";
            default -> "all";
        };
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
