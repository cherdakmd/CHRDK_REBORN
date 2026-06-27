package ru.example.vkchatjobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SkillManager {
    private final VKChatJobsPlugin plugin;

    public SkillManager(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public static class SkillDef {
        public String id;
        public String name;
        public String desc;
        public int reqLevel;
        public Material icon;
        public String reqSkill;

        public SkillDef(String id, String name, String desc, int reqLevel, Material icon) {
            this(id, name, desc, reqLevel, icon, null);
        }

        public SkillDef(String id, String name, String desc, int reqLevel, Material icon, String reqSkill) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.reqLevel = reqLevel;
            this.icon = icon;
            this.reqSkill = reqSkill;
        }
    }

    public List<SkillDef> getSkillsForJob(String job) {
        if (job.equals("miner")) {
            return Arrays.asList(
                new SkillDef("miner_double", "Удачливый шахтер", "10% шанс добыть х2 руды", 10, Material.DIAMOND_ORE),
                new SkillDef("miner_haste", "Спешка", "Пассивное ускорение копания с киркой", 15, Material.GOLDEN_PICKAXE, "miner_double"),
                new SkillDef("miner_magnet", "Магнит Руд", "15% шанс выбить слиток вместе с рудой", 20, Material.IRON_INGOT, "miner_haste"),
                new SkillDef("miner_blast", "Взрывное дело", "5% шанс взорвать руду и получить х3 лут", 25, Material.TNT, "miner_magnet"),
                new SkillDef("miner_night", "Ночное зрение", "Пассивное ночное зрение в шахте", 30, Material.GLOWSTONE, "miner_blast"),
                new SkillDef("miner_vein", "Жила", "Автоматически ломает соседние блоки той же руды", 35, Material.DIAMOND_PICKAXE, "miner_night"),
                new SkillDef("miner_fortune", "Удача Гномов", "+1 к эффекту Удачи при добыче руды", 40, Material.EMERALD, "miner_vein"),
                new SkillDef("miner_saturation", "Сытость", "Шкала голода не падает при добыче", 45, Material.COOKED_BEEF, "miner_fortune"),
                new SkillDef("miner_master", "Мастер глубин", "Иммунитет к урону от падения в шахтах", 50, Material.BEDROCK, "miner_saturation")
            );
        } else if (job.equals("woodcutter")) {
            return Arrays.asList(
                new SkillDef("wood_double", "Двойной дроп", "10% шанс добыть х2 дерева", 10, Material.OAK_LOG),
                new SkillDef("wood_apple", "Яблочный любитель", "5% шанс падают яблоки с бревен", 15, Material.APPLE, "wood_double"),
                new SkillDef("wood_cap", "Капитан Лесоруб", "Шанс срубить дерево целиком", 20, Material.IRON_AXE, "wood_apple"),
                new SkillDef("wood_regen", "Лесной Дух", "Регенерация ХП, пока рубите дерево", 25, Material.OAK_SAPLING, "wood_cap"),
                new SkillDef("wood_master", "Хозяин леса", "Не ломается топор при рубке", 30, Material.DIAMOND_AXE, "wood_regen"),
                new SkillDef("wood_haste", "Пила", "Пассивная Спешка I с топором", 35, Material.GOLDEN_AXE, "wood_master"),
                new SkillDef("wood_bird", "Птица", "Скорость II и прыжок II в лесу", 40, Material.FEATHER, "wood_haste"),
                new SkillDef("wood_lumberjack", "Лесоруб", "Срубляет дерево до 8 блоков вверх", 45, Material.CHAINMAIL_CHESTPLATE, "wood_bird"),
                new SkillDef("wood_forest", "Дух Леса", "Регенерация III и сопротивление в лесу", 50, Material.TOTEM_OF_UNDYING, "wood_lumberjack")
            );
        } else if (job.equals("farmer")) {
            return Arrays.asList(
                new SkillDef("farm_double", "Щедрый урожай", "10% шанс х2 урожая", 10, Material.WHEAT),
                new SkillDef("farm_auto", "Авто-посадка", "Автоматически сажает семя на место срубленного", 15, Material.BONE_MEAL, "farm_double"),
                new SkillDef("farm_gold", "Золотые руки", "Шанс 1% вырастить золотую морковь/яблоко", 20, Material.GOLDEN_CARROT, "farm_auto"),
                new SkillDef("farm_feed", "Сытость", "Шкала голода не тратится при сборе урожая", 25, Material.BREAD, "farm_gold"),
                new SkillDef("farm_master", "Деметра", "При сборе урожая есть шанс получить алмаз", 30, Material.DIAMOND_HOE, "farm_feed"),
                new SkillDef("farm_bone", "Костная мука", "Шанс получить костную муку при сборе", 35, Material.BONE_MEAL, "farm_master"),
                new SkillDef("farm_speed", "Быстрый сбор", "Скорость I при ходьбе по грядкам", 40, Material.SUGAR_CANE, "farm_bone"),
                new SkillDef("farm_demeter", "Щедрая Деметра", "20% шанс х2 урожая", 45, Material.GOLDEN_APPLE, "farm_speed"),
                new SkillDef("farm_nature", "Природа", "Регенерация I рядом с посевами", 50, Material.ENCHANTED_GOLDEN_APPLE, "farm_demeter")
            );
        } else if (job.equals("alchemist")) {
            return Arrays.asList(
                new SkillDef("alch_save", "Экономия", "10% шанс не потратить ингредиент при варке", 10, Material.NETHER_WART),
                new SkillDef("alch_double", "Доп. порция", "10% шанс сварить 2 зелья вместо 1", 15, Material.POTION, "alch_save"),
                new SkillDef("alch_long", "Долголетие", "+50% к длительности выпитых вами зелий", 20, Material.GLOWSTONE_DUST, "alch_double"),
                new SkillDef("alch_resist", "Иммунитет", "Сопротивление магии (урон от зелий -50%)", 25, Material.GHAST_TEAR, "alch_long"),
                new SkillDef("alch_master", "Философский камень", "Шанс сварить Зелье Неуязвимости", 30, Material.BLAZE_POWDER, "alch_resist"),
                new SkillDef("alch_splash", "Всплеск", "Все сваренные зелья автоматически всплесковые", 35, Material.SPLASH_POTION, "alch_master"),
                new SkillDef("alch_luck", "Удача алхимика", "Шанс найти редкие ингредиенты при варке", 40, Material.RABBIT_FOOT, "alch_splash"),
                new SkillDef("alch_philosopher", "Магистр", "Шанс скрафтить Зелье Огнестойкости", 45, Material.MAGMA_CREAM, "alch_luck"),
                new SkillDef("alch_immortality", "Бессмертие", "Шанс получить Тотем Бессмертия при варке", 50, Material.TOTEM_OF_UNDYING, "alch_philosopher")
            );
        } else if (job.equals("blacksmith")) {
            return Arrays.asList(
                new SkillDef("black_save", "Экономия металла", "10% шанс вернуть слиток при крафте", 10, Material.IRON_INGOT),
                new SkillDef("black_dur", "Крепкая сталь", "+20% прочности ко всем созданным предметам", 15, Material.ANVIL, "black_save"),
                new SkillDef("black_leg", "Легендарный шанс", "Увеличивает шанс прокнуть Легендарку", 20, Material.DIAMOND, "black_dur"),
                new SkillDef("black_repair", "Дешевый ремонт", "Ремонт в наковальне стоит меньше опыта", 25, Material.EXPERIENCE_BOTTLE, "black_leg"),
                new SkillDef("black_master", "Кузня Богов", "Шанс сразу скрафтить предмет с +1 к Заточке", 30, Material.NETHERITE_INGOT, "black_repair"),
                new SkillDef("black_reforge", "Перековка", "Шанс улучшить редкость при крафте", 35, Material.LAVA_BUCKET, "black_master"),
                new SkillDef("black_upgrade", "Инженер", "Снижает стоимость заточки в /runes", 40, Material.REDSTONE, "black_reforge"),
                new SkillDef("black_godforge", "Божественная Кузня", "Шанс получить 2 предмета при крафте", 45, Material.NETHER_STAR, "black_upgrade"),
                new SkillDef("black_perfection", "Совершенство", "Все созданные предметы получают +1 к базовым чарам", 50, Material.DIAMOND_BLOCK, "black_godforge")
            );
        } else if (job.equals("hunter")) {
            return Arrays.asList(
                new SkillDef("hunt_exp", "Опытный охотник", "+50% опыта профессии за убийство мобов", 10, Material.BONE),
                new SkillDef("hunt_loot", "Собиратель", "10% шанс двойного дропа с мобов", 15, Material.LEATHER, "hunt_exp"),
                new SkillDef("hunt_crit", "Точный удар", "+10% критического урона по мобам", 20, Material.ARROW, "hunt_loot"),
                new SkillDef("hunt_speed", "Кровожадность", "Скорость II после убийства", 25, Material.FEATHER, "hunt_crit"),
                new SkillDef("hunt_master", "Мясник", "Шанс мгновенно убить обычного моба при ХП < 20%", 30, Material.IRON_SWORD, "hunt_speed"),
                new SkillDef("hunt_track", "Следопыт", "Видите следы мобов (частицы)", 35, Material.COMPASS, "hunt_master"),
                new SkillDef("hunt_boss", "Убийца боссов", "+20% урона элитным мобам", 40, Material.DIAMOND_SWORD, "hunt_track"),
                new SkillDef("hunt_stealth", "Незаметность", "Слабость I и невидимость при приседании", 45, Material.ENDER_PEARL, "hunt_boss"),
                new SkillDef("hunt_legend", "Легенда охоты", "Шанс получить Осколок Артефакта с мобов", 50, Material.NETHER_STAR, "hunt_stealth")
            );
        } else if (job.equals("fisherman")) {
            return Arrays.asList(
                new SkillDef("fish_double", "Двойной улов", "10% шанс поймать х2 рыбы", 10, Material.COD),
                new SkillDef("fish_treasure", "Охотник за сокровищами", "5% шанс найти сокровище", 15, Material.CHEST, "fish_double"),
                new SkillDef("fish_speed", "Быстрая рука", "Скорость подматывания удочки", 20, Material.FISHING_ROD, "fish_treasure"),
                new SkillDef("fish_luck", "Морская удача", "Удача I во время рыбалки", 25, Material.TRIDENT, "fish_speed"),
                new SkillDef("fish_master", "Морской волк", "Шанс поймать редкую рыбу", 30, Material.TROPICAL_FISH, "fish_luck"),
                new SkillDef("fish_salmon", "Речной мастер", "Шанс поймать лосося вместо трески", 35, Material.SALMON, "fish_master"),
                new SkillDef("fish_neptune", "Дар Нептуна", "Дыхание под водой во время рыбалки", 40, Material.PUFFERFISH, "fish_salmon"),
                new SkillDef("fish_catch", "Ловец", "Шанс поймать полезный предмет", 45, Material.BUCKET, "fish_neptune"),
                new SkillDef("fish_god", "Бог рыбалки", "Шанс поймать Незеритовый обломок", 50, Material.NETHERITE_SCRAP, "fish_catch")
            );
        }
        return Collections.emptyList();
    }

    public void openSkillMenu(Player p, String job) {
        String jobName = plugin.getConfig().getString("jobs." + job + ".name", job);
        Inventory inv = Bukkit.createInventory(null, 36, ChatColor.DARK_GREEN + "Навыки: " + job);

        List<SkillDef> skills = getSkillsForJob(job);
        int pts = plugin.getJobsDataManager().getSkillPoints(p.getUniqueId(), job);
        int lvl = plugin.getJobsDataManager().getLevel(p.getUniqueId(), job);

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < skills.size() && i < slots.length; i++) {
            SkillDef sd = skills.get(i);
            boolean unlocked = plugin.getJobsDataManager().hasSkill(p.getUniqueId(), job, sd.id);
            boolean reqMet = sd.reqSkill == null || plugin.getJobsDataManager().hasSkill(p.getUniqueId(), job, sd.reqSkill);

            ItemStack item;
            if (unlocked) {
                item = new ItemStack(sd.icon);
            } else if (!reqMet) {
                item = new ItemStack(Material.BARRIER);
            } else if (lvl >= sd.reqLevel) {
                item = new ItemStack(Material.GRAY_DYE);
            } else {
                item = new ItemStack(Material.RED_DYE);
            }

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "skill_id"), PersistentDataType.STRING, sd.id);

            if (unlocked) {
                meta.setDisplayName(ChatColor.GREEN + sd.name);
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + sd.desc,
                    "",
                    ChatColor.AQUA + "✅ Навык изучен!"
                ));
            } else {
                if (!reqMet) {
                    meta.setDisplayName(ChatColor.DARK_RED + sd.name + " (Закрыто)");
                    meta.setLore(Arrays.asList(
                        ChatColor.DARK_GRAY + sd.desc,
                        "",
                        ChatColor.RED + "❌ Сначала изучите предыдущий навык",
                        ChatColor.RED + "❌ Требуется уровень: " + sd.reqLevel
                    ));
                } else if (lvl >= sd.reqLevel) {
                    meta.setDisplayName(ChatColor.YELLOW + sd.name);
                    meta.setLore(Arrays.asList(
                        ChatColor.GRAY + sd.desc,
                        "",
                        ChatColor.YELLOW + "Требуется уровень: " + sd.reqLevel,
                        ChatColor.YELLOW + "Доступно очков: " + pts,
                        pts > 0 ? ChatColor.GREEN + "▶ Нажмите, чтобы изучить!" : ChatColor.RED + "❌ Недостаточно очков навыков!"
                    ));
                } else {
                    meta.setDisplayName(ChatColor.RED + sd.name + " (Секретно)");
                    meta.setLore(Arrays.asList(
                        ChatColor.DARK_GRAY + "Достигните " + sd.reqLevel + " уровня,",
                        ChatColor.DARK_GRAY + "чтобы открыть этот навык."
                    ));
                }
            }
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.setDisplayName(ChatColor.YELLOW + "Очки Навыков: " + pts);
        iMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "Вы получаете 1 очко",
            ChatColor.GRAY + "каждые 10 уровней профессии."
        ));
        info.setItemMeta(iMeta);
        inv.setItem(31, info);

        p.openInventory(inv);
    }
}
