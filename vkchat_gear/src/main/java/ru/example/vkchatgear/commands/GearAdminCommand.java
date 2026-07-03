package ru.example.vkchatgear.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GearAdminCommand implements CommandExecutor, TabCompleter {
    private final VKChatGearPlugin plugin;

    public GearAdminCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vkchat.gear.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GOLD + "═══ ИМЕНОВАННОЕ СНАРЯЖЕНИЕ (35 предметов) ═══");
            sender.sendMessage(ChatColor.GRAY + "Формат: [Редкость] Имя | Материал | Энчант (уровень)");

            try {
                Field field = plugin.getGearManager().getClass().getDeclaredField("NAMED_GEAR");
                field.setAccessible(true);
                String[][] namedGear = (String[][]) field.get(null);
                for (String[] gear : namedGear) {
                    String name = gear[0];
                    String mat = gear[1];
                    String rarity = gear[2];
                    String enchant = gear[3];
                    String level = gear[4];

                    ChatColor color;
                    switch (rarity) {
                        case "ancient": color = ChatColor.GOLD; break;
                        case "legendary": color = ChatColor.DARK_PURPLE; break;
                        case "epic": color = ChatColor.BLUE; break;
                        case "rare": color = ChatColor.AQUA; break;
                        default: color = ChatColor.GREEN; break;
                    }
                    sender.sendMessage(color + "[" + rarity.toUpperCase() + "] " + ChatColor.WHITE + name +
                            ChatColor.GRAY + " | " + mat + " | " + enchant + " " + level);
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Ошибка чтения списка предметов.");
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("fragment")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Использование: /gearadmin fragment <игрок> <сет> [кол-во]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "Игрок не найден."); return true; }
            String setKey = args[2].toLowerCase();
            if (!plugin.getConfig().contains("sets." + setKey)) { sender.sendMessage(ChatColor.RED + "Такого сета нет."); return true; }
            int amount = args.length > 3 ? Math.max(1, Integer.parseInt(args[3])) : 1;
            ItemStack fragment = new ItemStack(Material.PAPER, amount);
            ItemMeta meta = fragment.getItemMeta();
            String setName = plugin.getConfig().getString("sets." + setKey + ".name", setKey);
            meta.setDisplayName(ChatColor.GOLD + "Фрагмент сета: " + setName);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Используется при ковке брони.");
            lore.add(ChatColor.GRAY + "Сетовые части больше не роллятся случайно.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "set_fragment"), PersistentDataType.STRING, setKey);
            fragment.setItemMeta(meta);
            target.getInventory().addItem(fragment);
            sender.sendMessage(ChatColor.GREEN + "Выдан фрагмент сета " + setName + " x" + amount + " игроку " + target.getName());
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /gearadmin <игрок> <сет> [материал]");
            sender.sendMessage(ChatColor.YELLOW + "Или: /gearadmin fragment <игрок> <сет> [кол-во]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден.");
            return true;
        }

        String setKey = args[1].toLowerCase();
        if (!plugin.getConfig().contains("sets." + setKey)) {
            sender.sendMessage(ChatColor.RED + "Такого сета не существует в конфиге!");
            return true;
        }

        Material baseMat = Material.DIAMOND;
        if (args.length > 2) {
            try {
                baseMat = Material.valueOf(args[2].toUpperCase());
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Неверный материал! Используем DIAMOND.");
            }
        }

        // Выдаем 4 куска брони
        Material[] parts = getArmorMaterials(baseMat);
        if (parts == null) {
            sender.sendMessage(ChatColor.RED + "Этот материал не поддерживает полный сет брони.");
            return true;
        }

        for (Material m : parts) {
            ItemStack item = new ItemStack(m);
            // Форсируем генерацию Легендарки (1% ролл не нужен, админская выдача)
            item = generateAdminSetPiece(item, target, setKey);
            target.getInventory().addItem(item);
        }

        sender.sendMessage(ChatColor.GREEN + "Вы выдали полный сет " + setKey + " игроку " + target.getName() + "!");
        target.sendMessage(ChatColor.GOLD + "✨ Админ выдал вам Легендарный сет брони!");
        return true;
    }
    
    private Material[] getArmorMaterials(Material base) {
        String n = base.name();
        if (n.contains("DIAMOND")) return new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        if (n.contains("NETHERITE")) return new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        if (n.contains("IRON")) return new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
        if (n.contains("GOLD")) return new Material[]{Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS};
        if (n.contains("LEATHER")) return new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        return null;
    }

    private ItemStack generateAdminSetPiece(ItemStack item, Player owner, String setKey) {
        // Мы прогоняем вещь через стандартный генератор, чтобы наложились крутые чары
        // Но потом принудительно переписываем её под нужный сет
        ItemStack result = plugin.getGearManager().generateGear(item, owner, true);
        
        ItemMeta meta = result.getItemMeta();
        java.util.List<String> lore = meta.getLore();
        
        // Меняем "Обычный" на "Легендарный" для красоты
        String setName = plugin.getConfig().getString("sets." + setKey + ".name", setKey);
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "[ЛЕГЕНДАРНЫЙ] " + ChatColor.WHITE + setName + " " + getBaseName(item.getType()));
        
        // Убираем старую строку сета, если она сгенерировалась случайно
        lore.removeIf(line -> org.bukkit.ChatColor.stripColor(line).startsWith("Часть сета:"));
        lore.add(ChatColor.GOLD + "Часть сета: " + setName);
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING, setKey);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_set_origin"), PersistentDataType.STRING, "admin");
        result.setItemMeta(meta);
        
        return result;
    }
    
    private String getBaseName(Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET")) return "Шлем";
        if (n.endsWith("_CHESTPLATE")) return "Нагрудник";
        if (n.endsWith("_LEGGINGS")) return "Поножи";
        if (n.endsWith("_BOOTS")) return "Сапоги";
        return "Броня";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vkchat.gear.admin")) return new ArrayList<>();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("fragment", "list"));
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            return filterPartial(args[0], options);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("fragment")) {
                return filterPartial(args[1], getOnlinePlayers());
            }
            return filterPartial(args[1], getOnlinePlayers());
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("fragment")) {
                return filterPartial(args[2], getSetNames());
            }
            return filterPartial(args[2], getSetNames());
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("fragment")) {
                return filterPartial(args[3], Arrays.asList("1", "2", "3", "4", "5", "10", "16", "32", "64"));
            }
            return filterPartial(args[3], Arrays.asList("DIAMOND", "NETHERITE", "IRON", "GOLD", "LEATHER"));
        }

        return new ArrayList<>();
    }

    private List<String> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> getSetNames() {
        return new ArrayList<>(plugin.getConfig().getConfigurationSection("sets").getKeys(false));
    }

    private List<String> filterPartial(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
