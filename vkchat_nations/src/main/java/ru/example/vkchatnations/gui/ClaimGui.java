package ru.example.vkchatnations.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClaimGui — GUI управления приватами (питание, прокачка, покупка).
 *
 * Извлечено из NationGuiListener:
 * - Магазин блоков привата (openClaimShop)
 * - Управление приватом (openClaimFeedGui)
 * - Прокачка привата (openClaimUpgradeGui)
 * - Обработка кликов по всем claim-инвентарям
 * - Тумблеры защит, доверенные, точка дома, расширение
 */
public class ClaimGui {

    private final VKChatNationsPlugin plugin;
    private static final String BUY_CLAIM_TITLE = "§8▸ §e§lПРИВАТ §8◂ §7Покупка";
    private static final String CLAIM_FEED_TITLE = "§8▸ §6§lПРИВАТ §8◂ §7Питание";
    private static final String CLAIM_UPGRADE_TITLE = "§8▸ §5§lПРИВАТ §8◂ §7Прокачка";

    private final java.util.Map<UUID, ChunkClaim> activeFeedingClaims = new java.util.concurrent.ConcurrentHashMap<>();

    public ClaimGui(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    public static String getBuyClaimTitle() { return BUY_CLAIM_TITLE; }
    public static String getClaimFeedTitle() { return CLAIM_FEED_TITLE; }
    public static String getClaimUpgradeTitle() { return CLAIM_UPGRADE_TITLE; }

    /**
     * Получить активный приват для игрока.
     */
    public ChunkClaim getActiveClaim(UUID playerId) {
        return activeFeedingClaims.get(playerId);
    }

    /**
     * Удалить активный приват при закрытии/выходе.
     */
    public void removeActiveClaim(UUID playerId) {
        activeFeedingClaims.remove(playerId);
    }

    // ═══════════════════════════════════════════════════════════════
    // ПОКУПКА БЛОКОВ ПРИВАТА
    // ═══════════════════════════════════════════════════════════════

    public void openClaimShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, BUY_CLAIM_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        int curCount = plugin.getNationManager().getClaimCount(p.getUniqueId());
        int maxCount = plugin.getNationManager().getMaxClaimsFor(p);
        int nextCost = plugin.getNationManager().getClaimCostFor(p);

        // Small Block
        ItemStack small = plugin.getNationManager().getSmallClaimBlockItem();
        ItemMeta sMeta = small.getItemMeta();
        List<String> sLore = sMeta.getLore();
        sLore.add("");
        sLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + nextCost + " реп. ВК");
        sLore.add(ChatColor.GRAY + "Твои приваты: " + curCount + "/" + maxCount);
        sLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        sMeta.setLore(sLore);
        sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, nextCost);
        sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_radius"), PersistentDataType.INTEGER, 8);
        small.setItemMeta(sMeta);

        // Medium Block
        ItemStack medium = plugin.getNationManager().getMediumClaimBlockItem();
        ItemMeta mMeta = medium.getItemMeta();
        List<String> mLore = mMeta.getLore();
        mLore.add("");
        mLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + (nextCost * 2) + " реп. ВК");
        mLore.add(ChatColor.GRAY + "Твои приваты: " + curCount + "/" + maxCount);
        mLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        mMeta.setLore(mLore);
        mMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, nextCost * 2);
        mMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_radius"), PersistentDataType.INTEGER, 16);
        medium.setItemMeta(mMeta);

        // Large Block
        ItemStack large = plugin.getNationManager().getLargeClaimBlockItem();
        ItemMeta lMeta = large.getItemMeta();
        List<String> lLore = lMeta.getLore();
        lLore.add("");
        lLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + (nextCost * 5) + " реп. ВК");
        lLore.add(ChatColor.GRAY + "Твои приваты: " + curCount + "/" + maxCount);
        lLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        lMeta.setLore(lLore);
        lMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, nextCost * 5);
        lMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_radius"), PersistentDataType.INTEGER, 32);
        large.setItemMeta(lMeta);

        inv.setItem(11, small);
        inv.setItem(13, medium);
        inv.setItem(15, large);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Назад");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ ПРИВАТОМ (ПИТАНИЕ)
    // ═══════════════════════════════════════════════════════════════

    public void openClaimFeedGui(Player p, ChunkClaim claim) {
        Inventory inv = Bukkit.createInventory(null, 27, CLAIM_FEED_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // 1. Покормить ресурсами
        ItemStack resItem = new ItemStack(Material.DIAMOND);
        ItemMeta resMeta = resItem.getItemMeta();
        resMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⚡ Покормить ресурсами");
        List<String> resLore = new ArrayList<>();
        resLore.add(ChatColor.GRAY + "Потратить ценные ресурсы из вашего инвентаря:");
        resLore.add(ChatColor.GRAY + "  • " + ChatColor.GOLD + "5 Золотых слитков " + ChatColor.GRAY + "-> " + ChatColor.GREEN + "+20 прочности");
        resLore.add(ChatColor.GRAY + "  • " + ChatColor.AQUA + "1 Алмаз " + ChatColor.GRAY + "-> " + ChatColor.GREEN + "+50 прочности");
        resLore.add(ChatColor.GRAY + "  • " + ChatColor.RED + "1 Незеритовый лом " + ChatColor.GRAY + "-> " + ChatColor.GREEN + "+200 прочности");
        resLore.add("");
        resLore.add(ChatColor.YELLOW + "▶ Кликните, чтобы скормить ресурсы! ◀");
        resMeta.setLore(resLore);
        resItem.setItemMeta(resMeta);

        // 2. Покормить репутацией ВК
        ItemStack repItem = new ItemStack(Material.REDSTONE);
        ItemMeta repMeta = repItem.getItemMeta();
        repMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⚡ Покормить репутацией ВК");
        List<String> repLore = new ArrayList<>();
        repLore.add(ChatColor.GRAY + "Продлить прочность привата за вашу личную");
        repLore.add(ChatColor.GRAY + "репутацию ВКонтакте:");
        repLore.add(ChatColor.GRAY + "  • " + ChatColor.GOLD + "15 репутации ВК " + ChatColor.GRAY + "-> " + ChatColor.GREEN + "+100 прочности");
        repLore.add("");
        repLore.add(ChatColor.YELLOW + "▶ Кликните, чтобы потратить репутацию ◀");
        repMeta.setLore(repLore);
        repItem.setItemMeta(repMeta);

        // 3. Информация
        ItemStack infoItem = new ItemStack(Material.ANVIL);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "ℹ Информация о привате");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Текущая прочность: " + ChatColor.GREEN + claim.getDurability() + "/" + claim.getMaxDurability());
        infoLore.add(ChatColor.GRAY + "Уровень: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()));
        infoLore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + claim.getRadius() + " блоков");
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        infoLore.add(ChatColor.GRAY + "Владелец: " + ChatColor.WHITE + (ownerName != null ? ownerName : claim.getOwner().toString().substring(0, 8)));
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Подсказка: Прочность падает ежедневно на -2 ед.");
        infoLore.add(ChatColor.GRAY + "Если прочность упадет до 0, приват разрушится,");
        infoLore.add(ChatColor.GRAY + "а блок привата исчезнет!");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);

        // 4. Прокачать приват
        ItemStack upgItem = new ItemStack(ChunkClaim.getLevelMaterial(claim.getLevel()));
        ItemMeta upgMeta = upgItem.getItemMeta();
        upgMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⬆ Прокачать приват");
        List<String> upgLore = new ArrayList<>();
        upgLore.add(ChatColor.GRAY + "Текущий уровень: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()));
        upgLore.add("");
        if (claim.canUpgrade()) {
            upgLore.add(ChatColor.GRAY + "Следующий уровень: " + ChunkClaim.getLevelColor(claim.getLevel() + 1) + (claim.getLevel() + 1) + " — " + ChunkClaim.getLevelName(claim.getLevel() + 1));
            upgLore.add(ChatColor.GRAY + "Цена: " + ChatColor.GOLD + claim.getNextUpgradeCost() + " реп. ВК");
            upgLore.add("");
            upgLore.add(ChatColor.YELLOW + "▶ Нажмите, чтобы открыть меню прокачки! ◀");
        } else {
            upgLore.add(ChatColor.LIGHT_PURPLE + "Достигнут максимальный уровень (5)!");
            upgLore.add(ChatColor.GRAY + "Приват полностью прокачан.");
        }
        upgMeta.setLore(upgLore);
        upgItem.setItemMeta(upgMeta);

        inv.setItem(11, resItem);
        inv.setItem(13, repItem);
        inv.setItem(15, infoItem);
        inv.setItem(4, upgItem);

        // Название привата
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameItem.getItemMeta();
        nameMeta.setDisplayName(ChatColor.AQUA + "✎ " + claim.getName());
        List<String> nameLore = new ArrayList<>();
        nameLore.add(ChatColor.GRAY + "Клик — переименовать в чате");
        nameMeta.setLore(nameLore);
        nameItem.setItemMeta(nameMeta);
        inv.setItem(0, nameItem);

        // Точка дома
        boolean hasHome = claim.hasHome();
        ItemStack homeItem = new ItemStack(hasHome ? Material.RED_BED : Material.WHITE_BED);
        ItemMeta homeMeta = homeItem.getItemMeta();
        homeMeta.setDisplayName(hasHome ? ChatColor.GREEN + "♲ Точка дома установлена" : ChatColor.GRAY + "♲ Точка дома не задана");
        List<String> homeLore = new ArrayList<>();
        if (hasHome) {
            homeLore.add(ChatColor.GRAY + "ЛКМ — телепортироваться");
            homeLore.add(ChatColor.GRAY + "ПКМ — удалить точку");
        } else {
            homeLore.add(ChatColor.GRAY + "Кликните, чтобы установить точку дома");
            homeLore.add(ChatColor.GRAY + "на месте, где вы стоите");
        }
        homeMeta.setLore(homeLore);
        homeItem.setItemMeta(homeMeta);
        inv.setItem(1, homeItem);

        // Авто-продление
        addToggle(inv, 2, claim, true, claim.isAutoPayEnabled(),
                "&a&lАвто-продление", "&c&lАвто-продление", "Авто-оплата прочности за репутацию (< 20%)", "");

        // Доверенные
        ItemStack trustItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta trustMeta = trustItem.getItemMeta();
        trustMeta.setDisplayName(ChatColor.GREEN + "✓ Доверенные: " + claim.getTrusted().size());
        List<String> trustLore = new ArrayList<>();
        trustLore.add(ChatColor.GRAY + "Клик — добавить игрока (напиши ник в чат)");
        for (UUID tid : claim.getTrusted()) {
            String tName = Bukkit.getOfflinePlayer(tid).getName();
            trustLore.add(ChatColor.GRAY + "  • " + ChatColor.GREEN + (tName != null ? tName : tid.toString().substring(0, 8)));
        }
        trustMeta.setLore(trustLore);
        trustItem.setItemMeta(trustMeta);
        inv.setItem(7, trustItem);

        // Расширение радиуса
        int expansions = claim.getExtraRadius();
        int baseR = claim.getBaseRadius();
        int cost = ChunkClaim.getRadiusExpandCost(expansions);
        ItemStack radiusItem = new ItemStack(expansions > 0 ? Material.WRITTEN_BOOK : Material.BOOK);
        ItemMeta radiusMeta = radiusItem.getItemMeta();
        radiusMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "⬡ Расширить зону");
        List<String> radiusLore = new ArrayList<>();
        radiusLore.add(ChatColor.GRAY + "Радиус: " + ChatColor.YELLOW + (baseR + expansions * 3) + " блоков");
        radiusLore.add(ChatColor.GRAY + "Цена расширения: " + ChatColor.GOLD + cost + " реп. ВК");
        radiusLore.add(ChatColor.GRAY + "+3 блока к радиусу");
        radiusMeta.setLore(radiusLore);
        radiusItem.setItemMeta(radiusMeta);
        inv.setItem(8, radiusItem);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Закрыть");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        // Тумблеры защит
        addToggle(inv, 20, claim, claim.getLevel() >= 2, claim.isExplosionProtectionEnabled(),
                "&a&lАнтивзрыв", "&c&lАнтивзрыв", "Защита блоков от TNT, криперов и взрывов", "2");
        addToggle(inv, 21, claim, claim.getLevel() >= 3, claim.isNoSpawnProtectionEnabled(),
                "&b&lПокой", "&c&lПокой", "Запрет спавна мобов (спавнеры работают)", "3");
        addToggle(inv, 23, claim, claim.getLevel() >= 4, claim.isFireProtectionEnabled(),
                "&a&lОгнеупорность", "&c&lОгнеупорность", "Защита от поджога, огня и лавы", "4");
        addToggle(inv, 24, claim, claim.getLevel() >= 5, claim.isPvpProtectionEnabled(),
                "&a&lЦитадель", "&c&lЦитадель", "Запрет PvP на территории", "5");

        p.openInventory(inv);
        activeFeedingClaims.put(p.getUniqueId(), claim);
    }

    // ═══════════════════════════════════════════════════════════════
    // ПРОКАЧКА ПРИВАТА
    // ═══════════════════════════════════════════════════════════════

    public void openClaimUpgradeGui(Player p, ChunkClaim claim) {
        Inventory inv = Bukkit.createInventory(null, 27, CLAIM_UPGRADE_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;

        // Шапка
        ItemStack statusItem = new ItemStack(ChunkClaim.getLevelMaterial(claim.getLevel()));
        ItemMeta statusMeta = statusItem.getItemMeta();
        statusMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⬆ Прокачка привата");
        List<String> statusLore = new ArrayList<>();
        statusLore.add(ChatColor.GRAY + "Текущий уровень: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()));
        statusLore.add(ChatColor.GRAY + "Прочность: " + ChatColor.GREEN + claim.getDurability() + "/" + claim.getMaxDurability());
        statusLore.add(ChatColor.GRAY + "Ваш баланс: " + ChatColor.GOLD + rep + " реп. ВК");
        statusMeta.setLore(statusLore);
        statusItem.setItemMeta(statusMeta);
        inv.setItem(4, statusItem);

        // 5 уровней в ряд
        int[] tierSlots = {11, 12, 13, 14, 15};
        for (int lvl : ChunkClaim.allLevels()) {
            int slot = tierSlots[lvl - 1];
            ItemStack item = new ItemStack(ChunkClaim.getLevelMaterial(lvl));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChunkClaim.getLevelColor(lvl) + "" + ChatColor.BOLD + "Уровень " + lvl + ": " + ChunkClaim.getLevelName(lvl));

            List<String> lore = new ArrayList<>();
            lore.addAll(ChunkClaim.getLevelDescription(lvl));
            lore.add("");

            if (lvl < claim.getLevel()) {
                lore.add(ChatColor.GREEN + "✓ Уже пройден");
            } else if (lvl == claim.getLevel()) {
                lore.add(ChatColor.AQUA + "★ Текущий уровень (активен)");
            } else if (lvl == claim.getLevel() + 1) {
                int cost = ChunkClaim.getUpgradeCost(claim.getLevel());
                lore.add(ChatColor.GRAY + "Цена повышения: " + ChatColor.GOLD + cost + " реп. ВК");
                lore.add("");
                if (!VKChatBridge.hasVkOrPass(p)) {
                    lore.add(ChatColor.RED + "▶ Привяжите ВК для прокачки! (/vklink)");
                } else if (rep >= cost) {
                    lore.add(ChatColor.YELLOW + "▶ Нажмите, чтобы прокачать!");
                } else {
                    lore.add(ChatColor.RED + "▶ Недостаточно репутации (нужно " + cost + ").");
                }
            } else {
                lore.add(ChatColor.DARK_GRAY + "🔒 Сначала прокачайте предыдущие уровни");
            }

            meta.setLore(lore);
            if (lvl == claim.getLevel() + 1) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "upgrade_target"), PersistentDataType.INTEGER, lvl);
            }
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Назад");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
        activeFeedingClaims.put(p.getUniqueId(), claim);
    }

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ — МАГАЗИН ПРИВАТОВ
    // ═══════════════════════════════════════════════════════════════

    public boolean handleClaimShopClick(Player p, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        if (item.getType() == Material.BARRIER) return true; // Назад

        NamespacedKey costKey = new NamespacedKey(plugin, "buy_block_cost");
        NamespacedKey radiusKey = new NamespacedKey(plugin, "buy_block_radius");

        if (item.getItemMeta().getPersistentDataContainer().has(costKey, PersistentDataType.INTEGER)) {
            int cost = item.getItemMeta().getPersistentDataContainer().get(costKey, PersistentDataType.INTEGER);
            int radius = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.INTEGER);

            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "❌ Для покупок привяжите ВКонтакте! (/vklink)");
                return true;
            }

            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп. (У вас: " + rep + ").");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }

            VKChatBridge.takeReputation(vkId, cost);

            ItemStack blockToGive;
            if (radius == 8) blockToGive = plugin.getNationManager().getSmallClaimBlockItem();
            else if (radius == 16) blockToGive = plugin.getNationManager().getMediumClaimBlockItem();
            else blockToGive = plugin.getNationManager().getLargeClaimBlockItem();

            if (!p.getInventory().addItem(blockToGive).isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(), blockToGive);
            }

            p.sendMessage(ChatColor.GREEN + "✓ Вы успешно купили Блок Привата радиусом " + radius + " блоков за " + ChatColor.GOLD + cost + " реп. ВК!");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ — УПРАВЛЕНИЕ ПРИВАТОМ
    // ═══════════════════════════════════════════════════════════════

    public boolean handleClaimFeedClick(Player p, ItemStack clicked, int rawSlot) {
        ChunkClaim claim = activeFeedingClaims.get(p.getUniqueId());
        if (claim == null) return false;
        if (clicked == null || !clicked.hasItemMeta()) return false;

        if (clicked.getType() == Material.BARRIER) { p.closeInventory(); return true; }

        // Слот 4 — прокачать
        if (rawSlot == 4) { openClaimUpgradeGui(p, claim); return true; }

        // Слот 0 — переименовать
        if (rawSlot == 0) {
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "✎ Напишите в чат новое название для привата:");
            p.sendMessage(ChatColor.GRAY + "(или напишите 'отмена' чтобы отменить)");
            plugin.getNationManager().setRenameClaim(p.getUniqueId(), claim);
            return true;
        }

        // Слот 1 — точка дома
        if (rawSlot == 1) {
            if (claim.hasHome()) {
                // ЛКМ = телепорт, ПКМ = удалить — обработка в NationGuiListener
                return false; // Пусть основной listener обработает
            } else {
                claim.setHome(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
                plugin.getNationManager().saveAll();
                p.sendMessage(ChatColor.GREEN + "♲ Точка дома установлена!");
                openClaimFeedGui(p, claim);
            }
            return true;
        }

        // Слот 2 — авто-продление
        if (rawSlot == 2) {
            claim.setAutoPay(!claim.isAutoPayEnabled());
            plugin.getNationManager().saveAll();
            p.sendMessage(ChatColor.GREEN + (claim.isAutoPayEnabled() ? "Авто-продление включено." : "Авто-продление выключено."));
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
            openClaimFeedGui(p, claim);
            return true;
        }

        // Слот 7 — доверенные
        if (rawSlot == 7) {
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "✎ Напишите ник игрока, которого хотите добавить в доверенные:");
            p.sendMessage(ChatColor.GRAY + "(или напишите 'отмена')");
            p.sendMessage(ChatColor.GRAY + "Чтобы удалить — напиши /nation untrust <ник>");
            plugin.getNationManager().setAddingTrusted(p.getUniqueId(), claim);
            return true;
        }

        // Слот 8 — расширение радиуса
        if (rawSlot == 8) {
            int expansions = claim.getExtraRadius();
            int cost = ChunkClaim.getRadiusExpandCost(expansions);
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "❌ Привяжите ВК! (/vklink)");
                return true;
            }
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации! Нужно " + cost + ", у вас " + rep);
                return true;
            }
            VKChatBridge.takeReputation(vkId, cost);
            claim.addExtraRadius(1);
            plugin.getNationManager().saveAll();
            p.sendMessage(ChatColor.GREEN + "⬡ Радиус расширен! Теперь: " + claim.getRadius() + " блоков.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openClaimFeedGui(p, claim);
            return true;
        }

        // Покормить ресурсами
        if (clicked.getType() == Material.DIAMOND) {
            if (p.getInventory().contains(Material.NETHERITE_SCRAP, 1)) {
                consumeInventoryItem(p, Material.NETHERITE_SCRAP, 1);
                claim.addDurability(200);
                p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 1 Незеритовый лом! Прочность привата увеличена на +200.");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            } else if (p.getInventory().contains(Material.DIAMOND, 1)) {
                consumeInventoryItem(p, Material.DIAMOND, 1);
                claim.addDurability(50);
                p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 1 Алмаз! Прочность привата увеличена на +50.");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            } else if (p.getInventory().contains(Material.GOLD_INGOT, 5)) {
                consumeInventoryItem(p, Material.GOLD_INGOT, 5);
                claim.addDurability(20);
                p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 5 Золотых слитков! Прочность привата увеличена на +20.");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            } else {
                p.sendMessage(ChatColor.RED + "❌ У вас нет нужных ресурсов (5 золота, 1 алмаз или 1 незерит) в инвентаре!");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }
            plugin.getNationManager().saveAll();
            openClaimFeedGui(p, claim);
            return true;
        }

        // Покормить репутацией
        if (clicked.getType() == Material.REDSTONE) {
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "❌ Для питания за репутацию привяжите ВКонтакте! (/vklink)");
                return true;
            }

            int cost = 15;
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " (Ваш баланс: " + rep + ").");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }

            VKChatBridge.takeReputation(vkId, cost);
            claim.addDurability(100);
            plugin.getNationManager().saveAll();
            p.sendMessage(ChatColor.GREEN + "✓ Прочность привата увеличена на +100 за 15 репутации ВК!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            openClaimFeedGui(p, claim);
            return true;
        }

        // Тумблеры защит (слоты 20,21,23,24)
        if (rawSlot >= 20 && rawSlot <= 24 && rawSlot != 22) {
            int reqLevel = rawSlot == 20 ? 2 : rawSlot == 21 ? 3 : rawSlot == 23 ? 4 : rawSlot == 24 ? 5 : 99;
            if (claim.getLevel() < reqLevel) {
                p.sendMessage(ChatColor.RED + "Нужен " + reqLevel + " уровень привата!");
                return true;
            }
            switch (rawSlot) {
                case 20 -> claim.setExplosionProtection(!claim.isExplosionProtectionEnabled());
                case 21 -> claim.setNoSpawnProtection(!claim.isNoSpawnProtectionEnabled());
                case 23 -> claim.setFireProtection(!claim.isFireProtectionEnabled());
                case 24 -> claim.setPvpProtection(!claim.isPvpProtectionEnabled());
            }
            plugin.getNationManager().saveAll();
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
            openClaimFeedGui(p, claim);
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ — ПРОКАЧКА ПРИВАТА
    // ═══════════════════════════════════════════════════════════════

    public boolean handleClaimUpgradeClick(Player p, ItemStack clicked) {
        ChunkClaim claim = activeFeedingClaims.get(p.getUniqueId());
        if (claim == null) return false;
        if (clicked == null || !clicked.hasItemMeta()) return false;

        if (clicked.getType() == Material.BARRIER) {
            openClaimFeedGui(p, claim);
            return true;
        }

        NamespacedKey upKey = new NamespacedKey(plugin, "upgrade_target");
        if (!clicked.getItemMeta().getPersistentDataContainer().has(upKey, PersistentDataType.INTEGER)) return false;

        int target = clicked.getItemMeta().getPersistentDataContainer().get(upKey, PersistentDataType.INTEGER);
        if (target != claim.getLevel() + 1) {
            p.sendMessage(ChatColor.RED + "Сначала прокачайте предыдущие уровни!");
            return true;
        }

        int vkId = VKChatBridge.getLinkedVkId(p);
        if (!VKChatBridge.hasVkOrPass(p)) {
            p.sendMessage(ChatColor.RED + "❌ Для прокачки привяжите ВКонтакте! (/vklink)");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return true;
        }

        int cost = claim.getNextUpgradeCost();
        int rep = VKChatBridge.getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " (У вас: " + rep + ").");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return true;
        }

        VKChatBridge.takeReputation(vkId, cost);
        claim.setLevel(claim.getLevel() + 1);
        claim.addDurability(0);
        plugin.getNationManager().saveAll();

        p.sendMessage("");
        p.sendMessage(ChatColor.LIGHT_PURPLE + "⬆ Приват прокачан до уровня " + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()) + "!");
        p.sendMessage(ChatColor.GRAY + "Списано " + ChatColor.GOLD + cost + " реп. ВК" + ChatColor.GRAY + ". Новый запас прочности: " + ChatColor.GREEN + claim.getMaxDurability() + ".");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);

        openClaimUpgradeGui(p, claim);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════

    private void addToggle(Inventory inv, int slot, ChunkClaim claim, boolean unlocked, boolean enabled,
                           String onName, String offName, String desc, String reqLevel) {
        ItemStack item = new ItemStack(unlocked ? (enabled ? Material.MAGMA_BLOCK : Material.COBBLESTONE) : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (unlocked) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', enabled ? onName + " &7[ВКЛ]" : offName + " &7[ВЫКЛ]"));
        } else {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7" + desc.split(" ")[0] + " &8(ур. " + reqLevel + ")"));
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        if (unlocked) {
            lore.add(enabled ? ChatColor.GREEN + "✓ Включена" : ChatColor.RED + "✗ Выключена");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Клик — переключить");
        } else {
            lore.add(ChatColor.RED + "Требуется " + reqLevel + " уровень привата");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void consumeInventoryItem(Player p, Material mat, int amount) {
        int toRemove = amount;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == mat) {
                if (item.getAmount() <= toRemove) {
                    toRemove -= item.getAmount();
                    p.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                    toRemove = 0;
                }
                if (toRemove == 0) break;
            }
        }
    }
}
