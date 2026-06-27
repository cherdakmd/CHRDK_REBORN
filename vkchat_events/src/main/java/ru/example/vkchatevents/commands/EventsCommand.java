package ru.example.vkchatevents.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventsCommand implements CommandExecutor, Listener {
    private final VKChatEventsPlugin plugin;
    private final String GUI_TITLE = ChatColor.GOLD + "📅 События и Квесты Сервера";

    public EventsCommand(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду могут выполнять только игроки!");
            return true;
        }

        openEventsDashboard((Player) sender);
        return true;
    }

    private String getCataclysmFriendlyName(String key) {
        if (key == null) return null;
        switch (key.toLowerCase()) {
            case "acid_rain": return "🌧️ Кислотный Дождь";
            case "earthquake": return "🌋 Землетрясение";
            case "tempest": return "⛈️ Грозовой Шторм";
            case "meteor_shower": return "☄️ Метеоритный Дождь";
            case "blizzard": return "❄️ Ледяной Буран";
            case "eclipse": return "🌑 Солнечное Затмение";
            case "reputation_bloom": return "✨ Золотой Век (Благословение)";
            case "angelic_grace": return "😇 Ангельская Благодать";
            case "star_shower": return "🌠 Звездопад Желаний";
            case "geysers": return "♨️ Поле Гейзеров";
            case "station_fall": return "☄️ Падение Станции";
            case "blood_moon_hunt": return "🌕 Кровавая Луна";
            case "treasure_comet": return "💎 Комета Сокровищ";
            default: return "⚡ Аномальное Явление";
        }
    }

    public void openEventsDashboard(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Заполнение фоновым серым стеклом
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // 1. Метеорит (Слот 10)
        boolean meteorActive = plugin.getMeteorManager().isActive();
        Location meteorLoc = plugin.getMeteorManager().getActiveLocation();
        ItemStack meteorItem = new ItemStack(Material.ANCIENT_DEBRIS);
        ItemMeta mMeta = meteorItem.getItemMeta();
        mMeta.setDisplayName(ChatColor.GOLD + "☄️ Космический Метеорит");
        List<String> mLore = new ArrayList<>();
        if (meteorActive && meteorLoc != null) {
            mLore.add(ChatColor.GREEN + "● АКТИВНО");
            mLore.add(ChatColor.GRAY + "Координаты приземления:");
            mLore.add(ChatColor.YELLOW + "  X: " + meteorLoc.getBlockX() + " | Z: " + meteorLoc.getBlockZ());
            mLore.add("");
            mLore.add(ChatColor.GRAY + "Добудьте древнее ядро,");
            mLore.add(ChatColor.GRAY + "пока метеорит не остыл полностью!");
        } else {
            mLore.add(ChatColor.RED + "○ Остыл / Ожидание");
            mLore.add(ChatColor.GRAY + "Космические аномалии затихли.");
        }
        mMeta.setLore(mLore);
        meteorItem.setItemMeta(mMeta);
        inv.setItem(10, meteorItem);

        // 2. Аирдроп (Слот 11)
        boolean airdropActive = plugin.getAirdropManager().isActive();
        ItemStack airdropItem = new ItemStack(Material.CHEST);
        ItemMeta aMeta = airdropItem.getItemMeta();
        aMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "📦 Военный Аирдроп");
        List<String> aLore = new ArrayList<>();
        if (airdropActive) {
            aLore.add(ChatColor.GREEN + "● СБРОШЕН: " + plugin.getAirdropManager().getActiveTierName());
            aLore.add("");
            aLore.add(ChatColor.GRAY + "Аирдроп упал в случайном месте.");
            aLore.add(ChatColor.GRAY + "Запросите координаты в ВК ");
            aLore.add(ChatColor.GRAY + "с помощью команды: " + ChatColor.YELLOW + "!аирдроп");
        } else {
            aLore.add(ChatColor.RED + "○ Ожидание падения");
            aLore.add(ChatColor.GRAY + "Следите за небом и анонсами!");
        }
        aMeta.setLore(aLore);
        airdropItem.setItemMeta(aMeta);
        inv.setItem(11, airdropItem);

        // 3. Разлом Бездны (Слот 12)
        boolean invasionActive = plugin.getInvasionManager().isActive();
        Location invasionLoc = plugin.getInvasionManager().getActiveLocation();
        ItemStack invasionItem = new ItemStack(Material.OBSIDIAN);
        ItemMeta iMeta = invasionItem.getItemMeta();
        iMeta.setDisplayName(ChatColor.DARK_PURPLE + "🌌 Разлом Бездны");
        List<String> iLore = new ArrayList<>();
        if (invasionActive && invasionLoc != null) {
            iLore.add(ChatColor.GREEN + "● РАЗЛОМ ОТКРЫТ");
            iLore.add(ChatColor.GRAY + "Координаты вторжения:");
            iLore.add(ChatColor.YELLOW + "  X: " + invasionLoc.getBlockX() + " | Z: " + invasionLoc.getBlockZ());
            iLore.add("");
            iLore.add(ChatColor.GRAY + "Уничтожайте мобов и закройте");
            iLore.add(ChatColor.GRAY + "разлом, чтобы спасти мир!");
        } else {
            iLore.add(ChatColor.RED + "○ Закрыт / Ожидание");
            iLore.add(ChatColor.GRAY + "Твари Бездны затаились в глубинах.");
        }
        iMeta.setLore(iLore);
        invasionItem.setItemMeta(iMeta);
        inv.setItem(12, invasionItem);

        // 4. Мировой Босс (Слот 13)
        boolean bossActive = plugin.getWrathManager().isActive();
        Location bossLoc = plugin.getWrathManager().getActiveLocation();
        ItemStack bossItem = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemMeta bMeta = bossItem.getItemMeta();
        bMeta.setDisplayName(ChatColor.DARK_RED + "☠️ Аватар Гнева (Босс)");
        List<String> bLore = new ArrayList<>();
        if (bossActive && bossLoc != null) {
            bLore.add(ChatColor.GREEN + "● БОСС ПРИЗВАН");
            bLore.add(ChatColor.GRAY + "Координаты босса:");
            bLore.add(ChatColor.YELLOW + "  X: " + (int)bossLoc.getX() + " | Z: " + (int)bossLoc.getZ());
            bLore.add("");
            bLore.add(ChatColor.GRAY + "Соберите рейд и сразите Аватара");
            bLore.add(ChatColor.GRAY + "ради ценнейшего легендарного лута!");
        } else {
            bLore.add(ChatColor.RED + "○ Не призван");
            bLore.add(ChatColor.GRAY + "Босс спит в своей темнице.");
        }
        bMeta.setLore(bLore);
        bossItem.setItemMeta(bMeta);
        inv.setItem(13, bossItem);

        // 5. Активный катаклизм/благословение (Слот 14)
        String activeCatKey = plugin.getWrathManager().getActiveCataclysm();
        String activeCatFriendly = getCataclysmFriendlyName(activeCatKey);
        ItemStack catItem = new ItemStack(Material.CLOCK);
        ItemMeta cMeta = catItem.getItemMeta();
        cMeta.setDisplayName(ChatColor.AQUA + "🌤️ Глобальная погода / Катаклизмы");
        List<String> cLore = new ArrayList<>();
        if (activeCatFriendly != null) {
            cLore.add(ChatColor.GREEN + "● АКТИВНО: " + ChatColor.YELLOW + activeCatFriendly);
            cLore.add("");
            cLore.add(ChatColor.GRAY + "В мире действует глобальное");
            cLore.add(ChatColor.GRAY + "событие. Будьте осторожны!");
        } else {
            cLore.add(ChatColor.YELLOW + "○ Тихая погода");
            cLore.add(ChatColor.GRAY + "В мире нет активных катаклизмов.");
        }
        cMeta.setLore(cLore);
        catItem.setItemMeta(cMeta);
        inv.setItem(14, catItem);

        // 6. Сюжетные Квесты (Слот 16)
        ItemStack questItem = new ItemStack(Material.BOOK);
        ItemMeta qMeta = questItem.getItemMeta();
        qMeta.setDisplayName(ChatColor.YELLOW + "📕 Ваши сюжетные квесты");
        List<String> qLore = new ArrayList<>();
        Map<String, Integer> progressMap = plugin.getQuestManager().getPlayerQuestProgress(p.getUniqueId());
        
        qLore.add(ChatColor.GRAY + "Ваш текущий прогресс цепочек:");
        qLore.add("");
        
        // Шахтер
        int pMiner = progressMap.getOrDefault("miner_path", 0);
        int rMiner = plugin.getConfig().getInt("quests.chains.miner_path.amount", 50);
        qLore.add(ChatColor.GOLD + "⛏️ Путь Шахтера:");
        qLore.add(ChatColor.GRAY + "  Цель: Убить 50 зомби в шахте.");
        qLore.add(ChatColor.GRAY + "  Прогресс: " + ChatColor.YELLOW + pMiner + "/" + rMiner);
        
        qLore.add("");
        
        // Кузнец
        int pSmith = progressMap.getOrDefault("blacksmith_path", 0);
        int rSmith = plugin.getConfig().getInt("quests.chains.blacksmith_path.amount", 10);
        qLore.add(ChatColor.GOLD + "🔨 Путь Кузнеца:");
        qLore.add(ChatColor.GRAY + "  Цель: Скрафтить 10 алмазных мечей.");
        qLore.add(ChatColor.GRAY + "  Прогресс: " + ChatColor.YELLOW + pSmith + "/" + rSmith);
        
        qMeta.setLore(qLore);
        questItem.setItemMeta(qMeta);
        inv.setItem(16, questItem);

        // 7. Баунти контракты (Слот 17)
        ItemStack bountyItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta bnyMeta = bountyItem.getItemMeta();
        bnyMeta.setDisplayName(ChatColor.RED + "🎯 Контракты на убийство (Bounty)");
        List<String> bnyLore = new ArrayList<>();
        bnyLore.add(ChatColor.GRAY + "Активные заказы игроков:");
        bnyLore.add("");
        
        Map<UUID, Integer> activeBounties = plugin.getBountyManager().getBounties();
        if (activeBounties.isEmpty()) {
            bnyLore.add(ChatColor.GRAY + "  Заказов нет. Вы можете объявить");
            bnyLore.add(ChatColor.GRAY + "  награду за голову игрока в ВК");
            bnyLore.add(ChatColor.GRAY + "  командой: " + ChatColor.YELLOW + "!заказ <ник> <реп.>");
        } else {
            for (Map.Entry<UUID, Integer> entry : activeBounties.entrySet()) {
                String targetName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (targetName != null) {
                    bnyLore.add(ChatColor.YELLOW + "  • " + ChatColor.RED + targetName + 
                            ChatColor.GRAY + " — Награда: " + ChatColor.GOLD + entry.getValue() + " реп. ВК");
                }
            }
        }
        bnyMeta.setLore(bnyLore);
        bountyItem.setItemMeta(bnyMeta);
        inv.setItem(17, bountyItem);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(GUI_TITLE)) {
            e.setCancelled(true);
        }
    }
}
