package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKChatAPI;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.ActiveAdventure;
import ru.example.vkchatoffline.managers.OfflineShopCatalog.ShopItem;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdventureManager implements Listener {
    private final VKChatOfflinePlugin plugin;
    private final Random random = new Random();
    private final Map<Integer, ActiveAdventure> active = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<Integer, Long> injuries = new ConcurrentHashMap<>();
    private final File file;
    private FileConfiguration data;
    private OfflineHospitalManager hospitalManager;
    private OfflineCampaignManager campaignManager;
    private OfflineRewardManager rewardManager;
    private OfflineProgressManager progressManager;

    private static final Set<String> COMMANDS = new HashSet<>(Arrays.asList(
            "!поход", "!походы", "!пойти", "!выбор", "!статуспохода", "!забрать", "!тайник", "!stash",
            "!adventure", "!adv", "!отменапоход", "!офлайн", "!offline", "!вопрос", "!открыть", "!класс", "!спутник", "!отдых", "!лечиться", "!лечение", "!госпиталь", "!психика", "!кампания", "!глава", "!коллекции", "!коллекция", "!отношения", "!магазин", "!лавка", "!купить", "!экипировка", "!снаряжение", "!использовать", "!юз", "!продатьтайник", "!продатьstash", "!навыки", "!навык", "!инвентарь", "!сумка", "!персонаж", "!профильпохода", "!дневник", "!журнал", "!ачивки", "!достижения", "!ежедневка", "!дейлик", "!офадмин", "!герой"
    ));

    public AdventureManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "adventures.yml");
        loadAll();
        hospitalManager = new OfflineHospitalManager(plugin, () -> data, active, this::addJournal, this::saveAll);
        campaignManager = new OfflineCampaignManager(plugin, () -> data, this::addJournal);
        rewardManager = new OfflineRewardManager(plugin, this::addJournal, this::saveAll);
        progressManager = new OfflineProgressManager(plugin, () -> data, this::addJournal);
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 20L * 10, 20L * plugin.getConfig().getLong("check-interval-seconds", 30));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cachePlayerSnapshot(e.getPlayer());
    }

    private void cachePlayerSnapshot(Player p) {
        try {
            int vkId = api().getLinkedVkId(p);
            if (vkId == -1) return;
            data.set("snapshots." + vkId + ".name", p.getName());
            data.set("snapshots." + vkId + ".gearPower", calculateGearPower(p));
            data.set("snapshots." + vkId + ".nation", detectNation(p));
            cacheJobs(vkId, p);
            saveAll();
        } catch (Throwable ignored) {}
    }

    private int calculateGearPower(Player p) {
        int score = 0;
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null) return 0;
        NamespacedKey lvlKey = new NamespacedKey(gearPlugin, "upgrade_level");
        NamespacedKey setKey = new NamespacedKey(gearPlugin, "gear_set");
        java.util.Map<String, Integer> setCounts = new java.util.HashMap<>();
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        items.add(p.getInventory().getItemInMainHand());
        items.addAll(java.util.Arrays.asList(p.getInventory().getArmorContents()));
        for (ItemStack item : items) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            int lvl = meta.getPersistentDataContainer().getOrDefault(lvlKey, PersistentDataType.INTEGER, 0);
            score += lvl;
            String set = meta.getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
            if (set != null) setCounts.put(set, setCounts.getOrDefault(set, 0) + 1);
        }
        for (int count : setCounts.values()) if (count >= 4) score += 20;
        return score;
    }

    private String detectNation(Player p) {
        try {
            org.bukkit.plugin.Plugin nations = Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (nations == null || !nations.isEnabled()) return "none";
            Object nm = nations.getClass().getMethod("getNationManager").invoke(nations);
            Object nation = nm.getClass().getMethod("getPlayerNation", Player.class).invoke(nm, p);
            return nation == null ? "none" : String.valueOf(nation);
        } catch (Throwable ignored) { return "none"; }
    }

    private void cacheJobs(int vkId, Player p) {
        try {
            org.bukkit.plugin.Plugin jobs = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobs == null || !jobs.isEnabled()) return;
            Object dm = jobs.getClass().getMethod("getJobsDataManager").invoke(jobs);
            int total = 0;
            for (String j : java.util.Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith")) {
                int lvl = (int) dm.getClass().getMethod("getLevel", java.util.UUID.class, String.class).invoke(dm, p.getUniqueId(), j);
                data.set("snapshots." + vkId + ".jobs." + j, lvl);
                total += lvl;
            }
            data.set("snapshots." + vkId + ".jobs.total", total);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (e.getPeer() != e.getSenderId()) return;
        String text = e.getMessage() == null ? "" : e.getMessage().trim();

        // Не перехватываем сообщения безопасности ядра: 2FA-кнопки "Войти" / "Блокировка"
        // должны доходить до VKCommandHandler, иначе подтверждение входа через VK ломается.
        if (isSecurityAuthText(text)) return;

        // В ЛС переносим на основной поток только сообщения, похожие на кнопки Offline Adventures.
        // Раньше здесь отменялись все не-командные сообщения, из-за чего ломались 2FA и другие core-кнопки.
        if (!text.startsWith("!") && mightBeOfflineButton(text)) {
            e.setCancelled(true);
            int vk = e.getSenderId();
            Bukkit.getScheduler().runTask(plugin, () -> handlePrettyButton(vk, text));
            return;
        }

        if (text.startsWith("!") && handlePrettyButton(e.getSenderId(), text)) {
            e.setCancelled(true);
        }
    }

    private boolean mightBeOfflineButton(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String plain = org.bukkit.ChatColor.stripColor(text)
                .replace("️", "")
                .replaceAll("^[^A-Za-zА-Яа-я0-9!]+", "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (plain.isEmpty()) return false;
        String[] exact = {
                "походы", "поход", "герой", "лавка", "помощь", "магазин",
                "рискнуть", "ударить", "осторожно", "защита", "исследовать", "приём", "прием", "отступить", "статус", "тайник",
                "персонаж", "класс", "спутник", "вопрос", "отмена", "отдых", "лечиться", "госпиталь", "психика", "кампания",
                "коллекции", "отношения", "сумка", "дневник", "ачивки", "дейлик", "расходники", "навыки", "экипировка",
                "воин", "следопыт", "жрец", "волк", "ворон", "алхимик", "мул",
                "продать тайник", "лечение травм", "терапия рассудка", "снять фобию",
                "назад", "использовать"
        };
        for (String l : exact) if (plain.equals(l)) return true;
        if (plain.startsWith("поход ") || plain.startsWith("тайник ") || plain.startsWith("глава ") || plain.startsWith("навык: ")) return true;
        if (plain.startsWith("купить ")) return true;
        if (plain.startsWith("использовать ")) return true;
        if (plain.startsWith("открыть ")) return true;
        return false;
    }

    private boolean handlePrettyButton(int vkId, String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String plain = org.bukkit.ChatColor.stripColor(text.trim())
                .replace("️", "")
                .replaceAll("^[^A-Za-zА-Яа-я0-9!]+", "")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (plain.equals("походы") || plain.equals("поход") || plain.equals("назад") || plain.equals("adventures")) { showMenuOrStatus(vkId); return true; }
        if (plain.equals("герой") || plain.equals("меню героя")) { showHeroMenu(vkId); return true; }
        if (plain.equals("лавка") || plain.equals("магазин")) { showOfflineShop(vkId, "main"); return true; }
        if (plain.equals("помощь") || plain.equals("вопрос")) { showQuestion(vkId, new String[]{"!вопрос"}); return true; }
        if (plain.equals("воин")) { chooseClass(vkId, new String[]{"!класс", "warrior"}); return true; }
        if (plain.equals("следопыт")) { chooseClass(vkId, new String[]{"!класс", "scout"}); return true; }
        if (plain.equals("маг")) { chooseClass(vkId, new String[]{"!класс", "mage"}); return true; }
        if (plain.equals("жрец")) { chooseClass(vkId, new String[]{"!класс", "cleric"}); return true; }
        String route = routeFromButton(plain);
        if (route != null) { startAdventure(vkId, new String[]{"!пойти", route}); return true; }
        if (plain.equals("рискнуть") || plain.equals("ударить")) { handleChoice(vkId, new String[]{"!выбор", "1"}); return true; }
        if (plain.equals("осторожно") || plain.equals("защита")) { handleChoice(vkId, new String[]{"!выбор", "2"}); return true; }
        if (plain.equals("исследовать") || plain.equals("приём") || plain.equals("прием")) { handleChoice(vkId, new String[]{"!выбор", "3"}); return true; }
        if (plain.equals("отступить")) { handleChoice(vkId, new String[]{"!выбор", "4"}); return true; }
        if (plain.equals("статус")) { showStatus(vkId); return true; }
        if (plain.equals("тайник")) { showStash(vkId, new String[]{"!тайник", "1"}); return true; }
        if (plain.startsWith("тайник ")) {
            String digits = plain.replaceAll("\\D+", "");
            showStash(vkId, new String[]{"!тайник", digits.isEmpty() ? "1" : digits});
            return true;
        }
        if (plain.equals("персонаж")) { showAdventureProfile(vkId); return true; }
        if (plain.equals("класс")) { chooseClass(vkId, new String[]{"!класс"}); return true; }
        if (plain.equals("спутник")) { chooseCompanion(vkId, new String[]{"!спутник"}); return true; }
        if (plain.equals("отмена")) { cancel(vkId); return true; }
        if (plain.startsWith("открыть ")) { unlockRoute(vkId, new String[]{"!открыть", routeFromOpenButton(plain)}); return true; }
        if (plain.equals("волк")) { chooseCompanion(vkId, new String[]{"!спутник", "wolf"}); return true; }
        if (plain.equals("ворон")) { chooseCompanion(vkId, new String[]{"!спутник", "raven"}); return true; }
        if (plain.equals("алхимик")) { chooseCompanion(vkId, new String[]{"!спутник", "alchemist"}); return true; }
        if (plain.equals("мул")) { chooseCompanion(vkId, new String[]{"!спутник", "mule"}); return true; }
        if (plain.equals("отдых")) { takeRest(vkId); return true; }
        if (plain.equals("лечиться")) { healSmart(vkId); return true; }
        if (plain.equals("госпиталь")) { showHospital(vkId); return true; }
        if (plain.equals("психика")) { showPsyche(vkId); return true; }
        if (plain.equals("кампания")) { showCampaign(vkId); return true; }
        if (plain.equals("коллекции")) { showCollections(vkId); return true; }
        if (plain.equals("отношения")) { showRelationships(vkId); return true; }
        if (plain.equals("лечение травм")) { useHospital(vkId, "trauma"); return true; }
        if (plain.equals("терапия рассудка")) { useHospital(vkId, "sanity"); return true; }
        if (plain.equals("снять фобию")) { useHospital(vkId, "fear"); return true; }
        if (plain.equals("экипировка")) { showOfflineEquipment(vkId); return true; }
        if (plain.equals("расходники")) { showConsumables(vkId); return true; }
        if (plain.equals("навыки")) { showOfflineSkills(vkId); return true; }
        if (plain.equals("продать тайник")) { sellStash(vkId, true); return true; }
        if (plain.equals("сумка")) { showAdventureInventory(vkId); return true; }
        if (plain.equals("дневник")) { showJournal(vkId); return true; }
        if (plain.equals("ачивки")) { showAchievements(vkId); return true; }
        if (plain.equals("дейлик")) { showDaily(vkId); return true; }
        if (plain.startsWith("купить ")) {
            String id = buyIdFromLabel(plain);
            if (id != null) { buyOfflineItem(vkId, id); return true; }
        }
        if (plain.startsWith("использовать ")) {
            String id = useIdFromLabel(plain);
            if (id != null) { useConsumable(vkId, id); return true; }
        }
        if (plain.startsWith("навык: ")) {
            String id = skillIdFromLabel(plain);
            if (id != null) { learnOfflineSkill(vkId, id); return true; }
        }
        if (plain.startsWith("глава ")) {
            String num = plain.replace("глава ", "").trim();
            String[] nums = {"i", "ii", "iii", "iv", "v", "vi"};
            for (int i = 0; i < nums.length; i++) {
                if (nums[i].equals(num)) { startCampaignChapter(vkId, String.valueOf(i + 1)); return true; }
            }
            if (num.matches("\\d+")) { startCampaignChapter(vkId, num); return true; }
        }
        if (plain.startsWith("как начать")) { showQuestion(vkId, new String[]{"!вопрос", "1"}); return true; }
        if (plain.startsWith("маршруты")) { showQuestion(vkId, new String[]{"!вопрос", "2"}); return true; }
        if (plain.startsWith("статус похода")) { showQuestion(vkId, new String[]{"!вопрос", "3"}); return true; }
        if (plain.startsWith("награды")) { showQuestion(vkId, new String[]{"!вопрос", "4"}); return true; }
        if (plain.startsWith("смерть")) { showQuestion(vkId, new String[]{"!вопрос", "5"}); return true; }
        if (plain.startsWith("отмена похода")) { showQuestion(vkId, new String[]{"!вопрос", "6"}); return true; }
        return false;
    }

    private String buyIdFromLabel(String plain) {
        if (plain.contains("оружие")) return "equip_weapon_iron";
        if (plain.contains("броню") || plain.contains("броня")) return "equip_armor_chain";
        if (plain.contains("талисман")) return "equip_talisman_sanity";
        if (plain.contains("инструмент")) return "equip_tool_lockpick";
        if (plain.contains("рюкзак")) return "equip_backpack_big";
        if (plain.contains("зелье лечения") || plain.contains("лечени")) return "potion_heal";
        if (plain.contains("зелье рассудка") || plain.contains("рассудок")) return "potion_sanity";
        if (plain.contains("антидот")) return "potion_antidote";
        if (plain.contains("свиток побега") || plain.contains("побег")) return "scroll_escape";
        if (plain.contains("свиток переброса") || plain.contains("переброс")) return "scroll_reroll";
        if (plain.contains("свиток очищения") || plain.contains("очищен")) return "scroll_cleanse";
        if (plain.contains("набор лагеря") || plain.contains("набор")) return "camp_kit";
        return null;
    }

    private String useIdFromLabel(String plain) {
        if (plain.contains("❤") || plain.contains("лечени")) return "potion_heal";
        if (plain.contains("🧠") || plain.contains("рассудок")) return "potion_sanity";
        if (plain.contains("☠") || plain.contains("антидот")) return "potion_antidote";
        if (plain.contains("📜") || plain.contains("побег")) return "scroll_escape";
        if (plain.contains("🎲") || plain.contains("переброс")) return "scroll_reroll";
        if (plain.contains("🕯") || plain.contains("очищен")) return "scroll_cleanse";
        if (plain.contains("⛺") || plain.contains("лагер")) return "camp_kit";
        return null;
    }

    private String skillIdFromLabel(String plain) {
        if (plain.contains("живучесть")) return "tough";
        if (plain.contains("клинок")) return "sharp";
        if (plain.contains("ловушки")) return "trap_sense";
        if (plain.contains("удача")) return "lucky";
        if (plain.contains("торговец")) return "trader";
        if (plain.contains("оккультизм")) return "occult";
        if (plain.contains("травник")) return "herbalist";
        if (plain.contains("носильщик")) return "packer";
        return null;
    }

    private boolean isSecurityAuthText(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("войти:") || t.contains("блокировка") || t.contains("2fa")
                || (t.matches(".*\\b\\d{4,6}\\b.*") && (t.contains("войти") || t.contains("код")));
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand().toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(cmd)) return;
        e.setCancelled(true);
        int peer = e.getPeerId();
        int sender = e.getSenderVkId();
        String[] args = e.getArgs() == null ? new String[]{cmd} : e.getArgs().clone();
        if (e.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleVKCommandSync(cmd, peer, sender, args));
        } else {
            handleVKCommandSync(cmd, peer, sender, args);
        }
    }

    private void handleVKCommandSync(String cmd, int peer, int sender, String[] args) {
        // В общей беседе — только одна подсказка, вся логика строго в ЛС.
        if (peer != sender && !cmd.equals("!офадмин")) {
            api().sendMessage(peer, "🔔 @id" + sender + ", оффлайн-походы работают только в ЛС бота. Я отправил подсказку тебе в личные сообщения.");
            api().sendKeyboard(sender, "⛺ CHRDK ADVENTURES\n\nОффлайн-походы работают здесь, в ЛС бота.\nВыбери маршрут кнопкой или напиши: !поход", keyboardMain());
            return;
        }

        if (cmd.equals("!поход") || cmd.equals("!походы") || cmd.equals("!adventure") || cmd.equals("!adv") || cmd.equals("!офлайн") || cmd.equals("!offline")) {
            showMenuOrStatus(sender);
        } else if (cmd.equals("!герой")) {
            showHeroMenu(sender);
        } else if (cmd.equals("!пойти")) {
            startAdventure(sender, args);
        } else if (cmd.equals("!выбор")) {
            handleChoice(sender, args);
        } else if (cmd.equals("!статуспохода")) {
            showStatus(sender);
        } else if (cmd.equals("!забрать") || cmd.equals("!тайник") || cmd.equals("!stash")) {
            showStash(sender, args);
        } else if (cmd.equals("!отменапоход")) {
            cancel(sender);
        } else if (cmd.equals("!вопрос")) {
            showQuestion(sender, args);
        } else if (cmd.equals("!открыть")) {
            unlockRoute(sender, args);
        } else if (cmd.equals("!класс")) {
            chooseClass(sender, args);
        } else if (cmd.equals("!спутник")) {
            chooseCompanion(sender, args);
        } else if (cmd.equals("!отдых")) {
            takeRest(sender);
        } else if (cmd.equals("!лечиться") || cmd.equals("!лечение")) {
            healSmart(sender);
        } else if (cmd.equals("!госпиталь")) {
            if (args.length >= 2) useHospital(sender, args[1]); else showHospital(sender);
        } else if (cmd.equals("!психика")) {
            showPsyche(sender);
        } else if (cmd.equals("!кампания")) {
            showCampaign(sender);
        } else if (cmd.equals("!глава")) {
            if (args.length >= 2) startCampaignChapter(sender, args[1]); else showCampaign(sender);
        } else if (cmd.equals("!коллекции") || cmd.equals("!коллекция")) {
            showCollections(sender);
        } else if (cmd.equals("!отношения")) {
            showRelationships(sender);
        } else if (cmd.equals("!магазин") || cmd.equals("!лавка")) {
            showOfflineShop(sender, args.length >= 2 ? args[1] : "main");
        } else if (cmd.equals("!купить")) {
            if (args.length < 2) showOfflineShop(sender, "main"); else buyOfflineItem(sender, args[1]);
        } else if (cmd.equals("!экипировка") || cmd.equals("!снаряжение")) {
            showOfflineEquipment(sender);
        } else if (cmd.equals("!использовать") || cmd.equals("!юз")) {
            if (args.length < 2) showConsumables(sender); else useConsumable(sender, args[1]);
        } else if (cmd.equals("!продатьтайник") || cmd.equals("!продатьstash")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) sellStash(sender, true); else previewSellStash(sender);
        } else if (cmd.equals("!навыки")) {
            showOfflineSkills(sender);
        } else if (cmd.equals("!навык")) {
            if (args.length < 2) showOfflineSkills(sender); else learnOfflineSkill(sender, args[1]);
        } else if (cmd.equals("!инвентарь") || cmd.equals("!сумка")) {
            showAdventureInventory(sender);
        } else if (cmd.equals("!персонаж") || cmd.equals("!профильпохода")) {
            showAdventureProfile(sender);
        } else if (cmd.equals("!дневник") || cmd.equals("!журнал")) {
            showJournal(sender);
        } else if (cmd.equals("!ачивки") || cmd.equals("!достижения")) {
            showAchievements(sender);
        } else if (cmd.equals("!ежедневка") || cmd.equals("!дейлик")) {
            showDaily(sender);
        } else if (cmd.equals("!офадмин")) {
            handleAdmin(sender, peer, args);
        }
        }


    private VKChatAPI api() { return VKChatPlugin.getInstance().getApi(); }

    private void showHeroMenu(int vkId) {
        api().sendKeyboard(vkId, "👤 Меню героя\n\nВыбери раздел: персонаж, класс, спутник, навыки, расходники, кампания или дневник.", keyboardHero());
    }

    private void showMenuOrStatus(int vkId) {
        if (active.containsKey(vkId)) { showStatus(vkId); return; }
        UUID uuid = api().getUuidByVkId(vkId);
        if (uuid == null) { api().sendMessage(vkId, "❌ Твой ВК не привязан к Minecraft аккаунту."); return; }

        StringBuilder sb = new StringBuilder();
        sb.append("⛺ CHRDK ADVENTURES\n\n");
        sb.append("👤 ").append(className(getPlayerClass(vkId))).append(" | ").append(companionName(getCompanion(vkId))).append("\n");
        sb.append("LVL ").append(getAdvLevel(vkId)).append(" | XP ").append(getAdvXp(vkId)).append("/").append(xpToNext(getAdvLevel(vkId))).append("\n");
        sb.append("🎒 Лут: /stash | 🗝 ключи в тайнике\n");
        sb.append("☠ Смерть: штраф, кулдаун и травма\n");
        sb.append("🛒 Магазин: !магазин | Навыки: !навыки\n");
        sb.append("📖 Кампания: !кампания | 🧠 Психика: !психика | 🏥 Госпиталь: !госпиталь\n\n");
        sb.append("🗺 Маршруты:\n");
        ConfigurationSection routes = plugin.getConfig().getConfigurationSection("adventures");
        if (routes != null) {
            for (String key : routes.getKeys(false)) {
                boolean unlocked = isRouteUnlocked(vkId, key);
                sb.append(routeCard(vkId, key, unlocked)).append("\n");
            }
        }
        sb.append("\n👇 Выбери действие кнопкой ниже.");
        api().sendKeyboard(vkId, sb.toString(), keyboardMain());
    }

    private void startAdventure(int vkId, String[] args) {
        UUID uuid = api().getUuidByVkId(vkId);
        if (uuid == null) { api().sendMessage(vkId, "❌ Твой ВК не привязан к Minecraft аккаунту."); return; }
        if (Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).isOnline()) {
            api().sendMessage(vkId, "❌ Походы доступны только когда персонаж оффлайн. Выйди с сервера и запусти поход в ЛС бота."); return;
        }
        if (active.containsKey(vkId)) { showStatus(vkId); return; }
        long now = System.currentTimeMillis();
        if (cooldowns.getOrDefault(vkId, 0L) > now) {
            int cost = healingCost(vkId);
            api().sendKeyboard(vkId, "⏳ Кулдаун после смерти: " + formatDuration(cooldowns.get(vkId) - now) + "\n\n" +
                    "💚 Можно вылечиться досрочно за 5% репутации ВК.\n" +
                    "Стоимость сейчас: " + cost + " реп.", keyboardHeal());
            return;
        }
        if (args.length < 2) { showMenuOrStatus(vkId); return; }

        String key = args[1].toLowerCase(Locale.ROOT);
        ConfigurationSection route = plugin.getConfig().getConfigurationSection("adventures." + key);
        if (route == null) { api().sendMessage(vkId, "❌ Маршрут не найден. Напиши !поход."); return; }
        if (!isRouteUnlocked(vkId, key)) {
            api().sendKeyboard(vkId, "🔒 Маршрут закрыт\n\n" +
                    routeEmoji(key) + " " + route.getString("name", key) + "\n" +
                    "🗝 Нужен ключ: " + cleanKeyName(key) + "\n\n" +
                    "Если ключ в тайнике — нажми «Открыть».", keyboardUnlock(key));
            return;
        }

        int cost = route.getInt("cost", 0);
        int rep = api().getReputation(vkId);
        if (rep < cost) { api().sendMessage(vkId, "❌ Недостаточно репутации. Нужно: " + cost + ", у тебя: " + rep + "."); return; }
        if (cost > 0) api().takeReputation(vkId, cost);

        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName() != null ? off.getName() : uuid.toString();
        ActiveAdventure adv = new ActiveAdventure(vkId, uuid, name, key, now);
        adv.maxStages = route.getInt("stages", 3);
        adv.maxHp = 100 + offlineEquipBonus(vkId, "hp");
        if (hasOfflineSkill(vkId, "tough")) adv.maxHp += 15;
        if ("alchemist".equals(getCompanion(vkId))) adv.maxHp += 10;
        if (hasTrauma(vkId, "deep_wound")) adv.maxHp = Math.max(50, adv.maxHp - 12);
        adv.hp = injuries.getOrDefault(vkId, 0L) > now ? Math.min(75, adv.maxHp) : adv.maxHp;
        adv.supplies = plugin.getConfig().getInt("mmorpg.base-supplies", 3) + Math.max(0, getAdvLevel(vkId) / 5);
        if ("mule".equals(getCompanion(vkId))) adv.supplies += 2;
        adv.supplies += offlineEquipBonus(vkId, "supplies");
        if (hasOfflineSkill(vkId, "packer")) adv.supplies += 2;
        adv.morale = Math.max(35, getSanity(vkId));
        adv.sanity = getSanity(vkId);
        adv.campaignChapter = chapterForRoute(key);
        adv.xpGained = 0;
        adv.inspiration = Math.max(0, getAdvLevel(vkId) / 3);
        if (hasTrauma(vkId, "nightmares")) adv.inspiration = Math.max(0, adv.inspiration - 1);
        adv.condition = "none";
        adv.gold = 0;
        adv.relics = 0;
        adv.blessing = "none";
        adv.hardDeadline = now + route.getInt("max-duration-minutes", 120) * 60_000L;
        adv.nextEventTime = now + plugin.getConfig().getLong("first-event-delay-seconds", 20) * 1000L;
        active.put(vkId, adv);
        addJournal(vkId, "🚶 Начат поход: " + route.getString("name", key));
        saveAll();

        api().sendKeyboard(vkId, "🚶 Поход начат\n\n" +
                routeEmoji(key) + " " + route.getString("name", key) + "\n" +
                "👤 " + name + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 Припасы: " + adv.supplies + " | 🧠 Мораль: " + adv.morale + "%\n" +
                "✨ Вдохновение: " + adv.inspiration + " | " + companionName(getCompanion(vkId)) + "\n" +
                conditionText(adv.condition) + "\n" +
                "📍 Этап: 0/" + adv.maxStages + "\n\n" +
                "⏳ Первый выбор появится скоро.", keyboardStatusOnly());
    }

    private void handleChoice(int vkId, String[] args) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null || !adv.waitingChoice) { api().sendMessage(vkId, "Сейчас нет события, требующего выбора."); return; }
        int choice = 0;
        if (args.length >= 2) { try { choice = Integer.parseInt(args[1]); } catch (Exception ignored) {} }
        if (choice < 1 || choice > 4) { api().sendKeyboard(vkId, "Выбери вариант 1–4.", keyboardChoices()); return; }
        resolveChoice(adv, choice, false);
    }

    private void createEvent(ActiveAdventure adv) {
        String[] types = {
                "combat", "combat", "trap", "trap", "survival", "survival",
                "riddle", "ambush", "curse", "treasure", "merchant", "shrine", "rare", "gathering", "camp", "mimic", "puzzle", "duel", "portal", "artifact", "patron", "heist", "disease", "baba_yaga", "leshy", "rusalka", "domovoi", "perun", "morana", "vodyanoy", "koshchey", "zmey", "bogatyr", "oracle", "tavern", "blacksmith", "moral", "nightmare", "memory", "companion", "collection", "extra", "extra", "extra", "extra", "boss"
        };
        if (adv.stage >= adv.maxStages - 1) adv.pendingType = "boss";
        else adv.pendingType = types[random.nextInt(types.length - 1)];
        adv.pendingTitle = eventTitle(adv.pendingType, adv.route);
        adv.waitingChoice = true;
        adv.choiceDeadline = System.currentTimeMillis() + plugin.getConfig().getLong("choice-timeout-seconds", 300) * 1000L;
        saveAll();
        api().sendKeyboard(adv.vkId, buildEventMessage(adv), keyboardForEvent(adv));
    }

    private String eventTitle(String type, String route) {
        switch (type) {
            case "combat": return randomOf("Бой с опасным противником", "Элитный следопыт", "Тварь учуяла кровь", "Столкновение у старой тропы");
            case "trap": return randomOf("Ржавая капканная петля", "Провал под ногами", "Стрелы из стены", "Проклятая растяжка", "Ловушка с шипами");
            case "ambush": return randomOf("Засада разбойников", "Окружение нежити", "Тихий выстрел из темноты", "Стая вышла на след");
            case "curse": return randomOf("Проклятый алтарь", "Шёпот мёртвых", "Туман безысходности", "Метка болотной ведьмы");
            case "treasure": return randomOf("Запертый сундук", "Зарытый тайник", "Пыльная шкатулка", "Следы старого каравана");
            case "merchant": return randomOf("Странствующий торговец", "Раненый проводник", "Скупщик трофеев", "Караван у костра");
            case "shrine": return randomOf("Святилище у дороги", "Рунический камень", "Забытая часовня", "Источник силы");
            case "riddle": return randomOf("Загадка древнего путника", "Надпись на камне", "Голос из саркофага", "Испытание памяти");
            case "survival": return randomOf("Проверка выживания", "Ночь без костра", "Голод и холод", "Опасная переправа", "Ядовитые испарения");
            case "gathering": return randomOf("Залежи ресурсов", "Травы у ручья", "Охотничья стоянка", "Следы редкой руды");
            case "camp": return randomOf("Привал у костра", "Ночной лагерь", "Передышка в руинах", "Тёплый грот");
            case "mimic": return randomOf("Сундук-мимик", "Живая шкатулка", "Зубастый тайник", "Ложная награда");
            case "puzzle": return randomOf("Комната с рычагами", "Зеркальный лабиринт", "Шахматная дверь", "Механизм древних");
            case "duel": return randomOf("Дуэль с чемпионом", "Честный поединок", "Вызов на арене", "Рыцарь-призрак");
            case "portal": return randomOf("Нестабильный портал", "Разрыв пространства", "Круг телепорта", "Дверь не туда");
            case "artifact": return randomOf("Спящий артефакт", "Проклятая реликвия", "Светящийся идол", "Осколок древних");
            case "patron": return randomOf("Вмешательство покровителя", "Сон у костра", "Шёпот божества", "Знак судьбы");
            case "heist": return randomOf("Кража у каравана", "Тихое проникновение", "Сейф разбойников", "Кошель стражника");
            case "disease": return randomOf("Болотная лихорадка", "Чёрная плесень", "Гнилой воздух", "Заражённая рана");
            case "baba_yaga": return randomOf("Избушка Бабы-Яги", "Ступа над болотом", "Костяная ограда", "Ведьмин котёл");
            case "leshy": return randomOf("Тропа Лешего", "Лес меняет местами деревья", "Зелёный хозяин чащи", "Шёпот старого бора");
            case "rusalka": return randomOf("Песня Русалки", "Лунный омут", "Волосы в тёмной воде", "Смех у берега");
            case "domovoi": return randomOf("Домовой у заброшенной печи", "Старый хранитель избы", "Шорох за полатями", "Маленький хозяин очага");
            case "perun": return randomOf("Испытание Перуна", "Громовой камень", "Молния над дубом", "Клятва воина");
            case "morana": return randomOf("Дыхание Мораны", "Ледяная пелена", "Чёрный снег", "Сон мёртвой зимы");
            case "vodyanoy": return randomOf("Водяной требует плату", "Трясина открыла глаза", "Сеть из тины", "Водоворот у коряг");
            case "koshchey": return randomOf("Игла Кощея", "Сундук на цепях", "Бессмертная тень", "Костяной договор");
            case "zmey": return randomOf("След Змея Горыныча", "Три пасти в дыму", "Пепел на траве", "Крылья закрыли солнце");
            case "bogatyr": return randomOf("Испытание Богатыря", "Камень у трёх дорог", "Поединок чести", "Тяжёлая булава");
            case "oracle": return randomOf("Оракул у чёрного зеркала", "Карты судьбы", "Предсказание на костях", "Шёпот будущего");
            case "tavern": return randomOf("Придорожная корчма", "Песни у очага", "Пьяная драка", "Слухи за кружкой медовухи");
            case "blacksmith": return randomOf("Кузня в пустоши", "Старый мастер у горна", "Раскалённая наковальня", "Чертёж на углях");
            case "moral": return randomOf("Моральный выбор", "Плачущий пленник", "Два пути и одна цена", "Кошель или жизнь");
            case "nightmare": return randomOf("Сон наяву", "Голос из прошлой смерти", "Фобия оживает", "Паническая атака у костра");
            case "memory": return randomOf("Обрывок памяти героя", "Письмо из прошлого", "Старый шрам напоминает цену", "Сон о доме");
            case "companion": return randomOf("Разговор со спутником", "Испытание доверия", "Спор у костра", "Верность на грани");
            case "collection": return randomOf("Редкая находка коллекционера", "Осколок хроники", "Знак древней экспедиции", "Фрагмент карты мира");
            case "extra": return randomExtraEventTitle();
            case "boss": return randomOf("Мини-босс маршрута", "Страж последнего этапа", "Главная угроза похода", "Хозяин этих земель");
            default: return randomOf("Редкая находка", "Необычный след", "Сияние в темноте", "Шанс на удачу");
        }
    }

    private void resolveChoice(ActiveAdventure adv, int choice, boolean timeout) {
        adv.waitingChoice = false;
        int rep = api().getReputation(adv.vkId);
        int routeDifficulty = plugin.getConfig().getInt("adventures." + adv.route + ".difficulty", 1);
        int deathHistory = data.getInt("stats." + adv.vkId + ".deaths", 0);
        String type = adv.pendingType == null ? "combat" : adv.pendingType;

        int dc = checkDc(type, routeDifficulty, deathHistory, rep, adv);
        int modifier = checkModifier(getPlayerClass(adv.vkId), type, choice, adv) + companionCheckModifier(getCompanion(adv.vkId), type, choice) + blessingCheckModifier(adv.blessing, type, choice) + integrationCheckModifier(adv.vkId, adv.route, type) + offlineEquipBonus(adv.vkId, "check");
        if (hasOfflineSkill(adv.vkId, "sharp") && isCombatEvent(type)) modifier += 2;
        if (hasOfflineSkill(adv.vkId, "occult") && (type.equals("curse") || type.equals("riddle") || type.equals("shrine"))) modifier += 2;

        int roll = 1 + random.nextInt(20);
        boolean usedInspiration = false;
        int firstRoll = roll;
        boolean bad = !(roll == 20 || (roll != 1 && roll + modifier >= dc));
        if (bad && adv.inspiration > 0 && plugin.getConfig().getBoolean("mmorpg.inspiration.auto-reroll", true)) {
            adv.inspiration--;
            usedInspiration = true;
            roll = 1 + random.nextInt(20);
            bad = !(roll == 20 || (roll != 1 && roll + modifier >= dc));
        }
        String action = actionName(choice, type);
        StringBuilder msg = new StringBuilder();
        msg.append(timeout ? "⏱ Авто-выбор\n\n" : "✅ Выбор принят\n\n");
        msg.append(action).append("\n");
        msg.append("🎲 Испытание: ").append(eventIcon(type)).append(" ").append(adv.pendingTitle).append("\n");
        msg.append("🎲 d20: ").append(usedInspiration ? firstRoll + " → " : "").append(roll).append(modifier >= 0 ? " +" : " ").append(modifier).append(" vs DC ").append(dc).append("\n");
        if (roll == 20) msg.append("🌟 Критический успех!\n");
        if (roll == 1) msg.append("💀 Критический провал!\n");
        if (usedInspiration) msg.append("✨ Вдохновение спасло от провала. Осталось: ").append(adv.inspiration).append("\n");
        msg.append("🥫 ").append(adv.supplies).append(" | 🧠 ").append(adv.morale).append("% | ").append(conditionText(adv.condition)).append("\n\n");

        // Особые добрые события: при осторожном/исследовательском выборе могут дать бонус даже без боя.
        if (!bad) {
            int bonus = successReward(type, routeDifficulty, choice);
            bonus += offlineEquipBonus(adv.vkId, "rep");
            if (hasOfflineSkill(adv.vkId, "lucky") && random.nextInt(100) < 20) bonus += 20;
            api().addReputation(adv.vkId, bonus);
            int xp = xpForEvent(type, routeDifficulty, true);
            adv.xpGained += xp;
            msg.append("✨ Исход: успех\n");
            msg.append("💚 Получено: +").append(bonus).append(" репутации | +").append(xp).append(" XP\n");
            if (roll == 20) {
                int crit = 15 + routeDifficulty * 5;
                api().addReputation(adv.vkId, crit);
                adv.xpGained += 10;
                msg.append("🌟 Крит-бонус: +").append(crit).append(" репутации и +10 XP\n");
            }
            applyPositiveEventEffects(adv, type, choice, msg);
            applyCompanionPositive(adv, type, choice, msg);
            maybeDropKey(adv, msg);
        } else {
            int damage = applyCompanionDamageReduction(applyClassDamageReduction(damageFor(type, routeDifficulty, choice), getPlayerClass(adv.vkId), type), getCompanion(adv.vkId), type);
            damage = Math.max(1, damage - offlineEquipBonus(adv.vkId, "armor"));
            if (hasOfflineSkill(adv.vkId, "tough")) damage = (int)Math.max(1, Math.round(damage * 0.9));

            int xp = xpForEvent(type, routeDifficulty, false);
            adv.xpGained += xp;
            adv.hp -= damage;
            msg.append("💥 Исход: неудача\n");
            msg.append("🩸 Потеряно HP: ").append(damage).append(" | +").append(xp).append(" XP за опыт\n");
            applyNegativeEventEffects(adv, type, choice, msg);
            applyCompanionNegative(adv, type, choice, msg);
            if (roll == 1) {
                applyCondition(adv, randomConditionFor(type), msg);
            }
            msg.append("❤️ Осталось: ").append(hpBar(Math.max(0, adv.hp), adv.maxHp)).append(" ").append(Math.max(0, adv.hp)).append("/").append(adv.maxHp).append("\n");

            int lethalChance = lethalChance(type, routeDifficulty, choice);
            if (adv.hp <= 0 || random.nextInt(100) < lethalChance || random.nextInt(100) < plugin.getConfig().getInt("adventures." + adv.route + ".death-chance", 5)) {
                killAdventure(adv, msg.append("\n").append(deathScene(type, adv.route)).append("\n").toString());
                return;
            }
        }

        consumeStageResources(adv, type, choice, bad, msg);

        adv.stage++;
        if (adv.stage >= adv.maxStages) {
            finishAdventure(adv, msg.toString());
        } else {
            adv.nextEventTime = System.currentTimeMillis() + plugin.getConfig().getLong("stage-delay-seconds", 60) * 1000L;
            saveAll();
            api().sendKeyboard(adv.vkId, msg.append("\n📍 Прогресс: ").append(stageBar(adv.stage, adv.maxStages)).append(" ").append(adv.stage).append("/").append(adv.maxStages).append("\n⏳ Следующий выбор появится позже.").toString(), keyboardStatusOnly());
        }
    }

    private int baseRisk(String type, int difficulty, int deaths, int rep) {
        return OfflineEventMath.baseRisk(type, difficulty, deaths, rep);
    }

    private int choiceRiskModifier(String type, int choice) {
        return OfflineEventMath.choiceRiskModifier(type, choice);
    }

    private int damageFor(String type, int difficulty, int choice) {
        return OfflineEventMath.damageFor(random, type, difficulty, choice);
    }

    private int lethalChance(String type, int difficulty, int choice) {
        return OfflineEventMath.lethalChance(plugin.getConfig(), type, difficulty, choice);
    }

    private int successReward(String type, int difficulty, int choice) {
        return OfflineEventMath.successReward(random, type, difficulty, choice);
    }

    private void applyPositiveEventEffects(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        if (type.equals("shrine")) {
            int heal = 8 + random.nextInt(10);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            msg.append("💖 Святилище восстановило HP: +").append(heal).append("\n");
            if (!"none".equals(adv.condition) && random.nextInt(100) < 60) {
                msg.append("🕯 Святилище сняло состояние: ").append(conditionText(adv.condition)).append("\n");
                adv.condition = "none";
            }
        } else if (type.equals("treasure")) {
            List<ItemStack> bonusItems = rollEventLoot(adv.route, "treasure");
            if (!bonusItems.isEmpty()) {
                plugin.getStashManager().addItems(adv.uuid, bonusItems);
                msg.append("🎁 Найден тайник: предметов отправлено в /stash: ").append(bonusItems.size()).append("\n");
            }
        } else if (type.equals("merchant")) {
            msg.append("🛒 Торговец дал скидочный жетон и подсказал безопасную тропу.\n");
            adv.hp = Math.min(adv.maxHp, adv.hp + 5);
        } else if (type.equals("camp")) {
            int heal = 10 + random.nextInt(12);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            adv.morale = Math.min(100, adv.morale + 18);
            adv.supplies = Math.max(0, adv.supplies - 1);
            msg.append("🔥 Привал восстановил HP: +").append(heal).append(", мораль +18%\n");
            if ("bleeding".equals(adv.condition) || "exhausted".equals(adv.condition)) {
                msg.append("🩹 На привале удалось обработать состояние: ").append(conditionText(adv.condition)).append("\n");
                adv.condition = "none";
            }
        } else if (type.equals("gathering")) {
            adv.supplies += 1 + random.nextInt(2);
            msg.append("🥫 Найдены припасы. Текущие припасы: ").append(adv.supplies).append("\n");
        } else if (type.equals("mimic")) {
            adv.gold += 20 + random.nextInt(30);
            msg.append("📦 Мимик выплюнул монеты. Золото +").append(adv.gold).append(" всего\n");
        } else if (type.equals("puzzle")) {
            adv.inspiration++;
            msg.append("🧩 Головоломка дала вдохновение +1.\n");
        } else if (type.equals("duel")) {
            adv.morale = Math.min(100, adv.morale + 15);
            msg.append("🏟 Победа в дуэли подняла мораль +15%.\n");
        } else if (type.equals("portal")) {
            if (adv.stage < adv.maxStages - 1) {
                adv.stage++;
                msg.append("🌀 Портал перебросил вперёд: этап +1.\n");
            } else msg.append("🌀 Портал стабилизировал финальный путь.\n");
        } else if (type.equals("artifact")) {
            adv.relics++;
            msg.append("🏺 Найдена реликвия похода. Реликвии: ").append(adv.relics).append("\n");
        } else if (type.equals("patron")) {
            adv.blessing = randomBlessing();
            msg.append("🌟 Получено благословение: ").append(blessingText(adv.blessing)).append("\n");
        } else if (type.equals("heist")) {
            int gold = 30 + random.nextInt(50);
            adv.gold += gold;
            msg.append("🗝 Удачная кража: золото +").append(gold).append("\n");
        } else if (type.equals("disease")) {
            msg.append("🧪 Болезнь удалось подавить травами и волей.\n");
            if ("poisoned".equals(adv.condition)) adv.condition = "none";
        } else if (isSlavicMyth(type)) {
            handleSlavicPositive(adv, type, choice, msg);
        } else if (type.equals("oracle")) {
            adv.inspiration += 1;
            adv.blessing = randomBlessing();
            msg.append("🔮 Оракул дал видение: вдохновение +1 и ").append(blessingText(adv.blessing)).append("\n");
        } else if (type.equals("tavern")) {
            adv.morale = Math.min(100, adv.morale + 20);
            adv.supplies += 1;
            msg.append("🍻 Корчма вернула силы: мораль +20%, припасы +1\n");
        } else if (type.equals("blacksmith")) {
            adv.gold += 35;
            msg.append("🔨 Кузнец продал трофеи: золото +35\n");
        } else if (type.equals("moral")) {
            if (choice == 2 || choice == 4) {
                adv.morale = Math.min(100, adv.morale + 25);
                adv.blessing = "wisdom";
                msg.append("⚖ Милосердие отмечено судьбой: мораль +25%, благословение Мудрости\n");
            } else {
                adv.gold += 25;
                msg.append("⚖ Ты выбрал выгоду: золото +25\n");
            }
        } else if (type.equals("nightmare")) {
            changeSanity(adv.vkId, 6, msg);
            msg.append("🧠 Герой победил кошмар: рассудок восстановлен.\n");
        } else if (type.equals("memory")) {
            changeSanity(adv.vkId, 8, msg);
            adv.inspiration++;
            msg.append("📜 Память укрепила волю: вдохновение +1.\n");
        } else if (type.equals("companion")) {
            addCompanionRelation(adv.vkId, getCompanion(adv.vkId), 6, msg);
            adv.morale = Math.min(100, adv.morale + 10);
        } else if (type.equals("collection")) {
            discoverCollection(adv.vkId, adv.route, msg);
            adv.relics++;
        } else if (type.equals("extra")) {
            applyExtraPositive(adv, choice, msg);
        } else if (type.equals("boss")) {
            List<ItemStack> bonusItems = rollEventLoot(adv.route, "boss");
            if (!bonusItems.isEmpty()) {
                plugin.getStashManager().addItems(adv.uuid, bonusItems);
                msg.append("👑 Босс повержен: трофеев в /stash: ").append(bonusItems.size()).append("\n");
            }
        } else if (type.equals("curse")) {
            msg.append("🕯 Ты развеял проклятие и избежал метки смерти.\n");
        }
    }

    private void applyNegativeEventEffects(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        if (type.equals("trap")) {
            msg.append("🪤 Ловушка сработала: кровь, металл и паника.\n");
            if (random.nextInt(100) < 45) applyCondition(adv, "bleeding", msg);
        } else if (type.equals("curse")) {
            if (random.nextInt(100) < 55) applyCondition(adv, "cursed", msg);
            int loss = Math.max(10, api().getReputation(adv.vkId) / 100);
            api().takeReputation(adv.vkId, loss);
            msg.append("🧿 Проклятие высосало репутацию: -").append(loss).append("\n");
        } else if (type.equals("merchant")) {
            msg.append("🪙 Сделка оказалась обманом.\n");
        } else if (type.equals("treasure")) {
            msg.append("📦 Сундук оказался приманкой.\n");
        } else if (type.equals("camp")) {
            adv.supplies = Math.max(0, adv.supplies - 1);
            msg.append("🔥 Костёр выдал позицию врагам. Припасы -1.\n");
        } else if (type.equals("gathering")) {
            msg.append("⛏ Ресурсы оказались в опасной зоне.\n");
        } else if (type.equals("mimic")) {
            msg.append("📦 Мимик вцепился в руку и сожрал часть добычи.\n");
            adv.gold = Math.max(0, adv.gold - 20);
        } else if (type.equals("puzzle")) {
            msg.append("🧩 Механизм ударил током и запутал путь.\n");
            adv.morale = Math.max(0, adv.morale - 10);
        } else if (type.equals("duel")) {
            msg.append("🏟 Чемпион опозорил персонажа. Мораль -18%.\n");
            adv.morale = Math.max(0, adv.morale - 18);
        } else if (type.equals("portal")) {
            msg.append("🌀 Портал выбросил в опасную зону. Следующая проверка будет тяжелее.\n");
            adv.condition = "exhausted";
        } else if (type.equals("artifact")) {
            msg.append("🏺 Реликвия оказалась проклятой.\n");
            applyCondition(adv, "cursed", msg);
        } else if (type.equals("patron")) {
            msg.append("🌑 Покровитель отвернулся. Вдохновение -1.\n");
            adv.inspiration = Math.max(0, adv.inspiration - 1);
        } else if (type.equals("heist")) {
            int loss = Math.max(15, api().getReputation(adv.vkId) / 80);
            api().takeReputation(adv.vkId, loss);
            msg.append("🗝 Кража сорвалась. Штраф репутации -").append(loss).append("\n");
        } else if (type.equals("disease")) {
            msg.append("🦠 Болезнь прицепилась к персонажу.\n");
            applyCondition(adv, randomOf("poisoned", "exhausted"), msg);
        } else if (isSlavicMyth(type)) {
            handleSlavicNegative(adv, type, choice, msg);
        } else if (type.equals("oracle")) {
            adv.inspiration = Math.max(0, adv.inspiration - 1);
            msg.append("🔮 Видение оказалось ложным: вдохновение -1\n");
        } else if (type.equals("tavern")) {
            adv.gold = Math.max(0, adv.gold - 15);
            msg.append("🍻 В корчме обокрали: золото -15\n");
        } else if (type.equals("blacksmith")) {
            adv.supplies = Math.max(0, adv.supplies - 1);
            msg.append("🔨 Искры подпалили сумку: припасы -1\n");
        } else if (type.equals("moral")) {
            adv.morale = Math.max(0, adv.morale - 20);
            msg.append("⚖ Тяжёлый выбор гложет душу: мораль -20%\n");
        } else if (type.equals("nightmare")) {
            changeSanity(adv.vkId, -12, msg);
            maybeAddPhobia(adv.vkId, adv.route, type, msg);
        } else if (type.equals("memory")) {
            changeSanity(adv.vkId, -6, msg);
            msg.append("📜 Воспоминание ранит сильнее клинка.\n");
        } else if (type.equals("companion")) {
            addCompanionRelation(adv.vkId, getCompanion(adv.vkId), -5, msg);
            adv.morale = Math.max(0, adv.morale - 8);
        } else if (type.equals("collection")) {
            changeSanity(adv.vkId, -4, msg);
            msg.append("🏺 Находка рассыпалась, оставив чувство потери.\n");
        } else if (type.equals("extra")) {
            applyExtraNegative(adv, choice, msg);
        } else if (type.equals("boss")) {
            adv.morale = Math.max(0, adv.morale - 25);
            msg.append("👑 Босс подавил волю персонажа. Мораль -25%.\n");
        }
    }

    private List<ItemStack> rollEventLoot(String route, String type) {
        String path = "event-loot." + type + ".items";
        if (!plugin.getConfig().contains(path)) return new ArrayList<>();
        return rollItems(path);
    }

    private int integrationCheckModifier(int vkId, String route, String type) {
        int mod = 0;
        int gear = snapshotGear(vkId);
        mod += Math.min(5, gear / 12);
        if (route.equals("mine")) mod += snapshotJob(vkId, "miner") / 10;
        if (route.equals("forest") || route.equals("swamp")) mod += snapshotJob(vkId, "farmer") / 12;
        if (type.equals("curse") || type.equals("disease") || type.equals("shrine")) mod += snapshotJob(vkId, "alchemist") / 10;
        if (type.equals("blacksmith") || type.equals("artifact")) mod += snapshotJob(vkId, "blacksmith") / 10;
        String nation = snapshotNation(vkId).toLowerCase();
        if ((route.equals("forest") || route.equals("swamp")) && nation.contains("pagan")) mod += 2;
        if (route.equals("castle") && nation.contains("imperial")) mod += 2;
        if ((route.equals("mine") || type.equals("blacksmith")) && nation.contains("soviet")) mod += 2;
        return Math.min(8, mod);
    }

    private int snapshotGear(int vkId) { return data.getInt("snapshots." + vkId + ".gearPower", 0); }
    private int snapshotJob(int vkId, String job) { return data.getInt("snapshots." + vkId + ".jobs." + job, 0); }
    private String snapshotNation(int vkId) { return data.getString("snapshots." + vkId + ".nation", "none"); }

    private int worldEventRiskModifier() {
        int mod = 0;
        try {
            if (VKChatPlugin.getInstance().getBloodMoonManager() != null && VKChatPlugin.getInstance().getBloodMoonManager().isActive()) mod += 6;
        } catch (Throwable ignored) {}
        try {
            org.bukkit.plugin.Plugin events = Bukkit.getPluginManager().getPlugin("VKChatEvents");
            if (events != null && events.isEnabled()) {
                Object wrath = events.getClass().getMethod("getWrathManager").invoke(events);
                Object cat = wrath.getClass().getMethod("getActiveCataclysm").invoke(wrath);
                if (cat != null) mod += 4;
            }
        } catch (Throwable ignored) {}
        return mod;
    }

    private double worldEventRewardMultiplier() {
        double mult = 1.0;
        try {
            if (VKChatPlugin.getInstance().getBloodMoonManager() != null && VKChatPlugin.getInstance().getBloodMoonManager().isActive()) mult += 0.25;
        } catch (Throwable ignored) {}
        return mult;
    }

    private void addIntegratedRewards(ActiveAdventure adv, java.util.List<ItemStack> items) {
        int diff = plugin.getConfig().getInt("adventures." + adv.route + ".difficulty", 1);
        if (random.nextInt(100) < 6 + diff * 3) items.add(createGearSetFragment());
        if (random.nextInt(100) < 8 + diff * 2) items.add(createArtifactShardReward());
        if (random.nextInt(100) < 10 + diff * 2) items.add(createRuneTokenReward());
        if (adv.route.equals("mine") && random.nextInt(100) < 18) items.add(namedItem(Material.IRON_NUGGET, "§7Осколок перековки", "§7Материал для будущих механик Gear"));
    }

    private ItemStack createGearSetFragment() {
        org.bukkit.plugin.Plugin gear = Bukkit.getPluginManager().getPlugin("VKChatGear");
        String[] sets = {"bogatyr", "sokol", "volhv", "koshchey", "tankist", "udarnik"};
        String[] names = {"Богатырь", "Ясный Сокол", "Волхв", "Бессмертный", "Танкист", "Ударник Труда"};
        int i = random.nextInt(sets.length);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Фрагмент сета: " + names[i]);
        meta.setLore(java.util.Arrays.asList("§7Найден в оффлайн-походе.", "§7Используется при ковке брони."));
        if (gear != null) meta.getPersistentDataContainer().set(new NamespacedKey(gear, "set_fragment"), PersistentDataType.STRING, sets[i]);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArtifactShardReward() {
        ItemStack item = namedItem(Material.PRISMARINE_CRYSTALS, "§d§lОсколок Древнего Артефакта", "§7Добыт в оффлайн-походе. §eПКМ в руке§7 для использования.");
        ItemMeta meta = item.getItemMeta();
        org.bukkit.plugin.Plugin mobs = Bukkit.getPluginManager().getPlugin("VKChatMobs");
        if (mobs != null) meta.getPersistentDataContainer().set(new NamespacedKey(mobs, "is_artifact_shard"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRuneTokenReward() {
        ItemStack item = namedItem(Material.GOLD_NUGGET, "§6§lДревний Жетон Рун", "§7Добыт в оффлайн-походе. §eПКМ в руке§7 для использования.");
        ItemMeta meta = item.getItemMeta();
        org.bukkit.plugin.Plugin mobs = Bukkit.getPluginManager().getPlugin("VKChatMobs");
        if (mobs != null) meta.getPersistentDataContainer().set(new NamespacedKey(mobs, "is_rune_token"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String randomExtraEventTitle() {
        java.util.List<String> list = plugin.getConfig().getStringList("extra-events.titles");
        if (list == null || list.isEmpty()) {
            return randomOf("Странное испытание судьбы", "Неизвестная комната", "Шанс на невозможное");
        }
        return list.get(random.nextInt(list.size()));
    }

    private void applyExtraPositive(ActiveAdventure adv, int choice, StringBuilder msg) {
        int roll = random.nextInt(8);
        if (roll == 0) { adv.gold += 20 + random.nextInt(45); msg.append("🪙 Судьба дала золото.\\n"); }
        else if (roll == 1) { adv.relics++; msg.append("🏺 Найдена малая реликвия.\\n"); }
        else if (roll == 2) { adv.inspiration++; msg.append("✨ Сцена вдохновила героя: вдохновение +1.\\n"); }
        else if (roll == 3) { adv.supplies += 1 + random.nextInt(2); msg.append("🥫 Найдены припасы.\\n"); }
        else if (roll == 4) { adv.hp = Math.min(adv.maxHp, adv.hp + 10 + random.nextInt(12)); msg.append("💖 Герой восстановил здоровье.\\n"); }
        else if (roll == 5) { adv.morale = Math.min(100, adv.morale + 15); msg.append("🧠 Мораль поднялась.\\n"); }
        else if (roll == 6) { adv.blessing = randomBlessing(); msg.append("🌟 Судьба дала ").append(blessingText(adv.blessing)).append("\\n"); }
        else { if (!"none".equals(adv.condition)) { msg.append("🩹 Состояние снято: ").append(conditionText(adv.condition)).append("\\n"); adv.condition = "none"; } else { adv.gold += 15; msg.append("🪙 Небольшая добыча.\\n"); } }
    }

    private void applyExtraNegative(ActiveAdventure adv, int choice, StringBuilder msg) {
        int roll = random.nextInt(8);
        if (roll == 0) { adv.gold = Math.max(0, adv.gold - 20); msg.append("🪙 Потеряны монеты.\\n"); }
        else if (roll == 1) { applyCondition(adv, randomConditionFor("extra"), msg); }
        else if (roll == 2) { adv.supplies = Math.max(0, adv.supplies - 1); msg.append("🥫 Припасы испорчены.\\n"); }
        else if (roll == 3) { adv.morale = Math.max(0, adv.morale - 12); msg.append("🧠 Мораль просела.\\n"); }
        else if (roll == 4) { adv.inspiration = Math.max(0, adv.inspiration - 1); msg.append("✨ Вдохновение потеряно.\\n"); }
        else if (roll == 5) { adv.blessing = "none"; msg.append("🌑 Благословение рассеялось.\\n"); }
        else if (roll == 6) { adv.hp -= 5 + random.nextInt(8); msg.append("💥 Дополнительный урон от странного исхода.\\n"); }
        else { msg.append("🎲 Судьба оставила дурной знак.\\n"); adv.morale = Math.max(0, adv.morale - 5); }
    }

    private boolean isSlavicMyth(String type) {
        return OfflineEventMath.isSlavicMyth(type);
    }

    private void handleSlavicPositive(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        switch (type) {
            case "baba_yaga":
                adv.relics++;
                msg.append("🧙 Баба-Яга признала хитрость: реликвия +1\\n");
                break;
            case "leshy":
                adv.supplies += 2;
                adv.morale = Math.min(100, adv.morale + 10);
                msg.append("🌲 Леший вывел к ягодной поляне: припасы +2, мораль +10%\\n");
                break;
            case "rusalka":
                adv.blessing = "life";
                msg.append("🌊 Русалка даровала благословение Жизни.\\n");
                break;
            case "domovoi":
                adv.hp = Math.min(adv.maxHp, adv.hp + 12);
                adv.condition = "none";
                msg.append("🏚 Домовой накормил и снял дурное состояние.\\n");
                break;
            case "perun":
                adv.blessing = "power";
                adv.inspiration++;
                msg.append("⚡ Перун отметил смелость: Сила и вдохновение +1.\\n");
                break;
            case "morana":
                adv.gold += 40;
                msg.append("❄ Морана отпустила за холодную плату: золото +40.\\n");
                break;
            case "vodyanoy":
                adv.supplies++;
                adv.gold += 20;
                msg.append("💧 Водяной обменял тайну на добычу: припасы +1, золото +20.\\n");
                break;
            case "koshchey":
                adv.relics += 2;
                msg.append("☠ Ты коснулся иглы Кощея и унёс две реликвии.\\n");
                break;
            case "zmey":
                adv.gold += 60;
                adv.morale = Math.min(100, adv.morale + 20);
                msg.append("🐉 След Змея привёл к кладу: золото +60, мораль +20%.\\n");
                break;
            case "bogatyr":
                adv.blessing = "power";
                adv.hp = Math.min(adv.maxHp, adv.hp + 15);
                msg.append("🛡 Богатырское испытание укрепило тело: HP +15 и Сила.\\n");
                break;
        }
    }

    private void handleSlavicNegative(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        switch (type) {
            case "baba_yaga": applyCondition(adv, "cursed", msg); msg.append("🧙 Ведьмин договор обернулся проклятием.\\n"); break;
            case "leshy": adv.supplies = Math.max(0, adv.supplies - 2); msg.append("🌲 Леший запутал тропу: припасы -2.\\n"); break;
            case "rusalka": applyCondition(adv, "exhausted", msg); msg.append("🌊 Песня русалки вытянула силы.\\n"); break;
            case "domovoi": adv.morale = Math.max(0, adv.morale - 15); msg.append("🏚 Домовой рассердился: мораль -15%.\\n"); break;
            case "perun": adv.hp -= 8; msg.append("⚡ Молния Перуна обожгла героя: -8 HP.\\n"); break;
            case "morana": applyCondition(adv, "exhausted", msg); adv.morale = Math.max(0, adv.morale - 12); msg.append("❄ Холод Мораны забрал силы и мораль.\\n"); break;
            case "vodyanoy": adv.gold = Math.max(0, adv.gold - 25); msg.append("💧 Водяной утащил монеты: золото -25.\\n"); break;
            case "koshchey": applyCondition(adv, "cursed", msg); adv.inspiration = 0; msg.append("☠ Кощей украл вдохновение.\\n"); break;
            case "zmey": adv.hp -= 10; applyCondition(adv, "burned", msg); msg.append("🐉 Змеиное пламя опалило путь.\\n"); break;
            case "bogatyr": adv.morale = Math.max(0, adv.morale - 10); msg.append("🛡 Испытание Богатыря оказалось слишком тяжёлым: мораль -10%.\\n"); break;
        }
    }

    private String deathScene(String type, String route) {
        if (type.equals("trap")) return randomOf("🪤 Смерть: скрытая плита открыла яму с шипами.", "🏹 Смерть: залп древних стрел пробил броню.", "☠ Смерть: яд ловушки остановил сердце.");
        if (type.equals("ambush")) return randomOf("🩸 Смерть: засада сомкнула кольцо, отступать было некуда.", "🐺 Смерть: стая настигла персонажа в темноте.");
        if (type.equals("curse")) return randomOf("🧿 Смерть: проклятие забрало душу у старого алтаря.", "🌫 Смерть: туман стёр дорогу и имя путника.");
        if (type.equals("survival")) return randomOf("🥶 Смерть: холод и голод оказались сильнее воли.", "🌊 Смерть: переправа сорвалась в бурлящую воду.");
        if (type.equals("boss")) return randomOf("👑 Смерть: страж маршрута добил героя на последнем рубеже.", "💀 Смерть: босс сломил защиту и волю персонажа.");
        if (type.equals("gathering")) return randomOf("⛏ Смерть: жадность к ресурсам завела слишком глубоко.", "🕳 Смерть: шахта обвалилась во время добычи.");
        if (type.equals("camp")) return randomOf("🔥 Смерть: дым костра привёл хищников прямо в лагерь.", "🌑 Смерть: ночной привал оказался последним.");
        if (type.equals("baba_yaga")) return randomOf("🧙 Смерть: Баба-Яга сварила героя в чёрном котле.", "🦴 Смерть: костяная ограда пополнилась новым черепом.");
        if (type.equals("leshy")) return randomOf("🌲 Смерть: Леший завёл героя туда, где дороги не возвращаются.", "🍃 Смерть: лес сомкнулся и забрал путника.");
        if (type.equals("rusalka")) return randomOf("🌊 Смерть: песня русалки увела под воду.", "💧 Смерть: омут закрылся над головой.");
        if (type.equals("zmey")) return randomOf("🐉 Смерть: Змей Горыныч испепелил путь и героя.", "🔥 Смерть: три пасти спорили, кому достанется добыча.");
        if (type.equals("koshchey")) return randomOf("☠ Смерть: Кощей спрятал душу героя рядом со своей иглой.", "🦴 Смерть: бессмертная тень забрала дыхание.");
        if (type.equals("extra")) return randomOf("🎲 Смерть: странное испытание оказалось последней страницей хроники.", "🕯 Смерть: судьба бросила кость — и кость выпала против героя.", "🗺 Смерть: неизвестная тропа больше не выпустила путника.");
        return randomOf("⚔ Смерть: противник оказался слишком силён.", "☠ Смерть: поход закончился последним вздохом в темноте.");
    }

    private String actionName(int choice, String type) {
        if (isCombatEvent(type)) {
            switch (choice) {
                case 1: return "⚔ Ударить";
                case 2: return "🛡 Защита";
                case 3: return "✨ Боевой приём";
                default: return "🏃 Отступить";
            }
        }
        switch (choice) {
            case 1: return "⚔ Рискнуть";
            case 2: return "🛡 Осторожно";
            case 3: return "🔍 Исследовать";
            default: return "🏃 Отступить";
        }
    }

    private void consumeStageResources(ActiveAdventure adv, String type, int choice, boolean bad, StringBuilder msg) {
        tickCondition(adv, msg);
        if (!type.equals("camp") && !type.equals("merchant")) {
            if (random.nextInt(100) < 55) adv.supplies--;
        }
        if (bad) adv.morale -= 8 + random.nextInt(8);
        else adv.morale += 2 + random.nextInt(5);
        adv.supplies = Math.max(0, adv.supplies);
        adv.morale = Math.max(0, Math.min(100, adv.morale));
        adv.sanity = Math.max(0, Math.min(100, adv.sanity + (adv.morale - adv.sanity) / 8));
        data.set("stats." + adv.vkId + ".sanity", adv.sanity);
        if (adv.supplies <= 0 && !type.equals("gathering")) {
            int hunger = 3 + random.nextInt(6);
            adv.hp -= hunger;
            msg.append("🍞 Нет припасов: голод наносит ").append(hunger).append(" HP.\n");
        }
        if (adv.morale <= 15 && random.nextInt(100) < 20) {
            msg.append("🧠 Мораль на грани: персонаж дрожит и ошибается чаще.\n");
        }
    }

    private int checkDc(String type, int difficulty, int deaths, int rep, ActiveAdventure adv) {
        int dc = 9 + difficulty * 2 + deaths;
        if (type.equals("trap") || type.equals("ambush")) dc += 2;
        if (type.equals("curse") || type.equals("boss")) dc += 3;
        if (type.equals("treasure")) dc += 1;
        if (adv.supplies <= 0) dc += 2;
        if (adv.morale < 40) dc += 2;
        if ("exhausted".equals(adv.condition)) dc += 2;
        if (adv.sanity < 40) dc += 2;
        if (hasPhobia(adv.vkId, type)) dc += 2;
        if (hasTrauma(adv.vkId, "shaky_hands") && (type.equals("trap") || type.equals("treasure") || type.equals("collection"))) dc += 1;
        if (hasTrauma(adv.vkId, "bad_omen") && (type.equals("curse") || type.equals("nightmare") || isSlavicMyth(type))) dc += 1;
        if ("cursed".equals(adv.condition) && (type.equals("curse") || type.equals("shrine") || type.equals("riddle"))) dc += 2;
        if (rep > 3000) dc += 1;
        return Math.min(25, Math.max(8, dc));
    }

    private int checkModifier(String cls, String type, int choice, ActiveAdventure adv) {
        int mod = Math.max(0, getAdvLevel(adv.vkId) / 2);
        if (choice == 2) mod += 1;
        if (choice == 3 && (type.equals("treasure") || type.equals("gathering") || type.equals("riddle"))) mod += 2;
        if (choice == 4 && (type.equals("ambush") || type.equals("trap"))) mod += 2;
        if ("warrior".equals(cls) && (type.equals("combat") || type.equals("boss") || type.equals("ambush"))) mod += 3;
        if ("scout".equals(cls) && (type.equals("trap") || type.equals("survival") || type.equals("gathering"))) mod += 3;
        if ("mage".equals(cls) && (type.equals("curse") || type.equals("riddle") || type.equals("shrine"))) mod += 3;
        if ("cleric".equals(cls) && (type.equals("curse") || type.equals("survival") || type.equals("camp"))) mod += 2;
        if (adv.morale >= 80) mod += 1;
        if (adv.morale < 25) mod -= 2;
        if (type.equals("memory") && adv.sanity >= 70) mod += 2;
        if (type.equals("companion") && companionRelation(adv.vkId, getCompanion(adv.vkId)) >= 40) mod += 2;
        if ("poisoned".equals(adv.condition)) mod -= 1;
        if ("cursed".equals(adv.condition)) mod -= 1;
        return mod;
    }

    private void applyCondition(ActiveAdventure adv, String condition, StringBuilder msg) {
        OfflineStatusEffects.applyCondition(random, adv, condition, msg);
    }

    private String randomConditionFor(String type) {
        return OfflineStatusEffects.randomConditionFor(random, type);
    }

    private String conditionText(String condition) {
        return OfflineStatusEffects.conditionText(condition);
    }

    private void tickCondition(ActiveAdventure adv, StringBuilder msg) {
        OfflineStatusEffects.tickCondition(random, adv, msg);
    }

    private String randomBlessing() {
        return OfflineStatusEffects.randomBlessing(random);
    }

    private String blessingText(String blessing) {
        return OfflineStatusEffects.blessingText(blessing);
    }

    private int blessingRiskModifier(String blessing, String type, int choice) {
        return OfflineStatusEffects.blessingRiskModifier(blessing, type, choice);
    }

    private int blessingCheckModifier(String blessing, String type, int choice) {
        return OfflineStatusEffects.blessingCheckModifier(blessing, type, choice);
    }

    private String getCompanion(int vkId) {
        return data.getString("stats." + vkId + ".companion", "none");
    }

    private String companionName(String c) {
        return OfflineCompanionManager.companionName(c);
    }

    private int companionRiskModifier(String comp, String type, int choice) {
        return OfflineCompanionManager.companionRiskModifier(comp, type, choice);
    }

    private int companionCheckModifier(String comp, String type, int choice) {
        return OfflineCompanionManager.companionCheckModifier(comp, type, choice);
    }

    private boolean advSafeSupplyTypes(String type) {
        return OfflineCompanionManager.advSafeSupplyTypes(type);
    }

    private int applyCompanionDamageReduction(int damage, String comp, String type) {
        return OfflineCompanionManager.applyCompanionDamageReduction(damage, comp, type);
    }

    private void applyCompanionPositive(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        OfflineCompanionManager.applyCompanionPositive(random, adv, getCompanion(adv.vkId), type, choice, msg);
    }

    private void applyCompanionNegative(ActiveAdventure adv, String type, int choice, StringBuilder msg) {
        OfflineCompanionManager.applyCompanionNegative(random, adv, getCompanion(adv.vkId), type, choice, msg);
    }

    private void chooseCompanion(int vkId, String[] args) {
        if (args.length < 2) {
            api().sendKeyboard(vkId, OfflineCompanionManager.chooseText(), keyboardCompanions());
            return;
        }
        String comp = OfflineCompanionManager.normalizeCompanion(args[1]);
        if (!OfflineCompanionManager.isValidCompanion(comp)) {
            api().sendMessage(vkId, "❌ Неизвестный спутник. Доступно: wolf/raven/alchemist/mule");
            return;
        }
        data.set("stats." + vkId + ".companion", comp);
        addJournal(vkId, "🐾 Выбран спутник: " + companionName(comp));
        saveAll();
        api().sendKeyboard(vkId, "✅ Спутник выбран: " + companionName(comp), keyboardMain());
    }

    private void takeRest(int vkId) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null) { api().sendKeyboard(vkId, "Активного похода нет.", keyboardMain()); return; }
        if (adv.waitingChoice) { api().sendKeyboard(vkId, "Сначала ответь на текущее событие.", keyboardChoices()); return; }
        if (adv.supplies <= 0) { api().sendKeyboard(vkId, "🥫 Нет припасов для отдыха.", keyboardStatusOnly()); return; }
        adv.supplies--;
        int heal = 12 + random.nextInt(10);
        adv.hp = Math.min(adv.maxHp, adv.hp + heal);
        adv.morale = Math.min(100, adv.morale + 15);
        if ("bleeding".equals(adv.condition) || "exhausted".equals(adv.condition)) adv.condition = "none";
        adv.nextEventTime += 30000L;
        saveAll();
        api().sendKeyboard(vkId, "🔥 Короткий отдых\\n\\n" +
                "🥫 Припасы -1\\n" +
                "💖 HP +" + heal + "\\n" +
                "🧠 Мораль +15%\\n" +
                "⏳ Следующее событие чуть позже.", keyboardStatusOnly());
    }

    private boolean tryDeathSave(ActiveAdventure adv, String preface) {
        int max = plugin.getConfig().getInt("mmorpg.death-saves.max", 1);
        if (adv.deathSavesUsed >= max) return false;
        adv.deathSavesUsed++;
        int dc = plugin.getConfig().getInt("mmorpg.death-saves.dc", 12);
        int roll = 1 + random.nextInt(20);
        int mod = Math.max(0, getAdvLevel(adv.vkId) / 4);
        if ("cleric".equals(getPlayerClass(adv.vkId))) mod += 2;
        if ("cursed".equals(adv.condition)) mod -= 2;
        boolean success = roll == 20 || (roll != 1 && roll + mod >= dc);
        if (!success) return false;
        adv.hp = 1;
        adv.morale = Math.max(5, adv.morale / 2);
        adv.condition = "exhausted";
        adv.waitingChoice = false;
        adv.nextEventTime = System.currentTimeMillis() + plugin.getConfig().getLong("stage-delay-seconds", 60) * 1000L;
        saveAll();
        api().sendKeyboard(adv.vkId, preface + "\\n🧬 Спасбросок смерти\\n\\n" +
                "🎲 d20: " + roll + (mod >= 0 ? " +" : " ") + mod + " vs DC " + dc + "\\n" +
                "✨ Персонаж выжил на 1 HP, но получил Истощение.", keyboardStatusOnly());
        return true;
    }

    private String getPlayerClass(int vkId) {
        return data.getString("stats." + vkId + ".class", "novice");
    }

    private String className(String cls) {
        return OfflineClassManager.className(cls);
    }

    private int classRiskModifier(String cls, String type, int choice) {
        return OfflineClassManager.classRiskModifier(cls, type, choice);
    }

    private int applyClassDamageReduction(int damage, String cls, String type) {
        return OfflineClassManager.applyClassDamageReduction(damage, cls, type);
    }

    private int xpForEvent(String type, int difficulty, boolean success) {
        return OfflineEventMath.xpForEvent(type, difficulty, success);
    }

    private int getAdvLevel(int vkId) { return progressManager.getAdvLevel(vkId); }
    private int getAdvXp(int vkId) { return progressManager.getAdvXp(vkId); }
    private int xpToNext(int level) { return progressManager.xpToNext(level); }

    private int addAdventureXp(int vkId, int amount) {
        return progressManager.addAdventureXp(vkId, amount);
    }

    private void chooseClass(int vkId, String[] args) {
        if (args.length < 2) {
            api().sendKeyboard(vkId, OfflineClassManager.chooseText(), keyboardClasses());
            return;
        }
        String cls = OfflineClassManager.normalizeClass(args[1]);
        if (!OfflineClassManager.isValidClass(cls)) {
            api().sendMessage(vkId, "❌ Неизвестный класс. Доступно: warrior/scout/mage/cleric");
            return;
        }
        data.set("stats." + vkId + ".class", cls);
        addJournal(vkId, "🧙 Выбран класс: " + className(cls));
        saveAll();
        api().sendKeyboard(vkId, "✅ Класс выбран: " + className(cls) + "\nОн будет влиять на риск, урон и события в походах.", keyboardMain());
    }

    private java.util.Map<String, ShopItem> offlineShopItems() {
        return OfflineShopCatalog.items();
    }


    private double getOfflineDonateShopMultiplier(int vkId) {
        Player p = api().getPlayerByVkId(vkId);
        if (p == null) return 1.0;
        if (p.hasPermission("vkchat.donate.offline.legend") || p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("offline2.donate.shop-multiplier.legend", 0.00);
        if (p.hasPermission("vkchat.donate.offline.star") || p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("offline2.donate.shop-multiplier.star", 0.00);
        if (p.hasPermission("vkchat.donate.offline.flame") || p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("offline2.donate.shop-multiplier.flame", 0.50);
        if (p.hasPermission("vkchat.donate.offline.spark") || p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("offline2.donate.shop-multiplier.spark", 0.80);
        return 1.0;
    }

    private double getOfflineDonateRewardMultiplier(int vkId) {
        Player p = api().getPlayerByVkId(vkId);
        if (p == null) return 1.0;
        if (p.hasPermission("vkchat.donate.offline.legend") || p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("offline2.donate.reward-multiplier.legend", 3.00);
        if (p.hasPermission("vkchat.donate.offline.star") || p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("offline2.donate.reward-multiplier.star", 2.20);
        if (p.hasPermission("vkchat.donate.offline.flame") || p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("offline2.donate.reward-multiplier.flame", 1.70);
        if (p.hasPermission("vkchat.donate.offline.spark") || p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("offline2.donate.reward-multiplier.spark", 1.30);
        return 1.0;
    }

    private int progressivePrice(int vkId, ShopItem item) {
        int lvl = getAdvLevel(vkId);
        double mult = 1.0 + Math.max(0, lvl - 1) * 0.04;
        if (hasOfflineSkill(vkId, "trader")) mult *= 0.90;
        mult *= getOfflineDonateShopMultiplier(vkId);
        return Math.max(1, (int)Math.round(item.price * mult));
    }

    private void showOfflineShop(int vkId, String section) {
        StringBuilder sb = new StringBuilder("🛒 Лавка походника\n\n");
        sb.append("Баланс: ").append(api().getReputation(vkId)).append(" реп. | LVL ").append(getAdvLevel(vkId)).append("\n\n");
        boolean consumables = section.equalsIgnoreCase("consumables") || section.equalsIgnoreCase("расходники");
        for (ShopItem it : offlineShopItems().values()) {
            if (consumables != it.type.equals("consumable")) continue;
            sb.append(it.name).append(" — ").append(progressivePrice(vkId, it)).append(" реп.\n");
            sb.append("   купить: !купить ").append(it.id).append("\n");
        }
        api().sendKeyboard(vkId, sb.toString(), consumables ? keyboardShopConsumables() : keyboardShopEquipment());
    }

    private void buyOfflineItem(int vkId, String id) {
        ShopItem it = offlineShopItems().get(id);
        if (it == null) { api().sendMessage(vkId, "❌ Товар не найден. Напиши !магазин"); return; }
        int price = progressivePrice(vkId, it);
        int rep = api().getReputation(vkId);
        if (rep < price) { api().sendMessage(vkId, "❌ Недостаточно репутации. Нужно: " + price + ", у тебя: " + rep); return; }
        api().takeReputation(vkId, price);
        if (it.type.equals("equipment")) {
            data.set("stats." + vkId + ".equipment." + it.slot, it.id);
            addJournal(vkId, "🛒 Куплено снаряжение: " + it.name);
            saveAll();
            api().sendKeyboard(vkId, "✅ Куплено и экипировано: " + it.name + "\nСлот: " + it.slot, keyboardMain());
        } else {
            int count = data.getInt("stats." + vkId + ".bag." + it.id, 0) + 1;
            data.set("stats." + vkId + ".bag." + it.id, count);
            addJournal(vkId, "🛒 Куплен расходник: " + it.name);
            saveAll();
            api().sendKeyboard(vkId, "✅ Куплено: " + it.name + " x1\nВ сумке: " + count, keyboardShopConsumables());
        }
    }

    private int offlineEquipBonus(int vkId, String stat) {
        int total = 0;
        for (String slot : java.util.Arrays.asList("weapon", "armor", "talisman", "tool", "backpack")) {
            ShopItem it = offlineShopItems().get(data.getString("stats." + vkId + ".equipment." + slot, ""));
            if (it == null) continue;
            if (stat.equals("hp")) total += it.hp;
            else if (stat.equals("check")) total += it.check;
            else if (stat.equals("armor")) total += it.armor;
            else if (stat.equals("risk")) total += it.risk;
            else if (stat.equals("supplies")) total += it.supplies;
            else if (stat.equals("rep")) total += it.rep;
        }
        return total;
    }

    private boolean hasOfflineSkill(int vkId, String id) {
        return data.getBoolean("stats." + vkId + ".skills." + id, false);
    }

    private int learnedOfflineSkills(int vkId) {
        ConfigurationSection sec = data.getConfigurationSection("stats." + vkId + ".skills");
        if (sec == null) return 0;
        int count = 0;
        for (String k : sec.getKeys(false)) if (sec.getBoolean(k)) count++;
        return count;
    }

    private int availableOfflineSkillPoints(int vkId) {
        return Math.max(0, getAdvLevel(vkId) / 2 - learnedOfflineSkills(vkId));
    }

    private void showOfflineSkills(int vkId) {
        StringBuilder sb = new StringBuilder("🧠 Навыки походника\n\n");
        sb.append("Очки: ").append(availableOfflineSkillPoints(vkId)).append(" | LVL ").append(getAdvLevel(vkId)).append("\n\n");
        String[][] defs = offlineSkillDefs();
        for (String[] d : defs) sb.append(hasOfflineSkill(vkId,d[0]) ? "✅ " : "⬜ ").append(d[1]).append(" — ").append(d[2]).append("\n   !навык ").append(d[0]).append("\n");
        api().sendKeyboard(vkId, sb.toString(), keyboardOfflineSkills());
    }

    private String[][] offlineSkillDefs() {
        return OfflineSkillCatalog.defs();
    }

    private void learnOfflineSkill(int vkId, String id) {
        if (hasOfflineSkill(vkId, id)) { api().sendMessage(vkId, "Навык уже изучен."); return; }
        if (availableOfflineSkillPoints(vkId) <= 0) { api().sendKeyboard(vkId, "❌ Нет очков навыков. Получай уровни в походах.", keyboardOfflineSkills()); return; }
        boolean exists = false; String title = id;
        for (String[] d : offlineSkillDefs()) if (d[0].equals(id)) { exists = true; title = d[1]; }
        if (!exists) { api().sendMessage(vkId, "❌ Навык не найден."); return; }
        data.set("stats." + vkId + ".skills." + id, true);
        addJournal(vkId, "🧠 Изучен навык: " + title);
        saveAll();
        api().sendKeyboard(vkId, "✅ Изучен навык: " + title, keyboardOfflineSkills());
    }

    private void showOfflineEquipment(int vkId) {
        StringBuilder sb = new StringBuilder("⚔ Экипировка походника\n\n");
        for (String slot : java.util.Arrays.asList("weapon", "armor", "talisman", "tool", "backpack")) {
            ShopItem it = offlineShopItems().get(data.getString("stats." + vkId + ".equipment." + slot, ""));
            sb.append(slot).append(": ").append(it == null ? "пусто" : it.name).append("\n");
        }
        sb.append("\nБонусы: HP+").append(offlineEquipBonus(vkId,"hp")).append(", check+").append(offlineEquipBonus(vkId,"check")).append(", armor+").append(offlineEquipBonus(vkId,"armor")).append(", supplies+").append(offlineEquipBonus(vkId,"supplies"));
        api().sendKeyboard(vkId, sb.toString(), keyboardShopEquipment());
    }

    private void showConsumables(int vkId) {
        StringBuilder sb = new StringBuilder("🌿 Расходники\n\n");
        for (ShopItem it : offlineShopItems().values()) if (it.type.equals("consumable")) sb.append(it.name).append(": ").append(data.getInt("stats." + vkId + ".bag." + it.id,0)).append("\n");
        api().sendKeyboard(vkId, sb.toString(), keyboardUseConsumables());
    }

    private void useConsumable(int vkId, String id) {
        int count = data.getInt("stats." + vkId + ".bag." + id, 0);
        if (count <= 0) { api().sendKeyboard(vkId, "❌ У тебя нет этого расходника.", keyboardUseConsumables()); return; }
        ActiveAdventure adv = active.get(vkId);
        if (adv == null) { api().sendKeyboard(vkId, "Расходники используются только в активном походе.", keyboardUseConsumables()); return; }
        if (adv.waitingChoice && id.equals("scroll_escape")) { api().sendKeyboard(vkId, "Сначала ответь на текущее событие или выбери отступление. Свиток побега нельзя использовать поверх активного выбора.", keyboardForEvent(adv)); return; }
        data.set("stats." + vkId + ".bag." + id, count - 1);
        String msg;
        boolean escaped = false;
        if (id.equals("potion_heal")) { int heal = hasOfflineSkill(vkId,"herbalist") ? 45 : 30; adv.hp = Math.min(adv.maxHp, adv.hp + heal); msg = "❤️ Лечение: +" + heal + " HP"; }
        else if (id.equals("potion_sanity")) { adv.morale = Math.min(100, adv.morale + 30); adv.sanity = Math.min(100, adv.sanity + 20); setSanity(vkId, adv.sanity); msg = "🧠 Рассудок/мораль восстановлены"; }
        else if (id.equals("potion_antidote")) { adv.condition = "none"; msg = "☠ Состояния сняты"; }
        else if (id.equals("scroll_escape")) { active.remove(vkId); escaped = true; msg = "📜 Побег из похода. Награды не начислены, но ты выжил."; }
        else if (id.equals("scroll_reroll")) { adv.inspiration++; msg = "🎲 Вдохновение +1"; }
        else if (id.equals("scroll_cleanse")) { adv.condition = "none"; msg = "🕯 Очищение применено"; }
        else { adv.supplies += 2; adv.morale = Math.min(100, adv.morale + 10); msg = "⛺ Лагерный набор: припасы +2, мораль +10%"; }
        saveAll();
        api().sendKeyboard(vkId, "✅ " + msg, escaped ? keyboardMain() : keyboardStatusOnly());
    }

    private void previewSellStash(int vkId) {
        api().sendKeyboard(vkId, rewardManager.buildSellStashPreview(vkId), keyboardSellStash());
    }

    private void sellStash(int vkId, boolean confirm) {
        api().sendKeyboard(vkId, rewardManager.sellStash(vkId), keyboardMain());
    }

    private int[] calculateStashSale(java.util.List<ItemStack> items) { return rewardManager.calculateStashSale(items); }
    private boolean isSellableStashItem(ItemStack item) { return rewardManager.isSellableStashItem(item); }
    private int estimateStashItemRep(ItemStack item) { return rewardManager.estimateStashItemRep(item); }

    private int getSanity(int vkId) { return hospitalManager.getSanity(vkId); }
    private void setSanity(int vkId, int value) { hospitalManager.setSanity(vkId, value); }
    private void changeSanity(int vkId, int delta, StringBuilder msg) { hospitalManager.changeSanity(vkId, delta, msg); }
    private java.util.List<String> getTraumas(int vkId) { return hospitalManager.getTraumas(vkId); }
    private boolean hasTrauma(int vkId, String id) { return hospitalManager.hasTrauma(vkId, id); }
    private void addTrauma(int vkId, String id) { hospitalManager.addTrauma(vkId, id); }
    private void addRandomTrauma(int vkId) { hospitalManager.addRandomTrauma(vkId); }
    private String traumaName(String id) { return hospitalManager.traumaName(id); }
    private String traumaLine(int vkId) { return hospitalManager.traumaLine(vkId); }
    private String getPhobia(int vkId) { return hospitalManager.getPhobia(vkId); }
    private void setPhobia(int vkId, String id) { hospitalManager.setPhobia(vkId, id); }
    private boolean hasPhobia(int vkId, String type) { return hospitalManager.hasPhobia(vkId, type, isCombatEvent(type), isSlavicMyth(type)); }
    private String phobiaForRoute(String route) { return hospitalManager.phobiaForRoute(route); }
    private void maybeAddPhobia(int vkId, String route, String type, StringBuilder msg) { hospitalManager.maybeAddPhobia(vkId, route, type, msg); }
    private String phobiaName(String id) { return hospitalManager.phobiaName(id); }
    private void showPsyche(int vkId) { api().sendKeyboard(vkId, hospitalManager.buildPsycheText(vkId), keyboardHospital()); }
    private void showHospital(int vkId) { api().sendKeyboard(vkId, hospitalManager.buildHospitalText(vkId), keyboardHospital()); }
    private void useHospital(int vkId, String mode) { api().sendKeyboard(vkId, hospitalManager.useHospital(vkId, mode), keyboardHospital()); }

    private String chapterForRoute(String route) { return campaignManager.chapterForRoute(route); }
    private String routeForChapter(String ch) { return campaignManager.routeForChapter(ch); }
    private String chapterName(String ch) { return campaignManager.chapterName(ch); }
    private boolean isChapterUnlocked(int vkId, String ch) { return campaignManager.isChapterUnlocked(vkId, ch); }
    private void completeCampaignForRoute(int vkId, String route) { campaignManager.completeCampaignForRoute(vkId, route); }
    private String campaignLine(int vkId) { return campaignManager.campaignLine(vkId); }
    private void showCampaign(int vkId) { api().sendKeyboard(vkId, campaignManager.buildCampaignText(vkId), keyboardCampaign()); }
    private void startCampaignChapter(int vkId, String ch) {
        if (!isChapterUnlocked(vkId, ch)) { api().sendKeyboard(vkId, "🔒 Эта глава ещё закрыта. Заверши предыдущую.", keyboardCampaign()); return; }
        startAdventure(vkId, new String[]{"!пойти", routeForChapter(ch)});
    }
    private String[] collectionIds() { return campaignManager.collectionIds(); }
    private String collectionName(String id) { return campaignManager.collectionName(id); }
    private String collectionForRoute(String route) { return campaignManager.collectionForRoute(route); }
    private void discoverCollection(int vkId, String route, StringBuilder msg) { campaignManager.discoverCollection(vkId, route, msg); }
    private void showCollections(int vkId) { api().sendKeyboard(vkId, campaignManager.buildCollectionsText(vkId), keyboardMain()); }

    private int companionRelation(int vkId, String comp) { if (comp == null || comp.equals("none")) return 0; return data.getInt("stats." + vkId + ".relations." + comp, 0); }
    private void addCompanionRelation(int vkId, String comp, int delta, StringBuilder msg) {
        if (comp == null || comp.equals("none")) return;
        int now = Math.max(-50, Math.min(100, companionRelation(vkId, comp) + delta));
        data.set("stats." + vkId + ".relations." + comp, now);
        if (msg != null && Math.abs(delta) >= 5) msg.append("🤝 Отношения со спутником: ").append(delta > 0 ? "+" : "").append(delta).append(" (").append(now).append(")\n");
    }
    private void showRelationships(int vkId) {
        StringBuilder sb = new StringBuilder("🤝 Отношения со спутниками\n\n");
        for (String c: java.util.Arrays.asList("wolf","raven","alchemist","mule")) sb.append(companionName(c)).append(": ").append(companionRelation(vkId,c)).append("/100\n");
        sb.append("\nВысокая дружба помогает в событиях спутника и даёт моральные бонусы.");
        api().sendKeyboard(vkId, sb.toString(), keyboardMain());
    }

    private void showAdventureInventory(int vkId) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null) {
            api().sendKeyboard(vkId, "🎲 Сумка походника\n\nАктивного похода нет.\nВ походе здесь будут показаны золото, реликвии, припасы, мораль, вдохновение и состояния.", keyboardMain());
            return;
        }
        api().sendKeyboard(vkId, "🎲 Сумка походника\n\n" +
                "🪙 Золото: " + adv.gold + "\n" +
                "🏺 Реликвии: " + adv.relics + "\n" +
                "🥫 Припасы: " + adv.supplies + "\n" +
                "🧠 Мораль: " + adv.morale + "%\n" +
                "✨ Вдохновение: " + adv.inspiration + "\n" +
                "🌟 Благословение: " + blessingText(adv.blessing) + "\n" +
                "Состояние: " + conditionText(adv.condition), keyboardStatusOnly());
    }

    private void showAdventureProfile(int vkId) {
        String cls = getPlayerClass(vkId);
        int level = getAdvLevel(vkId);
        int xp = getAdvXp(vkId);
        int progress = getProgress(vkId);
        int deaths = data.getInt("stats." + vkId + ".deaths", 0);
        api().sendKeyboard(vkId, "╔══════════════════════╗\n        👤 ПРОФИЛЬ ПОХОДНИКА\n╚══════════════════════╝\n\n" +
                "Класс: " + className(cls) + "\n" +
                "Спутник: " + companionName(getCompanion(vkId)) + "\n" +
                "Уровень: " + level + "\n" +
                "XP: " + xp + "/" + xpToNext(level) + "\n" +
                "Вдохновение в новом походе: " + Math.max(0, level / 3) + "\n" +
                "Завершённых походов: " + progress + "\n" +
                "Смертей: " + deaths + "\n" +
                "Рассудок: " + getSanity(vkId) + "%\n" +
                "Фобия: " + phobiaName(getPhobia(vkId)) + "\n" +
                "Травмы: " + traumaLine(vkId) + "\n" +
                "Кампания: " + campaignLine(vkId), keyboardClasses());
    }

    private void maybeDropKey(ActiveAdventure adv, StringBuilder msg) {
        List<String> keys = plugin.getConfig().getStringList("adventures." + adv.route + ".reward.keys");
        for (String key : keys) {
            if (!isRouteUnlocked(adv.vkId, key) && random.nextInt(100) < plugin.getConfig().getInt("key-drop-chance", 18)) {
                plugin.getStashManager().addItem(adv.uuid, plugin.getStashManager().namedKey(keyName(key)));
                msg.append("🗝 Найден ключ: ").append(cleanKeyName(key)).append("\n");
                msg.append("📦 Ключ отправлен в /stash.\n");
                break;
            }
        }
    }

    private void finishAdventure(ActiveAdventure adv, String preface) {
        active.remove(adv.vkId);
        int routeDifficulty = plugin.getConfig().getInt("adventures." + adv.route + ".difficulty", 1);
        int repMin = plugin.getConfig().getInt("adventures." + adv.route + ".reward.rep-min", 0);
        int repMax = plugin.getConfig().getInt("adventures." + adv.route + ".reward.rep-max", repMin);
        int rep = repMin + random.nextInt(Math.max(1, repMax - repMin + 1));
        rep += getProgress(adv.vkId) * 10 + routeDifficulty * 15;
        rep += adv.gold;
        rep += adv.relics * 75;
        if (hasOfflineSkill(adv.vkId, "trader")) rep = (int)Math.round(rep * 1.08);
        rep = (int)Math.round(rep * getOfflineDonateRewardMultiplier(adv.vkId));
        rep = (int) Math.round(rep * worldEventRewardMultiplier());
        api().addReputation(adv.vkId, rep);
        addProgress(adv.vkId, adv.route);
        completeCampaignForRoute(adv.vkId, adv.route);
        changeSanity(adv.vkId, 4 + routeDifficulty, null);
        if (random.nextInt(100) < 20 + routeDifficulty * 4) discoverCollection(adv.vkId, adv.route, null);

        List<ItemStack> items = rollItems("adventures." + adv.route + ".reward.items");
        addIntegratedRewards(adv, items);
        if (!items.isEmpty()) plugin.getStashManager().addItems(adv.uuid, items);
        int leveled = addAdventureXp(adv.vkId, adv.xpGained + routeDifficulty * 20);
        addJournal(adv.vkId, "🏆 Завершён поход: " + plugin.getConfig().getString("adventures." + adv.route + ".name", adv.route) + " (+" + rep + " реп.)");
        unlockAchievement(adv.vkId, "first_clear", "Первый успешный поход");
        unlockAchievement(adv.vkId, adv.route + "_complete", "Маршрут пройден: " + plugin.getConfig().getString("adventures." + adv.route + ".name", adv.route));
        if (getAdvLevel(adv.vkId) >= 5) unlockAchievement(adv.vkId, "level_5", "Походник V уровня");
        if (getAdvLevel(adv.vkId) >= 10) unlockAchievement(adv.vkId, "level_10", "Походник X уровня");
        if (getAdvLevel(adv.vkId) >= 20) unlockAchievement(adv.vkId, "level_20", "Походник XX уровня");
        if (getAdvLevel(adv.vkId) >= 30) unlockAchievement(adv.vkId, "level_30", "Походник XXX уровня");
        if (getAdvLevel(adv.vkId) >= 50) unlockAchievement(adv.vkId, "level_50", "Походник L уровня");
        if (adv.relics > 0) unlockAchievement(adv.vkId, "relic_hunter", "Охотник за реликвиями");
        if (adv.hp >= adv.maxHp) unlockAchievement(adv.vkId, "no_damage_run", "Безупречный поход");
        if (adv.gold >= 50) unlockAchievement(adv.vkId, "treasure_hunter", "Охотник за сокровищами");
        if (adv.relics >= 3) unlockAchievement(adv.vkId, "legendary_loot", "Легендарная добыча");

        // Проверка "все маршруты пройдены"
        String[] allRoutes = {"forest", "mine", "ruins", "swamp", "castle", "nether", "mountain", "underwater", "desert", "frozen", "volcanic", "shadow"};
        boolean allComplete = true;
        for (String r : allRoutes) {
            if (!data.getBoolean("achievements." + adv.vkId + "." + r + "_complete", false)) {
                allComplete = false;
                break;
            }
        }
        if (allComplete) unlockAchievement(adv.vkId, "all_routes_complete", "Все маршруты пройдены!");

        // === ДОПОЛНИТЕЛЬНЫЕ ДОСТИЖЕНИЯ (v2.1.0) ===

        // Маршруты: повторные прохождения (x3)
        int routeCount = data.getInt("stats." + adv.vkId + ".route_" + adv.route, 0) + 1;
        data.set("stats." + adv.vkId + ".route_" + adv.route, routeCount);
        if (routeCount >= 3) unlockAchievement(adv.vkId, adv.route + "_x3", adv.route + " x3");

        // Маршруты: без смертей
        if (data.getInt("stats." + adv.vkId + ".route_" + adv.route + "_deaths", 0) == 0) {
            unlockAchievement(adv.vkId, adv.route + "_no_death", adv.route + " без смертей");
        }

        // Классы: уникальные прохождения
        String playerClass = data.getString("class." + adv.vkId, "");
        if (!playerClass.isEmpty()) {
            unlockAchievement(adv.vkId, playerClass + "_only_run", "Только " + playerClass);
        }

        // Компаньоны: уникальные прохождения
        String companion = data.getString("companion." + adv.vkId, "");
        if (!companion.isEmpty()) {
            unlockAchievement(adv.vkId, companion + "_companion_run", "С " + companion);
        }

        // Репутация за поход
        if (rep >= 500) unlockAchievement(adv.vkId, "rep_500_single", "500 реп. за поход");
        if (rep >= 1000) unlockAchievement(adv.vkId, "rep_1000_single", "1000 реп. за поход");
        if (rep >= 2000) unlockAchievement(adv.vkId, "rep_2000_single", "2000 реп. за поход");
        if (rep >= 5000) unlockAchievement(adv.vkId, "rep_5000_single", "5000 реп. за поход");
        if (rep >= 10000) unlockAchievement(adv.vkId, "rep_10000_single", "10000 реп. за поход");

        // Выживание: этапы подряд
        if (adv.maxStages >= 3) unlockAchievement(adv.vkId, "survive_3_stages", "3 этапа подряд");
        if (adv.maxStages >= 5) unlockAchievement(adv.vkId, "survive_5_stages", "5 этапов подряд");
        if (adv.maxStages >= 7) unlockAchievement(adv.vkId, "survive_7_stages", "7 этапов подряд");
        if (adv.maxStages >= 10) unlockAchievement(adv.vkId, "survive_10_stages", "10 этапов подряд");

        // Прогрессия: уровень спутника
        int compLevel = data.getInt("companion-level." + adv.vkId, 0);
        if (compLevel >= 5) unlockAchievement(adv.vkId, "level_5_companion", "Спутник V уровня");
        if (compLevel >= 10) unlockAchievement(adv.vkId, "level_10_companion", "Спутник X уровня");
        if (compLevel >= 20) unlockAchievement(adv.vkId, "level_20_companion", "Спутник XX уровня");

        // Скрытые достижения
        if (data.getInt("stats." + adv.vkId + ".deaths", 0) >= 10) unlockAchievement(adv.vkId, "hidden_death_wish", "Желание смерти");
        if (data.getInt("stats." + adv.vkId + ".kills", 0) == 0 && routeCount >= 5) unlockAchievement(adv.vkId, "hidden_pacifist", "Пацифист");
        if (data.getInt("stats." + adv.vkId + ".gold_total", 0) >= 500) unlockAchievement(adv.vkId, "hidden_hoarder", "Скряга");

        // Ежедневки
        int dailyCount = data.getInt("stats." + adv.vkId + ".daily_total", 0) + 1;
        data.set("stats." + adv.vkId + ".daily_total", dailyCount);
        if (dailyCount >= 10) unlockAchievement(adv.vkId, "daily_complete_10", "10 ежедневок");
        if (dailyCount >= 25) unlockAchievement(adv.vkId, "daily_complete_25", "25 ежедневок");
        if (dailyCount >= 50) unlockAchievement(adv.vkId, "daily_complete_50", "50 ежедневок");
        if (dailyCount >= 100) unlockAchievement(adv.vkId, "daily_complete_100", "100 ежедневок");

        // Магазин
        int buyCount = data.getInt("stats." + adv.vkId + ".buy_total", 0);
        if (buyCount >= 10) unlockAchievement(adv.vkId, "buy_item_10", "10 покупок");
        if (buyCount >= 25) unlockAchievement(adv.vkId, "buy_item_25", "25 покупок");
        if (buyCount >= 50) unlockAchievement(adv.vkId, "buy_item_50", "50 покупок");

        // Кампания
        String[] chapters = {"1", "2", "3", "4", "5", "6"};
        for (String ch : chapters) {
            if (data.getBoolean("campaign." + adv.vkId + ".chapter_" + ch, false)) {
                unlockAchievement(adv.vkId, "campaign_chapter_" + ch, "Глава " + ch);
            }
        }

        // === ИНТЕГРАЦИЯ С JOBS (v2.1.0) ===
        try {
            org.bukkit.plugin.Plugin jobsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                int jobsXpPerStage = plugin.getConfig().getInt("mmorpg.integration.jobs-xp-per-stage", 50);
                int jobsXpPerCompletion = plugin.getConfig().getInt("mmorpg.integration.jobs-xp-per-completion", 200);
                int totalJobsXp = jobsXpPerStage * adv.maxStages + jobsXpPerCompletion;
                // Выдаём XP через VKChatJobs API (если игрок онлайн)
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(adv.uuid);
                if (op.isOnline()) {
                    org.bukkit.entity.Player p = op.getPlayer();
                    // XP будет выдан через JobsDataManager при следующем действии
                }
            }
        } catch (Exception ignored) {}

        // === ИНТЕГРАЦИЯ С NATIONS (v2.1.0) ===
        try {
            org.bukkit.plugin.Plugin nationsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                int nationRepPerStage = plugin.getConfig().getInt("mmorpg.integration.nation-rep-per-stage", 10);
                int nationRepPerCompletion = plugin.getConfig().getInt("mmorpg.integration.nation-rep-per-completion", 50);
                int totalNationRep = nationRepPerStage * adv.maxStages + nationRepPerCompletion;
                api().addReputation(adv.vkId, totalNationRep);
            }
        } catch (Exception ignored) {}
        progressDaily(adv.vkId, "complete", 1);
        if (adv.relics > 0) progressDaily(adv.vkId, "relic", adv.relics);
        if (adv.gold > 0) progressDaily(adv.vkId, "gold", adv.gold);
        saveAll();
        api().sendKeyboard(adv.vkId, preface + "\n" + buildFinishMessage(adv, rep, items.size()) + (leveled > 0 ? "\n\n🎉 Уровень походника повышен! Новый уровень: " + getAdvLevel(adv.vkId) : ""), keyboardMain());
    }

    private void killAdventure(ActiveAdventure adv, String preface) {
        if (tryDeathSave(adv, preface)) return;
        active.remove(adv.vkId);
        int repLoss = Math.max(50, api().getReputation(adv.vkId) * plugin.getConfig().getInt("death-rep-loss-percent", 10) / 100);
        api().takeReputation(adv.vkId, repLoss);
        long now = System.currentTimeMillis();
        cooldowns.put(adv.vkId, now + plugin.getConfig().getLong("death-cooldown-hours", 4) * 3_600_000L);
        injuries.put(adv.vkId, now + plugin.getConfig().getLong("injury-hours", 12) * 3_600_000L);
        data.set("stats." + adv.vkId + ".deaths", data.getInt("stats." + adv.vkId + ".deaths", 0) + 1);
        data.set("stats." + adv.vkId + ".route_" + adv.route + "_deaths", data.getInt("stats." + adv.vkId + ".route_" + adv.route + "_deaths", 0) + 1);
        addRandomTrauma(adv.vkId);
        changeSanity(adv.vkId, -25, null);
        setPhobia(adv.vkId, phobiaForRoute(adv.route));
        addAdventureXp(adv.vkId, Math.max(5, adv.xpGained / 3));
        addJournal(adv.vkId, "☠ Гибель в походе: " + plugin.getConfig().getString("adventures." + adv.route + ".name", adv.route));
        unlockAchievement(adv.vkId, "first_death", "Первая смерть в походе");
        saveAll();
        api().sendKeyboard(adv.vkId, preface + "\n" +
                "☠ Поход провален\n\n" +
                "💀 Персонаж погиб.\n" +
                "🔻 Штраф: -" + repLoss + " репутации\n" +
                "🩹 Травма: " + plugin.getConfig().getLong("injury-hours", 12) + " ч.\n" +
                "⏳ Кулдаун: " + plugin.getConfig().getLong("death-cooldown-hours", 4) + " ч.\n" +
                "🎒 Наград нет.", keyboardMain());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (ActiveAdventure adv : new ArrayList<>(active.values())) {
            if (adv.waitingChoice && now >= adv.choiceDeadline) {
                resolveChoice(adv, 1 + random.nextInt(4), true);
            } else if (!adv.waitingChoice && now >= adv.nextEventTime) {
                createEvent(adv);
            } else if (now >= adv.hardDeadline) {
                finishAdventure(adv, "⏳ Время похода истекло.");
            }
        }
    }

    private void showStatus(int vkId) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null) { api().sendKeyboard(vkId, "Активного похода нет.", keyboardMain()); return; }
        String msg = "⏳ Статус похода\n\n" +
                routeEmoji(adv.route) + " " + plugin.getConfig().getString("adventures." + adv.route + ".name", adv.route) + "\n" +
                "📍 Этап: " + adv.stage + "/" + adv.maxStages + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 " + adv.supplies + "   🧠 " + adv.morale + "%   🧩 Рассудок " + adv.sanity + "%   ⭐ " + adv.xpGained + " XP\n" +
                "✨ " + adv.inspiration + "   " + companionName(getCompanion(vkId)) + "\n" +
                "🪙 " + adv.gold + "   🏺 " + adv.relics + "   " + blessingText(adv.blessing) + "\n" + conditionText(adv.condition) + "\n" +
                (adv.waitingChoice ? "\n⚠ Нужен выбор: " + adv.pendingTitle : "\n⏳ Бот напишет, когда нужен выбор.");
        api().sendKeyboard(vkId, msg, adv.waitingChoice ? keyboardForEvent(adv) : keyboardStatusOnly());
    }

    private void showStash(int vkId, String[] args) {
        UUID uuid = api().getUuidByVkId(vkId);
        if (uuid == null) { api().sendMessage(vkId, "❌ Аккаунт не привязан."); return; }
        int page = 1;
        if (args.length >= 2) try { page = Integer.parseInt(args[1]); } catch (Exception ignored) {}
        api().sendKeyboard(vkId, plugin.getStashManager().renderPage(uuid, page, 8), keyboardStash(page));
    }

    private void cancel(int vkId) {
        ActiveAdventure adv = active.remove(vkId);
        saveAll();
        api().sendKeyboard(vkId, adv == null ? "Активного похода нет." : "🛑 Поход отменён. Входная стоимость не возвращается.", keyboardMain());
    }

    private void showQuestion(int vkId, String[] args) {
        int q = 0;
        if (args.length >= 2) try { q = Integer.parseInt(args[1]); } catch (Exception ignored) {}
        String text;
        switch (q) {
            case 1: text = "❓ Начало: выйди с сервера, напиши !поход, выбери маршрут кнопкой."; break;
            case 2: text = "🗺 Маршруты открываются ключами. Закрытый маршрут можно открыть командой !открыть <id>, если ключ лежит в /stash."; break;
            case 3: text = "⏳ Статус: !статуспохода. Бот пишет только когда нужен выбор."; break;
            case 4: text = "🎒 Награды: репутация начисляется в ВК, предметы идут в тайник. Смотри !тайник, забирай /stash."; break;
            case 5: text = "☠ Смерть: потеря репутации, кулдаун, травма, наград нет."; break;
            case 6: text = "🛑 Отмена: !отменапоход. Стоимость входа не возвращается."; break;
            default: text = "Выбери вопрос:";
        }
        api().sendKeyboard(vkId, text, keyboardFaq());
    }

    private void unlockRoute(int vkId, String[] args) {
        UUID uuid = api().getUuidByVkId(vkId);
        if (uuid == null) { api().sendMessage(vkId, "❌ Аккаунт не привязан."); return; }
        if (args.length < 2) { api().sendMessage(vkId, "Использование: !открыть <route>"); return; }
        String route = args[1].toLowerCase(Locale.ROOT);
        if (isRouteUnlocked(vkId, route)) { api().sendMessage(vkId, "✅ Этот маршрут уже открыт."); return; }
        if (plugin.getConfig().getConfigurationSection("adventures." + route) == null) { api().sendMessage(vkId, "❌ Маршрут не найден."); return; }
        if (!plugin.getStashManager().consumeNamedItem(uuid, Material.TRIPWIRE_HOOK, keyName(route))) {
            api().sendMessage(vkId, "❌ В тайнике нет ключа: " + keyName(route)); return;
        }
        data.set("unlocks." + vkId + "." + route, true);
        saveAll();
        api().sendKeyboard(vkId, "🔓 Маршрут открыт\n\n" +
                routeEmoji(route) + " " + plugin.getConfig().getString("adventures." + route + ".name", route) + "\n" +
                "Теперь он доступен в меню походов.", keyboardMain());
    }

    private void showJournal(int vkId) {
        java.util.List<String> journal = data.getStringList("journal." + vkId);
        if (journal.isEmpty()) {
            api().sendKeyboard(vkId, "📜 Дневник приключений\n\nПока пусто. Начни поход — события появятся здесь.", keyboardMain());
            return;
        }
        StringBuilder sb = new StringBuilder("📜 Дневник приключений\n\n");
        int from = Math.max(0, journal.size() - 10);
        for (int i = journal.size() - 1; i >= from; i--) sb.append("• ").append(journal.get(i)).append("\n");
        api().sendKeyboard(vkId, sb.toString(), keyboardMain());
    }

    private void addJournal(int vkId, String line) {
        java.util.List<String> journal = data.getStringList("journal." + vkId);
        String date = new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date());
        journal.add(date + " — " + line);
        while (journal.size() > 30) journal.remove(0);
        data.set("journal." + vkId, journal);
    }

    private void showAchievements(int vkId) {
        StringBuilder sb = new StringBuilder("🏆 Достижения походника (145 шт. по 999 реп.)\n\n");
        String[][] ach = {
                // === ПРОХОЖДЕНИЕ МАРШРУТОВ (12) ===
                {"forest_complete", "🌲 Пройден Лес"},
                {"mine_complete", "⛏ Пройдены Шахты"},
                {"ruins_complete", "🏛 Пройдены Руины"},
                {"swamp_complete", "☠ Пройдены Болота"},
                {"castle_complete", "🏰 Пройден Замок"},
                {"nether_complete", "🌋 Пройден Ад"},
                {"mountain_complete", "⛰ Пройдены Горы"},
                {"underwater_complete", "🌊 Пройден Храм"},
                {"desert_complete", "🏜 Пройдена Пустыня"},
                {"frozen_complete", "❄ Пройдены Ледяные Пики"},
                {"volcanic_complete", "🌋 Пройдены Вулканы"},
                {"shadow_complete", "🌑 Пройдено Царство Теней"},
                // === ОСОБЫЕ ДОСТИЖЕНИЯ (33) ===
                {"first_clear", "⭐ Первый успешный поход"},
                {"first_death", "💀 Первая смерть в походе"},
                {"level_5", "📗 Походник V уровня"},
                {"level_10", "📘 Походник X уровня"},
                {"level_20", "📙 Походник XX уровня"},
                {"level_30", "📕 Походник XXX уровня"},
                {"level_50", "👑 Походник L уровня"},
                {"relic_hunter", "🔮 Охотник за реликвиями"},
                {"all_routes_complete", "🗺 Все маршруты пройдены"},
                {"no_damage_run", "🛡 Безупречный поход"},
                {"speed_run", "⚡ Скоростной поход"},
                {"boss_slayer", "⚔ Убийца боссов"},
                {"trap_master", "🪤 Мастер ловушек"},
                {"riddle_solver", "🧩 Решатель загадок"},
                {"treasure_hunter", "💰 Охотник за сокровищами"},
                {"survivor", "🫡 Выживший"},
                {"explorer", "🧭 Первооткрыватель"},
                {"collector", "📦 Коллекционер"},
                {"companion_bond", "🐾 Связь со спутником"},
                {"class_master_warrior", "⚔ Мастер Воина"},
                {"class_master_scout", "🏹 Мастер Следопыта"},
                {"class_master_mage", "🔮 Мастер Мага"},
                {"class_master_cleric", "🕯 Мастер Жреца"},
                {"class_master_rogue", "🗡 Мастер Разбойника"},
                {"class_master_paladin", "🛡 Мастер Паладина"},
                {"class_master_ranger", "🎯 Мастер Рейнджера"},
                {"daily_streak_7", "📅 7 дней подряд"},
                {"daily_streak_30", "📅 30 дней подряд"},
                {"rep_milestone_1000", "💎 1000 репутации"},
                {"rep_milestone_5000", "💎 5000 репутации"},
                {"rep_milestone_10000", "💎 10000 репутации"},
                {"perfect_run", "✨ Идеальный поход"},
                {"legendary_loot", "🌟 Легендарная добыча"},
                // === МАРШРУТЫ: ПОВТОРНЫЕ ПРОХОЖДЕНИЯ (12) ===
                {"forest_x3", "🌲 Лес x3"},
                {"mine_x3", "⛏ Шахты x3"},
                {"ruins_x3", "🏛 Руины x3"},
                {"swamp_x3", "☠ Болота x3"},
                {"castle_x3", "🏰 Замок x3"},
                {"nether_x3", "🌋 Ад x3"},
                {"mountain_x3", "⛰ Горы x3"},
                {"underwater_x3", "🌊 Храм x3"},
                {"desert_x3", "🏜 Пустыня x3"},
                {"frozen_x3", "❄ Пики x3"},
                {"volcanic_x3", "🌋 Вулканы x3"},
                {"shadow_x3", "🌑 Тени x3"},
                // === МАРШРУТЫ: БЕЗ СМЕРТЕЙ (12) ===
                {"forest_no_death", "🌲 Лес без смертей"},
                {"mine_no_death", "⛏ Шахты без смертей"},
                {"ruins_no_death", "🏛 Руины без смертей"},
                {"swamp_no_death", "☠ Болота без смертей"},
                {"castle_no_death", "🏰 Замок без смертей"},
                {"nether_no_death", "🌋 Ад без смертей"},
                {"mountain_no_death", "⛰ Горы без смертей"},
                {"underwater_no_death", "🌊 Храм без смертей"},
                {"desert_no_death", "🏜 Пустыня без смертей"},
                {"frozen_no_death", "❄ Пики без смертей"},
                {"volcanic_no_death", "🌋 Вулканы без смертей"},
                {"shadow_no_death", "🌑 Тени без смертей"},
                // === КЛАССЫ: УНИКАЛЬНЫЕ ПРОХОЖДЕНИЯ (7) ===
                {"warrior_only_run", "⚔ Только Воин"},
                {"scout_only_run", "🏹 Только Следопыт"},
                {"mage_only_run", "🔮 Только Маг"},
                {"cleric_only_run", "🕯 Только Жрец"},
                {"rogue_only_run", "🗡 Только Разбойник"},
                {"paladin_only_run", "🛡 Только Паладин"},
                {"ranger_only_run", "🎯 Только Рейнджер"},
                // === КОМПАНЬОНЫ: УНИКАЛЬНЫЕ ПРОХОЖДЕНИЯ (8) ===
                {"wolf_companion_run", "🐺 С Волком"},
                {"raven_companion_run", "🦅 С Вороном"},
                {"alchemist_companion_run", "🧪 С Алхимиком"},
                {"mule_companion_run", "🐴 С Мулом"},
                {"dragon_companion_run", "🐲 С Драконом"},
                {"bear_companion_run", "🐻 С Медведем"},
                {"owl_companion_run", "🦉 С Совой"},
                {"snake_companion_run", "🐍 Со Змеёй"},
                // === НАВЫКИ: МАКСИМУМ (8) ===
                {"skill_tough_max", "💪 Живучесть MAX"},
                {"skill_sharp_max", "⚔ Клинок MAX"},
                {"skill_trap_max", "🪤 Ловушки MAX"},
                {"skill_lucky_max", "🍀 Удача MAX"},
                {"skill_trader_max", "💰 Торговец MAX"},
                {"skill_occult_max", "🔮 Оккультизм MAX"},
                {"skill_herbalist_max", "🌿 Травник MAX"},
                {"skill_packer_max", "🎒 Носильщик MAX"},
                // === БОЕВЫЕ ДОСТИЖЕНИЯ (10) ===
                {"kill_10_bosses", "⚔ 10 боссов"},
                {"kill_25_bosses", "⚔ 25 боссов"},
                {"kill_50_bosses", "⚔ 50 боссов"},
                {"kill_100_bosses", "⚔ 100 боссов"},
                {"survive_ambush_10", "🛡 10 засад"},
                {"survive_ambush_25", "🛡 25 засад"},
                {"survive_ambush_50", "🛡 50 засад"},
                {"dodge_trap_10", "🪤 10 ловушек"},
                {"dodge_trap_25", "🪤 25 ловушек"},
                {"dodge_trap_50", "🪤 50 ловушек"},
                // === ИССЛЕДОВАНИЕ (8) ===
                {"find_treasure_10", "💰 10 тайников"},
                {"find_treasure_25", "💰 25 тайников"},
                {"find_treasure_50", "💰 50 тайников"},
                {"find_treasure_100", "💰 100 тайников"},
                {"discover_secret_5", "🔍 5 секретов"},
                {"discover_secret_15", "🔍 15 секретов"},
                {"discover_secret_30", "🔍 30 секретов"},
                {"discover_secret_50", "🔍 50 секретов"},
                // === КОЛЛЕКЦИИ (6) ===
                {"collect_all_forest", "📦 Коллекция Леса"},
                {"collect_all_mine", "📦 Коллекция Шахт"},
                {"collect_all_ruins", "📦 Коллекция Руин"},
                {"collect_all_swamp", "📦 Коллекция Болот"},
                {"collect_all_castle", "📦 Коллекция Замка"},
                {"collect_all_nether", "📦 Коллекция Ада"},
                // === РЕПУТАЦИЯ (6) ===
                {"rep_500_single", "💎 500 реп. за поход"},
                {"rep_1000_single", "💎 1000 реп. за поход"},
                {"rep_2000_single", "💎 2000 реп. за поход"},
                {"rep_5000_single", "💎 5000 реп. за поход"},
                {"rep_10000_single", "💎 10000 реп. за поход"},
                {"rep_25000_total", "💎 25000 реп. всего"},
                // === ВЫЖИВАНИЕ (6) ===
                {"survive_3_stages", "🫡 3 этапа подряд"},
                {"survive_5_stages", "🫡 5 этапов подряд"},
                {"survive_7_stages", "🫡 7 этапов подряд"},
                {"survive_10_stages", "🫡 10 этапов подряд"},
                {"heal_ally_10", "💊 10 лечений"},
                {"heal_ally_25", "💊 25 лечений"},
                // === ПРОГРЕССИЯ (6) ===
                {"level_5_companion", "🐾 Спутник V уровня"},
                {"level_10_companion", "🐾 Спутник X уровня"},
                {"level_20_companion", "🐾 Спутник XX уровня"},
                {"max_morale", "😊 Макс. мораль"},
                {"max_hunger", "🍖 Макс. сытость"},
                {"max_sanity", "🧠 Макс. рассудок"},
                // === ЕЖЕДНЕВКИ (6) ===
                {"daily_complete_10", "📅 10 ежедневок"},
                {"daily_complete_25", "📅 25 ежедневок"},
                {"daily_complete_50", "📅 50 ежедневок"},
                {"daily_complete_100", "📅 100 ежедневок"},
                {"daily_streak_14", "📅 14 дней подряд"},
                {"daily_streak_60", "📅 60 дней подряд"},
                // === МАГАЗИН (6) ===
                {"buy_item_10", "🛒 10 покупок"},
                {"buy_item_25", "🛒 25 покупок"},
                {"buy_item_50", "🛒 50 покупок"},
                {"buy_all_equipment", "🛡 Всё снаряжение"},
                {"buy_all_consumables", "🧪 Все расходники"},
                {"spend_5000_rep", "💸 5000 реп. потрачено"},
                // === КАМПАНИЯ (6) ===
                {"campaign_chapter_1", "📖 Глава I"},
                {"campaign_chapter_2", "📖 Глава II"},
                {"campaign_chapter_3", "📖 Глава III"},
                {"campaign_chapter_4", "📖 Глава IV"},
                {"campaign_chapter_5", "📖 Глава V"},
                {"campaign_chapter_6", "📖 Глава VI"},
                // === СКРЫТЫЕ ДОСТИЖЕНИЯ (6) ===
                {"hidden_death_wish", "💀 Желание смерти"},
                {"hidden_pacifist", "🕊 Пацифист"},
                {"hidden_hoarder", "📦 Скряга"},
                {"hidden_speed_demon", "⚡ Демон скорости"},
                {"hidden_lucky_streak", "🍀 Полоса удачи"},
                {"hidden_unstoppable", "🔥 Неостановимый"}
        };
        for (String[] a : ach) sb.append(data.getBoolean("achievements." + vkId + "." + a[0], false) ? "✅ " : "⬜ ").append(a[1]).append("\n");
        api().sendKeyboard(vkId, sb.toString(), keyboardMain());
    }

    private void unlockAchievement(int vkId, String id, String title) {
        if (data.getBoolean("achievements." + vkId + "." + id, false)) return;
        data.set("achievements." + vkId + "." + id, true);
        addJournal(vkId, "🏆 Достижение: " + title);
        try { api().addReputation(vkId, plugin.getConfig().getInt("achievements.reward-rep", 999)); } catch (Exception ignored) {}
    }

    private String dailyDate() { return progressManager.dailyDate(); }
    private void ensureDaily(int vkId) { progressManager.ensureDaily(vkId); }
    private void showDaily(int vkId) { api().sendKeyboard(vkId, progressManager.buildDailyText(vkId), keyboardMain()); }
    private void progressDaily(int vkId, String type, int amount) { progressManager.progressDaily(vkId, type, amount); }

    private void handleAdmin(int sender, int peer, String[] args) {
        if (!isVkAdmin(sender)) { api().sendMessage(peer, "⛔ Нет доступа к админ-командам оффлайн-модуля."); return; }
        if (args.length < 2) { api().sendMessage(peer, "Админ: !офадмин list | cancel <vk> | finish <vk> | key <vk> <route> | stash <vk> | event <vk>"); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            StringBuilder sb = new StringBuilder("Активные походы:\n");
            for (ActiveAdventure adv : active.values()) sb.append(adv.vkId).append(" — ").append(adv.route).append(" stage ").append(adv.stage).append("/").append(adv.maxStages).append("\n");
            api().sendMessage(peer, sb.toString());
        } else if ((sub.equals("cancel") || sub.equals("finish") || sub.equals("event") || sub.equals("stash")) && args.length >= 3) {
            int target = Integer.parseInt(args[2]);
            if (sub.equals("cancel")) { active.remove(target); saveAll(); api().sendMessage(peer, "Ок, отменено."); }
            else if (sub.equals("finish")) { ActiveAdventure adv = active.get(target); if (adv != null) finishAdventure(adv, "Админ завершил поход."); api().sendMessage(peer, "Ок."); }
            else if (sub.equals("event")) { ActiveAdventure adv = active.get(target); if (adv != null) createEvent(adv); api().sendMessage(peer, "Ок."); }
            else { UUID uuid = api().getUuidByVkId(target); api().sendMessage(peer, uuid == null ? "Не привязан" : plugin.getStashManager().renderPage(uuid, 1, 20)); }
        } else if (sub.equals("key") && args.length >= 4) {
            int target = Integer.parseInt(args[2]); String route = args[3].toLowerCase(Locale.ROOT); UUID uuid = api().getUuidByVkId(target);
            if (uuid != null) plugin.getStashManager().addItem(uuid, plugin.getStashManager().namedKey(keyName(route)));
            api().sendMessage(peer, uuid == null ? "Не привязан" : "Ключ выдан.");
        }
    }


    private String routeCard(int vkId, String key, boolean unlocked) {
        return OfflineTextFactory.routeCard(plugin.getConfig(), key, unlocked, cleanKeyName(key));
    }

    private String buildEventMessage(ActiveAdventure adv) {
        return OfflineTextFactory.buildEventMessage(plugin.getConfig(), adv, blessingText(adv.blessing));
    }

    private String buildFinishMessage(ActiveAdventure adv, int rep, int itemCount) {
        return OfflineTextFactory.buildFinishMessage(plugin.getConfig(), adv, rep, itemCount, campaignLine(adv.vkId));
    }

    private String hpBar(int hp, int max) { return OfflineTextFactory.hpBar(hp, max); }
    private String stageBar(int stage, int max) { return OfflineTextFactory.stageBar(stage, max); }
    private String difficultyStars(int diff) { return OfflineTextFactory.difficultyStars(diff); }
    private String routeEmoji(String route) { return OfflineTextFactory.routeEmoji(route); }
    private String eventIcon(String type) { return OfflineTextFactory.eventIcon(type); }

    private String randomOf(String... values) {
        return values[random.nextInt(values.length)];
    }

    private String cleanKeyName(String route) {
        return OfflineTextFactory.cleanKeyName(keyName(route));
    }

    private String routeButtonLabel(String route) {
        switch (route) {
            case "forest": return "🌲 Лес";
            case "mine": return "⛏ Шахты";
            case "ruins": return "🏛 Руины";
            case "swamp": return "☠ Болота";
            case "castle": return "🏰 Замок";
            case "nether": return "🌋 Ад";
            default: return routeEmoji(route) + " " + route;
        }
    }

    private String routeFromButton(String text) {
        if (text == null) return null;
        text = text.trim().replace("️", "");
        if (text.equals("🌲 Лес") || text.equalsIgnoreCase("Лес")) return "forest";
        if (text.equals("⛏ Шахты") || text.equalsIgnoreCase("Шахты")) return "mine";
        if (text.equals("🏛 Руины") || text.equalsIgnoreCase("Руины")) return "ruins";
        if (text.equals("☠ Болота") || text.equalsIgnoreCase("Болота")) return "swamp";
        if (text.equals("🏰 Замок") || text.equalsIgnoreCase("Замок")) return "castle";
        if (text.equals("🌋 Ад") || text.equalsIgnoreCase("Ад")) return "nether";
        return null;
    }

    private String routeFromOpenButton(String text) {
        if (text.contains("Шахты")) return "mine";
        if (text.contains("Руины")) return "ruins";
        if (text.contains("Болота")) return "swamp";
        if (text.contains("Замок")) return "castle";
        if (text.contains("Ад")) return "nether";
        return "mine";
    }

    private boolean isVkAdmin(int vkId) { return plugin.getConfig().getIntegerList("vk-admins").contains(vkId); }
    private boolean isRouteUnlocked(int vkId, String route) { return plugin.getConfig().getBoolean("adventures." + route + ".default-unlocked", false) || data.getBoolean("unlocks." + vkId + "." + route, false); }
    private String keyName(String route) { return "§6Ключ: " + plugin.getConfig().getString("adventures." + route + ".name", route); }
    private int getProgress(int vkId) { return progressManager.getProgress(vkId); }
    private void addProgress(int vkId, String route) { progressManager.addProgress(vkId, route); }

    private List<ItemStack> rollItems(String path) {
        List<ItemStack> result = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(path)) {
            try {
                String[] p = line.split(";"); Material mat = Material.valueOf(p[0].trim().toUpperCase(Locale.ROOT));
                int min = p.length > 1 ? Integer.parseInt(p[1].trim()) : 1; int max = p.length > 2 ? Integer.parseInt(p[2].trim()) : min; int chance = p.length > 3 ? Integer.parseInt(p[3].trim()) : 100;
                if (random.nextInt(100) >= chance) continue;
                int amount = min + random.nextInt(Math.max(1, max - min + 1));
                while (amount > 0) { int stack = Math.min(64, amount); result.add(new ItemStack(mat, stack)); amount -= stack; }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private String keyboardShopEquipment() { return OfflineKeyboardFactory.shopEquipment(); }
    private String keyboardShopConsumables() { return OfflineKeyboardFactory.shopConsumables(); }
    private String keyboardUseConsumables() { return OfflineKeyboardFactory.useConsumables(); }
    private String keyboardOfflineSkills() { return OfflineKeyboardFactory.offlineSkills(); }
    private String keyboardSellStash() { return OfflineKeyboardFactory.sellStash(); }
    private String keyboardCampaign() { return OfflineKeyboardFactory.campaign(); }
    private String keyboardHospital() { return OfflineKeyboardFactory.hospital(); }

    private String keyboardMain() {
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("adventures");
        if (sec != null) for (String key : sec.getKeys(false)) { labels.add(routeButtonLabel(key)); ids.add(key); }
        return OfflineKeyboardFactory.main(labels, ids);
    }

    private String keyboardHero() { return OfflineKeyboardFactory.hero(); }

    private boolean isCombatEvent(String type) {
        return OfflineEventMath.isCombatEvent(type);
    }

    private String keyboardForEvent(ActiveAdventure adv) {
        if (adv != null && isCombatEvent(adv.pendingType)) return keyboardCombatChoices();
        return keyboardChoices();
    }

    private String keyboardCombatChoices() { return OfflineKeyboardFactory.combatChoices(); }

    private void healSmart(int vkId) {
        ActiveAdventure adv = active.get(vkId);
        if (adv != null) {
            healInAdventure(vkId, adv);
        } else {
            healAfterDeath(vkId);
        }
    }

    private void healInAdventure(int vkId, ActiveAdventure adv) {
        if (adv.hp >= adv.maxHp && "none".equals(adv.condition)) {
            api().sendKeyboard(vkId, "💚 Лечение не требуется: HP полный и негативных состояний нет.", keyboardStatusOnly());
            return;
        }
        if (adv.waitingChoice) {
            api().sendKeyboard(vkId, "⚠ Сейчас идёт событие выбора. Можно лечиться, но после лечения не забудь ответить на событие.", keyboardForEvent(adv));
        }

        int potionCount = data.getInt("stats." + vkId + ".bag.potion_heal", 0);
        String msg;
        if (potionCount > 0) {
            data.set("stats." + vkId + ".bag.potion_heal", potionCount - 1);
            int heal = hasOfflineSkill(vkId, "herbalist") ? 45 : 30;
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            msg = "❤️ Использовано зелье лечения: +" + heal + " HP. Осталось зелий: " + (potionCount - 1);
        } else if (adv.supplies > 0) {
            adv.supplies--;
            int heal = 18 + random.nextInt(10);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            adv.morale = Math.min(100, adv.morale + 5);
            if ("bleeding".equals(adv.condition) || "exhausted".equals(adv.condition)) adv.condition = "none";
            msg = "🔥 Перевязка и короткий привал: +" + heal + " HP, припасы -1, мораль +5%.";
        } else {
            int cost = (int)Math.max(1, Math.round(plugin.getConfig().getInt("offline2.active-healing.rep-cost", 120) * getOfflineDonateShopMultiplier(vkId)));
            int rep = api().getReputation(vkId);
            if (rep < cost) {
                api().sendKeyboard(vkId, "❌ Нет зелий, нет припасов и не хватает репутации на экстренное лечение. Нужно: " + cost + " реп.\nКупи зелья: !магазин расходники", keyboardStatusOnly());
                return;
            }
            api().takeReputation(vkId, cost);
            int heal = plugin.getConfig().getInt("offline2.active-healing.rep-heal", 25);
            adv.hp = Math.min(adv.maxHp, adv.hp + heal);
            msg = "💚 Экстренное лечение за репутацию: +" + heal + " HP. Списано: " + cost + " реп.";
        }
        saveAll();
        api().sendKeyboard(vkId, "✅ " + msg + "\n❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" + conditionText(adv.condition), adv.waitingChoice ? keyboardForEvent(adv) : keyboardStatusOnly());
    }

    private int healingCost(int vkId) {
        int rep = Math.max(0, api().getReputation(vkId));
        int cost = Math.max(1, (int) Math.ceil(rep * plugin.getConfig().getDouble("mmorpg.healing-after-death.cost-percent", 0.05)));
        return (int)Math.max(1, Math.round(cost * getOfflineDonateShopMultiplier(vkId)));
    }

    private void healAfterDeath(int vkId) {
        long now = System.currentTimeMillis();
        boolean hasCooldown = cooldowns.getOrDefault(vkId, 0L) > now;
        boolean hasInjury = injuries.getOrDefault(vkId, 0L) > now;
        if (!hasCooldown && !hasInjury) {
            api().sendKeyboard(vkId, "💚 Лечение не требуется. Нет кулдауна смерти или травмы.", keyboardMain());
            return;
        }
        int cost = healingCost(vkId);
        int rep = api().getReputation(vkId);
        if (rep < cost) {
            api().sendKeyboard(vkId, "❌ Недостаточно репутации для лечения. Нужно: " + cost + " реп.\nЭто 5% от текущей репутации ВК.", keyboardHeal());
            return;
        }
        api().takeReputation(vkId, cost);
        cooldowns.remove(vkId);
        injuries.remove(vkId);
        saveAll();
        api().sendKeyboard(vkId, "💚 Лечение завершено.\nСписано: " + cost + " репутации ВК (5%).\nКулдаун смерти и травма сняты — можно снова идти в поход.", keyboardMain());
    }

    private String keyboardHeal() { return OfflineKeyboardFactory.heal(); }
    private String keyboardChoices() { return OfflineKeyboardFactory.choices(); }
    private String keyboardStatusOnly() { return OfflineKeyboardFactory.statusOnly(); }
    private String keyboardUnlock(String route) { return OfflineKeyboardFactory.unlock(routeButtonLabel(route), route); }
    private String keyboardStash(int page) { return OfflineKeyboardFactory.stash(page); }
    private String keyboardFaq() { return OfflineKeyboardFactory.faq(); }
    private String keyboardClasses() { return OfflineKeyboardFactory.classes(); }
    private String keyboardCompanions() { return OfflineKeyboardFactory.companions(); }

    private String formatDuration(long ms) { long t = Math.max(0, ms / 1000); long h = t / 3600, m = (t % 3600) / 60, s = t % 60; return h > 0 ? h + "ч " + m + "м" : (m > 0 ? m + "м " + s + "с" : s + "с"); }

    public synchronized void loadAll() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs(); data = YamlConfiguration.loadConfiguration(file); active.clear(); cooldowns.clear(); injuries.clear();
        ConfigurationSection act = data.getConfigurationSection("active"); if (act != null) for (String vk : act.getKeys(false)) try { int id = Integer.parseInt(vk); String b = "active." + vk + "."; ActiveAdventure a = new ActiveAdventure(id, UUID.fromString(data.getString(b + "uuid")), data.getString(b + "player"), data.getString(b + "route"), data.getLong(b + "start")); a.stage=data.getInt(b+"stage"); a.maxStages=data.getInt(b+"maxStages"); a.hp=data.getInt(b+"hp"); a.maxHp=data.getInt(b+"maxHp",100); a.nextEventTime=data.getLong(b+"nextEventTime"); a.waitingChoice=data.getBoolean(b+"waiting"); a.choiceDeadline=data.getLong(b+"choiceDeadline"); a.pendingType=data.getString(b+"pendingType"); a.pendingTitle=data.getString(b+"pendingTitle"); a.hardDeadline=data.getLong(b+"hardDeadline"); a.supplies=data.getInt(b+"supplies",3); a.morale=data.getInt(b+"morale",100); a.xpGained=data.getInt(b+"xpGained",0); a.inspiration=data.getInt(b+"inspiration",0); a.condition=data.getString(b+"condition","none"); a.gold=data.getInt(b+"gold",0); a.relics=data.getInt(b+"relics",0); a.blessing=data.getString(b+"blessing","none"); a.sanity=data.getInt(b+"sanity",100); a.campaignChapter=data.getString(b+"campaignChapter",""); a.deathSavesUsed=data.getInt(b+"deathSavesUsed",0); active.put(id,a);} catch(Exception ignored){}
        ConfigurationSection cds = data.getConfigurationSection("cooldowns"); if (cds != null) for (String vk : cds.getKeys(false)) try { cooldowns.put(Integer.parseInt(vk), cds.getLong(vk)); } catch(Exception ignored){}
        ConfigurationSection inj = data.getConfigurationSection("injuries"); if (inj != null) for (String vk : inj.getKeys(false)) try { injuries.put(Integer.parseInt(vk), inj.getLong(vk)); } catch(Exception ignored){}
    }

    public synchronized void saveAll() {
        data.set("active", null); for (ActiveAdventure a : active.values()) { String b="active."+a.vkId+"."; data.set(b+"uuid",a.uuid.toString()); data.set(b+"player",a.playerName); data.set(b+"route",a.route); data.set(b+"start",a.startTime); data.set(b+"stage",a.stage); data.set(b+"maxStages",a.maxStages); data.set(b+"hp",a.hp); data.set(b+"maxHp",a.maxHp); data.set(b+"nextEventTime",a.nextEventTime); data.set(b+"waiting",a.waitingChoice); data.set(b+"choiceDeadline",a.choiceDeadline); data.set(b+"pendingType",a.pendingType); data.set(b+"pendingTitle",a.pendingTitle); data.set(b+"hardDeadline",a.hardDeadline); data.set(b+"supplies",a.supplies); data.set(b+"morale",a.morale); data.set(b+"xpGained",a.xpGained); data.set(b+"inspiration",a.inspiration); data.set(b+"condition",a.condition); data.set(b+"gold",a.gold); data.set(b+"relics",a.relics); data.set(b+"blessing",a.blessing); data.set(b+"sanity",a.sanity); data.set(b+"campaignChapter",a.campaignChapter); data.set(b+"deathSavesUsed",a.deathSavesUsed); }
        data.set("cooldowns", null); for (Map.Entry<Integer,Long> e: cooldowns.entrySet()) if(e.getValue()>System.currentTimeMillis()) data.set("cooldowns."+e.getKey(), e.getValue());
        data.set("injuries", null); for (Map.Entry<Integer,Long> e: injuries.entrySet()) if(e.getValue()>System.currentTimeMillis()) data.set("injuries."+e.getKey(), e.getValue());
        try { data.save(file); } catch (Exception e) { plugin.getLogger().warning("Не удалось сохранить adventures.yml: " + e.getMessage()); }
    }
}
