package ru.example.vkchatstarter.listeners;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatstarter.VKChatStarterPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Обработчик первого входа нового игрока.
 * Выдаёт стартовый набор, блок привата, артефакты, книгу и запускает квест.
 */
public class JoinListener implements Listener {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey starterKey;

    public JoinListener(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.starterKey = new NamespacedKey(plugin, "starter_received");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;
        Player p = e.getPlayer();

        // Только для новых игроков (или тех кто не получил набор)
        if (p.hasPlayedBefore() && p.getPersistentDataContainer().has(starterKey, PersistentDataType.INTEGER)) return;

        // Помечаем что набор получен
        p.getPersistentDataContainer().set(starterKey, PersistentDataType.INTEGER, 1);

        // === 1. ЗАЩИТА НОВОГО ИГРОКА ===
        int protectionSeconds = plugin.getConfig().getInt("settings.join-protection-seconds", 30);
        if (protectionSeconds > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, protectionSeconds * 20, 10, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, protectionSeconds * 20, 0, false, false));
            p.sendMessage(ChatColor.GREEN + "🛡 Ты защищён на " + protectionSeconds + " секунд!");
        }

        // === 2. ТЕЛЕПОРТ НА СПАВН ===
        if (plugin.getConfig().getBoolean("settings.teleport-to-spawn", true)) {
            Location spawn = p.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
            p.teleport(spawn);
        }

        // === 3. БЛОК ПРИВАТА (ОДИН) ===
        if (plugin.getConfig().getBoolean("settings.claim-block.enabled", true)) {
            ConfigurationSection claimSec = plugin.getConfig().getConfigurationSection("settings.claim-block");
            if (claimSec != null) {
                Material mat = Material.valueOf(claimSec.getString("material", "GOLD_BLOCK").toUpperCase());
                int radius = claimSec.getInt("radius", 8);
                String name = ChatColor.translateAlternateColorCodes('&', claimSec.getString("name", "&6&lБлок привата"));
                List<String> lore = new ArrayList<>();
                for (String line : claimSec.getStringList("lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                ItemStack claim = createClaimBlock(mat, name, lore, radius);
                p.getInventory().addItem(claim);
                p.sendMessage(ChatColor.GOLD + "🛡 Ты получил блок привата! Поставь его для защиты территории.");
            }
        }

        // === 4. СТАРТОВОЕ СНАРЯЖЕНИЕ ===
        giveEquipment(p, "settings.equipment.weapon");
        giveEquipment(p, "settings.equipment.pickaxe");
        giveEquipment(p, "settings.equipment.axe");
        giveEquipment(p, "settings.equipment.shovel");

        // === 5. СТАРТОВЫЕ РЕСУРСЫ ===
        giveResources(p);

        // === 6. БОНУС РЕПУТАЦИИ ===
        int starterRep = plugin.getConfig().getInt("settings.starter-reputation", 500);
        if (starterRep > 0) {
            try {
                int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    ru.example.vkchat.VKChatPlugin.getInstance().getApi().addReputation(vkId, starterRep);
                    p.sendMessage(ChatColor.GOLD + "✨ +" + starterRep + " репутации ВК за первый вход!");
                }
            } catch (Exception ignored) {}
        }

        // === 7. АРТЕФАКТЫ ===
        if (plugin.getConfig().getBoolean("settings.give-artifacts", true)) {
            giveArtifacts(p);
        }

        // === 8. КНИГА-РУКОВОДСТВО ===
        giveGuideBook(p);

        // === 9. ПРИВЕТСТВЕННОЕ СООБЩЕНИЕ ===
        String msg = plugin.getConfig().getString("settings.welcome-message");
        if (msg != null && !msg.isEmpty()) {
            p.sendMessage("");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            p.sendMessage("");
        }

        // Показ фич сервера
        p.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + "Фичи сервера:");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "/nation " + ChatColor.GRAY + "— выбор нации и приваты");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "/quest " + ChatColor.GRAY + "— обучение с наградами");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "/shop " + ChatColor.GRAY + "— торговля за репутацию");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "/runes " + ChatColor.GRAY + "— заточка предметов");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "!шахта " + ChatColor.GRAY + "в ВК — заработок офлайн");
        p.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GOLD + "/stash " + ChatColor.GRAY + "— забрать награды шахты");
        p.sendMessage("");

        // === 10. ИНИЦИАЛИЗАЦИЯ КВЕСТА ===
        initQuest(p);

        // === 11. ПОКАЗ ТАЙТЛА ===
        String firstQuestName = getFirstQuestName();
        p.sendTitle(
                ChatColor.GOLD + "★ CHRDK REBORN ★",
                ChatColor.YELLOW + "Первое задание: " + firstQuestName,
                20, 100, 20
        );

        // === 12. ЛОГИРОВАНИЕ ===
        plugin.getLogger().info("[Starter] Набор выдан игроку " + p.getName());
    }

    /**
     * Выдаёт предмет снаряжения из конфига.
     */
    private void giveEquipment(Player p, String configPath) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(configPath);
        if (sec == null) return;
        String matStr = sec.getString("material", "AIR");
        if (matStr.equals("AIR")) return;

        try {
            Material mat = Material.valueOf(matStr.toUpperCase());
            ItemStack item = new ItemStack(mat, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (sec.contains("name")) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', sec.getString("name")));
                }
                if (sec.contains("lore")) {
                    List<String> lore = new ArrayList<>();
                    for (String line : sec.getStringList("lore")) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    meta.setLore(lore);
                }
                if (sec.contains("enchants")) {
                    for (String ench : sec.getStringList("enchants")) {
                        String[] parts = ench.split(";");
                        if (parts.length == 2) {
                            try {
                                org.bukkit.enchantments.Enchantment e = org.bukkit.enchantments.Enchantment.getByName(parts[0].toUpperCase());
                                if (e != null) meta.addEnchant(e, Integer.parseInt(parts[1]), true);
                            } catch (Exception ignored) {}
                        }
                    }
                }
                item.setItemMeta(meta);
            }
            p.getInventory().addItem(item);
        } catch (Exception ignored) {}
    }

    /**
     * Выдаёт стартовые ресурсы из конфига.
     */
    private void giveResources(Player p) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("settings.resources");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec == null) continue;
            try {
                Material mat = Material.valueOf(itemSec.getString("material", "AIR").toUpperCase());
                int amount = itemSec.getInt("amount", 1);
                if (mat != Material.AIR) {
                    p.getInventory().addItem(new ItemStack(mat, amount));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Выдаёт случайные артефакты.
     */
    private void giveArtifacts(Player p) {
        try {
            org.bukkit.plugin.Plugin artPlugin = Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
            if (artPlugin != null && artPlugin.isEnabled()) {
                ru.example.vkchatartifacts.VKChatArtifactsPlugin vka = (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artPlugin;
                int count = plugin.getConfig().getInt("settings.artifact-count", 2);
                for (int i = 0; i < count; i++) {
                    ItemStack art = ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(vka, false);
                    if (art != null) p.getInventory().addItem(art);
                }
                p.sendMessage(ChatColor.GOLD + "✨ Ты получил " + count + " стартовых артефактов!");
            }
        } catch (Exception ignored) {}
    }

    /**
     * Выдаёт книгу-руководство.
     */
    private void giveGuideBook(Player p) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("guide-book");
        if (sec == null) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.setTitle(ChatColor.translateAlternateColorCodes('&', sec.getString("title", "Книга Скитальца")));
        meta.setAuthor(ChatColor.translateAlternateColorCodes('&', sec.getString("author", "Администрация")));

        List<String> pages = new ArrayList<>();
        for (String page : sec.getStringList("pages")) {
            pages.add(ChatColor.translateAlternateColorCodes('&', page));
        }
        if (!pages.isEmpty()) meta.setPages(pages);
        book.setItemMeta(meta);
        p.getInventory().addItem(book);
    }

    /**
     * Инициализирует квест нового игрока.
     */
    private void initQuest(Player p) {
        NamespacedKey stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        NamespacedKey progKey = new NamespacedKey(plugin, "starter_quest_progress");
        NamespacedKey deathKey = new NamespacedKey(plugin, "starter_quest_deaths");
        NamespacedKey startTimeKey = new NamespacedKey(plugin, "starter_quest_start_time");

        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, 0);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);
        p.getPersistentDataContainer().set(deathKey, PersistentDataType.INTEGER, 0);
        p.getPersistentDataContainer().set(startTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * Получает имя первого квеста из конфига.
     */
    private String getFirstQuestName() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("quest");
        if (sec != null) {
            List<Map<?, ?>> stages = sec.getMapList("stages");
            if (!stages.isEmpty()) {
                return (String) stages.get(0).get("name");
            }
        }
        return "Начни приключение!";
    }

    /**
     * Создаёт блок привата с PDC тегами для совместимости с VKChatNations.
     */
    private ItemStack createClaimBlock(Material material, String name, List<String> lore, int radius) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            // PDC тег для NationManager
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("vkchat_nations", "claim_block_radius"),
                    PersistentDataType.INTEGER, radius);
            item.setItemMeta(meta);
        }
        return item;
    }
}
