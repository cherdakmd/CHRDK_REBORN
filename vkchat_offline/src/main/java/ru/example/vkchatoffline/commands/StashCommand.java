package ru.example.vkchatoffline.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StashCommand implements CommandExecutor, TabCompleter {
    private final VKChatOfflinePlugin plugin;

    public StashCommand(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }
        Player p = (Player) sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            List<ItemStack> items = plugin.getStashManager().getItems(p.getUniqueId());
            if (items.isEmpty()) {
                p.sendMessage("§cТайник пуст.");
                return true;
            }
            p.sendMessage("§6⛏ Ваш тайник:");
            for (ItemStack item : items) {
                p.sendMessage("§7  • §f" + item.getAmount() + "x §e" + item.getType().name());
            }
            p.sendMessage("§7Используйте §e/stash §7чтобы забрать предметы.");
            return true;
        }
        List<ItemStack> items = plugin.getStashManager().getItems(p.getUniqueId());
        if (items.isEmpty()) {
            p.sendMessage("§cТайник пуст. Отправьтесь на смену через ВК бота (!шахта).");
            return true;
        }
        int count = items.size();
        for (ItemStack item : items) {
            if (p.getInventory().firstEmpty() == -1) {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            } else {
                p.getInventory().addItem(item);
            }
        }
        plugin.getStashManager().saveItems(p.getUniqueId(), new ArrayList<>());
        p.sendMessage("§a⛏ Тайник разобран! " + count + " предметов получено.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("list");
            return list;
        }
        return Collections.emptyList();
    }
}
