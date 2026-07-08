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
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatnations.VKChatNationsPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * MutationGui — GUI выбора и покупки мутаций нации.
 *
 * Извлечено из NationGuiListener:
 * - 6 наций × 5 мутаций = 30 мутаций
 * - Отображение статуса (разблокирована/заблокирована)
 * - Покупка мутации за 1500 реп. ВК
 */
public class MutationGui {

    private final VKChatNationsPlugin plugin;
    private static final String TITLE = ChatColor.LIGHT_PURPLE + "🧬 Мутации вашей Нации";

    public MutationGui(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    public static String getTitle() { return TITLE; }

    public void openMutationsSelection(Player p) {
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

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
        for (String d : desc) lore.add(d);
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

    /**
     * Обработать клик по мутации.
     * @return true если клик обработан (куплена/отклонена)
     */
    public boolean handleClick(Player p, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return false;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey mKey = new NamespacedKey(plugin, "mutation_id");

        if (!meta.getPersistentDataContainer().has(mKey, PersistentDataType.STRING)) return false;

        String mId = meta.getPersistentDataContainer().get(mKey, PersistentDataType.STRING);
        if (mId == null) return true;

        if (plugin.getNationManager().hasMutation(p, mId)) {
            p.sendMessage(ChatColor.YELLOW + "⚠️ Эта мутация у вас уже активна!");
            return true;
        }

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
            p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте! (/vklink)");
            return true;
        }

        int cost = 1500;
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep >= cost) {
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            plugin.getNationManager().unlockMutation(p.getUniqueId(), mId);

            String mName = meta.getDisplayName();
            p.sendMessage(" ");
            p.sendMessage(ChatColor.LIGHT_PURPLE + "🧬 [Мутация разблокирована] Вы успешно приобрели мутацию: "
                    + ChatColor.translateAlternateColorCodes('&', mName) + ChatColor.LIGHT_PURPLE + "!");
            p.sendMessage(ChatColor.GRAY + "Теперь этот пассивный эффект постоянно активен у вашего персонажа.");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

            openMutationsSelection(p);
        } else {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно личной репутации ВК! Требуется: " + cost + " (Ваш баланс: " + rep + ").");
        }
        return true;
    }
}
