package ru.example.vkchatoffline.managers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Каталог ВК-магазина оффлайн-походов.
 *
 * Вынесен из AdventureManager как первый безопасный шаг рефакторинга Offline 2.0.
 * Класс не хранит состояние игрока: только id, названия, цены и бонусы предметов.
 */
public final class OfflineShopCatalog {
    private OfflineShopCatalog() {}

    public static class ShopItem {
        public final String id;
        public final String name;
        public final String type;
        public final String slot;
        public final int price;
        public final int hp;
        public final int check;
        public final int armor;
        public final int risk;
        public final int supplies;
        public final int rep;

        public ShopItem(String id, String name, String type, String slot, int price, int hp, int check, int armor, int risk, int supplies, int rep) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.slot = slot;
            this.price = price;
            this.hp = hp;
            this.check = check;
            this.armor = armor;
            this.risk = risk;
            this.supplies = supplies;
            this.rep = rep;
        }
    }

    public static Map<String, ShopItem> items() {
        Map<String, ShopItem> m = new LinkedHashMap<>();
        m.put("equip_weapon_iron", new ShopItem("equip_weapon_iron", "🗡 Железный меч походника", "equipment", "weapon", 500, 0, 1, 0, 0, 0, 0));
        m.put("equip_armor_chain", new ShopItem("equip_armor_chain", "🛡 Кольчужная броня походника", "equipment", "armor", 650, 10, 0, 3, 0, 0, 0));
        m.put("equip_talisman_sanity", new ShopItem("equip_talisman_sanity", "🔮 Талисман ясного разума", "equipment", "talisman", 900, 0, 2, 0, 1, 0, 0));
        m.put("equip_tool_lockpick", new ShopItem("equip_tool_lockpick", "🗝 Набор отмычек", "equipment", "tool", 750, 0, 2, 0, 0, 0, 5));
        m.put("equip_backpack_big", new ShopItem("equip_backpack_big", "🎒 Большой рюкзак", "equipment", "backpack", 800, 0, 0, 0, 0, 3, 0));

        m.put("potion_heal", new ShopItem("potion_heal", "❤️ Зелье лечения", "consumable", "", 150, 0, 0, 0, 0, 0, 0));
        m.put("potion_sanity", new ShopItem("potion_sanity", "🧠 Зелье рассудка", "consumable", "", 180, 0, 0, 0, 0, 0, 0));
        m.put("potion_antidote", new ShopItem("potion_antidote", "☠ Антидот", "consumable", "", 140, 0, 0, 0, 0, 0, 0));
        m.put("scroll_escape", new ShopItem("scroll_escape", "📜 Свиток побега", "consumable", "", 450, 0, 0, 0, 0, 0, 0));
        m.put("scroll_reroll", new ShopItem("scroll_reroll", "🎲 Свиток переброса", "consumable", "", 350, 0, 0, 0, 0, 0, 0));
        m.put("scroll_cleanse", new ShopItem("scroll_cleanse", "🕯 Свиток очищения", "consumable", "", 300, 0, 0, 0, 0, 0, 0));
        m.put("camp_kit", new ShopItem("camp_kit", "⛺ Набор лагеря", "consumable", "", 220, 0, 0, 0, 0, 0, 0));
        return m;
    }
}
