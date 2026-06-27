package ru.example.vkchatoffline.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StashCommand implements CommandExecutor {
    private final VKChatOfflinePlugin plugin;

    public StashCommand(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        List<ItemStack> items = plugin.getStashManager().getItems(p.getUniqueId());
        if (items.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Твой виртуальный тайник пуст. Походы запускаются только в ЛС ВК бота.");
            return true;
        }

        List<ItemStack> remaining = new ArrayList<>();
        int added = 0;
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
            if (left.isEmpty()) {
                added++;
            } else {
                remaining.addAll(left.values());
            }
        }

        plugin.getStashManager().saveItems(p.getUniqueId(), remaining);
        if (added > 0) {
            p.sendMessage(ChatColor.GREEN + "✅ Забрано предметов из оффлайн-тайника: " + added + ".");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        if (!remaining.isEmpty()) {
            p.sendMessage(ChatColor.YELLOW + "⚠ Инвентарь заполнен. Остаток оставлен в тайнике: " + remaining.size() + " стак(ов).");
        }
        return true;
    }
}
