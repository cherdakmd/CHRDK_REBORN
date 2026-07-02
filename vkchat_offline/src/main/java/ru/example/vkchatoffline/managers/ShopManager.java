package ru.example.vkchatoffline.managers;

import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.PlayerData;
import ru.example.vkchatoffline.managers.ZoneData.*;

/**
 * Лавка — покупка ресурсов и сетовых частей за репутацию приключений
 */
public class ShopManager {
    private final VKChatOfflinePlugin plugin;
    private final PlayerData data;

    public ShopManager(VKChatOfflinePlugin plugin, PlayerData data) {
        this.plugin = plugin; this.data = data;
    }

    public void handleShop(int vkId, String[] args, AdventureManager mgr) {
        if (args.length < 3 || args[2].equals("main")) {
            String msg = "🛒 ЛАВКА ПРИКЛЮЧЕНИЙ\n\n"
                    + "💰 Ваш баланс приключений: " + data.getAdventureRep(vkId) + "\n\n"
                    + "🧪 Зелья — лечилки и баффы\n"
                    + "📦 Ресурсы — материалы для крафта\n"
                    + "🛡 Части сетов — экипировка";
            mgr.sendWithKb(vkId, msg, Keyboards.shopMenu());
            return;
        }

        switch (args[2]) {
            case "potions":
                showPotions(vkId, mgr);
                break;
            case "resources":
                showResources(vkId, mgr);
                break;
            case "pieces":
                showPieces(vkId, mgr);
                break;
            case "buy":
                if (args.length >= 4) buyItem(vkId, args[3], mgr, args);
                break;
            default:
                handleShop(vkId, new String[]{"adv", "shop", "main"}, mgr);
        }
    }

    private void showPotions(int vkId, AdventureManager mgr) {
        String msg = "🧪 ЗЕЛЬЯ\n\n"
                + "💚 Малое зелье (+25% HP) — 50 реп\n"
                + "❤ Среднее зелье (+50% HP) — 100 реп\n"
                + "💜 Эликсир энергии (полная) — 80 реп\n"
                + "🛡 Зелье защиты (+3 DEF на 3 хода) — 60 реп\n\n"
                + "Купить: !adv shop buy <название>";
        mgr.sendWithKb(vkId, msg, Keyboards.shopBack());
    }

    private void showResources(int vkId, AdventureManager mgr) {
        String msg = "📦 РЕСУРСЫ\n\n";
        for (ResourceType rt : ResourceType.values()) {
            msg += rt.icon + " " + rt.name + " — " + rt.repValue * 5 + " реп\n";
        }
        msg += "\nКупить: !adv shop buy <название>";
        mgr.sendWithKb(vkId, msg, Keyboards.shopBack());
    }

    private void showPieces(int vkId, AdventureManager mgr) {
        String msg = "🛡 ЧАСТИ СЕТОВ (по зонам)\n\n";
        int level = data.getLevel(vkId);
        for (Zone zone : Zone.values()) {
            if (level < zone.difficulty) continue;
            msg += zone.icon + " " + zone.name + ":\n";
            for (SetPiece p : SetPiece.values()) {
                if (p.zone == zone) {
                    boolean owned = data.hasPiece(vkId, p.name);
                    msg += (owned ? "  ✅ " : "  ⬜ ") + p.name + " — " + p.repCost + " реп\n";
                }
            }
        }
        msg += "\nКупить: !adv shop buy <название части>";
        mgr.sendWithKb(vkId, msg, Keyboards.shopBack());
    }

    private void buyItem(int vkId, String item, AdventureManager mgr, String[] args) {
        // Поиск по зельям
        int cost = 0;
        String name = "";

        switch (item.toLowerCase()) {
            case "малое": case "small": cost = 50; name = "Малое зелье"; break;
            case "среднее": case "medium": cost = 100; name = "Среднее зелье"; break;
            case "эликсир": case "energy": cost = 80; name = "Эликсир энергии"; break;
            case "защиты": case "defense": cost = 60; name = "Зелье защиты"; break;
        }

        if (cost == 0) {
            // Поиск по ресурсам
            for (ResourceType rt : ResourceType.values()) {
                if (rt.name().toLowerCase().startsWith(item.toLowerCase())) {
                    cost = rt.repValue * 5;
                    name = rt.name;
                    break;
                }
            }
        }

        if (cost == 0) {
            // Поиск по сетам
            for (SetPiece p : SetPiece.values()) {
                if (p.name.toLowerCase().contains(item.toLowerCase())) {
                    if (data.hasPiece(vkId, p.name)) {
                        mgr.sendMsg(vkId, "❌ У вас уже есть эта часть сета!");
                        return;
                    }
                    cost = p.repCost;
                    name = p.name;
                    break;
                }
            }
        }

        if (cost == 0) {
            mgr.sendMsg(vkId, "❌ Товар не найден: " + item);
            return;
        }

        int balance = data.getAdventureRep(vkId);
        if (balance < cost) {
            mgr.sendMsg(vkId, "❌ Недостаточно репы! Нужно " + cost + ", есть " + balance);
            return;
        }

        data.addAdventureRep(vkId, -cost);

        // Покупка сетовой части
        for (SetPiece p : SetPiece.values()) {
            if (p.name.equals(name)) {
                data.addPiece(vkId, p.name);
                java.util.UUID uuid = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
                if (uuid != null) {
                    org.bukkit.inventory.ItemStack itemStack = new org.bukkit.inventory.ItemStack(p.material, 1);
                    org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§d✦ " + p.name + " §7[" + p.setId + "]");
                        itemStack.setItemMeta(meta);
                    }
                    plugin.getStashManager().addItems(uuid, java.util.Collections.singletonList(itemStack));
                }
                mgr.sendWithKb(vkId, "✅ Куплено: " + p.name + " за " + cost + " реп. Отправлено в /stash!", Keyboards.shopBack());
                return;
            }
        }

        // Покупка ресурса
        for (ResourceType rt : ResourceType.values()) {
            if (rt.name().equals(name)) {
                data.addResource(vkId, rt.name(), 1);
                java.util.UUID uuid = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
                if (uuid != null) {
                    plugin.getStashManager().addItems(uuid,
                            java.util.Collections.singletonList(new org.bukkit.inventory.ItemStack(rt.material, 1)));
                }
                mgr.sendWithKb(vkId, "✅ Куплено: " + rt.icon + " " + rt.name + " за " + cost + " реп. В /stash!", Keyboards.shopBack());
                return;
            }
        }

        mgr.sendWithKb(vkId, "✅ Куплено: " + name + " за " + cost + " реп!", Keyboards.shopBack());
    }

    public void handleConfirm(int vkId, String action, AdventureManager mgr) {
        mgr.sendWithKb(vkId, "✅ Действие подтверждено.", Keyboards.shopBack());
    }
}
