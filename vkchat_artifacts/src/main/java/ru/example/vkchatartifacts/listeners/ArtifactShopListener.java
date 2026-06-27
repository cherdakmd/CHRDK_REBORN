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
    public static final String SHOP_TITLE = ChatColor.GOLD + "✨ Рынок Древних Артефактов";

    public ArtifactShopListener(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void openShop(VKChatArtifactsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, SHOP_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // 1. Случайный древний артефакт
        ItemStack art = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta artMeta = art.getItemMeta();
        artMeta.setDisplayName(ChatColor.YELLOW + "🏺 Случайный Древний Артефакт");
        List<String> artLore = new ArrayList<>();
        artLore.add(ChatColor.GRAY + "Загадочный артефакт с глубин океана.");
        artLore.add(ChatColor.GRAY + "Содержит 1 случайный бафф до III уровня");
        artLore.add(ChatColor.GRAY + "и одно случайное проклятие.");
        artLore.add("");
        artLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "2500 репутации ВК");
        artLore.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        artMeta.setLore(artLore);
        artMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "normal");
        artMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 2500);
        art.setItemMeta(artMeta);

        // 2. Случайная мифическая реликвия
        ItemStack relic = new ItemStack(Material.NETHER_STAR);
        ItemMeta relicMeta = relic.getItemMeta();
        relicMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Случайная Мифическая Реликвия");
        List<String> relicLore = new ArrayList<>();
        relicLore.add(ChatColor.GRAY + "Священная вещь, источающая чистый свет.");
        relicLore.add(ChatColor.GRAY + "Содержит гарантированный бафф V уровня,");
        relicLore.add(ChatColor.GRAY + "НЕ содержит проклятий и привязана к душе");
        relicLore.add(ChatColor.GRAY + "(не выпадает при смерти!).");
        relicLore.add("");
        relicLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "7500 репутации ВК");
        relicLore.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        relicMeta.setLore(relicLore);
        relicMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "relic");
        relicMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 7500);
        relic.setItemMeta(relicMeta);

        // 3. Свиток Очищения
        ItemStack scroll1 = new ItemStack(Material.PAPER);
        ItemMeta sMeta1 = scroll1.getItemMeta();
        sMeta1.setDisplayName(ChatColor.GREEN + "📜 Свиток Очищения Артефактов");
        List<String> sLore1 = new ArrayList<>();
        sLore1.add(ChatColor.GRAY + "Позволяет попытаться снять проклятие");
        sLore1.add(ChatColor.GRAY + "с артефакта, помещенного в левую руку.");
        sLore1.add("");
        sLore1.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "1000 репутации ВК");
        sLore1.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta1.setLore(sLore1);
        sMeta1.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "cleanse");
        sMeta1.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 1000);
        scroll1.setItemMeta(sMeta1);

        // 4. Сфера Побега
        ItemStack scroll2 = new ItemStack(Material.ENDER_EYE);
        ItemMeta sMeta2 = scroll2.getItemMeta();
        sMeta2.setDisplayName(ChatColor.GREEN + "🔮 Сфера Срочного Побега");
        List<String> sLore2 = new ArrayList<>();
        sLore2.add(ChatColor.GRAY + "Позволяет экстренно телепортироваться");
        sLore2.add(ChatColor.GRAY + "домой при активации в руках.");
        sLore2.add("");
        sLore2.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "800 репутации ВК");
        sLore2.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta2.setLore(sLore2);
        sMeta2.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "escape");
        sMeta2.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 800);
        scroll2.setItemMeta(sMeta2);

        // 5. Тотем Крови
        ItemStack scroll3 = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta sMeta3 = scroll3.getItemMeta();
        sMeta3.setDisplayName(ChatColor.RED + "🧛 Тотем Крови (Бессмертие)");
        List<String> sLore3 = new ArrayList<>();
        sLore3.add(ChatColor.GRAY + "Усиленный Тотем Бессмертия, полностью");
        sLore3.add(ChatColor.GRAY + "восстанавливающий ХП при активации.");
        sLore3.add("");
        sLore3.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "2000 репутации ВК");
        sLore3.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta3.setLore(sLore3);
        sMeta3.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "revive");
        sMeta3.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 2000);
        scroll3.setItemMeta(sMeta3);

        // 6. Свиток Чар Усиления
        ItemStack scroll4 = new ItemStack(Material.BOOK);
        ItemMeta sMeta4 = scroll4.getItemMeta();
        sMeta4.setDisplayName(ChatColor.AQUA + "📖 Свиток Чар Усиления");
        List<String> sLore4 = new ArrayList<>();
        sLore4.add(ChatColor.GRAY + "Усиливает все баффы артефактов");
        sLore4.add(ChatColor.GRAY + "на 50% в течение 10 минут.");
        sLore4.add("");
        sLore4.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "3000 репутации ВК");
        sLore4.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta4.setLore(sLore4);
        sMeta4.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "enchant_scroll");
        sMeta4.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 3000);
        scroll4.setItemMeta(sMeta4);

        // 7. Ремонтный Набор
        ItemStack scroll5 = new ItemStack(Material.ANVIL);
        ItemMeta sMeta5 = scroll5.getItemMeta();
        sMeta5.setDisplayName(ChatColor.GRAY + "🔨 Ремонтный Набор");
        List<String> sLore5 = new ArrayList<>();
        sLore5.add(ChatColor.GRAY + "Восстанавливает время жизни");
        sLore5.add(ChatColor.GRAY + "хрупкого артефакта на 24 часа.");
        sLore5.add("");
        sLore5.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "1500 репутации ВК");
        sLore5.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta5.setLore(sLore5);
        sMeta5.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "repair_kit");
        sMeta5.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 1500);
        scroll5.setItemMeta(sMeta5);

        // 8. Руна Обмена
        ItemStack scroll6 = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta sMeta6 = scroll6.getItemMeta();
        sMeta6.setDisplayName(ChatColor.DARK_PURPLE + "🔮 Руна Обмена");
        List<String> sLore6 = new ArrayList<>();
        sLore6.add(ChatColor.GRAY + "Перекатывает тип баффа артефакта");
        sLore6.add(ChatColor.GRAY + "на случайный новый.");
        sLore6.add("");
        sLore6.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "2000 репутации ВК");
        sLore6.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta6.setLore(sLore6);
        sMeta6.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "exchange_rune");
        sMeta6.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 2000);
        scroll6.setItemMeta(sMeta6);

        // 9. Тотем Укрепления
        ItemStack scroll7 = new ItemStack(Material.BEACON);
        ItemMeta sMeta7 = scroll7.getItemMeta();
        sMeta7.setDisplayName(ChatColor.GOLD + "🛡 Тотем Укрепления");
        List<String> sLore7 = new ArrayList<>();
        sLore7.add(ChatColor.GRAY + "Делает артефакт привязанным к душе");
        sLore7.add(ChatColor.GRAY + "(не выпадает при смерти).");
        sLore7.add("");
        sLore7.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "5000 репутации ВК");
        sLore7.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta7.setLore(sLore7);
        sMeta7.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "fort_totem");
        sMeta7.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 5000);
        scroll7.setItemMeta(sMeta7);

        // 10. Антидот Разложения
        ItemStack scroll8 = new ItemStack(Material.MILK_BUCKET);
        ItemMeta sMeta8 = scroll8.getItemMeta();
        sMeta8.setDisplayName(ChatColor.GREEN + "🧪 Антидот Разложения");
        List<String> sLore8 = new ArrayList<>();
        sLore8.add(ChatColor.GRAY + "Снимает любое проклятие");
        sLore8.add(ChatColor.GRAY + "с артефакта со 100% успехом.");
        sLore8.add("");
        sLore8.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "2500 репутации ВК");
        sLore8.add(ChatColor.YELLOW + "▶ Нажмите для покупки ◀");
        sMeta8.setLore(sLore8);
        sMeta8.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_type"), PersistentDataType.STRING, "decay_antipode");
        sMeta8.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_artifact_cost"), PersistentDataType.INTEGER, 2500);
        scroll8.setItemMeta(sMeta8);

        inv.setItem(10, art);
        inv.setItem(12, relic);
        inv.setItem(14, scroll1);
        inv.setItem(15, scroll2);
        inv.setItem(16, scroll3);
        inv.setItem(28, scroll4);
        inv.setItem(30, scroll5);
        inv.setItem(31, scroll6);
        inv.setItem(32, scroll7);
        inv.setItem(34, scroll8);

        p.openInventory(inv);
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
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте! (/vklink)");
                return;
            }

            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " (Ваш баланс: " + rep + ").");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return;
            }

            // Списываем репутацию
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

            ItemStack itemToGive = null;
            if (type.equals("normal")) {
                itemToGive = ArtifactFactory.generateArtifact(plugin, false);
            } else if (type.equals("relic")) {
                itemToGive = ArtifactFactory.generateArtifact(plugin, true);
            } else if (type.equals("cleanse")) {
                itemToGive = ConsumableFactory.generateCleanseScroll(plugin);
            } else if (type.equals("escape")) {
                itemToGive = ConsumableFactory.generateEscapeScroll(plugin);
            } else if (type.equals("revive")) {
                itemToGive = ConsumableFactory.generateReviveScroll(plugin);
            } else if (type.equals("enchant_scroll")) {
                itemToGive = ConsumableFactory.generateEnchantmentScroll(plugin);
            } else if (type.equals("repair_kit")) {
                itemToGive = ConsumableFactory.generateRepairKit(plugin);
            } else if (type.equals("exchange_rune")) {
                itemToGive = ConsumableFactory.generateExchangeRune(plugin);
            } else if (type.equals("fort_totem")) {
                itemToGive = ConsumableFactory.generateFortificationTotem(plugin);
            } else if (type.equals("decay_antipode")) {
                itemToGive = ConsumableFactory.generateDecayAntipode(plugin);
            }

            if (itemToGive != null) {
                p.getInventory().addItem(itemToGive).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                p.sendMessage(ChatColor.GREEN + "✓ Вы успешно приобрели " + item.getItemMeta().getDisplayName() + ChatColor.GREEN + " за " + ChatColor.GOLD + cost + " реп. ВК!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
        }
    }
}
