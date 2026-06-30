package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер эндер-руд — уникальные ресурсы Энда
 */
public class EndOreManager implements Listener {
    private final VKChatEndPlugin plugin;

    // Типы эндер-руд
    public enum EndOre {
        ENDER_CRYSTAL_ORE("Эндер-кристалл", Material.DIAMOND_ORE, 50, ChatColor.LIGHT_PURPLE),
        VOID_ORE("Руда Бездны", Material.OBSIDIAN, 30, ChatColor.DARK_PURPLE),
        CHORUS_ORE("Хорус-руда", Material.PURPUR_BLOCK, 40, ChatColor.DARK_PURPLE),
        ENDERITE_ORE("Эндеритовая руда", Material.ANCIENT_DEBRIS, 15, ChatColor.GOLD),
        SHULKER_ORE("Шалкер-руда", Material.SHULKER_BOX, 20, ChatColor.YELLOW);

        public final String displayName;
        public final Material material;
        public final int spawnChance; // шанс на чанк
        public final ChatColor color;

        EndOre(String displayName, Material material, int spawnChance, ChatColor color) {
            this.displayName = displayName;
            this.material = material;
            this.spawnChance = spawnChance;
            this.color = color;
        }
    }

    public EndOreManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    public int getOreCount() {
        return EndOre.values().length;
    }

    /**
     * Обработка добычи эндер-руды
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        Block block = e.getBlock();
        Material type = block.getType();

        // Проверяем, является ли блок эндер-рудой
        EndOre ore = getOreByMaterial(type);
        if (ore == null) return;

        // Шанс дропа
        int dropChance = plugin.getConfig().getInt("end.ores." + ore.name().toLowerCase() + ".drop-chance", 100);
        if (ThreadLocalRandom.current().nextInt(100) >= dropChance) {
            e.setDropItems(false);
            p.sendMessage(ChatColor.GRAY + "Руда рассыпалась в пыль...");
            return;
        }

        // Количество дропа
        int minDrop = plugin.getConfig().getInt("end.ores." + ore.name().toLowerCase() + ".min-drop", 1);
        int maxDrop = plugin.getConfig().getInt("end.ores." + ore.name().toLowerCase() + ".max-drop", 3);
        int dropAmount = minDrop + ThreadLocalRandom.current().nextInt(maxDrop - minDrop + 1);

        // Создать предмет
        ItemStack drop = createOreDrop(ore, dropAmount);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), drop);
        e.setDropItems(false);

        // Репутация
        int rep = plugin.getConfig().getInt("end.ores." + ore.name().toLowerCase() + ".reputation", 5);
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(p, rep / 2);

        p.sendMessage(ore.color + "✦ Добыто: " + ore.displayName + " x" + dropAmount);
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
    }

    /**
     * Создать дроп эндер-руды
     */
    private ItemStack createOreDrop(EndOre ore, int amount) {
        Material dropMaterial;
        String name;

        switch (ore) {
            case ENDER_CRYSTAL_ORE:
                dropMaterial = Material.DIAMOND;
                name = "§d✦ Эндер-кристалл";
                break;
            case VOID_ORE:
                dropMaterial = Material.ENDER_PEARL;
                name = "§5✦ Осколок Бездны";
                break;
            case CHORUS_ORE:
                dropMaterial = Material.CHORUS_FRUIT;
                name = "§5✦ Хорус-плод";
                break;
            case ENDERITE_ORE:
                dropMaterial = Material.NETHERITE_SCRAP;
                name = "§6✦ Эндеритовый обломок";
                break;
            case SHULKER_ORE:
                dropMaterial = Material.SHULKER_SHELL;
                name = "§e✦ Панцирь шалкера";
                break;
            default:
                dropMaterial = Material.ENDER_PEARL;
                name = "§d✦ Эндер-ресурс";
        }

        ItemStack item = new ItemStack(dropMaterial, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Получить тип руды по материалу
     */
    private EndOre getOreByMaterial(Material mat) {
        for (EndOre ore : EndOre.values()) {
            if (ore.material == mat) return ore;
        }
        return null;
    }

    /**
     * Проверить, является ли блок эндер-рудой
     */
    public boolean isEndOre(Material mat) {
        return getOreByMaterial(mat) != null;
    }
}
