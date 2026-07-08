package ru.example.vkchatartifacts.commands;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.listeners.ArtifactShopListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactCommand implements CommandExecutor, TabCompleter {
    private final VKChatArtifactsPlugin plugin;

    public ArtifactCommand(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (args.length > 0 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("статус") || args[0].equalsIgnoreCase("list"))) {
            showStatus(p);
            return true;
        }
        ArtifactShopListener.openShop(plugin, p);
        return true;
    }

    private void showStatus(Player p) {
        NamespacedKey isArtifactKey = new NamespacedKey(plugin, "is_artifact");
        NamespacedKey buffKey = new NamespacedKey(plugin, "buff_type");
        NamespacedKey levelKey = new NamespacedKey(plugin, "buff_level");
        NamespacedKey curseKey = new NamespacedKey(plugin, "curse_type");
        NamespacedKey expireKey = new NamespacedKey(plugin, "expire_time");
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> levels = new LinkedHashMap<>();
        int total = 0;
        int fragile = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
            total++;
            String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            Integer lvl = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
            String curse = meta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
            if (buff != null) {
                counts.put(buff, counts.getOrDefault(buff, 0) + 1);
                levels.put(buff, levels.getOrDefault(buff, 0) + (lvl == null ? 1 : lvl));
            }
            if (meta.getPersistentDataContainer().has(expireKey, PersistentDataType.LONG) || (curse != null && curse.equals("FRAGILE"))) fragile++;
        }
        p.sendMessage(ChatColor.GOLD + "✨ Активные артефакты в инвентаре: " + ChatColor.YELLOW + total);
        int max = plugin.getConfig().getInt("artifacts.max-artifacts", 5);
        String limitInfo = total > max ? ChatColor.RED + " (лимит " + max + " — лишние неактивны!)" : ChatColor.GRAY + " / " + max;
        p.sendMessage(ChatColor.GRAY + "Лимит: " + ChatColor.YELLOW + total + limitInfo);
        if (total <= 0) {
            p.sendMessage(ChatColor.GRAY + "Артефакты работают, если просто лежат в инвентаре. Купить: /artifacts");
            return;
        }
        for (String buff : counts.keySet()) {
            p.sendMessage(ChatColor.GRAY + "• " + ChatColor.AQUA + buff + ChatColor.GRAY + " x" + counts.get(buff) + " | сумм. уровень " + levels.get(buff));
        }
        if (fragile > 0) p.sendMessage(ChatColor.RED + "Хрупких/временных артефактов: " + fragile);
        p.sendMessage(ChatColor.DARK_GRAY + "Бонусы применяются автоматически и постоянно, пока артефакт в инвентаре.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("status", "статус", "list"));
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
