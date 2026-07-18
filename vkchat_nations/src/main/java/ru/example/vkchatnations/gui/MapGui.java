package ru.example.vkchatnations.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

import ru.example.vkchat.util.VKChatBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapGui implements Listener {
    private final VKChatNationsPlugin plugin;
    private final NamespacedKey chunkKeyX;
    private final NamespacedKey chunkKeyZ;
    private final Map<UUID, Long> tpCooldowns = new ConcurrentHashMap<>();
    private static final String GUI_TITLE = "§8▸ §7§lКАРТА §8◂ §7Территории";

    public MapGui(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        this.chunkKeyX = new NamespacedKey(plugin, "chunk_x");
        this.chunkKeyZ = new NamespacedKey(plugin, "chunk_z");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMap(Player p) {
        // Исправлено: 81 слот -> 54 слота (6x9 максимум для createInventory)
        // Отображаем 5x5 чанков вокруг игрока (2 в каждую сторону от центра)
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        Chunk centerChunk = p.getLocation().getChunk();
        int cx = centerChunk.getX();
        int cz = centerChunk.getZ();

        String pNation = plugin.getNationManager().getPlayerNation(p);

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                // Центрируем: 9 колонок = 4 левых + 1 центр + 4 правых
                // 6 строк = 2 верхних + 1 центр + 3 низа
                int dx = col - 4;  // от -4 до +4 (всего 9 чанков по X)
                int dz = row - 2;  // от -2 до +3 (всего 6 чанков по Z)

                int targetX = cx + dx;
                int targetZ = cz + dz;

                Chunk targetChunk = centerChunk.getWorld().getChunkAt(targetX, targetZ);
                ChunkClaim claim = plugin.getNationManager().getChunkClaim(targetChunk);

                Material mat = Material.GRAY_STAINED_GLASS_PANE;
                String name = ChatColor.GRAY + "Свободная территория";
                List<String> lore = new ArrayList<>();

                if (dx == 0 && dz == 0) {
                    lore.add(ChatColor.AQUA + "★ Вы находитесь в этом чанке ★");
                }


                if (claim != null) {
                    if (claim.getOwner().equals(p.getUniqueId())) {
                        mat = Material.LIME_STAINED_GLASS_PANE;
                        name = ChatColor.GREEN + "Ваша территория";
                        lore.add(ChatColor.GRAY + "Прочность: " + claim.getDurability());
                        lore.add(ChatColor.GRAY + "Уровень: " + claim.getLevel());
                        lore.add(ChatColor.WHITE + "ЛКМ: Управление приватом");
                        lore.add(ChatColor.WHITE + "Shift+ПКМ: Удалить приват");
                        lore.add(ChatColor.WHITE + "Колёсико: Телепорт (20 реп)");
                    } else if (claim.getTrusted().contains(p.getUniqueId())) {
                        mat = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                        name = ChatColor.BLUE + "Доверенная территория";
                        lore.add(ChatColor.GRAY + "Уровень: " + claim.getLevel());
                        lore.add(ChatColor.WHITE + "Владелец: " + safeName(claim.getOwner()));
                        lore.add(ChatColor.WHITE + "Колёсико: Телепорт (20 реп)");
                    } else if (pNation != null && pNation.equals(claim.getNation())) {
                        mat = Material.YELLOW_STAINED_GLASS_PANE;
                        name = ChatColor.YELLOW + "Территория союзника";
                        lore.add(ChatColor.WHITE + "Владелец: " + safeName(claim.getOwner()));
                    } else {
                        mat = Material.RED_STAINED_GLASS_PANE;
                        name = ChatColor.RED + "Чужая территория";
                        lore.add(ChatColor.WHITE + "Нация: " + claim.getNation());
                    }
                } else {
                    lore.add(ChatColor.WHITE + "ЛКМ: Заприватить");
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(name);
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(chunkKeyX, PersistentDataType.INTEGER, targetX);
                meta.getPersistentDataContainer().set(chunkKeyZ, PersistentDataType.INTEGER, targetZ);
                item.setItemMeta(meta);

                int slot = row * 9 + col;
                if (slot < 54) {
                    inv.setItem(slot, item);
                }
            }
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        Integer cx = item.getItemMeta().getPersistentDataContainer().get(chunkKeyX, PersistentDataType.INTEGER);
        Integer cz = item.getItemMeta().getPersistentDataContainer().get(chunkKeyZ, PersistentDataType.INTEGER);
        
        if (cx == null || cz == null) return;

        Chunk targetChunk = p.getWorld().getChunkAt(cx, cz);
        ChunkClaim claim = plugin.getNationManager().getChunkClaim(targetChunk);
        ClickType click = e.getClick();

        

        if (claim == null) {
            if (click == ClickType.LEFT) {
                plugin.getNationManager().claimChunk(p, targetChunk);
                openMap(p);
            }
        } else {
            if (claim.getOwner().equals(p.getUniqueId())) {
                if (click == ClickType.LEFT) {
                    plugin.getClaimGui().openClaimFeedGui(p, claim);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    plugin.getNationManager().unclaimChunk(p, targetChunk);
                    openMap(p);
                } else if (click == ClickType.MIDDLE) {
                    teleportToClaim(p, targetChunk);
                }
            } else if (claim.getTrusted().contains(p.getUniqueId())) {
                if (click == ClickType.MIDDLE) {
                    teleportToClaim(p, targetChunk);
                }
            }
        }
    }


    private void teleportToClaim(Player p, Chunk c) {
        long last = tpCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (System.currentTimeMillis() - last < 300000) { // 5 mins
            long left = (300000 - (System.currentTimeMillis() - last)) / 1000;
            p.sendMessage(ChatColor.RED + "Телепорт на кулдауне! Осталось: " + left + " сек.");
            return;
        }

        int vkId = VKChatBridge.getLinkedVkId(p);
        if (!VKChatBridge.hasVkOrPass(p)) {
            p.sendMessage(ChatColor.RED + "Для телепортации нужно привязать ВК! (/vklink)");
            return;
        }

        int cost = 20;
        if (VKChatBridge.getReputation(vkId) < cost) {
            p.sendMessage(ChatColor.RED + "Недостаточно Репутации ВК! Нужно: " + cost);
            return;
        }

        VKChatBridge.takeReputation(vkId, cost);
        tpCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
        
        Location target = new Location(c.getWorld(), (c.getX() << 4) + 8, c.getWorld().getHighestBlockYAt((c.getX() << 4) + 8, (c.getZ() << 4) + 8) + 1, (c.getZ() << 4) + 8);
        p.teleport(target);
        p.sendMessage(ChatColor.GREEN + "Вы телепортировались на приват! Списано 20 репутации.");
    }

    private static String safeName(java.util.UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}
