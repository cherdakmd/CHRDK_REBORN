package ru.example.vkchatchat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FilterCommand implements CommandExecutor, TabCompleter {
    private final VKChatChatPlugin plugin;

    public FilterCommand(VKChatChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vkchat.chat.filter")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
            case "delete":
            case "del":
                return handleRemove(sender, args);
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender);
            case "status":
                return handleStatus(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/filter add <слово>");
            return true;
        }

        StringBuilder wordBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) wordBuilder.append(" ");
            wordBuilder.append(args[i]);
        }
        String word = wordBuilder.toString();

        plugin.getWordFilter().addWord(word);
        plugin.getWordFilter().loadConfig();
        sender.sendMessage(ChatColor.GREEN + "Слово добавлено в фильтр: " + ChatColor.YELLOW + word);
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/filter remove <слово>");
            return true;
        }

        StringBuilder wordBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) wordBuilder.append(" ");
            wordBuilder.append(args[i]);
        }
        String word = wordBuilder.toString();

        if (plugin.getWordFilter().removeWord(word)) {
            sender.sendMessage(ChatColor.GREEN + "Слово удалено из фильтра: " + ChatColor.YELLOW + word);
        } else {
            sender.sendMessage(ChatColor.RED + "Слово не найдено в фильтре: " + ChatColor.YELLOW + word);
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getWordFilter().loadConfig();
        sender.sendMessage(ChatColor.GREEN + "Конфигурация фильтра перезагружена.");
        sender.sendMessage(ChatColor.GRAY + "Слов в списке: " + plugin.getWordFilter().getForbiddenWords().size());
        sender.sendMessage(ChatColor.GRAY + "Режим: " + plugin.getWordFilter().getMode());
        sender.sendMessage(ChatColor.GRAY + "Активен: " + (plugin.getWordFilter().isEnabled() ? "да" : "нет"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> words = plugin.getWordFilter().getForbiddenWords();
        if (words.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Список запрещённых слов пуст.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Запрещённые слова (" + words.size() + ") ===");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (line.length() > 0) line.append(ChatColor.GRAY).append(", ");
            line.append(ChatColor.YELLOW).append(words.get(i));
            if (line.length() > 60 || i == words.size() - 1) {
                sender.sendMessage(line.toString());
                line = new StringBuilder();
            }
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        WordFilter filter = plugin.getWordFilter();
        sender.sendMessage(ChatColor.GOLD + "=== Статус фильтра ===");
        sender.sendMessage(ChatColor.YELLOW + "Активен: " + ChatColor.WHITE + (filter.isEnabled() ? "да" : "нет"));
        sender.sendMessage(ChatColor.YELLOW + "Режим: " + ChatColor.WHITE + filter.getMode());
        sender.sendMessage(ChatColor.YELLOW + "Мут: " + ChatColor.WHITE + filter.getMuteDuration() + " сек.");
        sender.sendMessage(ChatColor.YELLOW + "Предупреждение: " + ChatColor.WHITE + (filter.isWarnPlayer() ? "да" : "нет"));
        sender.sendMessage(ChatColor.YELLOW + "Слов в списке: " + ChatColor.WHITE + filter.getForbiddenWords().size());
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Фильтр чата ===");
        sender.sendMessage(ChatColor.YELLOW + "/filter add <слово> " + ChatColor.GRAY + "— добавить слово");
        sender.sendMessage(ChatColor.YELLOW + "/filter remove <слово> " + ChatColor.GRAY + "— удалить слово");
        sender.sendMessage(ChatColor.YELLOW + "/filter list " + ChatColor.GRAY + "— список запрещённых слов");
        sender.sendMessage(ChatColor.YELLOW + "/filter status " + ChatColor.GRAY + "— статус фильтра");
        sender.sendMessage(ChatColor.YELLOW + "/filter reload " + ChatColor.GRAY + "— перезагрузить конфиг");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("vkchat.chat.filter")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "status", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
