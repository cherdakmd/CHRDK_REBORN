package ru.example.vkchatoffline.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.ArrayList;
import java.util.List;

public class StashCommand implements CommandExecutor {
    private final VKChatOfflinePlugin plugin;

    public StashCommand(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }
        Player p = (Player) sender;
        List<ItemStack> items = plugin.getStashManager().getItems(p.getUniqueId());
        if (items.isEmpty()) {
            p.sendMessage("§cТайник пуст. Отправьтесь на смену через ВК бота (!шахта).");
            return true;
        }
        for (ItemStack item : items) {
            if (p.getInventory().firstEmpty() == -1) {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            } else {
                p.getInventory().addItem(item);
            }
        }
        plugin.getStashManager().saveItems(p.getUniqueId(), new ArrayList<>());
        p.sendMessage("§a⛏ Тайник разобран! " + items.size() + " предметов получено.");
        return true;
    }
}
