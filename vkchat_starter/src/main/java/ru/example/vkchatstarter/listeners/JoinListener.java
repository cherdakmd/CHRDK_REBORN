package ru.example.vkchatstarter.listeners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatstarter.VKChatStarterPlugin;

import java.util.ArrayList;
import java.util.List;

public class JoinListener implements Listener {
    private final VKChatStarterPlugin plugin;

    public JoinListener(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        Player p = e.getPlayer();
        
        // Проверяем, заходил ли игрок ранее на сервер
        if (!p.hasPlayedBefore()) {
            
            // Выдача брони - ПОЛНОСТЬЮ УДАЛЕНО по запросу (есть национальная броня)

            // Выдача предметов
            ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("settings.items");
            if (itemsSec != null) {
                for (String key : itemsSec.getKeys(false)) {
                    ItemStack item = buildItem(itemsSec.getConfigurationSection(key));
                    if (item != null) {
                        p.getInventory().addItem(item);
                    }
                }
            }

            // Выдача ДВУХ случайных кастомных артефактов при первом заходе!
            try {
                org.bukkit.plugin.Plugin artPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
                if (artPlugin != null && artPlugin.isEnabled()) {
                    ru.example.vkchatartifacts.VKChatArtifactsPlugin vka = (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artPlugin;
                    ItemStack art1 = ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(vka, false);
                    ItemStack art2 = ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(vka, false);
                    if (art1 != null) p.getInventory().addItem(art1);
                    if (art2 != null) p.getInventory().addItem(art2);
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Вам выдано два Древних Стартовых Артефакта! Держите их в инвентаре для получения бонусов.");
                }
            } catch (Throwable ignored) {}
            
            // Выдача переписанной Книги-Руководства
            ItemStack guideBook = new ItemStack(Material.WRITTEN_BOOK);
            org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) guideBook.getItemMeta();
            
            if (bookMeta != null) {
                bookMeta.setTitle(org.bukkit.ChatColor.GOLD + "Книга Скитальца");
                bookMeta.setAuthor(org.bukkit.ChatColor.RED + "Администрация");
                
                List<String> pages = new ArrayList<>();
                
                // Страница 1: Добро пожаловать
                pages.add(org.bukkit.ChatColor.DARK_BLUE + "" + org.bukkit.ChatColor.BOLD + "★ CHRDK REBORN ★\n\n" + 
                          org.bukkit.ChatColor.BLACK + "Приветствуем тебя в нашем уникальном RPG-мире!\n\n" +
                          "Первым делом напиши команду:\n" + org.bukkit.ChatColor.BLUE + "/vklink\n\n" + 
                          org.bukkit.ChatColor.BLACK + "Связка с ВК подарит тебе " + org.bukkit.ChatColor.GOLD + "1000 стартовой репутации ВК" + org.bukkit.ChatColor.BLACK + "!");
                
                // Страница 2: Нации и Приваты
                pages.add(org.bukkit.ChatColor.DARK_GREEN + "" + org.bukkit.ChatColor.BOLD + "⛺ БЛОЧНЫЕ ПРИВАТЫ\n\n" + 
                          org.bukkit.ChatColor.BLACK + "Приваты чанками упразднены! Теперь приваты ставятся через блоки привата (Золотой, Изумрудный, Алмазный).\n" +
                          "Купить блоки можно в " + org.bukkit.ChatColor.BLUE + "/n buyclaim\n\n" +
                          org.bukkit.ChatColor.BLACK + "Питай свой приват ресурсами или репутацией через " + org.bukkit.ChatColor.BLUE + "/n feed" + org.bukkit.ChatColor.BLACK + "!");
                
                // Страница 3: Заточка и Пробуждение
                pages.add(org.bukkit.ChatColor.DARK_PURPLE + "" + org.bukkit.ChatColor.BOLD + "🛡️ ЭВОЛЮЦИЯ & РУНЫ\n\n" + 
                          org.bukkit.ChatColor.BLACK + "Усиливай снаряжение через кузню и руны до " + org.bukkit.ChatColor.DARK_RED + "+20" + org.bukkit.ChatColor.BLACK + "!\n" +
                          "Покупай Кристаллы Заточки и Руны на бирже рун " + org.bukkit.ChatColor.BLUE + "/runes" + org.bukkit.ChatColor.BLACK + ".\n\n" +
                          "Цены на бирже рун динамические и зависят от спроса игроков!");
                
                // Страница 4: Личные Мутации
                pages.add(org.bukkit.ChatColor.DARK_AQUA + "" + org.bukkit.ChatColor.BOLD + "🧬 ЛИЧНЫЕ МУТАЦИИ\n\n" + 
                          org.bukkit.ChatColor.BLACK + "В меню " + org.bukkit.ChatColor.BLUE + "/nation" + org.bukkit.ChatColor.BLACK + " доступна Лаборатория Мутаций.\n\n" +
                          "Каждый игрок может купить лично для себя 5 мощных пассивных/активных мутаций своей нации по цене " + org.bukkit.ChatColor.GOLD + "1500 репутации" + org.bukkit.ChatColor.BLACK + " за каждую!");
                
                // Страница 5: Экономика и Сфинкс
                pages.add(org.bukkit.ChatColor.GOLD + "" + org.bukkit.ChatColor.BOLD + "💰 РЫНОК И ПОХОДЫ\n\n" + 
                          org.bukkit.ChatColor.BLACK + "* Продавай 16 видов ресурсов на динамической бирже " + org.bukkit.ChatColor.BLUE + "/shop" + org.bukkit.ChatColor.BLACK + ".\n" +
                          "* Запускай оффлайн-походы в беседе ВК командой " + org.bukkit.ChatColor.BLUE + "!поход" + org.bukkit.ChatColor.BLACK + ".\n\n" +
                          org.bukkit.ChatColor.DARK_GRAY + "Группа ВКонтакте:\n" + org.bukkit.ChatColor.BLUE + "vk.com/chrdk_reborn");
                
                bookMeta.setPages(pages);
                guideBook.setItemMeta(bookMeta);
                p.getInventory().addItem(guideBook);
            }

            String msg = plugin.getConfig().getString("settings.welcome-message");
            if (msg != null && !msg.isEmpty()) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            }
            
            p.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "starter_quest_stage"), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            p.sendTitle(org.bukkit.ChatColor.GOLD + "Обучение", org.bukkit.ChatColor.YELLOW + "Задание: Срубить 5 дерева", 20, 100, 20);
        }
    }

    private ItemStack buildItem(ConfigurationSection section) {
        if (section == null) return null;
        String matStr = section.getString("material", "AIR");
        if (matStr.equals("AIR")) return null;
        
        Material m;
        try {
            m = Material.valueOf(matStr.toUpperCase());
        } catch (Exception ex) {
            return null;
        }
        
        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(m, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        if (section.contains("name")) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', section.getString("name")));
        }
        
        if (section.contains("lore")) {
            List<String> lore = new ArrayList<>();
            for (String l : section.getStringList("lore")) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', l));
            }
            meta.setLore(lore);
        }
        
        if (section.contains("enchants")) {
            for (String enchStr : section.getStringList("enchants")) {
                String[] parts = enchStr.split(";");
                if (parts.length == 2) {
                    try {
                        org.bukkit.enchantments.Enchantment e = org.bukkit.enchantments.Enchantment.getByName(parts[0].toUpperCase());
                        int lvl = Integer.parseInt(parts[1]);
                        if (e != null) {
                            meta.addEnchant(e, lvl, true);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
}
