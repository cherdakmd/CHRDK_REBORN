package ru.example.vkchatnations.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NationGuiListener implements Listener {
    private final VKChatNationsPlugin plugin;
    private final String NATION_SELECT_TITLE = ChatColor.DARK_RED + "ВЫБЕРИТЕ НАЦИЮ";
    private final String NATION_INFO_TITLE = ChatColor.GOLD + "Управление Нацией";
    private final String NATION_SHOP_TITLE = ChatColor.GOLD + "🛒 Магазин Нации";
    private final String BUY_CLAIM_TITLE = ChatColor.GOLD + "🛒 Покупка блоков привата";
    private final String CLAIM_FEED_TITLE = ChatColor.GOLD + "⚡ Питание блока привата";
    private final String CLAIM_UPGRADE_TITLE = ChatColor.DARK_PURPLE + "⬆ Прокачка привата";
    
    private final java.util.Map<UUID, ChunkClaim> activeFeedingClaims = new java.util.concurrent.ConcurrentHashMap<>();

    public NationGuiListener(VKChatNationsPlugin plugin) {
        this.plugin = plugin;

        // Каждые 2 секунды проверяем авторизованных игроков без нации и открываем им окно выбора
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (VKChatPlugin.getInstance().getApi().isFullyAuthorized(p)) {
                        if (!plugin.getNationManager().hasNation(p)) {
                            if (!p.getOpenInventory().getTitle().equals(NATION_SELECT_TITLE)) {
                                openNationSelection(p);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }, 40L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Запрос проверяется шедулером выше только ПОСЛЕ того, как игрок полностью авторизуется и привяжет ВК.
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(NATION_SELECT_TITLE)) {
            Player p = (Player) event.getPlayer();
            // Если игрок авторизован, но пытается закрыть меню выбора наций без выбора — переоткрываем!
            if (VKChatPlugin.getInstance().getApi().isFullyAuthorized(p) && !plugin.getNationManager().hasNation(p)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline() && !plugin.getNationManager().hasNation(p)) {
                        openNationSelection(p);
                    }
                });
            }
        }
    }

    public void openNationSelection(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, NATION_SELECT_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, glass);
        }
        
        // 1. Совет
        inv.setItem(10, createNationSelectionItem(Material.IRON_PICKAXE, "&c&lСовет", "soviet_light",
            "&7«Мир, Труд, Май!» — Великий союз трудящихся",
            "&7и ученых, созидающих процветание.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффект Спешки II &7под землей (Y < 50)",
            "&e• Мутация «Стахановец»:",
            "  &7+20% шанс двойного угля/железа/алмазов.",
            "&e• Мутация «Артельный Труд»:",
            "  &7+10% скорость копания за соратника рядом.",
            "&e• Свинцовая Кожа &7(Иммунитет к Кислотному Дождю)",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Ударник Труда» &7(Спешка III при ношении)"
        ));

        // 2. КГБ
        inv.setItem(19, createNationSelectionItem(Material.RED_BANNER, "&4&lКГБ", "soviet_dark",
            "&7«Секретность и контроль» — Тайный орган,",
            "&7действующий из тени ради общего порядка.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффекты Скорости I &7и &eНевидимости I",
            "&e• Мутация «Спецагент КГБ»:",
            "  &7Невидимость на 10с при шифте без оружия.",
            "&e• Мутация «Противоядие КГБ»:",
            "  &7Полный иммунитет к эффектам Отравления.",
            "&e• Мутация «Ночной Охотник»:",
            "  &7+20% к урону ночью и в темноте.",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Танкист» &7(Сопротивление II, Медлительность II)"
        ));
        
        // 3. Волхвы
        inv.setItem(13, createNationSelectionItem(Material.ENCHANTED_BOOK, "&a&lВолхвы", "pagan_light",
            "&7«Единение с Матерью-Природой» — Мудрецы,",
            "&7черпающие созидательную силу из лесов.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффект Регенерации I",
            "&e• Мутация «Единение с Лесом»:",
            "  &7Регенерация II и Скорость I в лесах.",
            "&e• Мутация «Сила Волхвов»:",
            "  &745% шанс мгновенно вырастить посевы рукой.",
            "&e• Мутация «Целебные Травы»:",
            "  &7+4 HP (2 сердца) при поедании ягод и яблок.",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Волхв» &7(Дыхание под водой, Грация дельфина)"
        ));

        // 4. Культ
        inv.setItem(22, createNationSelectionItem(Material.BONE, "&2&lКульт", "pagan_dark",
            "&7«Тьма Нави» — Древний Культ Чернобога,",
            "&7обретающий силу через кровь и мучения врагов.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффект Силы I &7(Постоянное увеличение урона)",
            "&e• Мутация «Жертвенные Когти»:",
            "  &7Восполнение 10% здоровья от ХП жертвы.",
            "&e• Мутация «Касание Нави»:",
            "  &715% шанс иссушить цель на 4 секунды.",
            "&e• Мутация «Кровавая Ярость»:",
            "  &7+30% ближнего урона при ХП < 30%.",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Бессмертный» &7(Сопротивление I, Медлительность I)"
        ));
        
        // 5. Русь
        inv.setItem(16, createNationSelectionItem(Material.SHIELD, "&e&lРусь", "imperial_light",
            "&7«За Царя и Отечество!» — Великие витязи,",
            "&7несокрушимо стоящие на страже границ.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффект Сопротивления Урону I",
            "&e• Мутация «Богатырская Закалка»:",
            "  &7Иммунитет к отдаче + Резист I при блоке щитом.",
            "&e• Мутация «Оберег Руси»:",
            "  &7Неуязвимость на 3 секунды при ХП < 15%.",
            "&e• Мутация «Твердая Поступь»:",
            "  &7Иммунитет к трещинам Землетрясения.",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Богатырь» &7(Сопротивление отбрасыванию, Сила I)"
        ));

        // 6. Опричнина
        inv.setItem(25, createNationSelectionItem(Material.NETHERITE_SWORD, "&6&lОпричнина", "imperial_dark",
            "&7«Кара божия» — Грозные слуги престола,",
            "&7искореняющие ересь и предательство.",
            "&f",
            "&6⚡ Стартовые эффекты и бонусы:",
            "&e• Эффект Прыгучести II",
            "&e• Мутация «Карательный Меч»:",
            "  &7Каждый 5-й удар наносит критический урон x1.5.",
            "&e• Мутация «Аура Страха»:",
            "  &7Мобам рядом накладывается Замедление и Слабость.",
            "&e• Мутация «Опричный Прыжок»:",
            "  &7Дарует полный иммунитет к урону от падения,",
            "&f",
            "&d🛡️ Классовый сет брони при вступлении:",
            "&a• Сет «Ясный Сокол» &7(Скорость III, Прыгучесть III)"
        ));

        p.openInventory(inv);
    }

    private ItemStack createNationSelectionItem(Material m, String name, String nationId, String... descLines) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        
        List<String> lore = new ArrayList<>();
        for (String line : descLines) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "▶ Нажмите, чтобы присягнуть на верность! ◀");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nation_id"), PersistentDataType.STRING, nationId);
        i.setItemMeta(meta);
        return i;
    }

    public void openGui(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) {
            openNationSelection(p);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, NATION_INFO_TITLE);
        String name = plugin.getConfig().getString("nations." + nation + ".name", nation);
        int bank = plugin.getNationManager().getBank(nation);
        
        // Считаем активные приваты игрока
        int claimCount = 0;
        for (ru.example.vkchatnations.data.ChunkClaim claim : plugin.getNationManager().getNationClaims().values()) {
            if (claim.getOwner().equals(p.getUniqueId())) {
                claimCount++;
            }
        }
        
        ItemStack info = createItem(Material.BEACON, ChatColor.translateAlternateColorCodes('&', name),
            ChatColor.GRAY + "Ваша глобальная нация.", ChatColor.GRAY + "Вы не можете атаковать своих соотечественников.");
        
        ItemStack energy = createItem(Material.REDSTONE_BLOCK, ChatColor.AQUA + "Ваша собственность",
            ChatColor.GRAY + "Активных приватов: " + ChatColor.GREEN + claimCount + " / 5",
            ChatColor.GRAY + "Казна нации: " + ChatColor.YELLOW + bank);

        ItemStack mutations = createItem(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE + "🧬 Лаборатория Мутаций",
            ChatColor.GRAY + "Откройте меню прокачки пассивных",
            ChatColor.GRAY + "и активных мутаций вашего персонажа.",
            "",
            ChatColor.YELLOW + "Нажмите, чтобы открыть меню!");

        ItemStack shop = createItem(Material.CHEST, ChatColor.GOLD + "🛒 Магазин Нации",
            ChatColor.GRAY + "Приобретайте уникальное национальное",
            ChatColor.GRAY + "снаряжение и классовую броню фракции.",
            "",
            ChatColor.YELLOW + "Нажмите, чтобы открыть магазин!");
            
        ItemStack claimBuy = createItem(Material.GOLD_BLOCK, ChatColor.YELLOW + "🔰 Покупка Блоков Привата",
            ChatColor.GRAY + "Приобретайте блоки привата разной мощности",
            ChatColor.GRAY + "и ставьте их в мире для защиты земель.",
            "",
            ChatColor.YELLOW + "Нажмите для открытия!");
        
        inv.setItem(9, info);
        inv.setItem(11, mutations);
        inv.setItem(13, energy);
        inv.setItem(15, shop);
        inv.setItem(17, claimBuy);

        p.openInventory(inv);
    }

    private ItemStack createItem(Material m, String name, String... loreLines) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();
        for (String l : loreLines) {
            if (l != null && !l.isEmpty() && !l.contains(" ")) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nation_id"), PersistentDataType.STRING, l);
            } else if (l != null) {
                lore.add(ChatColor.translateAlternateColorCodes('&', l));
            }
        }
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    public void openClaimShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, BUY_CLAIM_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Small Block
        ItemStack small = plugin.getNationManager().getSmallClaimBlockItem();
        ItemMeta sMeta = small.getItemMeta();
        List<String> sLore = sMeta.getLore();
        sLore.add("");
        sLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "100 реп. ВК");
        sLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        sMeta.setLore(sLore);
        sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, 100);
        sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_radius"), PersistentDataType.INTEGER, 8);
        small.setItemMeta(sMeta);

        // Medium Block
        ItemStack medium = plugin.getNationManager().getMediumClaimBlockItem();
        ItemMeta mMeta = medium.getItemMeta();
        List<String> mLore = mMeta.getLore();
        mLore.add("");
        mLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "250 реп. ВК");
        mLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        mMeta.setLore(mLore);
        mMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, 250);
        mMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_radius"), PersistentDataType.INTEGER, 16);
        medium.setItemMeta(mMeta);

        // Large Block
        ItemStack large = plugin.getNationManager().getLargeClaimBlockItem();
        ItemMeta lMeta = large.getItemMeta();
        List<String> lLore = lMeta.getLore();
        lLore.add("");
        lLore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + "600 реп. ВК");
        lLore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        lMeta.setLore(lLore);
        lMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buy_block_cost"), PersistentDataType.INTEGER, 600);
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

    public void openClaimFeedGui(Player p, ChunkClaim claim) {
        activeFeedingClaims.put(p.getUniqueId(), claim);
        Inventory inv = Bukkit.createInventory(null, 27, CLAIM_FEED_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

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
        infoLore.add(ChatColor.GRAY + "Владелец: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(claim.getOwner()).getName());
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Подсказка: Прочность падает ежедневно на -2 ед.");
        infoLore.add(ChatColor.GRAY + "Если прочность упадет до 0, приват разрушится,");
        infoLore.add(ChatColor.GRAY + "а блок привата исчезнет!");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);

        // 4. Прокачка привата (5 уровней)
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

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Закрыть");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
    }

    public void openClaimUpgradeGui(Player p, ChunkClaim claim) {
        activeFeedingClaims.put(p.getUniqueId(), claim);
        Inventory inv = Bukkit.createInventory(null, 27, CLAIM_UPGRADE_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatPlugin.getInstance().getApi().getReputation(vkId) : 0;

        // Шапка: текущий статус привата
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

        // 5 уровней прокачки в ряд (слоты 11–15)
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
                if (vkId == -1) {
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

        // Кнопка «Назад»
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Назад");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
    }

    public void openNationShop(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, NATION_SHOP_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        String setKey = getNationSetKey(nation);
        String setName = getSetName(setKey);
        String setBonus = getSetBonus(setKey);

        inv.setItem(10, createShopArmorItem(Material.IRON_HELMET, "Шлем " + setName, setKey, setBonus, "helmet", 150));
        inv.setItem(11, createShopArmorItem(Material.IRON_CHESTPLATE, "Нагрудник " + setName, setKey, setBonus, "chestplate", 150));
        inv.setItem(12, createShopArmorItem(Material.IRON_LEGGINGS, "Поножи " + setName, setKey, setBonus, "leggings", 150));
        inv.setItem(13, createShopArmorItem(Material.IRON_BOOTS, "Сапоги " + setName, setKey, setBonus, "boots", 150));

        addNationalShopItems(inv, nation, 14, 15, 16);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "« Назад");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        p.openInventory(inv);
    }

    private String getSetName(String setKey) {
        switch (setKey) {
            case "udarnik": return "Ударник Труда";
            case "tankist": return "Танкист";
            case "volhv": return "Волхв";
            case "koshchey": return "Бессмертный";
            case "bogatyr": return "Богатырь";
            case "sokol": return "Ясный Сокол";
            default: return "Ударник";
        }
    }

    private String getSetBonus(String setKey) {
        switch (setKey) {
            case "udarnik": return "&c[Бонус Сета: Спешка III, дебафф: Слабость I]";
            case "tankist": return "&c[Бонус Сета: Сопротивление I, Невидимость I, дебафф: Медлительность II]";
            case "volhv": return "&a[Бонус Сета: Дыхание под водой, Регенерация II днем под солнцем]";
            case "koshchey": return "&a[Бонус Сета: Сила I, дебафф: Медлительность I]";
            case "bogatyr": return "&a[Бонус Сета: Сопротивление I, Сила I, дебафф: Утомление I]";
            case "sokol": return "&a[Бонус Сета: Скорость II, Прыгучесть II]";
            default: return "";
        }
    }

    private ItemStack createShopArmorItem(Material mat, String name, String setKey, String setBonus, String type, int cost) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Редкость: " + ChatColor.LIGHT_PURPLE + "[ЭПИЧЕСКИЙ]");
        lore.add("");
        lore.add(ChatColor.GOLD + "Часть сета: " + ChatColor.YELLOW + setKey);
        lore.add(ChatColor.translateAlternateColorCodes('&', setBonus));
        lore.add("");
        lore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + cost + " реп. ВК");
        lore.add(ChatColor.YELLOW + "▶ Кликните для покупки ◀");
        meta.setLore(lore);

        NamespacedKey itemKey = new NamespacedKey(plugin, "shop_item_type");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "armor_" + type);
        item.setItemMeta(meta);
        return item;
    }

    private void addNationalShopItems(Inventory inv, String nation, int s1, int s2, int s3) {
        if (nation.equals("soviet_light")) {
            inv.setItem(s1, createNationalItem(Material.IRON_PICKAXE, "☭ Кирка Пятилетки", "soviet_pickaxe", 250,
                "&eПассивный эффект: &aИндустриализация",
                "&7Позволяет ломать блоки руды",
                "&7на 15% быстрее обычного."));
            inv.setItem(s2, createNationalItem(Material.BOOK, "📕 Манифест Пролетариата", "soviet_manifesto", 250,
                "&eАктивное свойство (ПКМ): &aГолос Труда",
                "&7Дарует эффект Силы I всем союзникам",
                "&7в радиусе 8 блоков на 30 сек. Кд 1 мин."));
            inv.setItem(s3, createNationalItem(Material.QUARTZ, "⚙️ Индустриальный Кристалл", "soviet_crystal", 250,
                "&eАктивное свойство (ПКМ): &aУдарный План",
                "&7Дарует эффект Спешки II на 1 минуту.",
                "&7Перезарядка: 2 минуты."));
        } else if (nation.equals("soviet_dark")) {
            inv.setItem(s1, createNationalItem(Material.LEATHER_HELMET, "🕶️ Тактические Очки КГБ", "kgb_glasses", 250,
                "&eПассивный эффект: &aТепловизор",
                "&7Дарует постоянное Ночное Зрение",
                "&7при ношении данного шлема."));
            inv.setItem(s2, createNationalItem(Material.IRON_SWORD, "🗡️ Кинжал Смерша", "kgb_dagger", 250,
                "&eПассивный эффект: &aТихая Ликвидация",
                "&7Удары по противникам со спины",
                "&7накладывают Иссушение I на 5 сек."));
            inv.setItem(s3, createNationalItem(Material.GLASS_BOTTLE, "🧪 Сыворотка Скрытности", "kgb_serum", 250,
                "&eАктивное свойство (ПКМ): &aМаскировка",
                "&7Дарует Невидимость I и Скорость II",
                "&7на 30 секунд. Кд 2 минуты."));
        } else if (nation.equals("pagan_light")) {
            inv.setItem(s1, createNationalItem(Material.WOODEN_HOE, "🌿 Древесный Посох Волхвов", "pagan_staff", 250,
                "&eАктивное свойство (ПКМ): &aДыхание Леса",
                "&7Мгновенно исцеляет вас на 4 HP.",
                "&7Перезарядка: 30 секунд."));
            inv.setItem(s2, createNationalItem(Material.FEATHER, "🪶 Оберег ветра Стрибога", "pagan_amulet", 250,
                "&eАктивное свойство (ПКМ): &aПорыв Ветра",
                "&7Резко запускает вас вперед и вверх",
                "&7(двойной прыжок). Кд 15 секунд."));
            inv.setItem(s3, createNationalItem(Material.HONEY_BOTTLE, "🏺 Целебный Отвар Лешего", "pagan_brew", 250,
                "&eАктивное свойство (ПКМ): &aЖивая Вода",
                "&7Восполняет 10 HP и дает Регенерацию II",
                "&7на 15 секунд. Кулдаун: 1 минута."));
        } else if (nation.equals("pagan_dark")) {
            inv.setItem(s1, createNationalItem(Material.WITHER_SKELETON_SKULL, "💀 Оскверненный Идол Нави", "pagan_idol", 250,
                "&eАктивное свойство (ПКМ): &aГнев Нави",
                "&7Накладывает Иссушение II на всех врагов",
                "&7в радиусе 6 блоков на 6с. Кд 1 мин."));
            inv.setItem(s2, createNationalItem(Material.IRON_HOE, "🌾 Ритуальный Серп Жатвы", "pagan_sickle", 250,
                "&eПассивный эффект: &aЖатва Душ",
                "&7Каждое убийство монстра восполняет",
                "&7вам 1 HP здоровья."));
            inv.setItem(s3, createNationalItem(Material.DRAGON_BREATH, "🩸 Кровь Жертвенного Алтаря", "pagan_infusion", 250,
                "&eАктивное свойство (ПКМ): &aКульт Ярости",
                "&7Дает Силу II на 15 сек, но накладывает",
                "&7Иссушение I на 5 сек. Кд 1 мин."));
        } else if (nation.equals("imperial_light")) {
            inv.setItem(s1, createNationalItem(Material.BLAZE_ROD, "👑 Золотой Царский Скипетр", "imperial_scepter", 250,
                "&eАктивное свойство (ПКМ): &aВоля Монарха",
                "&7Дарует Сопротивление II союзникам",
                "&7в радиусе 8 блоков на 10с. Кд 1 мин."));
            inv.setItem(s2, createNationalItem(Material.SHIELD, "🛡️ Богатырский Щит Святогора", "imperial_shield", 250,
                "&eПассивный эффект: &aСтена Руси",
                "&7Блокирует 50% входящего урона",
                "&7при активной защите щитом."));
            inv.setItem(s3, createNationalItem(Material.BREAD, "🍞 Царский Каравай Насыщения", "imperial_bread", 250,
                "&eActive Property: &aRussian Satiety",
                "&7Full satiety fill and saturation for 2 mins."));
        } else if (nation.equals("imperial_dark")) {
            inv.setItem(s1, createNationalItem(Material.IRON_SWORD, "🗡️ Карающая Сабля Опричника", "imperial_saber", 250,
                "&eПассивный эффект: &aСекущий Удар",
                "&7Каждый удар имеет 25% шанс вызвать",
                "&7кровотечение (эффект яда) на 4 сек."));
            inv.setItem(s2, createNationalItem(Material.IRON_NUGGET, "⛓️ Кандалы Тайного Сыска", "imperial_shackles", 250,
                "&eАктивное свойство (ПКМ): &aВзять под стражу",
                "&7Накладывает Замедление V на цель в руке",
                "&7на 3 секунды. Кулдаун 45 сек."));
            inv.setItem(s3, createNationalItem(Material.HONEY_BOTTLE, "🍷 Золотой Кубок Грозного", "imperial_cup", 250,
                "&eАктивное свойство (ПКМ): &aЦарский Гнев",
                "&7Дарует Силу III на 10 секунд, но накладывает",
                "&7Медлительность I на 5 секунд. Кд 1 мин."));
        }
    }

    private ItemStack createNationalItem(Material mat, String name, String itemId, int cost, String... desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Категория: " + ChatColor.YELLOW + "Национальный Предмет");
        lore.add("");
        for (String d : desc) {
            lore.add(ChatColor.translateAlternateColorCodes('&', d));
        }
        lore.add("");
        lore.add(ChatColor.RED + "Цена: " + ChatColor.GOLD + cost + " реп. ВК");
        lore.add(ChatColor.YELLOW + "▶ Кликните, чтобы приобрести ◀");
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private String getNationSetKey(String nationId) {
        switch (nationId) {
            case "soviet_light": return "udarnik";
            case "soviet_dark": return "tankist";
            case "pagan_light": return "volhv";
            case "pagan_dark": return "koshchey";
            case "imperial_light": return "bogatyr";
            case "imperial_dark": return "sokol";
            default: return "udarnik";
        }
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

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        if (title.equals(NATION_SELECT_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            
            String nationId = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "nation_id"), PersistentDataType.STRING);
            if (nationId != null) {
                plugin.getNationManager().setPlayerNation(p, nationId);
                p.closeInventory();
                
                String nationName = plugin.getConfig().getString("nations." + nationId + ".name", nationId);
                p.sendMessage(" ");
                p.sendMessage(ChatColor.GREEN + "✓ Вы успешно присягнули на верность Нации: " + ChatColor.translateAlternateColorCodes('&', nationName) + ChatColor.GREEN + "!");
                p.sendMessage(ChatColor.GRAY + "Вам выдан стартовый эпический комплект брони вашей Нации. Наденьте его, чтобы активировать пассивный бонус сета!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                giveNationArmorSet(p, getNationSetKey(nationId));
            }
        } 
        else if (title.equals(NATION_INFO_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) {
                if (e.getCurrentItem().getType() == Material.BREWING_STAND) {
                    openMutationsSelection(p);
                } else if (e.getCurrentItem().getType() == Material.CHEST) {
                    openNationShop(p);
                } else if (e.getCurrentItem().getType() == Material.GOLD_BLOCK) {
                    openClaimShop(p);
                }
            }
        }
        else if (title.equals(BUY_CLAIM_TITLE)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;

            if (item.getType() == Material.BARRIER) {
                openGui(p);
                return;
            }

            NamespacedKey costKey = new NamespacedKey(plugin, "buy_block_cost");
            NamespacedKey radiusKey = new NamespacedKey(plugin, "buy_block_radius");
            
            if (item.getItemMeta().getPersistentDataContainer().has(costKey, PersistentDataType.INTEGER)) {
                int cost = item.getItemMeta().getPersistentDataContainer().get(costKey, PersistentDataType.INTEGER);
                int radius = item.getItemMeta().getPersistentDataContainer().get(radiusKey, PersistentDataType.INTEGER);

                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1) {
                    p.sendMessage(ChatColor.RED + "❌ Для покупок привяжите ВКонтакте! (/vklink)");
                    return;
                }

                int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
                if (rep < cost) {
                    p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп. (У вас: " + rep + ").");
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    return;
                }

                // Списываем репутацию
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

                // Выдаем блок привата
                ItemStack blockToGive;
                if (radius == 8) {
                    blockToGive = plugin.getNationManager().getSmallClaimBlockItem();
                } else if (radius == 16) {
                    blockToGive = plugin.getNationManager().getMediumClaimBlockItem();
                } else {
                    blockToGive = plugin.getNationManager().getLargeClaimBlockItem();
                }

                if (!p.getInventory().addItem(blockToGive).isEmpty()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), blockToGive);
                }

                p.sendMessage(ChatColor.GREEN + "✓ Вы успешно купили Блок Привата радиусом " + radius + " блоков за " + ChatColor.GOLD + cost + " реп. ВК!");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }
        else if (title.equals(CLAIM_FEED_TITLE)) {
            e.setCancelled(true);
            ChunkClaim claim = activeFeedingClaims.get(p.getUniqueId());
            if (claim == null) return;

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            if (clicked.getType() == Material.BARRIER) {
                p.closeInventory();
                return;
            }

            // Кнопка «Прокачать приват» (слот 4)
            if (e.getRawSlot() == 4) {
                openClaimUpgradeGui(p, claim);
                return;
            }

            if (clicked.getType() == Material.DIAMOND) {
                // Покормить ресурсами
                if (p.getInventory().contains(Material.NETHERITE_SCRAP, 1)) {
                    consumeInventoryItem(p, Material.NETHERITE_SCRAP, 1);
                    claim.addDurability(200);
                    p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 1 Незеритовый лом! Прочность привата увеличена на +200.");
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
                }
                else if (p.getInventory().contains(Material.DIAMOND, 1)) {
                    consumeInventoryItem(p, Material.DIAMOND, 1);
                    claim.addDurability(50);
                    p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 1 Алмаз! Прочность привата увеличена на +50.");
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                }
                else if (p.getInventory().contains(Material.GOLD_INGOT, 5)) {
                    consumeInventoryItem(p, Material.GOLD_INGOT, 5);
                    claim.addDurability(20);
                    p.sendMessage(ChatColor.GREEN + "✓ Вы скормили 5 Золотых слитков! Прочность привата увеличена на +20.");
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                } else {
                    p.sendMessage(ChatColor.RED + "❌ У вас нет нужных ресурсов (5 золота, 1 алмаз или 1 незерит) в инвентаре!");
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    return;
                }
                plugin.getNationManager().saveAll();
                openClaimFeedGui(p, claim);
            }
            else if (clicked.getType() == Material.REDSTONE) {
                // Покормить репутацией ВК
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1) {
                    p.sendMessage(ChatColor.RED + "❌ Для питания за репутацию привяжите ВКонтакте! (/vklink)");
                    return;
                }

                int cost = 15;
                int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
                if (rep < cost) {
                    p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " (Ваш баланс: " + rep + ").");
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    return;
                }

                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                claim.addDurability(100);
                plugin.getNationManager().saveAll();

                p.sendMessage(ChatColor.GREEN + "✓ Прочность привата увеличена на +100 за 15 репутации ВК!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                openClaimFeedGui(p, claim);
            }
        }
        else if (title.equals(CLAIM_UPGRADE_TITLE)) {
            e.setCancelled(true);
            ChunkClaim claim = activeFeedingClaims.get(p.getUniqueId());
            if (claim == null) return;

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            if (clicked.getType() == Material.BARRIER) {
                openClaimFeedGui(p, claim);
                return;
            }

            // Проверяем, что кликнули по предмету следующего уровня
            NamespacedKey upKey = new NamespacedKey(plugin, "upgrade_target");
            if (!clicked.getItemMeta().getPersistentDataContainer().has(upKey, PersistentDataType.INTEGER)) return;

            int target = clicked.getItemMeta().getPersistentDataContainer().get(upKey, PersistentDataType.INTEGER);

            // Защита: можно прокачать только следующий уровень
            if (target != claim.getLevel() + 1) {
                p.sendMessage(ChatColor.RED + "Сначала прокачайте предыдущие уровни!");
                return;
            }

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Для прокачки привяжите ВКонтакте! (/vklink)");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return;
            }

            int cost = claim.getNextUpgradeCost();
            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " (У вас: " + rep + ").");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return;
            }

            // Списываем репутацию и повышаем уровень
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            claim.setLevel(claim.getLevel() + 1);
            // При повышении уровня расширяется потолок прочности — даём бонус-«зарядку»
            claim.addDurability(0);
            plugin.getNationManager().saveAll();

            p.sendMessage("");
            p.sendMessage(ChatColor.LIGHT_PURPLE + "⬆ Приват прокачан до уровня " + claim.getLevel() + " — " + ChunkClaim.getLevelName(claim.getLevel()) + "!");
            p.sendMessage(ChatColor.GRAY + "Списано " + ChatColor.GOLD + cost + " реп. ВК" + ChatColor.GRAY + ". Новый запас прочности: " + ChatColor.GREEN + claim.getMaxDurability() + ".");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);

            openClaimUpgradeGui(p, claim);
        }
        else if (title.equals(NATION_SHOP_TITLE)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;

            if (item.getType() == Material.BARRIER) {
                openGui(p);
                return;
            }

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Для покупок привяжите ВКонтакте! (/vklink)");
                return;
            }

            int cost = 0;
            if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING)) {
                cost = 150;
            } else if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "national_item"), PersistentDataType.STRING)) {
                cost = 250;
            }

            if (cost == 0) return;

            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп. (У вас: " + rep + ").");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return;
            }

            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

            org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
            String nation = plugin.getNationManager().getPlayerNation(p);
            String setKey = getNationSetKey(nation);

            ItemStack itemToGive = null;

            if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING)) {
                String type = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_item_type"), PersistentDataType.STRING);
                Material mat = Material.IRON_HELMET;
                String pieceName = "Шлем";
                if (type.contains("chestplate")) { mat = Material.IRON_CHESTPLATE; pieceName = "Нагрудник"; }
                else if (type.contains("leggings")) { mat = Material.IRON_LEGGINGS; pieceName = "Поножи"; }
                else if (type.contains("boots")) { mat = Material.IRON_BOOTS; pieceName = "Сапоги"; }

                itemToGive = createArmorPiece(mat, pieceName + " " + getSetName(setKey), setKey, getSetBonus(setKey), gearPlugin);
            } else {
                itemToGive = item.clone();
                ItemMeta meta = itemToGive.getItemMeta();
                List<String> lore = meta.getLore();
                if (lore.size() >= 3) {
                    lore.remove(lore.size() - 1);
                    lore.remove(lore.size() - 1);
                    lore.remove(lore.size() - 1);
                }
                meta.setLore(lore);
                itemToGive.setItemMeta(meta);
            }

            if (itemToGive != null) {
                if (!p.getInventory().addItem(itemToGive).isEmpty()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), itemToGive);
                }
                p.sendMessage(ChatColor.GREEN + "✓ Вы успешно приобрели " + item.getItemMeta().getDisplayName() + ChatColor.GREEN + " за " + ChatColor.GOLD + cost + " реп. ВК!");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }
        else if (title.equals(ChatColor.LIGHT_PURPLE + "🧬 Мутации вашей Нации")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            NamespacedKey mKey = new NamespacedKey(plugin, "mutation_id");
            
            if (meta.getPersistentDataContainer().has(mKey, PersistentDataType.STRING)) {
                String mId = meta.getPersistentDataContainer().get(mKey, PersistentDataType.STRING);
                
                if (mId != null) {
                    if (plugin.getNationManager().hasMutation(p, mId)) {
                        p.sendMessage(ChatColor.YELLOW + "⚠️ Эта мутация у вас уже активна!");
                        return;
                    }
                    
                    int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                    if (vkId == -1) {
                        p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте! (/vklink)");
                        return;
                    }

                    int cost = 1500; // Мутации покупаются индивидуально за 1500 репутации ВК!
                    int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
                    if (rep >= cost) {
                        // Списываем репутацию лично у игрока
                        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                        plugin.getNationManager().unlockMutation(p.getUniqueId(), mId);
                        
                        String mName = meta.getDisplayName();
                        p.sendMessage(" ");
                        p.sendMessage(ChatColor.LIGHT_PURPLE + "🧬 [Мутация разблокирована] Вы успешно приобрели мутацию: " + ChatColor.translateAlternateColorCodes('&', mName) + ChatColor.LIGHT_PURPLE + "!");
                        p.sendMessage(ChatColor.GRAY + "Теперь этот пассивный эффект постоянно активен у вашего персонажа.");
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                        
                        openMutationsSelection(p);
                    } else {
                        p.sendMessage(ChatColor.RED + "❌ Недостаточно личной репутации ВК! Требуется: " + cost + " (Ваш баланс: " + rep + ").");
                    }
                }
            }
        }
    }

    private void giveNationArmorSet(Player p, String setKey) {
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null) return;

        Material helmetMat = Material.IRON_HELMET;
        Material chestMat = Material.IRON_CHESTPLATE;
        Material legsMat = Material.IRON_LEGGINGS;
        Material bootsMat = Material.IRON_BOOTS;

        String setName = getSetName(setKey);
        String setBonus = getSetBonus(setKey);

        ItemStack helmet = createArmorPiece(helmetMat, "Шлем Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack chest = createArmorPiece(chestMat, "Нагрудник Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack legs = createArmorPiece(legsMat, "Поножи Нации (" + setName + ")", setKey, setBonus, gearPlugin);
        ItemStack boots = createArmorPiece(bootsMat, "Сапоги Нации (" + setName + ")", setKey, setBonus, gearPlugin);

        for (ItemStack item : new ItemStack[]{helmet, chest, legs, boots}) {
            if (!p.getInventory().addItem(item).isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }
    }

    private ItemStack createArmorPiece(Material mat, String displayName, String setKey, String setBonus, org.bukkit.plugin.Plugin gearPlugin) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + displayName);
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Редкость: " + ChatColor.LIGHT_PURPLE + "[ЭПИЧЕСКИЙ]");
        lore.add(ChatColor.GRAY + "Выдан при вступлении в Нацию.");
        lore.add("");
        lore.add(ChatColor.GOLD + "Часть сета: " + ChatColor.YELLOW + setKey);
        lore.add(ChatColor.translateAlternateColorCodes('&', setBonus));
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(gearPlugin, "gear_set");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, setKey);
        meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "gear_set_origin"), PersistentDataType.STRING, "nation");

        item.setItemMeta(meta);
        return item;
    }

    public void openMutationsSelection(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.LIGHT_PURPLE + "🧬 Мутации вашей Нации");

        if (nation.equals("soviet_light")) {
            addMutationItem(inv, 11, "stakhanovite", "⛏️ Стахановец", Material.GOLDEN_PICKAXE, p,
                "§7Спешка II при работе под землей (Y < 50)",
                "§7и шанс 20% на двойной дроп угля/железа.");
                
            addMutationItem(inv, 12, "collective_labor", "👥 Артельный Труд", Material.IRON_SHOVEL, p,
                "§7Увеличивает скорость копания на 10%",
                "§7за каждого гражданина нации в радиусе 15 блоков.");
                
            addMutationItem(inv, 13, "acid_shield", "🌧️ Свинцовая Кожа", Material.SLIME_BALL, p,
                "§7Дарует полный иммунитет к катаклизму",
                "§7Кислотный Дождь (кислота не вредит вам и броне!).");
                
            addMutationItem(inv, 14, "soviet_magnet", "⚙️ Индустриальный Магнит", Material.HOPPER, p,
                "§7Дарует пассивное увеличение собираемого опыта",
                "§7и выпадения предметов при копании руды на +15%.");
                
            addMutationItem(inv, 15, "proletarian_health", "🍎 Здоровье Рабочего", Material.APPLE, p,
                "§7Дарует постоянное пассивное состояние сытости I",
                "§7(уровень голода вашего персонажа никогда не падает).");
                
        } else if (nation.equals("soviet_dark")) {
            addMutationItem(inv, 11, "kgb_clandestine", "👤 Спецагент КГБ", Material.ENDER_PEARL, p,
                "§7Дает полную невидимость на 10 сек при",
                "§7приседании (Shift), если в руках нет оружия.");
                
            addMutationItem(inv, 12, "poison_immunity", "🧪 Противоядие КГБ", Material.MILK_BUCKET, p,
                "§7Дает постоянную пассивную защиту",
                "§7и полный иммунитет к эффекту Отравления.");
                
            addMutationItem(inv, 13, "dark_assassin", "🗡️ Ночной Охотник", Material.OBSIDIAN, p,
                "§7Увеличивает весь ваш урон на +20% ночью",
                "§7или при уровне освещения блока <= 7.");
                
            addMutationItem(inv, 14, "kgb_radar", "🛰️ Радар КГБ", Material.COMPASS, p,
                "§7Постоянно подсвечивает (эффект Glowing)",
                "§7всех враждебных мобов в радиусе 12 блоков.");
                
            addMutationItem(inv, 15, "tactical_strike", "💥 Тактический Удар", Material.GOLDEN_SWORD, p,
                "§7Каждый ваш первый удар по цели из состояния",
                "§7невидимости наносит сокрушительный тройной урон (x3.0).");
                
        } else if (nation.equals("pagan_light")) {
            addMutationItem(inv, 11, "forest_communion", "🌿 Единение с Лесом", Material.OAK_SAPLING, p,
                "§7Дает постоянную Регенерацию II и Скорость I,",
                "§7когда вы находитесь в лесных/таежных биомах.");
                
            addMutationItem(inv, 12, "nature_regrowth", "🌾 Сила Волхвов", Material.WHEAT_SEEDS, p,
                "§7Шанс 45% мгновенно вырастить пшеницу,",
                "§7картофель или морковь при клике пустой рукой.");
                
            addMutationItem(inv, 13, "herbal_healing", "🍎 Целебные Травы", Material.SWEET_BERRIES, p,
                "§7Поедание яблок или сладких ягод",
                "§7мгновенно восполняет вам 4 HP (2 сердца).");
                
            addMutationItem(inv, 14, "pagan_luck", "🍀 Милость Ярилы", Material.RABBIT_FOOT, p,
                "§7Дарует постоянный пассивный эффект удачи I",
                "§7на сервере (повышает лут и шансы улова).");
                
            addMutationItem(inv, 15, "pagan_breath", "🔱 Дыхание Водяного", Material.PRISMARINE_SHARD, p,
                "§7Дарует вашему персонажу постоянную способность",
                "§7дышать под водой (эффект Водного дыхания).");
                
        } else if (nation.equals("pagan_dark")) {
            addMutationItem(inv, 11, "vampiric_claws", "🩸 Жертвенные Когти", Material.GHAST_TEAR, p,
                "§7Восстанавливает 10% от макс. ХП жертвы",
                "§7при убийстве монстров или игроков.");
                
            addMutationItem(inv, 12, "wither_touch", "💀 Касание Нави", Material.WITHER_SKELETON_SKULL, p,
                "§7Любой ваш удар в ближнем бою имеет",
                "§7шанс 15% отравить врага Иссушением на 4 сек.");
                
            addMutationItem(inv, 13, "blood_rage", "😡 Кровавая Ярость", Material.RED_DYE, p,
                "§7Когда ваше здоровье падает ниже 30%,",
                "§7весь ваш урон в ближнем бою возрастает на +30%.");
                
            addMutationItem(inv, 14, "pagan_fear", "😱 Ужас Чернобога", Material.SOUL_SAND, p,
                "§7Каждый полученный вами удар имеет шанс 10%",
                "§7наложить эффект Иссушения I на атакующего вас врага.");
                
            addMutationItem(inv, 15, "shadow_speed", "👣 Теневой Шаг", Material.FEATHER, p,
                "§7Дарует пассивный эффект Скорости I в ночное время",
                "§7(активируется автоматически с наступлением сумерек).");
                
        } else if (nation.equals("imperial_light")) {
            addMutationItem(inv, 11, "bogatyr_resolve", "🛡️ Богатырская Закалка", Material.ANVIL, p,
                "§7Иммунитет к отбрасыванию + Сопротивление I",
                "§7при блокировании щитом от атак.");
                
            addMutationItem(inv, 12, "sacred_shield", "✨ Оберег Руси", Material.TOTEM_OF_UNDYING, p,
                "§7Дарует полную неуязвимость на 3 секунды,",
                "§7если здоровье падает ниже 15% (кулдаун 2 мин).");
                
            addMutationItem(inv, 13, "seismic_stabilizer", "⚓ Твердая Поступь", Material.IRON_BOOTS, p,
                "§7Дарует полный иммунитет к провалам грунта",
                "§7и трещинам во время Землетрясений.");
                
            addMutationItem(inv, 14, "imperial_patience", "👑 Царское Терпение", Material.SHIELD, p,
                "§7Снижает весь входящий урон на 10%,",
                "§7если у вашего персонажа полностью полное здоровье.");
                
            addMutationItem(inv, 15, "hero_of_villages", "💎 Милость Монарха", Material.EMERALD, p,
                "§7Вы получаете постоянную пассивную печать Героя Деревни I,",
                "§7дарующую огромные скидки при торговле со всеми жителями.");
                
        } else if (nation.equals("imperial_dark")) {
            addMutationItem(inv, 11, "punisher_strike", "⚔️ Карательный Меч", Material.NETHERITE_AXE, p,
                "§7Каждый 5-й удар по одной и той же цели",
                "§7наносит гарантированный критический урон (x1.5).");
                
            addMutationItem(inv, 12, "terror_aura", "💀 Аура Страха", Material.SOUL_SAND, p,
                "§7Все враждебные монстры вокруг вас в радиусе",
                "§78 блоков получают Замедление I и Слабость I.");
                
            addMutationItem(inv, 13, "gravity_leap", "🪶 Опричный Прыжок", Material.PHANTOM_MEMBRANE, p,
                "§7Дарует полный иммунитет к урону от падения,",
                "§7если высота вашего падения меньше 15 блоков.");
                
            addMutationItem(inv, 14, "oprichnik_fury", "⚡ Ярость Опричника", Material.REDSTONE_ORE, p,
                "§7Каждое убийство игрока или монстра дарует вам",
                "§7эффект Скорости II на 5 секунд.");
                
            addMutationItem(inv, 15, "iron_skin", "🛡️ Стальная Воля", Material.IRON_CHESTPLATE, p,
                "§7Ваша кожа крепка как латы. Дарует",
                "§7постоянный пассивный эффект Сопротивления урону I.");
        }

        p.openInventory(inv);
    }

    private void addMutationItem(Inventory inv, int slot, String id, String name, Material mat, Player p, String... desc) {
        boolean unlocked = plugin.getNationManager().hasMutation(p.getUniqueId(), id);
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Категория: Мутация Нации");
        lore.add("");
        for (String d : desc) {
            lore.add(d);
        }
        lore.add("");
        
        if (unlocked) {
            lore.add("§a✓ РАЗБЛОКИРОВАНО");
            lore.add("§7Ваш личный пассивный эффект постоянно активен!");
        } else {
            lore.add("§c❌ ЗАБЛОКИРОВАНО");
            lore.add("§eСтоимость разблокировки: §b1500 реп. ВК (лично)");
            
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            int rep = vkId != -1 ? VKChatPlugin.getInstance().getApi().getReputation(vkId) : 0;
            lore.add("§7Ваш баланс: §e" + rep + " реп. ВК");
            
            if (rep >= 1500) {
                lore.add("§a▶ Нажмите, чтобы приобрести!");
            } else {
                lore.add("§c▶ Недостаточно личной репутации ВК.");
            }
        }
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mutation_id"), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
