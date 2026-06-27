package ru.example.vkchatmobs.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.data.ContractManager;
import ru.example.vkchatmobs.listeners.MobListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MobCommand implements CommandExecutor, Listener, TabCompleter {
    private final VKChatMobsPlugin plugin;
    private final ContractManager contractManager;
    private final String TITLE = ChatColor.DARK_RED + "☠ Охота и угрозы";

    public MobCommand(VKChatMobsPlugin plugin, ContractManager contractManager) {
        this.plugin = plugin;
        this.contractManager = contractManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && isAdminSub(args[0])) return handleAdmin(sender, args);
        if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков. Админ: /mobs spawn|list|reload|stop"); return true; }
        Player p = (Player) sender;
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("accept") || sub.equals("взять")) { acceptContract(p); return true; }
            if (sub.equals("status")) { displayStatus(p); return true; }
        }
        openGui(p);
        return true;
    }

    private void openGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fill(inv);
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Профиль охотника",
                ChatColor.GRAY + "Ранг: " + contractManager.getHunterRank(p),
                ChatColor.GRAY + "Выполнено контрактов: " + ChatColor.YELLOW + contractManager.getCompletedContracts(p),
                ChatColor.GRAY + "Jobs-Охотник: " + ChatColor.AQUA + contractManager.getHunterJobLevel(p),
                ChatColor.GRAY + "Активных элиток: " + ChatColor.RED + (plugin.getHardcoreMobManager() != null ? plugin.getHardcoreMobManager().getActiveEliteCount() : 0),
                ChatColor.GRAY + "Мировых угроз: " + ChatColor.RED + (plugin.getEvents2Manager() != null ? plugin.getEvents2Manager().getActiveThreatCount() : 0)));

        ContractManager.ContractType c = contractManager.getActiveContract(p);
        if (c != null) {
            inv.setItem(20, item(Material.WRITABLE_BOOK, ChatColor.GREEN + "Активный контракт",
                    ChatColor.GRAY + "Название: " + c.getDisplayName(),
                    ChatColor.GRAY + "Задача: " + c.getDescription(),
                    ChatColor.GRAY + "Прогресс: " + ChatColor.YELLOW + contractManager.getProgress(p) + "/" + c.getRequired(),
                    ChatColor.GRAY + "Награда: " + ChatColor.GREEN + "+" + c.getRepReward() + " реп. ВК",
                    ChatColor.GOLD + "Жетоны: " + c.getRuneTokens() + " | Осколки: " + c.getArtifactShards()));
        } else {
            long cd = contractManager.getCooldownRemaining(p);
            if (cd > 0) inv.setItem(20, item(Material.CLOCK, ChatColor.RED + "Контракт недоступен", ChatColor.GRAY + "Кулдаун: " + format(cd)));
            else inv.setItem(20, button(Material.LIME_CONCRETE, "accept_contract", ChatColor.GREEN + "Взять контракт", ChatColor.GRAY + "Смешанный контракт по твоему рангу.", ChatColor.GRAY + "Ранги + Jobs-Охотник открывают сложные задания."));
        }

        inv.setItem(22, item(Material.ZOMBIE_HEAD, ChatColor.RED + "Элитные мобы",
                ChatColor.GRAY + "Хардкорные архетипы и стихии.",
                ChatColor.GRAY + "Сложность зависит от состояния мира:",
                ChatColor.GRAY + "ночь, пещеры, Nether/End, события."));
        inv.setItem(24, item(Material.WITHER_SKELETON_SKULL, ChatColor.DARK_RED + "Рейд-боссы",
                ChatColor.GRAY + "3 фазы: старт → усиление → ярость.",
                ChatColor.GRAY + "Телеграфы, прислужники, зоны.",
                ChatColor.GRAY + "Лут: участники + топ урона + добивший."));
        inv.setItem(30, item(Material.END_CRYSTAL, ChatColor.LIGHT_PURPLE + "Мировые угрозы",
                ChatColor.GRAY + "5 фаз: предвестники, порталы, волны, командиры, босс.",
                ChatColor.GRAY + "Триггеры от убийств и активности мира.",
                ChatColor.GRAY + "Спавн сдвигается от защищённых зон."));
        inv.setItem(32, item(Material.CHEST, ChatColor.GOLD + "Награды",
                ChatColor.GRAY + "ВК-репутация, ванильный лут, Gear-награды.",
                ChatColor.GRAY + "Фрагменты сетов, руны, осколки артефактов.",
                ChatColor.GRAY + "Свитки Кузни 2.0 с рейдов."));
        inv.setItem(40, item(Material.BARRIER, ChatColor.RED + "Антифарм",
                ChatColor.GRAY + "Спавнеры не дают редкие награды.",
                ChatColor.GRAY + "Есть кулдауны, дневные лимиты и no-AFK логика."));
        inv.setItem(49, item(Material.PAPER, ChatColor.AQUA + "Команды",
                ChatColor.YELLOW + "/mobs" + ChatColor.GRAY + " — это меню",
                ChatColor.YELLOW + "/mobs accept" + ChatColor.GRAY + " — взять контракт",
                ChatColor.YELLOW + "/mobs status" + ChatColor.GRAY + " — текстовый статус"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        Player p = (Player) e.getWhoClicked();
        String action = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "mobs_gui_action"), PersistentDataType.STRING);
        if ("accept_contract".equals(action)) { acceptContract(p); Bukkit.getScheduler().runTask(plugin, () -> openGui(p)); }
    }

    private void acceptContract(Player p) {
        long cd = contractManager.getCooldownRemaining(p);
        if (cd > 0) { p.sendMessage("§c❌ Следующий контракт будет доступен через " + format(cd)); return; }
        if (contractManager.hasActiveContract(p)) { p.sendMessage("§c❌ У вас уже есть активный контракт!"); return; }
        contractManager.generateContract(p);
    }

    private boolean isAdminSub(String sub) {
        sub = sub.toLowerCase();
        return sub.equals("spawn") || sub.equals("list") || sub.equals("reload") || sub.equals("give") || sub.equals("debug") || sub.equals("contract") || sub.equals("stop") || sub.equals("threat");
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchatmobs.admin")) { sender.sendMessage(ChatColor.RED + "Нет прав vkchatmobs.admin"); return true; }
        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) { plugin.reloadConfig(); sender.sendMessage(ChatColor.GREEN + "VKChatMobs config перезагружен."); return true; }
        if (sub.equals("stop")) { if (plugin.getEvents2Manager() != null) plugin.getEvents2Manager().stopAllThreats(); sender.sendMessage(ChatColor.GREEN + "Mobs/Events 2.0 угрозы остановлены."); return true; }
        if (sub.equals("list") || sub.equals("debug")) {
            int elites = plugin.getHardcoreMobManager() != null ? plugin.getHardcoreMobManager().getActiveEliteCount() : 0;
            sender.sendMessage(ChatColor.GOLD + "VKChatMobs: активных элиток: " + elites);
            sender.sendMessage(ChatColor.GRAY + (plugin.getEvents2Manager() != null ? plugin.getEvents2Manager().listThreats() : "Events2 выключен"));
            return true;
        }
        if (sub.equals("threat")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Только игроку для точки."); return true; }
            if (plugin.getEvents2Manager() != null) plugin.getEvents2Manager().startThreatNear((Player)sender);
            sender.sendMessage(ChatColor.GREEN + "Мировая угроза запущена рядом."); return true;
        }
        if (sub.equals("give")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Только игроку."); return true; }
            Player p = (Player) sender;
            if (args.length < 2) { p.sendMessage("/mobs give <token|shard>"); return true; }
            if (args[1].equalsIgnoreCase("token")) p.getInventory().addItem(MobListener.getRuneToken());
            else if (args[1].equalsIgnoreCase("shard")) p.getInventory().addItem(MobListener.getArtifactShard());
            else p.sendMessage("/mobs give <token|shard>");
            return true;
        }
        if (sub.equals("spawn")) {
            if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Использование: /mobs spawn <elite|mini|raid|world> [archetype] [element] [player]"); return true; }
            Player target = null;
            if (args.length >= 5) target = Bukkit.getPlayerExact(args[4]);
            if (target == null && sender instanceof Player) target = (Player) sender;
            if (target == null) { sender.sendMessage(ChatColor.RED + "Укажи игрока для координат спавна."); return true; }
            Location loc = target.getLocation();
            String archetype = args.length >= 3 ? args[2] : null;
            String element = args.length >= 4 ? args[3] : null;
            plugin.getHardcoreMobManager().spawnCustom(loc, args[1], archetype, element);
            sender.sendMessage(ChatColor.GREEN + "Заспавнен hardcore mob tier=" + args[1]); return true;
        }
        if (sub.equals("contract")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                Player target = Bukkit.getPlayerExact(args[2]); if (target == null) { sender.sendMessage(ChatColor.RED + "Игрок не найден."); return true; }
                contractManager.generateContract(target); sender.sendMessage(ChatColor.GREEN + "Контракт пересоздан для " + target.getName()); return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/mobs contract reset <player>"); return true;
        }
        return true;
    }

    private void displayStatus(Player p) {
        p.sendMessage(" ");
        p.sendMessage("§8================§e [ОХОТА И УГРОЗЫ] §8================");
        p.sendMessage("§fРанг: " + contractManager.getHunterRank(p) + " §7| Jobs hunter: §b" + contractManager.getHunterJobLevel(p));
        if (contractManager.hasActiveContract(p)) {
            ContractManager.ContractType c = contractManager.getActiveContract(p);
            p.sendMessage("§fКонтракт: " + c.getDisplayName());
            p.sendMessage("§fЗадача: §b" + c.getDescription());
            p.sendMessage("§fПрогресс: §e" + contractManager.getProgress(p) + " / " + c.getRequired());
            p.sendMessage("§fНаграда: §a+" + c.getRepReward() + " репутации ВК");
        } else p.sendMessage("§a/mobs accept §f— взять контракт.");
        p.sendMessage("§8======================================================");
    }

    private String format(long ms) { long t = Math.max(0, ms/1000); long h=t/3600, m=(t%3600)/60; return h + " ч. " + m + " мин."; }
    private ItemStack button(Material mat, String action, String name, String... lore) { ItemStack it = item(mat, name, lore); ItemMeta meta = it.getItemMeta(); meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mobs_gui_action"), PersistentDataType.STRING, action); it.setItemMeta(meta); return it; }
    private ItemStack item(Material mat, String name, String... lore) { ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta(); meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); it.setItemMeta(meta); return it; }
    private void fill(Inventory inv) { ItemStack f = item(Material.BLACK_STAINED_GLASS_PANE, " "); for (int i=0;i<inv.getSize();i++) inv.setItem(i, f); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("accept", "взять", "status", "spawn", "list", "debug", "reload", "give", "contract", "stop", "threat"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spawn")) {
                completions.addAll(Arrays.asList("elite", "mini", "raid", "world"));
            } else if (sub.equals("give")) {
                completions.addAll(Arrays.asList("token", "shard"));
            } else if (sub.equals("contract")) {
                completions.addAll(Arrays.asList("reset"));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spawn")) {
                completions.addAll(Arrays.asList("tank", "assassin", "archer", "shaman", "necromancer", "hunter", "warlord", "berserker", "paladin", "ranger"));
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    completions.add(online.getName());
                }
            }
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
