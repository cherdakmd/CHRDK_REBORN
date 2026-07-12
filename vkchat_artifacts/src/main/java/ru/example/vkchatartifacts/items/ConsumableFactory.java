package ru.example.vkchatartifacts.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;

import java.util.ArrayList;
import java.util.List;

public class ConsumableFactory {

    public static ItemStack generateCleanseScroll(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + " Свиток Очищения");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Древний магический свиток.");
        lore.add(ChatColor.GRAY + "Позволяет снять Проклятие с артефакта.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Шанс успеха: " + plugin.getConfig().getInt("consumables.cleanse.success-chance", 50) + "%");
        lore.add(ChatColor.RED + "Шанс поломки артефакта: " + plugin.getConfig().getInt("consumables.cleanse.break-chance", 30) + "%");
        lore.add("");
        lore.add(ChatColor.AQUA + "▶ Положите артефакт в левую руку");
        lore.add(ChatColor.AQUA + "▶ Возьмите свиток в правую руку и нажмите ПКМ");
        
        meta.setLore(lore);
        meta.setCustomModelData(40);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "CLEANSE");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateReviveScroll(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + " Тотем Крови (Воскрешение)");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Мощный артефакт из плоти и крови.");
        lore.add(ChatColor.GRAY + "Спасет вас от верной гибели один раз,");
        lore.add(ChatColor.GRAY + "восстановив полное здоровье.");
        lore.add("");
        lore.add(ChatColor.RED + "☠ Сгорает при использовании.");
        lore.add(ChatColor.DARK_GRAY + "Нужно держать в любой руке.");
        
        meta.setLore(lore);
        meta.setCustomModelData(41);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "REVIVE");
        
        item.setItemMeta(meta);
        return item;
    }
    
    public static ItemStack generateEscapeScroll(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + " Сфера Побега");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Сфера, наполненная магией Энда.");
        lore.add(ChatColor.GRAY + "При активации телепортирует вас на");
        lore.add(ChatColor.GRAY + "точку дома (/home) через 3 секунды.");
        lore.add("");
        lore.add(ChatColor.AQUA + "▶ Нажмите ПКМ, чтобы активировать.");
        
        meta.setLore(lore);
        meta.setCustomModelData(42);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "ESCAPE");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateEnchantmentScroll(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + " Свиток Чар Усиления");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Магический свиток, усиливающий все баффы");
        lore.add(ChatColor.GRAY + "артефактов на 50% в течение 10 минут.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Длительность: 10 минут");
        lore.add(ChatColor.AQUA + "▶ Нажмите ПКМ, чтобы активировать.");
        
        meta.setLore(lore);
        meta.setCustomModelData(43);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "ENCHANTMENT_SCROLL");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateRepairKit(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GRAY + "" + ChatColor.BOLD + " Ремонтный Набор");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Восстанавливает время жизни хрупкого");
        lore.add(ChatColor.GRAY + "артефакта на 24 часа.");
        lore.add("");
        lore.add(ChatColor.AQUA + "▶ Положите хрупкий артефакт в левую руку");
        lore.add(ChatColor.AQUA + "▶ Возьмите набор в правую руку и нажмите ПКМ");
        
        meta.setLore(lore);
        meta.setCustomModelData(44);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "REPAIR_KIT");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateExchangeRune(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + " Руна Обмена");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Древняя руна, перекатывающая тип баффа");
        lore.add(ChatColor.GRAY + "артефакта на случайный новый.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Шанс успеха: 100%");
        lore.add(ChatColor.AQUA + "▶ Положите артефакт в левую руку");
        lore.add(ChatColor.AQUA + "▶ Возьмите руну в правую руку и нажмите ПКМ");
        
        meta.setLore(lore);
        meta.setCustomModelData(45);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "EXCHANGE_RUNE");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateFortificationTotem(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + " Тотем Укрепления");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Делает артефакт привязанным к душе,");
        lore.add(ChatColor.GRAY + "не изменяя его проклятие.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Привязанные артефакты не выпадают при смерти.");
        lore.add(ChatColor.AQUA + "▶ Положите артефакт в левую руку");
        lore.add(ChatColor.AQUA + "▶ Возьмите тотем в правую руку и нажмите ПКМ");
        
        meta.setLore(lore);
        meta.setCustomModelData(46);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "FORTIFICATION_TOTEM");
        
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack generateDecayAntipode(VKChatArtifactsPlugin plugin) {
        ItemStack item = new ItemStack(Material.MILK_BUCKET);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + " Антидот Разложения");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Мощное противоядие, снимающее любое");
        lore.add(ChatColor.GRAY + "проклятие с артефакта со 100% успехом.");
        lore.add("");
        lore.add(ChatColor.GREEN + "Шанс успеха: 100%");
        lore.add(ChatColor.AQUA + "▶ Положите артефакт в левую руку");
        lore.add(ChatColor.AQUA + "▶ Возьмите антидот в правую руку и нажмите ПКМ");
        
        meta.setLore(lore);
        meta.setCustomModelData(47);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_consumable"), PersistentDataType.STRING, "DECAY_ANTIPODE");
        
        item.setItemMeta(meta);
        return item;
    }
}
