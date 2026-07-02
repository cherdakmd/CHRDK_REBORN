package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.PlayerData;
import ru.example.vkchatoffline.managers.ZoneData.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Главный менеджер оффлайн-приключений v3.0
 * Конечный автомат состояний с боёвкой, лутом и сетами
 */
public class AdventureManager {
    private final VKChatOfflinePlugin plugin;
    private final PlayerData playerData;
    private final CombatManager combat;
    private final ShopManager shop;
    private final Map<Integer, AdventureState> states = new ConcurrentHashMap<>();
    private final Random rnd = ThreadLocalRandom.current();

    public enum State { MENU, CHOOSING, ADVENTURING, EVENT, COMBAT, RESULT, DEAD, COMPLETE }

    public static class AdventureState {
        public State state = State.MENU;
        public Zone zone;
        public int stage, maxStages;
        public int hp, maxHp, energy, maxEnergy;
        public int baseAtk, baseDef;
        public String className;
        public int supplies;
        public int gold, repEarned;
        public boolean waitingInput;
        public long eventTime;
        public String eventType, eventText;
        public int eventChoice;
        public EnemyType enemy;
        public int enemyHp, enemyMaxHp;
        public int combatRound, effectTurns;
        public String effectType;
        public boolean defending;
        public int skillCooldown;
        public List<String> earnedPieces = new ArrayList<>();
        public Map<String, Integer> earnedResources = new HashMap<>();
    }

    public AdventureManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.playerData = plugin.getPlayerData();
        this.combat = new CombatManager();
        this.shop = new ShopManager(plugin, playerData);
    }

    // ===== ОБРАБОТКА КОМАНД =====
    public void handleCommand(int vkId, String cmd, String[] args) {
        AdventureState s = states.computeIfAbsent(vkId, k -> new AdventureState());

        switch (cmd) {
            case "!adv":
                if (args.length <= 1) { showMenu(vkId); return; }
                handleSub(vkId, args[1], args);
                break;
            case "!поход": case "!приключение":
                showMenu(vkId);
                break;
        }
    }

    private void handleSub(int vkId, String sub, String[] args) {
        AdventureState s = states.get(vkId);
        if (s == null) s = new AdventureState();

        switch (sub) {
            case "menu": showMenu(vkId); break;
            case "start": startAdventure(vkId, args.length > 2 ? args[2] : ""); break;
            case "hero": showHero(vkId); break;
            case "class": showClassSelect(vkId); break;
            case "setclass": setClass(vkId, args); break;
            case "act": handleEventAction(vkId, args.length > 2 ? args[2] : "risk"); break;
            case "atk": combat.handleAction(this, vkId, 1); break;
            case "def": combat.handleAction(this, vkId, 2); break;
            case "skill": combat.handleAction(this, vkId, 3, args.length > 2 ? args[2] : "1"); break;
            case "potion": combat.handleAction(this, vkId, 4); break;
            case "flee": combat.handleAction(this, vkId, 5); break;
            case "continue": advanceAdventure(vkId); break;
            case "claim": claimRewards(vkId); break;
            case "heal": healPlayer(vkId); break;
            case "status": showStatus(vkId); break;
            case "stats": showStats(vkId); break;
            case "info": showInfo(vkId); break;
            case "shop": showShop(vkId, args); break;
            case "bag": showBag(vkId); break;
            case "confirm": handleConfirm(vkId, args); break;
            case "cancel": showMenu(vkId); break;
        }
    }

    // ===== ГЛАВНОЕ МЕНЮ =====
    private void showMenu(int vkId) {
        showMenu(vkId, null);
    }

    private void showMenu(int vkId, String prefix) {
        AdventureState s = states.computeIfAbsent(vkId, k -> new AdventureState());
        s.state = State.MENU;
        s.zone = null;

        int level = playerData.getLevel(vkId);
        String cls = playerData.hasClass(vkId) ? playerData.getClassName(vkId) : "Нет класса";

        String msg = (prefix != null ? prefix + "\n\n" : "")
                + "⚔ CHRDK ADVENTURES v3.0 ⚔\n\n"
                + "👤 Уровень: " + level + " | Класс: " + cls + "\n\n"
                + "🌲 Тёмный лес — ур.1\n"
                + "⛏ Глубокие шахты — ур.2\n"
                + "🏛 Древние руины — ур.3\n"
                + "🔥 Незер-пустоши — ур.4\n"
                + "❄️ Ледяная тундра — ур.5\n"
                + "🌑 Грань Бездны — ур.6\n\n"
                + "Выбери зону кнопкой ниже:";

        sendWithKb(vkId, msg, Keyboards.mainMenu());
    }

    // ===== НАЧАЛО ПОХОДА =====
    private void startAdventure(int vkId, String zoneId) {
        AdventureState s = states.computeIfAbsent(vkId, k -> new AdventureState());

        if (s.state == State.ADVENTURING || s.state == State.COMBAT || s.state == State.EVENT) {
            sendMsg(vkId, "❌ У вас уже активное приключение!");
            return;
        }

        if (!playerData.hasClass(vkId)) {
            sendMsg(vkId, "❌ Сначала выберите класс! Команда: !adv class");
            return;
        }

        Zone zone = findZone(zoneId);
        if (zone == null) { showMenu(vkId); return; }

        int level = playerData.getLevel(vkId);
        if (level < zone.difficulty) {
            sendMsg(vkId, "❌ Нужен уровень " + zone.difficulty + "! Ваш: " + level);
            return;
        }

        int daily = playerData.getAdventuresToday(vkId);
        int maxDaily = plugin.getConfig().getInt("general.max-adventures-per-day", 10);
        if (daily >= maxDaily) {
            sendMsg(vkId, "❌ Лимит приключений на сегодня (" + maxDaily + ")! Жди завтра.");
            return;
        }

        int cdMin = plugin.getConfig().getInt("general.cooldown-minutes", 30);
        if (playerData.isOnCooldown(vkId, cdMin)) {
            long left = (cdMin * 60000L - (System.currentTimeMillis() - playerData.getCooldown(vkId))) / 60000;
            sendMsg(vkId, "❌ Кулдаун! Осталось " + left + " мин.");
            return;
        }

        // Инициализация
        String clsName = playerData.getClassName(vkId);
        ClassType ct = ClassType.valueOf(clsName);
        int lvl = playerData.getLevel(vkId);

        s.zone = zone;
        s.stage = 0;
        s.maxStages = zone.stages;
        s.className = clsName;
        s.hp = ct.baseHp + lvl * 10;
        s.maxHp = s.hp;
        s.maxEnergy = 100;
        s.energy = s.maxEnergy;
        s.baseAtk = ct.baseAtk + lvl * 2;
        s.baseDef = ct.baseDef + lvl;
        s.supplies = 3 + lvl;
        s.gold = 0;
        s.repEarned = 0;
        s.state = State.ADVENTURING;
        s.waitingInput = false;
        s.earnedPieces.clear();
        s.earnedResources.clear();
        s.eventTime = System.currentTimeMillis() + 5000;

        playerData.incrementDaily(vkId);
        playerData.setCooldown(vkId, System.currentTimeMillis());

        sendWithKb(vkId, zone.color + "⚔ " + zone.name + " — ПОХОД НАЧАТ!\n\n"
                + zone.icon + " Сложность: " + zone.difficulty + "/6\n"
                + "📍 Этапов: " + zone.stages + "\n"
                + "❤ HP: " + s.hp + "/" + s.maxHp + "\n"
                + "⚡ Энергия: " + s.energy + "/" + s.maxEnergy + "\n"
                + "🥫 Припасы: " + s.supplies + "\n\n"
                + "⏳ Первое событие через 5 сек...", Keyboards.adventureActions());
    }

    // ===== ВЫБОР КЛАССА =====
    private void showClassSelect(int vkId) {
        String msg = "⚔ ВЫБОР КЛАССА\n\n";
        for (ClassType ct : ClassType.values()) {
            msg += ct.icon + " " + ct.name + " — " + ct.desc + "\n"
                    + "   ❤" + ct.baseHp + " ⚔" + ct.baseAtk + " 🛡" + ct.baseDef + "\n\n";
        }
        sendWithKb(vkId, msg, Keyboards.classSelect());
    }

    private void setClass(int vkId, String[] args) {
        if (args.length < 3) return;
        try {
            ClassType ct = ClassType.valueOf(args[2].toUpperCase());
            playerData.setClass(vkId, ct.name());
            showMenu(vkId);
        } catch (Exception e) {
            sendMsg(vkId, "❌ Неверный класс. !adv class чтобы выбрать.");
        }
    }

    // ===== ГЕРОЙ =====
    private void showHero(int vkId) {
        int level = playerData.getLevel(vkId);
        String cls = playerData.hasClass(vkId) ? playerData.getClassName(vkId) : "Нет";
        int xp = playerData.getXp(vkId);
        int xpNeed = playerData.getXpNeeded(vkId);
        int kills = playerData.getEnemiesKilled(vkId);
        int bosses = playerData.getBossesKilled(vkId);
        int adv = playerData.getAdventuresCompleted(vkId);

        String msg = "👤 ГЕРОЙ\n\n"
                + "⭐ Уровень: " + level + " (XP: " + xp + "/" + xpNeed + ")\n"
                + "⚔ Класс: " + cls + "\n"
                + "💀 Убито: " + kills + " | ☠ Боссов: " + bosses + "\n"
                + "🗺 Походов: " + adv + "\n\n"
                + "Выберите раздел:";
        sendWithKb(vkId, msg, Keyboards.heroMenu());
    }

    // ===== СОБЫТИЯ =====
    public void tickEvents() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, AdventureState> e : states.entrySet()) {
            AdventureState s = e.getValue();
            if (s.state == State.ADVENTURING && !s.waitingInput && now >= s.eventTime) {
                triggerEvent(e.getKey());
            }
        }
    }

    private void triggerEvent(int vkId) {
        AdventureState s = states.get(vkId);
        if (s == null || s.state != State.ADVENTURING) return;

        s.stage++;
        if (s.stage >= s.maxStages) {
            if (rnd.nextBoolean()) {
                startBossFight(vkId);
            } else {
                startRandomEvent(vkId);
            }
        } else {
            if (rnd.nextDouble() < 0.4) {
                startRandomEvent(vkId);
            } else {
                startMonsterFight(vkId);
            }
        }
    }

    private void startRandomEvent(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.EVENT;
        s.waitingInput = true;

        String[] event = ZoneData.EVENTS[rnd.nextInt(ZoneData.EVENTS.length)];
        s.eventType = event[0];
        s.eventText = event[1] + "\n" + event[2];

        sendWithKb(vkId, s.zone.color + "📌 Этап " + s.stage + "/" + s.maxStages + "\n\n"
                + event[1] + "\n" + ChatColor.GRAY + event[2] + "\n\n"
                + ChatColor.YELLOW + "Ваши действия: " + event[3], Keyboards.adventureActions());
    }

    private void startMonsterFight(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.COMBAT;
        s.waitingInput = false;
        s.defending = false;
        s.combatRound = 0;
        s.skillCooldown = 0;

        List<EnemyType> zoneEnemies = getZoneEnemies(s.zone, false);
        s.enemy = zoneEnemies.get(rnd.nextInt(zoneEnemies.size()));
        int scale = s.zone.difficulty + s.stage;
        s.enemyMaxHp = s.enemy.hp + scale * 15;
        s.enemyHp = s.enemyMaxHp;

        combat.showCombatUI(this, vkId);
    }

    private void startBossFight(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.COMBAT;
        s.waitingInput = false;
        s.defending = false;
        s.combatRound = 0;
        s.skillCooldown = 0;

        List<EnemyType> bosses = getZoneEnemies(s.zone, true);
        s.enemy = bosses.get(rnd.nextInt(bosses.size()));
        int scale = s.zone.difficulty + s.stage;
        s.enemyMaxHp = s.enemy.hp + scale * 30;
        s.enemyHp = s.enemyMaxHp;

        combat.showCombatUI(this, vkId,
                ChatColor.DARK_RED + "☠ БОСС: " + s.enemy.icon + " " + s.enemy.name + "! "
                + ChatColor.YELLOW + "Готовьтесь к тяжёлому бою!");
    }

    private void handleEventAction(int vkId, String action) {
        AdventureState s = states.get(vkId);
        if (s == null || s.state != State.EVENT) return;

        s.waitingInput = false;
        boolean success = rnd.nextInt(100) < 60;
        String msg;

        switch (s.eventType) {
            case "trap":
                if (action.equals("risk") || action.equals("disable")) {
                    if (success) { msg = "✅ Обезврежено! +10 XP"; playerData.addXp(vkId, 10); }
                    else { msg = "💥 Ловушка сработала! −" + (10 + s.zone.difficulty * 5) + " HP"; s.hp -= 10 + s.zone.difficulty * 5; }
                } else { msg = "🏃 Вы обошли ловушку."; }
                break;
            case "treasure":
                if (action.equals("search") || action.equals("open")) {
                    int gold = 5 + rnd.nextInt(10 + s.zone.difficulty * 5);
                    s.gold += gold;
                    msg = "✅ Сундук открыт! +" + gold + " золота, +15 XP";
                    playerData.addXp(vkId, 15);
                    addRandomResource(vkId, s);
                } else { msg = "🚶 Вы прошли мимо."; }
                break;
            case "merchant":
                if (action.equals("search") || action.equals("trade")) {
                    int gold = 3 + rnd.nextInt(8);
                    s.gold += gold;
                    msg = "✅ Выгодная сделка! +" + gold + " золота, +10 XP";
                    playerData.addXp(vkId, 10);
                    if (rnd.nextBoolean()) addRandomResource(vkId, s);
                } else { msg = "👋 Торговец ушёл."; }
                break;
            case "shrine":
                if (action.equals("search") || action.equals("pray")) {
                    int heal = s.maxHp / 4;
                    s.hp = Math.min(s.maxHp, s.hp + heal);
                    msg = "✨ Благословение! +" + heal + " HP, +20 XP";
                    playerData.addXp(vkId, 20);
                } else { msg = "🚶 Вы прошли мимо святилища."; }
                break;
            case "camp":
                if (action.equals("cautious") || action.equals("rest")) {
                    int heal = s.maxHp / 3;
                    s.hp = Math.min(s.maxHp, s.hp + heal);
                    s.energy = s.maxEnergy;
                    msg = "💤 Отдых! +" + heal + " HP, энергия полна.";
                } else { msg = "🚶 Продолжаем путь."; }
                break;
            case "riddle":
                if (action.equals("search") || action.equals("solve")) {
                    if (success) { msg = "✅ Загадка разгадана! +25 XP, +1 припас"; s.supplies++; playerData.addXp(vkId, 25); }
                    else { msg = "❌ Неверно... +5 XP"; playerData.addXp(vkId, 5); }
                } else { msg = "🚶 Загадка осталась неразгаданной."; }
                break;
            case "ambush":
                startMonsterFight(vkId);
                return;
            case "cave":
                if (action.equals("risk") || action.equals("enter")) {
                    startMonsterFight(vkId);
                    return;
                } else { msg = "🚶 Обошли пещеру."; }
                break;
            default:
                msg = "✅ Событие пройдено.";
        }

        if (s.hp <= 0) {
            handleDeath(vkId);
            return;
        }

        s.state = State.RESULT;
        sendWithKb(vkId, msg, Keyboards.afterEvent());
    }

    // ===== ПРОДВИЖЕНИЕ И ЗАВЕРШЕНИЕ =====
    private void advanceAdventure(int vkId) {
        AdventureState s = states.get(vkId);
        s.waitingInput = false;
        s.eventTime = System.currentTimeMillis() + 3000;
        s.state = State.ADVENTURING;

        if (s.stage >= s.maxStages) {
            completeAdventure(vkId);
        } else {
        sendWithKb(vkId, s.zone.color + "📍 Этап " + s.stage + "/" + s.maxStages + " — продолжаем путь...\n⏳ Следующее событие через 3 сек.", Keyboards.adventureActions());
        }
    }

    private void completeAdventure(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.COMPLETE;

        int baseRep = s.zone.difficulty * 30 + s.stage * 10 + s.gold;
        int setChance = 15 + s.zone.difficulty * 5;
        List<String> found = new ArrayList<>();

        // Шанс на сетовую часть
        if (rnd.nextInt(100) < setChance) {
            SetPiece piece = randomPiece(s.zone);
            if (piece != null && !playerData.hasPiece(vkId, piece.name)) {
                playerData.addPiece(vkId, piece.name);
                s.earnedPieces.add(piece.name);
                found.add(piece.name);
            }
        }

        // Ресурсы
        addRandomResource(vkId, s);
        addRandomResource(vkId, s);

        playerData.addXp(vkId, s.zone.difficulty * 25);
        playerData.addAdventure(vkId);

        try {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, baseRep);
        } catch (Exception ignored) {}

        String msg = s.zone.color + "✅ ПОХОД ЗАВЕРШЁН!\n\n"
                + s.zone.icon + " " + s.zone.name + "\n"
                + "📍 Этапов: " + s.stage + "/" + s.maxStages + "\n"
                + "❤ HP: " + s.hp + "/" + s.maxHp + "\n"
                + "💰 Золото: " + s.gold + "\n"
                + "⭐ Репутация: +" + baseRep + "\n";

        if (!found.isEmpty()) {
            msg += "\n🎁 НАЙДЕНЫ ЧАСТИ СЕТА:\n";
            for (String p : found) msg += "  ✦ " + p + "\n";
        }
        if (!s.earnedResources.isEmpty()) {
            msg += "\n📦 РЕСУРСЫ:\n";
            for (Map.Entry<String, Integer> r : s.earnedResources.entrySet()) {
                msg += "  " + r.getKey() + " x" + r.getValue() + "\n";
            }
        }

        msg += "\n🎉 Нажми «Забрать награды» чтобы получить!";
        sendWithKb(vkId, msg, Keyboards.adventureComplete());
    }

    private void claimRewards(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.MENU;

        // Сетовые части в тайник
        for (String pieceName : s.earnedPieces) {
            SetPiece piece = findPiece(pieceName);
            if (piece != null) {
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(piece.material, 1);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§d✦ " + piece.name + " §7[" + piece.setId + "]");
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Часть сета: " + piece.setId + " (" + piece.slot + ")");
                    lore.add("§7Зона: " + piece.zone.name);
                    lore.add("§7Редкость: " + piece.rarity + "/7");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                java.util.UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
                if (uuid != null) {
                    plugin.getStashManager().addItems(uuid, Collections.singletonList(item));
                }
            }
        }

        // Ресурсы в тайник
        for (Map.Entry<String, Integer> res : s.earnedResources.entrySet()) {
            ResourceType rt = findResource(res.getKey());
            if (rt != null) {
                java.util.UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
                if (uuid != null) {
                    List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
                    int amount = Math.min(res.getValue(), 64);
                    items.add(new org.bukkit.inventory.ItemStack(rt.material, amount));
                    plugin.getStashManager().addItems(uuid, items);
                }
            }
        }

        s.earnedPieces.clear();
        s.earnedResources.clear();

        showMenu(vkId, "🎁 Награды отправлены в тайник! Используй /stash в игре.");
    }

    private void handleDeath(int vkId) {
        AdventureState s = states.get(vkId);
        s.state = State.DEAD;

        double penalty = plugin.getConfig().getDouble("general.death-penalty", 0.15);
        int repLoss = (int)(s.repEarned * penalty);
        s.repEarned -= repLoss;

        sendWithKb(vkId, "💀 ВЫ ПОГИБЛИ!\n\n"
                + "Потеряно: " + repLoss + " репутации\n"
                + "Используйте лечение чтобы восстановиться.", Keyboards.afterCombatLose());
    }

    private void healPlayer(int vkId) {
        AdventureState s = states.get(vkId);
        s.hp = s.maxHp;
        s.energy = s.maxEnergy;
        s.state = State.MENU;
        showMenu(vkId, "💚 Полностью исцелены! Возвращайтесь в приключения.");
    }

    // ===== СТАТУС И СТАТИСТИКА =====
    private void showStatus(int vkId) {
        AdventureState s = states.get(vkId);
        if (s == null || s.state == State.MENU) {
            sendMsg(vkId, "❌ Нет активного приключения.");
            return;
        }
        String msg = "📊 СТАТУС ПОХОДА\n\n"
                + s.zone.icon + " " + s.zone.name + " | Этап " + s.stage + "/" + s.maxStages + "\n"
                + "❤ HP: " + s.hp + "/" + s.maxHp + "\n"
                + "⚡ Энергия: " + s.energy + "/" + s.maxEnergy + "\n"
                + "🥫 Припасы: " + s.supplies + " | 💰 Золото: " + s.gold + "\n"
                + "⭐ Репа: " + s.repEarned;
        sendMsg(vkId, msg);
    }

    private void showStats(int vkId) {
        int level = playerData.getLevel(vkId);
        int xp = playerData.getXp(vkId);
        int xpNeed = playerData.getXpNeeded(vkId);
        int kills = playerData.getEnemiesKilled(vkId);
        int bosses = playerData.getBossesKilled(vkId);
        int adv = playerData.getAdventuresCompleted(vkId);

        String msg = "📊 СТАТИСТИКА ГЕРОЯ\n\n"
                + "⭐ Уровень: " + level + "\n"
                + "✨ XP: " + xp + "/" + xpNeed + "\n"
                + "💀 Врагов убито: " + kills + "\n"
                + "☠ Боссов убито: " + bosses + "\n"
                + "🗺 Походов завершено: " + adv + "\n\n"
                + "🎒 Частей сетов собрано: " + playerData.getCollectedPieces(vkId).size();
        sendMsg(vkId, msg);
    }

    private void showInfo(int vkId) {
        String msg = "📜 ОБ ИГРЕ\n\n"
                + "⚔ Приключения — оффлайн RPG через ВК бота.\n\n"
                + "🗺 6 зон с уникальными врагами и боссами.\n"
                + "⚔ 5 классов с навыками.\n"
                + "🛡 6 сетов брони (24 части).\n"
                + "📦 Ресурсы и золото.\n"
                + "⭐ Репутация и уровни.\n\n"
                + "Команды:\n"
                + "!adv — главное меню\n"
                + "!adv hero — меню героя\n"
                + "!adv stats — статистика\n"
                + "/stash — тайник в игре";
        sendMsg(vkId, msg);
    }

    // ===== ЛАВКА =====
    private void showShop(int vkId, String[] args) {
        shop.handleShop(vkId, args, this);
    }

    private void showBag(int vkId) {
        List<String> pieces = playerData.getCollectedPieces(vkId);
        String msg = "🎒 СОБРАННЫЕ ЧАСТИ СЕТОВ\n\n";
        if (pieces.isEmpty()) {
            msg += "Пока ничего не собрано.\n";
        } else {
            Map<String, List<String>> bySet = new LinkedHashMap<>();
            for (String p : pieces) {
                SetPiece sp = findPiece(p);
                if (sp != null) bySet.computeIfAbsent(sp.setId, k -> new ArrayList<>()).add(sp.slot);
            }
            for (Map.Entry<String, List<String>> e : bySet.entrySet()) {
                msg += "🛡 Сет " + e.getKey() + ": " + e.getValue().size() + "/4\n";
                for (String slot : e.getValue()) msg += "  ✓ " + slot + "\n";
            }
        }
        msg += "\n💰 Ресурсы:\n";
        for (ResourceType rt : ResourceType.values()) {
            int count = playerData.getResource(vkId, rt.name());
            if (count > 0) msg += "  " + rt.icon + " " + rt.name + " x" + count + "\n";
        }
        sendWithKb(vkId, msg, Keyboards.heroMenu());
    }

    private void handleConfirm(int vkId, String[] args) {
        if (args.length < 3) return;
        shop.handleConfirm(vkId, args[2], this);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ =====
    private Zone findZone(String id) {
        try { return Zone.valueOf(id.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private List<EnemyType> getZoneEnemies(Zone zone, boolean boss) {
        List<EnemyType> result = new ArrayList<>();
        for (EnemyType e : EnemyType.values()) {
            String eZone = getEnemyZone(e);
            boolean isBoss = e.name.contains("BOSS") || e.name.contains("LORD")
                    || e.name.contains("DRAGON") || e.name.contains("Хранитель")
                    || e.name.contains("Повелитель") || e.name.contains("Владыка")
                    || e.name.contains("Император");
            if (eZone != null && eZone.equals(zone.name()) && isBoss == boss) {
                result.add(e);
            }
        }
        if (result.isEmpty()) result.add(boss ? EnemyType.TREANT_BOSS : EnemyType.WOLF);
        return result;
    }

    private String getEnemyZone(EnemyType enemy) {
        switch (enemy) {
            case WOLF: case SPIDER: case ENT: case TREANT_BOSS:
                return "🌲 Тёмный лес";
            case SKELETON: case CAVE_SPIDER: case GOLEM: case WORM_BOSS:
                return "⛏ Глубокие шахты";
            case ZOMBIE: case GHOST: case GUARDIAN: case LICH_BOSS:
                return "🏛 Древние руины";
            case BLAZE: case PIGLIN: case WITHER_SKELETON: case NETHER_LORD:
                return "🔥 Незер-пустоши";
            case STRAY: case ICE_GOLEM: case FROST_WYRM: case FROST_DRAGON:
                return "❄️ Ледяная тундра";
            case ENDERMITE: case VOID_WALKER: case SHADOW: case VOID_LORD:
                return "🌑 Грань Бездны";
            default: return null;
        }
    }

    private SetPiece randomPiece(Zone zone) {
        List<SetPiece> zonePieces = new ArrayList<>();
        for (SetPiece p : SetPiece.values()) {
            if (p.zone == zone) zonePieces.add(p);
        }
        return zonePieces.isEmpty() ? null : zonePieces.get(rnd.nextInt(zonePieces.size()));
    }

    private SetPiece findPiece(String name) {
        for (SetPiece p : SetPiece.values()) if (p.name.equals(name)) return p;
        return null;
    }

    private ResourceType findResource(String name) {
        for (ResourceType rt : ResourceType.values()) if (rt.name().equals(name)) return rt;
        return null;
    }

    private void addRandomResource(int vkId, AdventureState s) {
        ResourceType[] pool = ResourceType.values();
        ResourceType rt = pool[rnd.nextInt(Math.min(6, pool.length))];
        int amount = 1 + rnd.nextInt(3 + s.zone.difficulty);
        s.earnedResources.merge(rt.name(), amount, Integer::sum);
        playerData.addResource(vkId, rt.name(), amount);
    }

    public AdventureState getState(int vkId) { return states.get(vkId); }
    public PlayerData getPlayerData() { return playerData; }
    public CombatManager getCombat() { return combat; }
    public Map<Integer, AdventureState> getStates() { return states; }

    public void sendMsg(int vkId, String msg) {
        try { VKChatPlugin.getInstance().getApi().sendMessage(vkId, msg); } catch (Exception ignored) {}
    }

    /**
     * Отправить одно сообщение с текстом И клавиатурой
     */
    public void sendWithKb(int vkId, String text, String kb) {
        try { VKChatPlugin.getInstance().getApi().sendKeyboard(vkId, text, kb); } catch (Exception ignored) {}
    }
}
