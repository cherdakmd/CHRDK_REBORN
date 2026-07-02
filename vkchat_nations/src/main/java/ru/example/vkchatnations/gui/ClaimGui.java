package ru.example.vkchatnations.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchat.VKChatPlugin;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class ClaimGui implements Listener {
    private final VKChatNationsPlugin plugin;
    private final NamespacedKey chunkKeyX;
    private final NamespacedKey chunkKeyZ;

    public ClaimGui(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        this.chunkKeyX = new NamespacedKey(plugin, "claim_x");
        this.chunkKeyZ = new NamespacedKey(plugin, "claim_z");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openGui(Player p, Chunk chunk) {
        ChunkClaim claim = plugin.getNationManager().getChunkClaim(chunk);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы не можете управлять этим приватом.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Управление Приватом");

        // Информация о привате
        ItemStack info = new ItemStack(Material.BEACON);
        ItemMeta metaInfo = info.getItemMeta();
        metaInfo.setDisplayName(ChatColor.AQUA + "Информация");
        List<String> loreInfo = new ArrayList<>();
        loreInfo.add(ChatColor.GRAY + "Прочность (Аренда): " + ChatColor.GREEN + claim.getDurability() + "/" + claim.getMaxDurability());
        loreInfo.add(ChatColor.GRAY + "Уровень привата: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()));
        loreInfo.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + claim.getRadius() + " блоков");
        loreInfo.add("");
        loreInfo.add(ChatColor.GRAY + "Доверенных игроков: " + ChatColor.WHITE + claim.getTrusted().size());
        for (UUID trustedId : claim.getTrusted()) {
            String tName = Bukkit.getOfflinePlayer(trustedId).getName();
            loreInfo.add(ChatColor.GRAY + "  • " + ChatColor.GREEN + (tName != null ? tName : trustedId.toString().substring(0, 8)));
        }
        loreInfo.add("");
        loreInfo.add(ChatColor.GRAY + "Налог с доверенных: " + ChatColor.GREEN + (claim.getTrusted().size() * 2) + " реп/день");
        loreInfo.add(ChatColor.GRAY + "Ежедневный износ: " + ChatColor.RED + "-2 прочности/день");
        metaInfo.setLore(loreInfo);
        metaInfo.getPersistentDataContainer().set(chunkKeyX, PersistentDataType.INTEGER, chunk.getX());
        metaInfo.getPersistentDataContainer().set(chunkKeyZ, PersistentDataType.INTEGER, chunk.getZ());
        info.setItemMeta(metaInfo);
        inv.setItem(13, info);

        // Оплата ресурсами
        ItemStack resPay = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta resMeta = resPay.getItemMeta();
        resMeta.setDisplayName(ChatColor.AQUA + "Оплатить Ресурсами");
        List<String> resLore = new ArrayList<>();
        resLore.add(ChatColor.GRAY + "Цена: " + ChatColor.WHITE + "1 Алмазный Блок");
        resLore.add(ChatColor.GRAY + "Вы получите: " + ChatColor.GREEN + "+50 Прочности");
        resLore.add("");
        resLore.add(ChatColor.YELLOW + "Кликните, чтобы оплатить");
        resMeta.setLore(resLore);
        resPay.setItemMeta(resMeta);
        inv.setItem(11, resPay);

        // Оплата Репутацией
        ItemStack repPay = new ItemStack(Material.EMERALD);
        ItemMeta repMeta = repPay.getItemMeta();
        repMeta.setDisplayName(ChatColor.GREEN + "Оплатить Репутацией ВК");
        List<String> repLore = new ArrayList<>();
        repLore.add(ChatColor.GRAY + "Цена: " + ChatColor.WHITE + "50 Репутации");
        repLore.add(ChatColor.GRAY + "Вы получите: " + ChatColor.GREEN + "+50 Прочности");
        repLore.add("");
        repLore.add(ChatColor.YELLOW + "Кликните, чтобы оплатить");
        repMeta.setLore(repLore);
        repPay.setItemMeta(repMeta);
        inv.setItem(15, repPay);

        // Прокачка уровня (единое 5-уровневое меню)
        ItemStack upgPay = new ItemStack(ChunkClaim.getLevelMaterial(claim.getLevel()));
        ItemMeta upgMeta = upgPay.getItemMeta();
        upgMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "⬆ Прокачать Приват");
        List<String> upgLore = new ArrayList<>();
        upgLore.add(ChatColor.GRAY + "Текущий уровень: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()));
        upgLore.add("");
        if (claim.canUpgrade()) {
            upgLore.add(ChatColor.GRAY + "Откройте меню 5-уровневой прокачки:");
            upgLore.add(ChatColor.GRAY + "Антивзрыв → Покой → Огнеупорность → Цитадель");
            upgLore.add(ChatColor.GRAY + "Цена следующего: " + ChatColor.GOLD + claim.getNextUpgradeCost() + " реп. ВК");
            upgLore.add("");
            upgLore.add(ChatColor.YELLOW + "Кликните, чтобы открыть меню прокачки");
        } else {
            upgLore.add(ChatColor.LIGHT_PURPLE + "Максимальный уровень (5) достигнут!");
        }
        upgMeta.setLore(upgLore);
        upgPay.setItemMeta(upgMeta);
        inv.setItem(22, upgPay);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(ChatColor.DARK_BLUE + "Управление Приватом")) return;
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack info = e.getInventory().getItem(13);
        if (info == null || !info.hasItemMeta()) return;

        Integer cx = info.getItemMeta().getPersistentDataContainer().get(chunkKeyX, PersistentDataType.INTEGER);
        Integer cz = info.getItemMeta().getPersistentDataContainer().get(chunkKeyZ, PersistentDataType.INTEGER);
        if (cx == null || cz == null) return;

        Chunk chunk = p.getWorld().getChunkAt(cx, cz);
        ChunkClaim claim = plugin.getNationManager().getChunkClaim(chunk);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) {
            p.closeInventory();
            return;
        }

        int slot = e.getRawSlot();
        if (slot == 11) { // Resources
            if (p.getInventory().contains(Material.DIAMOND_BLOCK, 1)) {
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    if (it != null && it.getType() == Material.DIAMOND_BLOCK) {
                        it.setAmount(it.getAmount() - 1);
                        break;
                    }
                }
                claim.addDurability(50);
                plugin.getNationManager().saveAll();
                p.sendMessage(ChatColor.GREEN + "Вы пожертвовали 1 Алмазный Блок. Прочность +50!");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1f);
                openGui(p, chunk);
            } else {
                p.sendMessage(ChatColor.RED + "У вас нет 1 Алмазного Блока!");
            }
        }
        else if (slot == 15) { // Reputation
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "Сначала привяжите ВК! (/vklink)");
                return;
            }
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) >= 50) {
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, 50);
                claim.addDurability(50);
                plugin.getNationManager().saveAll();
                p.sendMessage(ChatColor.GREEN + "Списано 50 Репутации. Прочность +50!");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openGui(p, chunk);
            } else {
                p.sendMessage(ChatColor.RED + "Недостаточно Репутации ВК!");
            }
        }
        else if (slot == 22) { // Upgrade — открываем единое меню прокачки
            plugin.getGuiListener().openClaimUpgradeGui(p, claim);
        }
    }
}
