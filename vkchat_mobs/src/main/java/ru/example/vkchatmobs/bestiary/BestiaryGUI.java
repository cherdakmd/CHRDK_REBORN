package ru.example.vkchatmobs.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.util.*;

public class BestiaryGUI {

    private final VKChatMobsPlugin plugin;
    private final Player player;
    private final BestiaryManager manager;

    public BestiaryGUI(VKChatMobsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.manager = plugin.getBestiaryManager();
    }

    public void openMainMenu() {
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §a§lБЕСТИАРИЙ §8◂ §7Энциклопедия");

        List<EntityType> mobs = getTrackedMobs();
        int slot = 0;
        for (EntityType type : mobs) {
            String key = type.name();
            int kc = manager.getKills(player, key);
            ItemStack icon = getMobIcon(type);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Убито: §e" + kc);
            lore.add("");
            List<Integer> avail = manager.getAvailableMilestones(player, key);
            if (!avail.isEmpty()) {
                lore.add("§a✓ Доступны награды!");
                for (int t : avail) {
                    BestiaryManager.MilestoneDef def = manager.getMilestones().get(t);
                    if (def != null) {
                        lore.add(" §a▸ " + t + " убийств: §f+" + def.hpBonus + " HP" +
                            (def.damageBonus > 0 ? ", +" + (Math.round(def.damageBonus * 100)) + "% урона" : "") +
                            (def.repReward > 0 ? ", +" + def.repReward + " реп." : ""));
                    }
                }
                lore.add("");
                lore.add("§eНажми, чтобы забрать!");
            } else {
                BestiaryManager.MilestoneDef next = getNextMilestone(kc);
                if (next != null) {
                    lore.add("§7До следующей: §e" + (next.kills - kc) + " убийств");
                    lore.add("§7Награда: §f+" + next.hpBonus + " HP" +
                        (next.damageBonus > 0 ? ", +" + (Math.round(next.damageBonus * 100)) + "% урона" : "") +
                        (next.repReward > 0 ? ", +" + next.repReward + " реп." : ""));
                } else {
                    lore.add("§aВсе награды получены!");
                }
                lore.add("");
                lore.add("§7ЛКМ — подробнее");
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
            if (slot >= 45) break;
        }

        for (int i = 45; i < 54; i++) inv.setItem(i, spacer());
        int totalKills = manager.getTotalKills(player);
        int totalHp = manager.getTotalHpBonus(player);
        double totalDmg = manager.getTotalDamageBonus(player);
        inv.setItem(49, createItem(Material.NETHER_STAR, "§6§lСТАТИСТИКА",
            "§7Всего убийств: §e" + totalKills,
            "§7Бонус HP: §a+" + totalHp,
            "§7Бонус урона: §a+" + (Math.round(totalDmg * 100)) + "%"));
        inv.setItem(50, createItem(Material.BOOK, "§f← Назад в меню охоты"));

        player.openInventory(inv);
    }

    public void openDetail(EntityType type) {
        String key = type.name();
        int kc = manager.getKills(player, key);

        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §a§l" + formatTypeName(type) + " §8◂");
        for (int i = 0; i < 27; i++) inv.setItem(i, spacer());

        ItemStack icon = getMobIcon(type);
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§7Убито: §e" + kc);
        lore.add("");

        lore.add("§6▸ Milestones:");
        boolean hasUnclaimed = false;
        for (Map.Entry<Integer, BestiaryManager.MilestoneDef> e : manager.getMilestones().entrySet()) {
            int t = e.getKey();
            BestiaryManager.MilestoneDef def = e.getValue();
            boolean isClaimed = manager.isMilestoneClaimed(player, key, t);
            if (kc >= t) {
                String status = isClaimed ? "§a✓" : "§e✗ Забрать!";
                if (!isClaimed) hasUnclaimed = true;
                lore.add(" §7" + t + ": +" + def.hpBonus + " HP" +
                    (def.damageBonus > 0 ? ", +" + (Math.round(def.damageBonus * 100)) + "%" : "") +
                    (def.repReward > 0 ? ", +" + def.repReward + " реп." : "") +
                    " " + status);
            } else {
                lore.add(" §8" + t + ": ??? (" + (t - kc) + " ост.)");
            }
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        inv.setItem(13, icon);

        if (hasUnclaimed) {
            inv.setItem(11, createItem(Material.LIME_WOOL, "§a§lЗАБРАТЬ ВСЁ", "§7Кликни, чтобы получить все доступные награды"));
        }
        inv.setItem(15, createItem(Material.BARRIER, "§f← Назад"));

        player.openInventory(inv);
    }

    public static List<EntityType> getTrackedMobs() {
        return Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.BLAZE,
            EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.SLIME, EntityType.HUSK,
            EntityType.DROWNED, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.PHANTOM,
            EntityType.PIGLIN, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.ENDERMITE, EntityType.SILVERFISH, EntityType.SHULKER, EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN, EntityType.EVOKER, EntityType.VINDICATOR, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.VEX, EntityType.WITHER
        );
    }

    public static String formatTypeName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public static ItemStack getMobIcon(EntityType type) {
        try {
            Material mat = Material.valueOf(type.name() + "_SPAWN_EGG");
            return createItem(mat, "§6§l" + formatTypeName(type));
        } catch (Exception e) {
            return createItem(Material.BONE, "§6§l" + formatTypeName(type));
        }
    }

    private BestiaryManager.MilestoneDef getNextMilestone(int kills) {
        for (Map.Entry<Integer, BestiaryManager.MilestoneDef> e : manager.getMilestones().entrySet()) {
            if (kills < e.getKey()) return e.getValue();
        }
        return null;
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack spacer() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
