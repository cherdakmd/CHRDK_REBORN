package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.StashManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный менеджер офлайн-походов v4.0
 * Конечный автомат состояний
 */
public class AdventureManager implements Listener {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<Integer, Long> injuries = new ConcurrentHashMap<>();
    private final Random rand = new Random();

    // Состояния игрока
    public enum State {
        MENU,           // Главное меню
        ROUTE_SELECT,   // Выбор маршрута
        IN_ADVENTURE,   // В походе (ожидание события)
        CHOICE,         // Выбор действия
        COMBAT,         // Бой
        RESULT,         // Результат
        DEAD,           // Мёртв
        VICTORY         // Победа
    }

    // Данные игрока
    public static class PlayerState {
        public State state = State.MENU;
        public String route = "";
        public int stage = 0;
        public int maxStages = 3;
        public int hp = 100;
        public int maxHp = 100;
        public int supplies = 5;
        public int morale = 100;
        public int gold = 0;
        public int xp = 0;
        public boolean waitingChoice = false;
        public String eventType = "";
        public String eventTitle = "";
        public long choiceDeadline = 0;
        public long nextEventTime = 0;

        // Бой
        public String enemyName = "";
        public int enemyHp = 0;
        public int enemyMaxHp = 0;
        public int enemyAtk = 0;
        public int enemyDef = 0;
        public int round = 0;
        public int maxRounds = 5;
        public boolean isBoss = false;
    }

    public AdventureManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    // ═══ ОБРАБОТКА КОМАНД ═══
    public void handleCommand(int vkId, String cmd, String[] args) {
        PlayerState state = states.computeIfAbsent(vkId, k -> new PlayerState());

        switch (cmd) {
            case "!поход": case "!походы":
                showMenu(vkId);
                break;
            case "!пойти":
                if (args.length > 0) startAdventure(vkId, args[0]);
                break;
            case "!выбор":
                if (args.length > 0) handleChoice(vkId, args[0]);
                break;
            case "!статус":
                showStatus(vkId);
                break;
            case "!герой":
                showHeroMenu(vkId);
                break;
            case "!характеристики": case "!статы":
                sendMessage(vkId, "📊 Характеристики пока в разработке!");
                break;
            case "!бой":
                if (state.state == State.COMBAT) {
                    showCombatUI(vkId);
                } else {
                    sendMessage(vkId, "❌ Нет активного боя.");
                }
                break;
            case "!продолжить":
                if (state.state == State.RESULT) {
                    advanceStage(vkId);
                }
                break;
            case "!забрать":
                if (state.state == State.VICTORY) {
                    claimRewards(vkId);
                }
                break;
            case "!лечиться":
                healPlayer(vkId);
                break;
        }
    }

    // ═══ ПОКАЗ МЕНЮ ═══
    private void showMenu(int vkId) {
        PlayerState state = states.get(vkId);
        if (state == null) state = new PlayerState();

        // Проверка кулдауна
        if (injuries.containsKey(vkId) && System.currentTimeMillis() < injuries.get(vkId)) {
            long left = (injuries.get(vkId) - System.currentTimeMillis()) / 60000;
            sendMessage(vkId, "❌ Ты ранен! Подожди " + left + " мин.");
            sendKeyboard(vkId, "Ранен", Keyboards.afterDefeat());
            return;
        }

        state.state = State.MENU;
        states.put(vkId, state);

        String msg = "⛺ CHRDK ADVENTURES\n\n" +
                "Выбери маршрут:\n" +
                "🌲 Лес — легко\n" +
                "⛏ Шахты — средне\n" +
                "🏛 Руины — сложно\n" +
                "🌿 Болота — яд\n" +
                "🏰 Замок — очень сложно\n" +
                "🔥 Незер — экстрим\n\n" +
                "Нажми кнопку!";

        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Выбери маршрут", Keyboards.routeSelection());
    }

    // ═══ НАЧАЛО ПОХОДА ═══
    private void startAdventure(int vkId, String route) {
        PlayerState state = states.getOrDefault(vkId, new PlayerState());

        state.state = State.IN_ADVENTURE;
        state.route = route;
        state.stage = 0;
        state.maxStages = 3;
        state.hp = 100;
        state.maxHp = 100;
        state.supplies = 5;
        state.morale = 100;
        state.gold = 0;
        state.xp = 0;
        state.nextEventTime = System.currentTimeMillis() + 10000;

        states.put(vkId, state);

        String msg = "🚶 Поход начат!\n\n" +
                "📍 " + route + "\n" +
                "❤️ HP: " + state.hp + "/" + state.maxHp + "\n" +
                "🥫 Припасы: " + state.supplies + "\n" +
                "📍 Этап: 0/" + state.maxStages + "\n\n" +
                "⏳ Первый выбор появится скоро...";

        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Поход начат!", Keyboards.adventureChoices());
    }

    // ═══ СОЗДАНИЕ СОБЫТИЯ ═══
    private void createEvent(PlayerState state) {
        String[] types = {"combat", "combat", "trap", "survival", "treasure", "combat"};
        String type = state.stage >= state.maxStages ? "boss" : types[rand.nextInt(types.length)];

        String title = getEventTitle(type);
        state.eventType = type;
        state.eventTitle = title;
        state.waitingChoice = true;
        state.choiceDeadline = System.currentTimeMillis() + 300000;
        state.state = State.CHOICE;
    }

    // ═══ ОБРАБОТКА ВЫБОРА ═══
    private void handleChoice(int vkId, String choiceStr) {
        PlayerState state = states.get(vkId);
        if (state == null || state.state != State.CHOICE) {
            sendMessage(vkId, "❌ Нет активного выбора.");
            return;
        }

        int choice;
        try { choice = Integer.parseInt(choiceStr); } catch (Exception e) { choice = 1; }

        // Если бой — запускаем бой
        if (state.eventType.equals("combat") || state.eventType.equals("boss")) {
            startCombat(vkId, choice);
            return;
        }

        // Не-боевое событие
        resolveNonCombat(vkId, choice);
    }

    // ═══ НАЧАЛО БОЯ ═══
    private void startCombat(int vkId, int choice) {
        PlayerState state = states.get(vkId);
        state.state = State.COMBAT;
        state.waitingChoice = false;

        // Создаём врага
        state.enemyName = state.eventTitle;
        state.enemyMaxHp = 50 + (state.stage * 20) + (state.isBoss ? 100 : 0);
        state.enemyHp = state.enemyMaxHp;
        state.enemyAtk = 5 + (state.stage * 2);
        state.enemyDef = 2 + state.stage;
        state.round = 1;
        state.maxRounds = state.isBoss ? 10 : 5;

        showCombatUI(vkId);
    }

    // ═══ ПОКАЗ ИНТЕРФЕЙСЯ БОЯ ═══
    private void showCombatUI(int vkId) {
        PlayerState state = states.get(vkId);

        String msg = "═══════════════════════════════════════\n" +
                "⚔️ БОЙ: " + state.enemyName + "\n" +
                "═══════════════════════════════════════\n\n" +
                "❤️ Вы: " + state.hp + "/" + state.maxHp + " HP\n" +
                "   " + getHpBar(state.hp, state.maxHp, 20) + "\n\n" +
                "❤️ Враг: " + state.enemyHp + "/" + state.enemyMaxHp + " HP\n" +
                "   " + getHpBar(state.enemyHp, state.enemyMaxHp, 20) + "\n\n" +
                "┌─────────────────────────────────────┐\n" +
                "│ Раунд " + state.round + "/" + state.maxRounds + "                           │\n" +
                "│ [1] ⚔️ Атака  [2] 🛡️ Защита        │\n" +
                "│ [3] 🔥 Способность  [4] 🧪 Зелье   │\n" +
                "│ [5] 🏃 Побег                        │\n" +
                "└─────────────────────────────────────┘";

        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Бой!", Keyboards.combatActions());
    }

    // ═══ ОБРАБОТКА БОЕВОГО ДЕЙСТВИЯ ═══
    public void handleCombatAction(int vkId, int action) {
        PlayerState state = states.get(vkId);
        if (state == null || state.state != State.COMBAT) return;

        String result = "";
        boolean playerDefending = false;

        // Ход игрока
        switch (action) {
            case 1: // Атака
                int playerDmg = Math.max(1, (10 + rand.nextInt(5)) - state.enemyDef);
                state.enemyHp -= playerDmg;
                result += "⚔️ Вы наносите удар! → " + playerDmg + " урона!\n";
                break;
            case 2: // Защита
                playerDefending = true;
                result += "🛡️ Вы в защитной стойке!\n";
                break;
            case 3: // Способность
                int skillDmg = Math.max(1, (15 + rand.nextInt(8)) - state.enemyDef);
                state.enemyHp -= skillDmg;
                result += "🔥 Способность! → " + skillDmg + " урона!\n";
                break;
            case 4: // Зелье
                int heal = 20 + rand.nextInt(10);
                state.hp = Math.min(state.maxHp, state.hp + heal);
                result += "🧪 Зелье! → +" + heal + " HP!\n";
                break;
            case 5: // Побег
                if (!state.isBoss && rand.nextInt(100) < 30) {
                    result += "🏃 Вы сбежали!\n";
                    state.state = State.RESULT;
                    sendMessage(vkId, result);
                    sendKeyboard(vkId, "Побег", Keyboards.afterChoice());
                    return;
                }
                result += "🏃 Побег не удался!\n";
                break;
        }

        // Проверка победы
        if (state.enemyHp <= 0) {
            state.state = State.VICTORY;
            result += "\n☠️ Враг повержен!\n";
            result += "🏆 +" + (10 + state.stage * 5) + " репутации\n";
            result += "✨ +" + (8 + state.stage * 3) + " XP\n";
            result += "🪙 +" + (1 + rand.nextInt(3)) + " золота\n";

            sendMessage(vkId, result);
            sendKeyboard(vkId, "Победа!", Keyboards.afterVictory());
            return;
        }

        // Ход врага
        int enemyDmg = Math.max(1, (state.enemyAtk + rand.nextInt(3)) - (playerDefending ? 5 : 0));
        if (rand.nextDouble() < 0.1) { // 10% уклонение
            result += "💨 Вы уклонились!";
        } else {
            state.hp -= enemyDmg;
            result += "💥 Враг атакует! → " + enemyDmg + " урона!";
        }

        // Проверка смерти
        if (state.hp <= 0) {
            state.state = State.DEAD;
            result += "\n\n💀 Вы погибли!";
            sendMessage(vkId, result);
            sendKeyboard(vkId, "Поражение", Keyboards.afterDefeat());
            injuries.put(vkId, System.currentTimeMillis() + 43200000); // 12 часов
            return;
        }

        // Следующий раунд
        state.round++;
        if (state.round > state.maxRounds) {
            state.state = State.DEAD;
            result += "\n\n⏰ Время боя истекло!";
            sendMessage(vkId, result);
            sendKeyboard(vkId, "Время вышло", Keyboards.afterDefeat());
            return;
        }

        // Показать результат и следующий раунд
        result += "\n\nРаунд " + state.round + "/" + state.maxRounds;
        sendMessage(vkId, result);
        showCombatUI(vkId);
    }

    // ═══ НЕ-БОЕВОЕ СОБЫТИЕ ═══
    private void resolveNonCombat(int vkId, int choice) {
        PlayerState state = states.get(vkId);
        state.waitingChoice = false;

        int roll = rand.nextInt(20) + 1;
        boolean success = roll >= 10;

        String msg = "✅ Выбор принят\n\n";
        msg += "🎲 " + state.eventTitle + "\n";
        msg += "🎲 d20: " + roll + " vs DC 10\n\n";

        if (success) {
            int rep = 10 + state.stage * 5;
            int xp = 8 + state.stage * 3;
            state.xp += xp;
            state.gold += rand.nextInt(5) + 1;
            state.morale = Math.min(100, state.morale + 5);

            try { VKChatPlugin.getInstance().getApi().addReputation(vkId, rep); } catch (Exception ignored) {}

            msg += "✨ Успех!\n";
            msg += "💚 +" + rep + " репутации | +" + xp + " XP\n";
            msg += "🪙 +" + state.gold + " золота";
        } else {
            int damage = 5 + rand.nextInt(10);
            state.hp -= damage;
            state.morale = Math.max(0, state.morale - 10);

            msg += "💥 Неудача!\n";
            msg += "🩸 -" + damage + " HP\n";
            msg += "❤️ HP: " + state.hp + "/" + state.maxHp;

            if (state.hp <= 0) {
                state.state = State.DEAD;
                msg += "\n\n💀 Вы погибли!";
                sendMessage(vkId, msg);
                sendKeyboard(vkId, "Поражение", Keyboards.afterDefeat());
                injuries.put(vkId, System.currentTimeMillis() + 43200000);
                return;
            }
        }

        state.state = State.RESULT;
        sendMessage(vkId, msg);
        sendKeyboard(vkId, "Результат", Keyboards.afterChoice());
    }

    // ═══ ПРОДОЛЖЕНИЕ ПОХОДА ═══
    private void advanceStage(int vkId) {
        PlayerState state = states.get(vkId);
        state.stage++;

        if (state.stage >= state.maxStages) {
            // Поход завершён
            state.state = State.VICTORY;
            int totalRep = 50 + state.stage * 20 + state.gold;

            try { VKChatPlugin.getInstance().getApi().addReputation(vkId, totalRep); } catch (Exception ignored) {}

            String msg = "✅ Поход завершён!\n\n" +
                    "📍 Этапов: " + state.stage + "/" + state.maxStages + "\n" +
                    "❤️ HP: " + state.hp + "/" + state.maxHp + "\n" +
                    "💰 Золото: " + state.gold + "\n" +
                    "✨ XP: " + state.xp + "\n\n" +
                    "🏆 Награда: +" + totalRep + " репутации";

            sendMessage(vkId, msg);
            sendKeyboard(vkId, "Успех!", Keyboards.afterAdventure());
        } else {
            // Следующее событие
            state.state = State.IN_ADVENTURE;
            state.nextEventTime = System.currentTimeMillis() + 30000;
            sendMessage(vkId, "📍 Этап: " + state.stage + "/" + state.maxStages + "\n⏳ Следующее событие через 30 сек...");
            sendKeyboard(vkId, "Поход", Keyboards.adventureChoices());
        }
    }

    // ═══ ЗАБОР НАГРАД ═══
    private void claimRewards(int vkId) {
        PlayerState state = states.get(vkId);
        state.state = State.MENU;

        sendMessage(vkId, "🎁 Награды получены! Проверь инвентарь.");
        sendKeyboard(vkId, "Меню", Keyboards.routeSelection());
    }

    // ═══ ЛЕЧЕНИЕ ═══
    private void healPlayer(int vkId) {
        PlayerState state = states.get(vkId);
        if (state == null) return;

        if (state.hp >= state.maxHp) {
            sendMessage(vkId, "✅ Ты полностью здоров!");
        } else {
            int heal = state.maxHp - state.hp;
            state.hp = state.maxHp;
            sendMessage(vkId, "💚 Вылечен! +" + heal + " HP");
        }
        sendKeyboard(vkId, "Герой", Keyboards.heroMenu());
    }

    // ═══ ПОКАЗ СТАТУСА ═══
    private void showStatus(int vkId) {
        PlayerState state = states.get(vkId);
        if (state == null || state.state == State.MENU) {
            sendMessage(vkId, "❌ Нет активного похода.");
            return;
        }

        String msg = "📊 Статус похода\n\n" +
                "📍 " + state.route + " | Этап: " + state.stage + "/" + state.maxStages + "\n" +
                "❤️ HP: " + state.hp + "/" + state.maxHp + "\n" +
                "🥫 Припасы: " + state.supplies + "\n" +
                "🧠 Мораль: " + state.morale + "%\n" +
                "🪙 Золото: " + state.gold + "\n" +
                "✨ XP: " + state.xp;

        sendMessage(vkId, msg);
    }

    // ═══ МЕНЮ ГЕРОЯ ═══
    private void showHeroMenu(int vkId) {
        sendMessage(vkId, "👤 Меню героя\n\nВыбери раздел:");
        sendKeyboard(vkId, "Герой", Keyboards.heroMenu());
    }

    // ═══ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ═══
    private String getEventTitle(String type) {
        switch (type) {
            case "combat": return "⚔ Бой с противником";
            case "boss": return "☠ БОСС!";
            case "trap": return "🪤 Ловушка";
            case "survival": return "🌿 Выживание";
            case "treasure": return "💰 Сокровище";
            default: return "🎲 Событие";
        }
    }

    private String getHpBar(int current, int max, int length) {
        int filled = (int)((double) current / max * length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) bar.append(i < filled ? "█" : "░");
        return bar.toString();
    }

    private void sendMessage(int vkId, String msg) {
        try { VKChatPlugin.getInstance().getApi().sendMessage(vkId, msg); } catch (Exception ignored) {}
    }

    private void sendKeyboard(int vkId, String title, String keyboard) {
        try { VKChatPlugin.getInstance().getApi().sendKeyboard(vkId, title, keyboard); } catch (Exception ignored) {}
    }

    // ═══ ТИК ═══
    private void startTickTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<Integer, PlayerState> entry : states.entrySet()) {
                    PlayerState state = entry.getValue();
                    if (state.state == State.IN_ADVENTURE && now >= state.nextEventTime) {
                        createEvent(state);
                        showChoiceUI(entry.getKey());
                    } else if (state.state == State.CHOICE && state.waitingChoice && now >= state.choiceDeadline) {
                        resolveNonCombat(entry.getKey(), rand.nextInt(4) + 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    private void showChoiceUI(int vkId) {
        PlayerState state = states.get(vkId);
        String msg = "⚠ Выбор в походе\n\n" +
                "📍 " + state.route + " | Этап: " + (state.stage + 1) + "/" + state.maxStages + "\n" +
                "❤️ HP: " + state.hp + "/" + state.maxHp + "\n" +
                "🥫 " + state.supplies + "   🧠 " + state.morale + "%\n\n" +
                "🎲 " + state.eventTitle + "\n" +
                "⏳ Ответ: ~300 сек.";

        sendMessage(vkId, msg);

        if (state.eventType.equals("combat") || state.eventType.equals("boss")) {
            sendKeyboard(vkId, "Бой!", Keyboards.combatActions());
        } else {
            sendKeyboard(vkId, "Выбор", Keyboards.adventureChoices());
        }
    }

    public void saveAll() {}
}
