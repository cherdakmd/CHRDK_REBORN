package ru.example.vkchatoffline.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.combat.CombatManager;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный менеджер офлайн-походов
 */
public class AdventureManager implements Listener {
    private final VKChatOfflinePlugin plugin;
    private final File file;
    private FileConfiguration data;
    private final Map<Integer, ActiveAdventure> active = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<Integer, Long> injuries = new ConcurrentHashMap<>();

    public static class ActiveAdventure {
        public int vkId;
        public String playerName, route;
        public long startTime, nextEventTime, choiceDeadline;
        public int stage, maxStages, hp, maxHp, supplies, morale, xpGained, gold;
        public boolean waitingChoice;
        public String pendingType, pendingTitle;
        public ActiveAdventure(int vkId, String playerName, String route) {
            this.vkId = vkId; this.playerName = playerName; this.route = route;
            this.startTime = System.currentTimeMillis();
            this.hp = 100; this.maxHp = 100; this.supplies = 5; this.morale = 100;
            this.stage = 0; this.maxStages = 3; this.waitingChoice = false;
        }
    }

    public AdventureManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "adventures.yml");
        loadAll();
        startTickTask();
    }

    public boolean isActiveAdventure(int vkId) { return active.containsKey(vkId); }

    // ═══ ПОКАЗ МЕНЮ ВЫБОРА МАРШРУТА ═══
    public void showMenu(int vkId) {
        String msg = "⛺ CHRDK ADVENTURES\n\n" +
                "Выбери маршрут для похода:\n\n" +
                "🌲 Лес — легкий, для новичков\n" +
                "⛏ Шахты — средний, много ресурсов\n" +
                "🏛 Руины — сложный, ценный лут\n" +
                "🌿 Болота — опасный, яд\n" +
                "🏰 Замок — очень сложный\n" +
                "🔥 Незер — экстрим\n\n" +
                "Нажми кнопку для выбора!";

        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Выбери маршрут", OfflineKeyboardFactory.routeSelection());
    }

    // ═══ НАЧАЛО ПОХОДА ═══
    public void startAdventure(int vkId, String route) {
        if (active.containsKey(vkId)) {
            sendMessage(vkId, "❌ У тебя уже есть активный поход!");
            sendKeyboard(vkId, "Поход активен", OfflineKeyboardFactory.adventureChoices());
            return;
        }
        if (injuries.containsKey(vkId) && System.currentTimeMillis() < injuries.get(vkId)) {
            long left = (injuries.get(vkId) - System.currentTimeMillis()) / 60000;
            sendMessage(vkId, "❌ Ты ранен! Подожди " + left + " мин.");
            sendKeyboard(vkId, "Ранен", OfflineKeyboardFactory.afterDefeat());
            return;
        }

        ActiveAdventure adv = new ActiveAdventure(vkId, "Игрок", route);
        adv.nextEventTime = System.currentTimeMillis() + 10000;
        active.put(vkId, adv);

        String msg = "🚶 Поход начат!\n\n" +
                "📍 " + route + "\n" +
                "👤 " + adv.playerName + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 Припасы: " + adv.supplies + "\n" +
                "📍 Этап: 0/" + adv.maxStages + "\n\n" +
                "⏳ Первый выбор появится скоро...";

        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Поход начат!", OfflineKeyboardFactory.adventureChoices());
        saveAll();
    }

    // ═══ СОЗДАНИЕ СОБЫТИЯ ═══
    private void createEvent(ActiveAdventure adv) {
        String[] types = {"combat", "combat", "trap", "survival", "treasure", "encounter", "combat", "boss"};
        String type = adv.stage >= adv.maxStages ? "boss" : types[new Random().nextInt(types.length)];

        String title = getEventTitle(type);
        adv.pendingType = type;
        adv.pendingTitle = title;
        adv.waitingChoice = true;
        adv.choiceDeadline = System.currentTimeMillis() + 300000;

        String msg = "⚠ Выбор в походе\n\n" +
                "📍 " + adv.route + " | Этап: " + (adv.stage + 1) + "/" + adv.maxStages + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 " + adv.supplies + "   🧠 " + adv.morale + "%\n\n" +
                "🎲 " + title + "\n" +
                "⏳ Ответ: ~300 сек.\n\n" +
                "Выбери действие кнопкой!";

        sendMessage(adv.vkId, msg);

        // Клавиатура зависит от типа события
        if (isCombatEvent(type)) {
            sendKeyboard(adv.vkId, "Бой!", OfflineKeyboardFactory.combatActions());
        } else {
            sendKeyboard(adv.vkId, "Выбор", OfflineKeyboardFactory.adventureChoices());
        }
    }

    // ═══ ОБРАБОТКА ВЫБОРА ═══
    public void handleChoice(int vkId, String[] args) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null || !adv.waitingChoice) {
            sendMessage(vkId, "❌ Нет активного выбора.");
            return;
        }

        int choice;
        try { choice = Integer.parseInt(args[1]); } catch (Exception e) { choice = 1; }

        // Если это бой — запускаем CombatManager
        if (isCombatEvent(adv.pendingType)) {
            String enemyName = adv.pendingTitle != null ? adv.pendingTitle : "Противник";
            int enemyLevel = 5 + adv.stage * 3;
            boolean isBoss = adv.pendingType.equals("boss") || adv.stage >= adv.maxStages;

            var encounter = plugin.getCombatManager().startCombat(vkId, enemyName, enemyLevel, isBoss);
            adv.waitingChoice = false;

            sendMessage(vkId, encounter.getCombatDescription());
            sendKeyboard(vkId, "Бой!", OfflineKeyboardFactory.combatActions());
            return;
        }

        // Для не-боевых событий — стандартная обработка
        resolveChoice(adv, choice, false);
    }

    // ═══ РЕШЕНИЕ НЕ-БОЕВОГО СОБЫТИЯ ═══
    private void resolveChoice(ActiveAdventure adv, int choice, boolean timeout) {
        Random rand = new Random();
        int roll = rand.nextInt(20) + 1;
        boolean success = roll >= 10;

        StringBuilder msg = new StringBuilder();
        if (timeout) msg.append("⏱ Авто-выбор\n\n");
        else msg.append("✅ Выбор принят\n\n");

        msg.append("🎲 ").append(adv.pendingTitle).append("\n");
        msg.append("🎲 d20: ").append(roll).append(" vs DC 10\n\n");

        if (success) {
            int repReward = 10 + adv.stage * 5;
            int xp = 8 + adv.stage * 3;
            adv.xpGained += xp;
            adv.gold += rand.nextInt(5) + 1;
            adv.morale = Math.min(100, adv.morale + 5);

            try { VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, repReward); } catch (Exception ignored) {}

            msg.append("✨ Исход: успех\n");
            msg.append("💚 +" + repReward + " репутации | +" + xp + " XP\n");
            msg.append("🪙 +" + adv.gold + " золота\n");
        } else {
            int damage = 5 + rand.nextInt(10);
            adv.hp -= damage;
            adv.morale = Math.max(0, adv.morale - 10);

            msg.append("💥 Исход: неудача\n");
            msg.append("🩸 Потеряно HP: " + damage + "\n");
            msg.append("❤️ Осталось: " + adv.hp + "/" + adv.maxHp + "\n");

            if (adv.hp <= 0) {
                killAdventure(adv, "Поражение в походе");
                sendMessage(adv.vkId, msg.toString());
                sendKeyboard(adv.vkId, "Поражение", OfflineKeyboardFactory.afterDefeat());
                return;
            }
        }

        adv.stage++;
        adv.waitingChoice = false;

        if (adv.stage >= adv.maxStages) {
            finishAdventure(adv);
        } else {
            adv.nextEventTime = System.currentTimeMillis() + 30000;
            msg.append("\n📍 Прогресс: ").append(adv.stage).append("/").append(adv.maxStages);
            sendMessage(adv.vkId, msg.toString());
            sendKeyboard(adv.vkId, "Продолжить", OfflineKeyboardFactory.afterChoice());
        }
    }

    // ═══ ОБРАБОТКА БОЕВЫХ ДЕЙСТВИЙ ═══
    public void handleCombatAction(int vkId, CombatManager.CombatAction action) {
        if (!plugin.getCombatManager().isInCombat(vkId)) {
            sendMessage(vkId, "❌ Нет активного боя.");
            return;
        }

        var result = plugin.getCombatManager().processAction(vkId, action);
        sendMessage(vkId, result.message);

        if (result.success && result.encounter != null) {
            // Бой продолжается
            sendMessage(vkId, result.encounter.getCombatDescription());
            sendKeyboard(vkId, "Раунд " + result.encounter.round, OfflineKeyboardFactory.combatActions());
        } else if (result.success && result.encounter == null) {
            // Победа
            sendMessage(vkId, result.getResultDescription());

            if (result.rewards != null) {
                int rep = result.rewards.getOrDefault("reputation", 0);
                int xp = result.rewards.getOrDefault("xp", 0);
                plugin.getRewardManager().grantAdventureReward(vkId, result.loot, rep, xp);

                ActiveAdventure adv = active.get(vkId);
                if (adv != null) {
                    adv.xpGained += xp;
                    adv.gold += result.rewards.getOrDefault("gold", 0);
                    adv.stage++;
                    adv.waitingChoice = false;

                    if (adv.stage >= adv.maxStages) {
                        finishAdventure(adv);
                    } else {
                        adv.nextEventTime = System.currentTimeMillis() + 5000;
                        sendMessage(vkId, "📍 Прогресс: " + adv.stage + "/" + adv.maxStages);
                        sendKeyboard(vkId, "Победа!", OfflineKeyboardFactory.afterVictory());
                    }
                }
            }
        } else {
            // Поражение
            sendMessage(vkId, result.getResultDescription());
            ActiveAdventure adv = active.get(vkId);
            if (adv != null) killAdventure(adv, "Поражение в бою");
            sendKeyboard(vkId, "Поражение", OfflineKeyboardFactory.afterDefeat());
        }
    }

    // ═══ ТИК — проверка событий ═══
    private void startTickTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (ActiveAdventure adv : active.values()) {
                    if (adv.waitingChoice) {
                        if (now >= adv.choiceDeadline) {
                            resolveChoice(adv, new Random().nextInt(4) + 1, true);
                        }
                    } else if (now >= adv.nextEventTime) {
                        createEvent(adv);
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    // ═══ ЗАВЕРШЕНИЕ ПОХОДА ═══
    private void finishAdventure(ActiveAdventure adv) {
        int baseRep = 50 + adv.stage * 20;
        int totalRep = baseRep + adv.gold;
        int xp = adv.xpGained;

        try { VKChatPlugin.getInstance().getApi().addReputation(adv.vkId, totalRep); } catch (Exception ignored) {}
        plugin.getCharacterManager().addXp(adv.vkId, xp);

        var loot = plugin.getLootManager().generateLoot(adv.stage * 5, adv.route, false);
        plugin.getRewardManager().grantAdventureReward(adv.vkId, loot, 0, 0);

        var chapter = plugin.getCampaignManager().getCurrentChapter(adv.vkId);
        if (chapter != null && adv.route.equalsIgnoreCase(chapter.route)) {
            plugin.getCampaignManager().completeChapter(adv.vkId, chapter.id);
            plugin.getRewardManager().grantChapterReward(adv.vkId, chapter.id);
        }

        String msg = "✅ Поход завершен!\n\n" +
                "📍 Этапов: " + adv.stage + "/" + adv.maxStages + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "💰 Золото: " + adv.gold + "\n" +
                "✨ XP: " + xp + "\n\n" +
                "🏆 Награда: +" + totalRep + " репутации\n" +
                "📦 Предметы в тайнике: /stash";

        sendMessage(adv.vkId, msg);
        sendKeyboard(adv.vkId, "Успех!", OfflineKeyboardFactory.afterAdventure());

        active.remove(adv.vkId);
        saveAll();
    }

    // ═══ СМЕРТЬ ═══
    private void killAdventure(ActiveAdventure adv, String reason) {
        cooldowns.put(adv.vkId, System.currentTimeMillis() + 14400000);
        injuries.put(adv.vkId, System.currentTimeMillis() + 43200000);
        active.remove(adv.vkId);
        saveAll();
    }

    // ═══ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ═══
    private boolean isCombatEvent(String type) {
        return type.equals("combat") || type.equals("boss") || type.equals("ambush");
    }

    private String getEventTitle(String type) {
        switch (type) {
            case "combat": return "⚔ Бой с противником";
            case "boss": return "☠ БОСС!";
            case "trap": return "🪤 Ловушка";
            case "survival": return "🌿 Выживание";
            case "treasure": return "💰 Сокровище";
            case "encounter": return "👤 Встреча";
            default: return "🎲 Событие";
        }
    }

    private void sendMessage(int vkId, String msg) {
        try { VKChatPlugin.getInstance().getApi().sendMessage(vkId, msg); } catch (Exception ignored) {}
    }

    private void sendKeyboard(int vkId, String title, String keyboard) {
        try { VKChatPlugin.getInstance().getApi().sendKeyboard(vkId, title, keyboard); } catch (Exception ignored) {}
    }

    // ═══ ПОКАЗ СТАТУСА ═══
    public void showStatus(int vkId) {
        ActiveAdventure adv = active.get(vkId);
        if (adv == null) { sendMessage(vkId, "❌ Нет активного похода."); return; }

        String msg = "📊 Статус похода\n\n" +
                "📍 " + adv.route + " | Этап: " + adv.stage + "/" + adv.maxStages + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 Припасы: " + adv.supplies + "\n" +
                "🧠 Мораль: " + adv.morale + "%\n" +
                "🪙 Золото: " + adv.gold + "\n" +
                "✨ XP: " + adv.xpGained;

        sendMessage(vkId, msg);
    }

    // ═══ ПОКАЗ ГЕРОЯ ═══
    public void showHero(int vkId) {
        String info = plugin.getCharacterManager().getCharacterInfo(vkId);
        sendMessage(vkId, info);
        sendKeyboard(vkId, "Герой", OfflineKeyboardFactory.heroMenu());
    }

    // ═══ ПОКАЗ НАВЫКОВ ═══
    public void showSkills(int vkId) {
        String info = plugin.getSkillTreeManager().getSkillTreeInfo(vkId);
        sendMessage(vkId, info);
        sendKeyboard(vkId, "Навыки", OfflineKeyboardFactory.skillTree());
    }

    // ═══ ПОКАЗ КАМПАНИИ ═══
    public void showCampaign(int vkId) {
        String info = plugin.getCampaignManager().getCampaignInfo(vkId);
        sendMessage(vkId, info);
        sendKeyboard(vkId, "Кампания", OfflineKeyboardFactory.campaignMenu());
    }

    // ═══ ОБРАБОТКА КОМАНД ═══
    public void handleCommand(int vkId, String cmd, String[] args) {
        switch (cmd) {
            case "!поход": case "!походы": showMenu(vkId); break;
            case "!пойти": if (args.length > 0) startAdventure(vkId, args[0]); break;
            case "!выбор": handleChoice(vkId, args); break;
            case "!статус": showStatus(vkId); break;
            case "!герой": showHero(vkId); break;
            case "!навыки": showSkills(vkId); break;
            case "!кампания": showCampaign(vkId); break;
            case "!характеристики": case "!статы":
                sendMessage(vkId, plugin.getCharacterManager().getCharacterInfo(vkId));
                break;
            case "!бой":
                if (plugin.getCombatManager().isInCombat(vkId)) {
                    var encounter = plugin.getCombatManager().getActiveCombat(vkId);
                    sendMessage(vkId, encounter.getCombatDescription());
                    sendKeyboard(vkId, "Бой", OfflineKeyboardFactory.combatActions());
                } else {
                    sendMessage(vkId, "❌ Нет активного боя.");
                }
                break;
            case "!продолжить":
                ActiveAdventure adv = active.get(vkId);
                if (adv != null && adv.waitingChoice) {
                    sendMessage(vkId, "⏳ Ожидание выбора...");
                    if (isCombatEvent(adv.pendingType)) {
                        sendKeyboard(vkId, "Бой", OfflineKeyboardFactory.combatActions());
                    } else {
                        sendKeyboard(vkId, "Выбор", OfflineKeyboardFactory.adventureChoices());
                    }
                } else {
                    sendMessage(vkId, "❌ Нет активного похода.");
                }
                break;
        }
    }

    // ═══ СОХРАНЕНИЕ/ЗАГРУЗКА ═══
    public void loadAll() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void saveAll() {
        try { data.save(file); } catch (Exception ignored) {}
    }
}
