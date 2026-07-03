package ru.example.vkchatgear.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatgear.VKChatGearPlugin;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.util.VKChatBridge;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SalvageCommand implements CommandExecutor, Listener, TabCompleter {
    private final VKChatGearPlugin plugin;
    public static final String GUI_TITLE = ChatColor.DARK_GREEN + "♻️ Утилизация снаряжения";

    public SalvageCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эту команду может использовать только игрок!");
            return true;
        }

        Player p = (Player) sender;
        openSalvageGui(p);
        return true;
    }

    public void openSalvageGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Заполнение серым стеклом (верхняя и нижняя рамки)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, glass);
        }
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Ряд 1 (слоты 9..17) оставляем пустыми (null) для массового ввода предметов
        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, null);
        }

        // Slot 22: Кнопка массовой утилизации
        ItemStack actionBtn = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta actionMeta = actionBtn.getItemMeta();
        actionMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "♻️ Начать утилизацию");
        List<String> actionLore = new ArrayList<>();
        actionLore.add(ChatColor.GRAY + "Положите один или несколько предметов");
        actionLore.add(ChatColor.GRAY + "в средний ряд (слоты 9-17) и нажмите");
        actionLore.add(ChatColor.GRAY + "эту кнопку для мгновенного разбора.");
        actionLore.add("");
        actionLore.add(ChatColor.GRAY + "Стоимость разборки каждого: " + ChatColor.GOLD + "5 реп. ВК");
        actionLore.add(ChatColor.YELLOW + "▶ Запустить массовую переплавку ◀");
        actionMeta.setLore(actionLore);
        actionBtn.setItemMeta(actionMeta);
        inv.setItem(22, actionBtn);

        // Slot 26: Книга информации
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoBtn.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "ℹ Справка об Утилизации");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Разбирайте ненужное MMO-оружие и броню:");
        infoLore.add(ChatColor.GRAY + "  • " + ChatColor.GRAY + "Обычный " + ChatColor.GRAY + "-> " + ChatColor.GOLD + "10 реп. / 50 XP");
        infoLore.add(ChatColor.GRAY + "  • " + ChatColor.GREEN + "Необычный " + ChatColor.GRAY + "-> " + ChatColor.GOLD + "25 реп. / 100 XP");
        infoLore.add(ChatColor.GRAY + "  • " + ChatColor.BLUE + "Редкий " + ChatColor.GRAY + "-> " + ChatColor.GOLD + "75 реп. / 250 XP");
        infoLore.add(ChatColor.GRAY + "  • " + ChatColor.LIGHT_PURPLE + "Эпический " + ChatColor.GRAY + "-> " + ChatColor.GOLD + "200 реп. / 500 XP");
        infoLore.add(ChatColor.GRAY + "  • " + ChatColor.GOLD + "Легендарный " + ChatColor.GRAY + "-> " + ChatColor.GOLD + "500 реп. / 1000 XP");
        infoLore.add("");
        infoLore.add(ChatColor.AQUA + "💎 Ресурсы по Тиру и Редкости:");
        infoLore.add(ChatColor.GRAY + "  Чем выше тир материала (Дерево ➔ Незерит)");
        infoLore.add(ChatColor.GRAY + "  и редкость предмета, тем ценнее лут!");
        infoLore.add(ChatColor.GRAY + "  • Например, при утилизации ");
        infoLore.add(ChatColor.GOLD + "    Деревянной Легендарки " + ChatColor.GRAY + "выпадают " + ChatColor.AQUA + "Алмазы!");
        infoLore.add("");
        infoLore.add(ChatColor.AQUA + "⚡ БОНУСЫ:");
        infoLore.add(ChatColor.GRAY + "  • Заточка: " + ChatColor.YELLOW + "+10% наград за уровень");
        infoLore.add(ChatColor.GRAY + "  • Руны: " + ChatColor.YELLOW + "+100 репутации за каждую руну!");
        infoLore.add(ChatColor.GRAY + "  • Кузнец: " + ChatColor.YELLOW + "+1% к репутации/XP за уровень");
        infoLore.add(ChatColor.GRAY + "  • Джекпот: " + ChatColor.GREEN + "3% шанс удвоить всю награду!");
        infoMeta.setLore(infoLore);
        infoBtn.setItemMeta(infoMeta);
        inv.setItem(26, infoBtn);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        
        e.setCancelled(true); // Всегда отменяем — защита от двойного клика
        int slot = e.getRawSlot();
        
        // Разрешаем перемещение в средний ряд (слоты для предметов) и инвентарь игрока
        if ((slot >= 9 && slot <= 17) || slot >= 27) {
            e.setCancelled(false);
            return;
        }
        
        if (slot == 22) {
            Player p = (Player) e.getWhoClicked();
            processSalvage(p, e.getInventory());
        }
    }

    private void processSalvage(Player p, Inventory inv) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "❌ Для утилизации привяжите свой ВКонтакте! (/vklink)");
            return;
        }

        // Сканируем средний ряд на наличие предметов для утилизации
        List<Integer> slotsToSalvage = new ArrayList<>();
        int itemsCount = 0;

        for (int i = 9; i <= 17; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    List<String> lore = item.getItemMeta().getLore();
                    if (lore != null && lore.toString().contains("Редкость:")) {
                        slotsToSalvage.add(i);
                        itemsCount += item.getAmount();
                    }
                }
            }
        }

        if (slotsToSalvage.isEmpty()) {
            p.sendMessage(ChatColor.RED + "❌ Положите один или несколько MMO-предметов в средний ряд утилизации!");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.2f);
            return;
        }

        // Общая стоимость разборки (налог): 5 репутации за каждый предмет в стопке
        int totalTax = itemsCount * 5;
        int currentRep = VKChatBridge.getReputation(vkId);

        if (currentRep < totalTax) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации для массового налога разборки! Требуется " + totalTax + " реп. ВК (У вас: " + currentRep + ").");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            return;
        }

        // Списываем налог
        VKChatBridge.takeReputation(vkId, totalTax);

        int totalFinalRep = 0;
        int totalFinalExp = 0;
        List<ItemStack> allReturns = new ArrayList<>();
        java.util.Random random = new java.util.Random();

        // Бонус профессии "Кузнец" (+1% за уровень)
        double jobBonusMultiplier = 1.0;
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                int blacksmithLvl = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, p.getUniqueId(), "blacksmith");
                jobBonusMultiplier += (blacksmithLvl * 0.01);
            }
        } catch (Exception ignored) {}

        // Разбираем все предметы
        for (int slot : slotsToSalvage) {
            ItemStack item = inv.getItem(slot);
            int itemAmount = item.getAmount();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore();
            String loreString = lore != null ? lore.toString() : "";

            // Определение редкости по лору
            String rarity = "common";
            if (loreString.contains("ЛЕГЕНДАРНЫЙ")) {
                rarity = "legendary";
            } else if (loreString.contains("Эпический")) {
                rarity = "epic";
            } else if (loreString.contains("Редкий")) {
                rarity = "rare";
            } else if (loreString.contains("Необычный")) {
                rarity = "uncommon";
            }

            int baseRep = 10;
            int baseExp = 50;

            switch (rarity) {
                case "legendary": baseRep = 500; baseExp = 1000; break;
                case "epic": baseRep = 200; baseExp = 500; break;
                case "rare": baseRep = 75; baseExp = 250; break;
                case "uncommon": baseRep = 25; baseExp = 100; break;
                default: baseRep = 10; baseExp = 50; break;
            }

            // Уровень заточки (+10% за уровень)
            int upgradeLvl = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
            double upgradeMultiplier = 1.0 + (upgradeLvl * 0.1);

            // Руны
            int runeCount = 0;
            if (lore != null) {
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line).trim();
                    if (line.contains("✨") && !stripped.contains("Редкость") && !stripped.contains("Эволюция") && !stripped.contains("Очищено") && !stripped.contains("ПРОБУЖДЕНИЕ")) {
                        runeCount++;
                    }
                }
            }
            int runeBonus = runeCount * 100;

            // Считаем награды для каждого предмета в стаке
            for (int a = 0; a < itemAmount; a++) {
                int itemRep = (int) Math.round((baseRep * upgradeMultiplier + runeBonus) * jobBonusMultiplier);
                int itemExp = (int) Math.round((baseExp * upgradeMultiplier) * jobBonusMultiplier);

                totalFinalRep += itemRep;
                totalFinalExp += itemExp;

                // Начисляем возвращаемый лут на основе материала и редкости!
                List<ItemStack> returns = calculateSalvageReturns(item.getType(), rarity);
                allReturns.addAll(returns);
            }

            // Удаляем предмет из слота
            inv.setItem(slot, null);
        }

        // Просчет Критического разбора (Джекпот, шанс 3% - удвоение!)
        boolean isCrit = random.nextInt(100) < 3;
        if (isCrit) {
            totalFinalRep *= 2;
            totalFinalExp *= 2;
        }

        // Начисляем репутацию и опыт
        VKChatBridge.addPoints(vkId, totalFinalRep);
        p.giveExp(totalFinalExp);

        // Объединяем одинаковые предметы для чистого вывода в чат и инвентарь
        Map<Material, Integer> consolidated = new HashMap<>();
        List<ItemStack> finalDropList = new ArrayList<>();

        for (ItemStack ret : allReturns) {
            consolidated.put(ret.getType(), consolidated.getOrDefault(ret.getType(), 0) + ret.getAmount());
        }

        for (Map.Entry<Material, Integer> entry : consolidated.entrySet()) {
            Material mat = entry.getKey();
            int amt = entry.getValue();
            
            while (amt > 0) {
                int stackSize = Math.min(mat.getMaxStackSize(), amt);
                finalDropList.add(new ItemStack(mat, stackSize));
                amt -= stackSize;
            }
        }

        // Выдаем объединенный возвращенный лут
        for (ItemStack drop : finalDropList) {
            p.getInventory().addItem(drop).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
        }

        // Эффекты
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.2f, 0.8f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
        p.getWorld().spawnParticle(org.bukkit.Particle.LAVA, p.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);

        if (isCrit) {
            p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, p.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 0.15);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.2f);
            p.sendMessage(ChatColor.GOLD + "🌟 [КРИТИЧЕСКИЙ РАЗБОР] Награды за всю партию удвоены!");
        }

        StringBuilder msg = new StringBuilder();
        msg.append("&d&l[Утилизация] &aВы успешно утилизировали партию из &e").append(itemsCount).append(" предметов&a!\n");
        msg.append("&aПолучено: &b+").append(totalFinalRep).append(" репутации ВК &aи &e+").append(totalFinalExp).append(" опыта MC!\n");
        
        if (!consolidated.isEmpty()) {
            msg.append("&aИзвлечены ресурсы: ");
            for (Map.Entry<Material, Integer> entry : consolidated.entrySet()) {
                msg.append("&e• ").append(entry.getValue()).append(" шт. ").append(entry.getKey().name()).append(" ");
            }
        }
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.toString().trim()));
    }

    private int getMaterialTier(Material mat) {
        String name = mat.name();
        if (name.contains("WOODEN") || name.contains("LEATHER") || name.contains("BOW") || name.contains("SHIELD")) return 1;
        if (name.contains("STONE") || name.contains("CHAINMAIL")) return 2;
        if (name.contains("IRON")) return 3;
        if (name.contains("GOLDEN") || name.contains("GOLD")) return 4;
        if (name.contains("DIAMOND")) return 5;
        if (name.contains("NETHERITE")) return 6;
        return 1; // По умолчанию
    }

    private List<ItemStack> calculateSalvageReturns(Material baseMaterial, String rarity) {
        List<ItemStack> returns = new ArrayList<>();
        int matTier = getMaterialTier(baseMaterial);
        
        int rarityTier = 1;
        switch (rarity) {
            case "legendary": rarityTier = 5; break;
            case "epic": rarityTier = 4; break;
            case "rare": rarityTier = 3; break;
            case "uncommon": rarityTier = 2; break;
            default: rarityTier = 1; break;
        }
        
        int lootTier = matTier + rarityTier - 1; // Диапазон от 1 до 10
        java.util.Random rand = new java.util.Random();
        
        switch (lootTier) {
            case 1:
                returns.add(new ItemStack(Material.OAK_LOG, 1 + rand.nextInt(2)));
                if (rand.nextInt(100) < 50) returns.add(new ItemStack(Material.COAL, 1 + rand.nextInt(2)));
                break;
            case 2:
                returns.add(new ItemStack(Material.COBBLESTONE, 2 + rand.nextInt(3)));
                returns.add(new ItemStack(Material.COAL, 1 + rand.nextInt(2)));
                break;
            case 3:
                returns.add(new ItemStack(Material.IRON_INGOT, 1 + rand.nextInt(2)));
                break;
            case 4:
                returns.add(new ItemStack(Material.GOLD_INGOT, 1 + rand.nextInt(2)));
                if (rand.nextInt(100) < 50) returns.add(new ItemStack(Material.REDSTONE, 2 + rand.nextInt(3)));
                break;
            case 5: // Деревянная Легендарка или обычный Алмазный предмет!
                returns.add(new ItemStack(Material.DIAMOND, 1));
                if (rand.nextInt(100) < 30) returns.add(new ItemStack(Material.EMERALD, 1));
                break;
            case 6:
                returns.add(new ItemStack(Material.DIAMOND, 1 + rand.nextInt(2)));
                returns.add(new ItemStack(Material.EMERALD, 1 + rand.nextInt(2)));
                break;
            case 7:
                returns.add(new ItemStack(Material.DIAMOND, 2 + rand.nextInt(2)));
                returns.add(new ItemStack(Material.QUARTZ, 3 + rand.nextInt(5)));
                break;
            case 8:
                if (rand.nextInt(100) < 40) {
                    returns.add(new ItemStack(Material.NETHERITE_SCRAP, 1));
                } else {
                    returns.add(new ItemStack(Material.DIAMOND, 3 + rand.nextInt(2)));
                }
                break;
            case 9:
                if (rand.nextInt(100) < 20) {
                    returns.add(new ItemStack(Material.NETHERITE_INGOT, 1));
                } else {
                    returns.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + rand.nextInt(2)));
                    returns.add(new ItemStack(Material.DIAMOND, 4 + rand.nextInt(2)));
                }
                break;
            case 10: // Алмазная или незеритовая легендарка!
                returns.add(new ItemStack(Material.NETHERITE_INGOT, 1));
                returns.add(new ItemStack(Material.DIAMOND, 4 + rand.nextInt(3)));
                if (rand.nextInt(100) < 15) {
                    returns.add(new ItemStack(Material.NETHER_STAR, 1));
                }
                break;
        }
        return returns;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        
        // Безопасный массовый возврат предметов на закрытии GUI (предотвращение потери вещей)
        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        List<ItemStack> toReturn = new ArrayList<>();

        for (int i = 9; i <= 17; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                toReturn.add(item);
            }
        }

        if (!toReturn.isEmpty()) {
            for (ItemStack item : toReturn) {
                p.getInventory().addItem(item).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
            }
            p.sendMessage(ChatColor.YELLOW + "⚠ Снаряжение из слотов утилизации возвращено в инвентарь.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
