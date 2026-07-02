package ru.example.vkchatend.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatend.VKChatEndPlugin;
import ru.example.vkchatend.managers.EndManager;
import ru.example.vkchatend.managers.EndBossManager;
import ru.example.vkchatend.managers.EndOreManager;
import ru.example.vkchatend.managers.EndCityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Команда /end — управление Эндом
 */
public class EndCommand implements CommandExecutor, TabCompleter {
    private final VKChatEndPlugin plugin;

    public EndCommand(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда только для игроков!");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            showHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tp":
            case "teleport":
            case "телепорт":
                plugin.getEndManager().teleportToEnd(p);
                break;

            case "info":
            case "инфо":
            case "profile":
            case "профиль":
                p.sendMessage(plugin.getEndManager().getPlayerInfo(p));
                break;

            case "boss":
            case "босс":
                showBossInfo(p);
                break;

            case "ores":
            case "руды":
                showOreInfo(p);
                break;

            case "artifacts":
            case "артефакты":
                showArtifactInfo(p);
                break;

            case "rifts":
            case "разломы":
                showRiftInfo(p);
                break;

            case "corruption":
            case "коррупция":
                showCorruptionInfo(p);
                break;

            case "cities":
            case "города":
                showCityInfo(p);
                break;

            case "cleanup":
            case "очистка":
                if (!p.hasPermission("vkchat.end.admin")) {
                    p.sendMessage(ChatColor.RED + "Нет прав.");
                    break;
                }
                int removed = plugin.getEndInvasionManager().cleanupInvasionMobs();
                Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "⚔ Очистка: удалено " + removed + " мобов вторжения и шалкеров из обычного мира.");
                break;

            case "help":
            case "помощь":
                showHelp(p);
                break;

            default:
                showHelp(p);
                break;
        }

        return true;
    }

    private void showHelp(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ 🐉 ЭНД — Команды ═══");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end tp" + ChatColor.GRAY + " — телепорт в Энд");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end info" + ChatColor.GRAY + " — профиль Энда");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end boss" + ChatColor.GRAY + " — информация о боссах");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end ores" + ChatColor.GRAY + " — эндер-руды");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end artifacts" + ChatColor.GRAY + " — эндер-артефакты");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end rifts" + ChatColor.GRAY + " — разломы");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end corruption" + ChatColor.GRAY + " — коррупция");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end cities" + ChatColor.GRAY + " — эндер-города");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "/end cleanup" + ChatColor.GRAY + " — удалить мобов вторжения (админ)");
    }

    private void showBossInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ ☠ Боссы Энда ═══");
        for (EndBossManager.BossType type : EndBossManager.BossType.values()) {
            p.sendMessage(type.color + "• " + type.displayName +
                    ChatColor.GRAY + " — " + (int) type.baseHealth + " HP, +" + type.repReward + " реп.");
        }
    }

    private void showOreInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ ⛏ Эндер-руды ═══");
        for (EndOreManager.EndOre ore : EndOreManager.EndOre.values()) {
            p.sendMessage(ore.color + "• " + ore.displayName +
                    ChatColor.GRAY + " — шанс: " + ore.spawnChance + "%");
        }
    }

    private void showArtifactInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ ✦ Эндер-артефакты ═══");
        p.sendMessage(ChatColor.GRAY + "Всего: " + ChatColor.WHITE + plugin.getEndArtifactManager().getArtifactCount());
        p.sendMessage(ChatColor.GRAY + "Дроп: с мобов в Энде (2% шанс)");
        p.sendMessage(ChatColor.GRAY + "Лут: эндер-города");
    }

    private void showRiftInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ 🌀 Разломы ═══");
        p.sendMessage(ChatColor.GRAY + "Активных: " + ChatColor.WHITE + plugin.getEndRiftManager().getActiveRiftCount());
        p.sendMessage(ChatColor.GRAY + "Порталы в Энд из обычного мира");
        p.sendMessage(ChatColor.GRAY + "ПКМ по порталу для телепортации");
    }

    private void showCorruptionInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ ☠ Коррупция ═══");
        p.sendMessage(ChatColor.GRAY + "Коррупция заражает острова Энда");
        p.sendMessage(ChatColor.GRAY + "Очищение: /end purify");
        p.sendMessage(ChatColor.GRAY + "Стоимость: 1000 реп.");
    }

    private void showCityInfo(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "═══ 🏰 Эндер-города ═══");
        for (EndCityManager.CityType type : EndCityManager.CityType.values()) {
            p.sendMessage(type.color + "• " + type.displayName +
                    ChatColor.GRAY + " — сложность: " + type.difficulty);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("tp", "info", "boss", "ores", "artifacts", "rifts", "corruption", "cities", "cleanup", "help")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
