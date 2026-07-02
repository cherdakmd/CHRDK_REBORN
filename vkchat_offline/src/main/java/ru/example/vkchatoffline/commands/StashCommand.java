package ru.example.vkchatoffline.commands;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import java.util.*;

public class StashCommand implements CommandExecutor {
    private final VKChatOfflinePlugin plugin;
    public StashCommand(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков!"); return true; }
        Player p = (Player) sender;

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!"); return true; }

        UUID uuid = p.getUniqueId();
        List<ItemStack> items = plugin.getStashManager().getItems(uuid);

        if (items.isEmpty()) {
            p.sendMessage(ChatColor.YELLOW + "Тайник пуст.");
            return true;
        }

        // Выдать предметы
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
            if (!left.isEmpty()) remaining.addAll(left.values());
        }

        if (remaining.isEmpty()) {
            plugin.getStashManager().saveItems(uuid, new ArrayList<>());
            p.sendMessage(ChatColor.GREEN + "Все предметы из тайника получены!");
        } else {
            plugin.getStashManager().saveItems(uuid, remaining);
            p.sendMessage(ChatColor.YELLOW + "Часть предметов получена! Остальное осталось в тайнике.");
        }

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }
}
