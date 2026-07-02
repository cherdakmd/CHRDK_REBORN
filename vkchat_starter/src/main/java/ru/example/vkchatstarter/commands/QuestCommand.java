package ru.example.vkchatstarter.commands;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatstarter.VKChatStarterPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuestCommand implements CommandExecutor, TabCompleter {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progKey;
    private final List<String> stageNames = new ArrayList<>();
    private final List<Integer> stageAmounts = new ArrayList<>();

    public QuestCommand(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        this.progKey = new NamespacedKey(plugin, "starter_quest_progress");
        loadStages();
    }

    private void loadStages() {
        ConfigurationSection quest = plugin.getConfig().getConfigurationSection("quest");
        if (quest == null) return;
        List<Map<?, ?>> stageList = quest.getMapList("stages");
        for (Map<?, ?> raw : stageList) {
            stageNames.add((String) raw.get("name"));
            stageAmounts.add((int) raw.get("amount"));
        }
    }

    public void reloadStages() {
        stageNames.clear();
        stageAmounts.clear();
        loadStages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return true;
        }
        Player p = (Player) sender;

        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            p.sendMessage(ChatColor.GOLD + "=== Все этапы обучения ===");
            for (int i = 0; i < stageNames.size(); i++) {
                boolean done = getPlayerStage(p) > i;
                String prefix = done ? ChatColor.GREEN + "✔ " : ChatColor.GRAY + "○ ";
                String suffix = done ? ChatColor.GRAY + " (выполнено)" : "";
                p.sendMessage(prefix + ChatColor.YELLOW + (i + 1) + ". " + ChatColor.WHITE + stageNames.get(i) + suffix);
            }
            if (getPlayerStage(p) >= stageNames.size()) {
                p.sendMessage(ChatColor.GOLD + "🎉 Все этапы пройдены!");
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reset") && p.hasPermission("vkchat.admin")) {
            if (args.length < 2) {
                p.sendMessage(ChatColor.YELLOW + "Используй: /quest reset <ник>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                p.sendMessage(ChatColor.RED + "Игрок не найден!");
                return true;
            }
            target.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, 0);
            target.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);
            p.sendMessage(ChatColor.GREEN + "Квест " + target.getName() + " сброшен!");
            target.sendMessage(ChatColor.YELLOW + "Ваш квест обучения сброшен!");
            return true;
        }

        int stage = p.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
        int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);

        if (stage >= stageNames.size()) {
            p.sendMessage(ChatColor.GOLD + "Квест обучения завершён!");
            return true;
        }

        int totalStages = stageNames.size();
        p.sendMessage(ChatColor.GOLD + "=== Квест обучения ===");
        p.sendMessage(ChatColor.YELLOW + "Этап: " + ChatColor.WHITE + (stage + 1) + "/" + totalStages);
        p.sendMessage(ChatColor.YELLOW + "Задание: " + ChatColor.WHITE + stageNames.get(stage));
        if (stageAmounts.get(stage) > 1 && prog > 0) {
            p.sendMessage(ChatColor.YELLOW + "Прогресс: " + ChatColor.WHITE + prog + "/" + stageAmounts.get(stage));
        }
        p.sendMessage(ChatColor.GRAY + "Награда: +50 репутации ВК за этап");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            if (sender.hasPermission("vkchat.admin")) {
                completions.add("reset");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
        }
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }

    private int getPlayerStage(Player p) {
        return p.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
    }
}
