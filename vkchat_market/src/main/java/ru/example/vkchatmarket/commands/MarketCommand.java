package ru.example.vkchatmarket.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.model.MarketCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {
    private final VKChatMarketPlugin plugin;

    public MarketCommand(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length > 0) {
            String cat = args[0].toLowerCase();
            if (cat.equals("menu") || cat.equals("меню")) {
                MarketGui.openMainMenu(plugin, p);
                return true;
            }
            MarketCategory mc = MarketCategory.fromConfig(cat);
            if (mc != null || cat.equals("all") || cat.equals("все")) {
                MarketGui.openCategory(plugin, p, mc != null ? mc.configKey() : "all", 0);
                return true;
            }
        }

        MarketGui.openMainMenu(plugin, p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("menu", "all"));
            for (MarketCategory cat : MarketCategory.values()) {
                options.add(cat.configKey());
            }
            String last = args[0].toLowerCase();
            return options.stream().filter(s -> s.startsWith(last)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
