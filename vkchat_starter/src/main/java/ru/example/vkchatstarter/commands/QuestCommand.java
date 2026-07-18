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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ru.example.vkchatstarter.QuestDataManager;

public class QuestCommand implements CommandExecutor, TabCompleter {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progKey;
    private final NamespacedKey deathKey;
    private final NamespacedKey startTimeKey;
    private final NamespacedKey skippedKey;
    private final List<String> stageNames = new ArrayList<>();
    private final List<Integer> stageAmounts = new ArrayList<>();

    public QuestCommand(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        this.progKey = new NamespacedKey(plugin, "starter_quest_progress");
        this.deathKey = new NamespacedKey(plugin, "starter_quest_deaths");
        this.startTimeKey = new NamespacedKey(plugin, "starter_quest_start_time");
        this.skippedKey = new NamespacedKey(plugin, "starter_quest_skipped");
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

        if (args.length >= 1 && args[0].equalsIgnoreCase("progress")) {
            showProgress(p);
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
            target.getPersistentDataContainer().set(deathKey, PersistentDataType.INTEGER, 0);
            target.getPersistentDataContainer().set(startTimeKey, PersistentDataType.LONG, 0L);
            target.getPersistentDataContainer().set(skippedKey, PersistentDataType.INTEGER, 0);
            plugin.getQuestDataManager().clearData(target.getUniqueId());
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

    private void showProgress(Player p) {
        int stage = getPlayerStage(p);

        if (stage >= stageNames.size()) {
            p.sendMessage("§8▸ §6§lКВЕСТ ОБУЧЕНИЯ §8◂ §7Завершён");
            p.sendMessage("");
            p.sendMessage("  §a✔ §fВсе этапы пройдены!");
            return;
        }

        int totalStages = stageNames.size();
        int completedStages = stage;
        int currentAmount = stageAmounts.get(stage);
        int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);

        QuestDataManager.PlayerQuestData data = plugin.getQuestDataManager().getData(p.getUniqueId());

        long startTime = p.getPersistentDataContainer().getOrDefault(startTimeKey, PersistentDataType.LONG, 0L);
        if (startTime == 0) startTime = data.startTime;

        String timePlayed = formatTimePlayed(startTime);

        String objective = stageNames.get(stage);
        String progressLine = currentAmount > 1 ? objective + " (" + prog + "/" + currentAmount + ")" : objective;

        p.sendMessage("§8▸ §6§lКВЕСТ ОБУЧЕНИЯ §8◂ §7Прогресс");
        p.sendMessage("");
        p.sendMessage("  §eЭтап: §f" + (stage + 1) + " §7из §f" + totalStages);
        p.sendMessage("  §eЗадание: §f" + objective);
        p.sendMessage("  §eПрогресс: " + buildProgressBar(currentAmount, prog) + " §7" + (currentAmount > 1 ? prog + "/" + currentAmount : ""));
        p.sendMessage("  §eВыполнено: §f" + completedStages + " §7из §f" + totalStages + " §7этапов");
        p.sendMessage("  §eВремя: §f" + timePlayed);
        p.sendMessage("  §eЦель: §f" + progressLine);

        int deaths = p.getPersistentDataContainer().getOrDefault(deathKey, PersistentDataType.INTEGER, 0);
        if (data.deaths > deaths) deaths = data.deaths;
        p.sendMessage("  §eСмертей: §f" + deaths);

        List<String> unlockedAchievements = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : data.achievements.entrySet()) {
            if (entry.getValue()) {
                unlockedAchievements.add(entry.getKey());
            }
        }
        if (unlockedAchievements.isEmpty()) {
            p.sendMessage("  §eДостижения: §7нет");
        } else {
            p.sendMessage("  §eДостижения: §a" + String.join("§7, §a", unlockedAchievements));
        }

        p.sendMessage("");
        p.sendMessage("  §7Награда за этап: §a+" + plugin.getConfig().getConfigurationSection("quest").getInt("reward-per-stage", 75) + " реп.");
    }

    private String buildProgressBar(int total, int current) {
        int barLength = 20;
        double percent = total > 0 ? (double) current / total : 0;
        int filled = (int) Math.round(percent * barLength);
        filled = Math.min(filled, barLength);

        StringBuilder bar = new StringBuilder();
        bar.append("§8");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("§a█");
            } else {
                bar.append("§7░");
            }
        }
        int percentVal = (int) Math.round(percent * 100);
        bar.append("§f ").append(percentVal).append("%");
        return bar.toString();
    }

    private String formatTimePlayed(long startTimeMillis) {
        if (startTimeMillis == 0) return "не начато";
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        if (elapsed < 0) return "не начато";

        long hours = TimeUnit.MILLISECONDS.toHours(elapsed);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;

        if (hours > 0) {
            return hours + "ч " + minutes + "м " + seconds + "с";
        } else if (minutes > 0) {
            return minutes + "м " + seconds + "с";
        } else {
            return seconds + "с";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("progress");
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
