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
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchatnations.gui.ClaimGui;
import ru.example.vkchatnations.gui.MutationGui;
import ru.example.vkchatnations.gui.NationShopGui;

import java.util.ArrayList;
import java.util.List;

/**
 * NationGuiListener — тонкий оркестратор GUI наций.
 *
 * Рефакторинг v3.3: декомпозиция God-класса (1477 строк → ~280 строк):
 * - NationShopGui: магазин + броня + национальные предметы
 * - MutationGui: мутации + покупка
 * - ClaimGui: управление приватами (питание/прокачка/покупка)
 * - NationGuiListener: выбор нации + главное меню + маршрутизация
 */
public class NationGuiListener implements Listener {
    private final VKChatNationsPlugin plugin;
    private final String NATION_SELECT_TITLE = "§8▸ §4§lНАЦИИ §8◂ §7Выбор";
    private final String NATION_INFO_TITLE = "§8▸ §6§lНАЦИЯ §8◂ §7Управление";

    private final NationShopGui shopGui;
    private final MutationGui mutationGui;
    private final ClaimGui claimGui;

    public NationGuiListener(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        this.shopGui = new NationShopGui(plugin);
        this.mutationGui = new MutationGui(plugin);
        this.claimGui = new ClaimGui(plugin);

        // Каждые 2 секунды — автопросмотр для игроков без нации
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (VKChatBridge.isFullyAuthorized(p)) {
                        if (!plugin.getNationManager().hasNation(p)) {
                            if (!p.getOpenInventory().getTitle().equals(NATION_SELECT_TITLE)) {
                                openNationSelection(p);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, 40L, 40L);
    }

    // Геттеры для под-GUI (используются из команд и других listeners)
    public NationShopGui getShopGui()      { return shopGui; }
    public MutationGui getMutationGui()    { return mutationGui; }
    public ClaimGui getClaimGui()          { return claimGui; }

    // Методы-делегаты для обратной совместимости (вызываются из NationCommand, NationListener)
    public void openClaimShop(Player p)                      { claimGui.openClaimShop(p); }
    public void openClaimFeedGui(Player p, ChunkClaim claim)  { claimGui.openClaimFeedGui(p, claim); }

    // ═══════════════════════════════════════════════════════════════
    // ВЫБОР НАЦИИ
    // ═══════════════════════════════════════════════════════════════

    public void openNationSelection(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, NATION_SELECT_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 36; i++) inv.setItem(i, glass);

        inv.setItem(10, createNationSelectionItem(Material.IRON_PICKAXE, "&c&lСоюз", "soviet_light",
                "&7«Мир, Труд, Май!» — Братство рабочих",
                "&7и инженеров, строящих новый мир.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффект Спешки II &7под землей (Y < 50)",
                "&e• Мутация «Стахановец»:",
                "  &7+20% шанс двойного угля/железа/алмазов.",
                "&e• Мутация «Артельный Труд»:",
                "  &7+10% скорость копания за соратника рядом.",
                "&e• Свинцовая Кожа &7(Иммунитет к Кислотному Дождю)",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Ударник Труда» &7(Спешка III при ношении)"));

        inv.setItem(19, createNationSelectionItem(Material.RED_BANNER, "&4&lЧека", "soviet_dark",
                "&7«Тень над Республикой» — Хранители",
                "&7порядка, укрытые плащом тишины.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффекты Скорости I &7и &eНевидимости I",
                "&e• Мутация «Спецагент»:",
                "  &7Невидимость на 10с при шифте без оружия.",
                "&e• Мутация «Противоядие»:",
                "  &7Полный иммунитет к эффектам Отравления.",
                "&e• Мутация «Ночной Охотник»:",
                "  &7+20% к урону ночью и в темноте.",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Танкист» &7(Сопротивление II, Медлительность II)"));

        inv.setItem(13, createNationSelectionItem(Material.ENCHANTED_BOOK, "&a&lВедуны", "pagan_light",
                "&7«Мудрость Леса» — Хранители древних",
                "&7знаний, черпающие силу из природы.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффект Регенерации I",
                "&e• Мутация «Единение с Лесом»:",
                "  &7Регенерация II и Скорость I в лесах.",
                "&e• Мутация «Сила Ведунов»:",
                "  &745% шанс мгновенно вырастить посевы рукой.",
                "&e• Мутация «Целебные Травы»:",
                "  &7+4 HP при поедании ягод и яблок.",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Ведун» &7(Дыхание под водой, Грация дельфина)"));

        inv.setItem(22, createNationSelectionItem(Material.BONE, "&2&lНавь", "pagan_dark",
                "&7«Дыхание Бездны» — Последователи",
                "&7Чернобога, пьющие силу из крови.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффект Силы I &7(Постоянное увеличение урона)",
                "&e• Мутация «Жертвенные Когти»:",
                "  &7Восполнение 10% здоровья от ХП жертвы.",
                "&e• Мутация «Касание Нави»:",
                "  &715% шанс иссушить цель на 4 секунды.",
                "&e• Мутация «Кровавая Ярость»:",
                "  &7+30% ближнего урона при ХП < 30%.",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Бессмертный» &7(Сопротивление I, Медлительность I)"));

        inv.setItem(16, createNationSelectionItem(Material.SHIELD, "&e&lРусь", "imperial_light",
                "&7«За Царя и Отечество!» — Витязи,",
                "&7несокрушимо стоящие на страже границ.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффект Сопротивления Урону I",
                "&e• Мутация «Богатырская Закалка»:",
                "  &7Иммунитет к отдаче + Резист I при блоке щитом.",
                "&e• Мутация «Оберег Руси»:",
                "  &7Неуязвимость на 3 секунды при ХП < 15%.",
                "&e• Мутация «Твёрдая Поступь»:",
                "  &7Иммунитет к трещинам Землетрясения.",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Богатырь» &7(Сопротивление отбрасыванию, Сила I)"));

        inv.setItem(25, createNationSelectionItem(Material.NETHERITE_SWORD, "&6&lГроза", "imperial_dark",
                "&7«Кара Государева» — Неумолимая",
                "&7длань престола, разящая крамолу.",
                "&f", "&6⚡ Стартовые эффекты и бонусы:",
                "&e• Эффект Прыгучести II",
                "&e• Мутация «Карательный Меч»:",
                "  &7Каждый 5-й удар наносит критический урон x1.5.",
                "&e• Мутация «Аура Страха»:",
                "  &7Мобам рядом накладывается Замедление и Слабость.",
                "&e• Мутация «Опричный Прыжок»:",
                "  &7Дарует полный иммунитет к урону от падения.",
                "&f", "&d🛡 Классовый сет брони при вступлении:",
                "&a• Сет «Ясный Сокол» &7(Скорость III, Прыгучесть III)"));

        p.openInventory(inv);
    }

    private ItemStack createNationSelectionItem(Material m, String name, String nationId, String... descLines) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        int members = plugin.getNationManager().getMemberCount(nationId);

        List<String> lore = new ArrayList<>();
        for (String line : descLines) lore.add(ChatColor.translateAlternateColorCodes('&', line));
        lore.add("");
        lore.add(ChatColor.AQUA + "Жителей: " + ChatColor.WHITE + members);
        lore.add(ChatColor.YELLOW + "▶ Нажмите, чтобы присягнуть на верность! ◀");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nation_id"), PersistentDataType.STRING, nationId);
        i.setItemMeta(meta);
        return i;
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЛАВНОЕ МЕНЮ НАЦИИ
    // ═══════════════════════════════════════════════════════════════

    public void openGui(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) { openNationSelection(p); return; }

        Inventory inv = Bukkit.createInventory(null, 27, NATION_INFO_TITLE);
        String name = plugin.getConfig().getString("nations." + nation + ".name", nation);
        int bank = plugin.getNationManager().getBank(nation);

        int claimCount = 0;
        for (ChunkClaim claim : plugin.getNationManager().getNationClaims().values()) {
            if (claim.getOwner().equals(p.getUniqueId())) claimCount++;
        }

        ItemStack info = createItem(Material.BEACON, ChatColor.translateAlternateColorCodes('&', name),
                ChatColor.GRAY + "Ваша глобальная нация.",
                ChatColor.GRAY + "Вы не можете атаковать своих соотечественников.");

        ItemStack energy = createItem(Material.REDSTONE_BLOCK, ChatColor.AQUA + "Ваша собственность",
                ChatColor.GRAY + "Активных приватов: " + ChatColor.GREEN + claimCount + " / 5",
                ChatColor.GRAY + "Казна нации: " + ChatColor.YELLOW + bank);

        ItemStack mutations = createItem(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE + "🧬 Лаборатория Мутаций",
                ChatColor.GRAY + "Откройте меню прокачки пассивных",
                ChatColor.GRAY + "и активных мутаций вашего персонажа.",
                "", ChatColor.YELLOW + "Нажмите, чтобы открыть меню!");

        ItemStack shop = createItem(Material.CHEST, ChatColor.GOLD + "🛒 Магазин Нации",
                ChatColor.GRAY + "Приобретайте уникальное национальное",
                ChatColor.GRAY + "снаряжение и классовую броню фракции.",
                "", ChatColor.YELLOW + "Нажмите, чтобы открыть магазин!");

        ItemStack claimBuy = createItem(Material.GOLD_BLOCK, ChatColor.YELLOW + "🔰 Покупка Блоков Привата",
                ChatColor.GRAY + "Приобретайте блоки привата разной мощности",
                ChatColor.GRAY + "и ставьте их в мире для защиты земель.",
                "", ChatColor.YELLOW + "Нажмите для открытия!");

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

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТЧИКИ СОБЫТИЙ
    // ═══════════════════════════════════════════════════════════════

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { /* шедулер проверяет после авторизации */ }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        Player p = (Player) event.getPlayer();

        if (title.equals(ClaimGui.getClaimFeedTitle()) || title.equals(ClaimGui.getClaimUpgradeTitle())) {
            claimGui.removeActiveClaim(p.getUniqueId());
        }
        if (title.equals(NATION_SELECT_TITLE)) {
            if (VKChatBridge.isFullyAuthorized(p) && !plugin.getNationManager().hasNation(p)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline() && !plugin.getNationManager().hasNation(p)) {
                        openNationSelection(p);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onPlayerQuitCleanup(org.bukkit.event.player.PlayerQuitEvent e) {
        claimGui.removeActiveClaim(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // Выбор нации
        if (title.equals(NATION_SELECT_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

            String nationId = e.getCurrentItem().getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "nation_id"), PersistentDataType.STRING);
            if (nationId != null) {
                plugin.getNationManager().setPlayerNation(p, nationId);
                p.closeInventory();

                String nationName = plugin.getConfig().getString("nations." + nationId + ".name", nationId);
                p.sendMessage(" ");
                p.sendMessage(ChatColor.GREEN + "✓ Вы успешно присягнули на верность Нации: "
                        + ChatColor.translateAlternateColorCodes('&', nationName) + ChatColor.GREEN + "!");
                p.sendMessage(ChatColor.GRAY + "Вам выдан стартовый эпический комплект брони вашей Нации. Наденьте его, чтобы активировать пассивный бонус сета!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                shopGui.giveNationArmorSet(p, NationShopGui.getNationSetKey(nationId));
            }
            return;
        }

        // Главное меню нации
        if (title.equals(NATION_INFO_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) {
                if (e.getCurrentItem().getType() == Material.BREWING_STAND) mutationGui.openMutationsSelection(p);
                else if (e.getCurrentItem().getType() == Material.CHEST) shopGui.openNationShop(p);
                else if (e.getCurrentItem().getType() == Material.GOLD_BLOCK) claimGui.openClaimShop(p);
            }
            return;
        }

        // Магазин блоков привата
        if (title.equals(ClaimGui.getBuyClaimTitle())) {
            e.setCancelled(true);
            if (claimGui.handleClaimShopClick(p, e.getCurrentItem())) {
                if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.BARRIER) openGui(p);
            }
            return;
        }

        // Управление приватом
        if (title.equals(ClaimGui.getClaimFeedTitle())) {
            e.setCancelled(true);
            ChunkClaim claim = claimGui.getActiveClaim(p.getUniqueId());

            // Особый случай: точка дома с ЛКМ/ПКМ
            if (e.getRawSlot() == 1 && claim != null && claim.hasHome()) {
                if (e.isLeftClick()) {
                    p.closeInventory();
                    org.bukkit.World w = Bukkit.getWorld(claim.getWorldName());
                    if (w != null) {
                        p.teleport(new org.bukkit.Location(w, claim.getHomeX(), claim.getHomeY(), claim.getHomeZ()));
                        p.sendMessage(ChatColor.GREEN + "♲ Телепорт к дому привата.");
                    } else {
                        p.sendMessage(ChatColor.RED + "Мир привата не найден.");
                    }
                } else {
                    claim.removeHome();
                    plugin.getNationManager().saveAll();
                    p.sendMessage(ChatColor.RED + "♲ Точка дома удалена.");
                    claimGui.openClaimFeedGui(p, claim);
                }
                return;
            }

            claimGui.handleClaimFeedClick(p, e.getCurrentItem(), e.getRawSlot());
            return;
        }

        // Прокачка привата
        if (title.equals(ClaimGui.getClaimUpgradeTitle())) {
            e.setCancelled(true);
            claimGui.handleClaimUpgradeClick(p, e.getCurrentItem());
            return;
        }

        // Магазин нации
        if (title.equals("§8▸ §e§lНАЦИЯ §8◂ §7Магазин")) {
            e.setCancelled(true);
            if (shopGui.handleClick(p, e.getCurrentItem())) {
                if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.BARRIER) openGui(p);
            }
            return;
        }

        // Мутации
        if (title.equals(MutationGui.getTitle())) {
            e.setCancelled(true);
            mutationGui.handleClick(p, e.getCurrentItem());
        }
    }
}
