package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatjobs.VKChatJobsPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.Expedition;
import ru.example.vkchatoffline.data.ExpeditionStorage;
import ru.example.vkchatoffline.data.Riddle;
import ru.example.vkchatoffline.utils.EventGenerator;
import ru.example.vkchatoffline.utils.EventResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Управление приключениями и походами.
 * Обрабатывает команды игроков и координирует генерацию событий.
 */
public class AdventureCommandManager {
    private final VKChatOfflinePlugin plugin;
    private final FileConfiguration config;
    private final Map<Integer, Expedition> activeExpeditions = new ConcurrentHashMap<>();
    private final Map<Integer, String> playerPets = new ConcurrentHashMap<>();
    private final ExpeditionStorage expeditionStorage;
    private final Map<Integer, EventResult> pendingEvents = new ConcurrentHashMap<>();
    private final List<Riddle> riddles = new ArrayList<>();
    private EventGenerator eventGenerator;

    public ExpeditionStorage getExpeditionStorage() {
        return expeditionStorage;
    }

    public AdventureCommandManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.expeditionStorage = new ExpeditionStorage();
        this.eventGenerator = new EventGenerator(config);
        this.playerPets.putAll(expeditionStorage.loadAllPets());
        this.activeExpeditions.putAll(expeditionStorage.loadAllExpeditions());
        loadRiddles();
        plugin.getLogger().info("Загружено походов: " + activeExpeditions.size() + ", питомцев: " + playerPets.size() + ", загадок: " + riddles.size());
        Bukkit.getScheduler().runTaskLater(plugin, this::restorePendingChoicesAfterLoad, 40L);
        startExpeditionTask();
    }

    private void loadRiddles() {
        riddles.clear();
        if (config.contains("riddles")) {
            List<Map<?, ?>> riddleList = config.getMapList("riddles");
            if (riddleList != null) {
                for (Map<?, ?> map : riddleList) {
                    String question = String.valueOf(map.get("question"));
                    @SuppressWarnings("unchecked")
                    List<String> answers = (List<String>) map.get("answers");
                    if (answers != null) {
                        answers = answers.stream().map(String::toLowerCase).map(String::trim).collect(java.util.stream.Collectors.toList());
                    }
                    String successReward = map.containsKey("success") ? String.valueOf(map.get("success")) : "";
                    String failReward = map.containsKey("fail") ? String.valueOf(map.get("fail")) : "";
                    riddles.add(new Riddle(question, answers, successReward, failReward));
                }
            }
        }
        if (riddles.isEmpty()) {
            riddles.add(new Riddle(
                "Что можно встретить один раз в минуте, два раза в моменте и ни разу в тысяче лет?",
                Arrays.asList("буква м", "м", "букву м"),
                "rep:100;item:EMERALD;2;5",
                "hp:25;rep:-20"
            ));
            riddles.add(new Riddle(
                "Зимой и летом одним цветом?",
                Arrays.asList("елка", "ель", "ёлка", "сосна"),
                "rep:80;item:GOLD_INGOT;1;3",
                "hp:15;rep:-10"
            ));
            riddles.add(new Riddle(
                "Какое вещество в Майнкрафте позволяет дышать под водой при варке зелий?",
                Arrays.asList("иглобрюх", "рыба-иглобрюх", "иглобрюха"),
                "rep:120;item:PRISMARINE_SHARD;1;3",
                "hp:30"
            ));
        }
    }

    /**
     * Инициализация после загрузки конфига.
     */
    public void reload() {
        this.eventGenerator = new EventGenerator(plugin.getConfig());
        loadRiddles();
    }

    /**
     * Проверка, находится ли игрок в походе.
     */
    public boolean isInExpedition(int vkId) {
        return activeExpeditions.containsKey(vkId);
    }

    /**
     * Получение экземпляра похода.
     */
    public Expedition getExpedition(int vkId) {
        return activeExpeditions.get(vkId);
    }

    /**
     * Обработка команды !поход.
     */
    public void handleExpeditionCommand(int peer, int sender, String[] args) {
        UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(sender);
        if (uuid == null) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Твой аккаунт не привязан к серверу Minecraft!");
            return;
        }

        // Проверка, онлайн ли игрок
        if (Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).isOnline()) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Ты находишься в игре! Команда !поход доступна только для оффлайн-игроков.");
            return;
        }

        // Проверка на смену
        ShiftManager shiftManager = plugin.getShiftManager();
        if (shiftManager != null && shiftManager.isWorking(sender)) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Ты сейчас на смене! Сначала дождись её окончания.");
            return;
        }

        // Проверка текущего похода
        if (activeExpeditions.containsKey(sender)) {
            Expedition exp = activeExpeditions.get(sender);
            if (exp.getEndTime() > 0) {
                long leftMillis = exp.getEndTime() - System.currentTimeMillis();
                if (leftMillis > 0) {
                    VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                            " Твой персонаж восстанавливается после тяжелых ран в лазарете.\n" +
                            "Осталось до выхода: " + formatDuration(leftMillis));
                    return;
                }
                untrackExpedition(sender);
            } else {
                long expeditionLeftMillis = exp.getExpeditionEndTime() - System.currentTimeMillis();
                if (exp.getExpeditionEndTime() > 0 && expeditionLeftMillis > 0) {
                    StringBuilder status = new StringBuilder();
                    status.append("⏳ Твой персонаж уже находится в походе!\n");
                    status.append("📍 Локация: ").append(getDungeonName(exp.getDungeonType())).append("\n");
                    status.append("📍 Этап: ").append(exp.getStage()).append("/").append(exp.getMaxStages()).append("\n");
                    status.append("❤️ Здоровье: ").append(exp.getHp()).append("/").append(exp.getMaxHp()).append("\n");
                    status.append("📊 Прогресс похода: ").append(getProgressPercent(exp)).append("%\n");
                    status.append("⏳ До конца похода: ").append(formatDuration(expeditionLeftMillis)).append("\n");
                    status.append("🕐 Ожидаемое завершение: ").append(formatClockTime(exp.getExpeditionEndTime()));
                    if (!exp.isWaitingChoice() && exp.getNextEventTime() > System.currentTimeMillis()) {
                        status.append("\n⏱ До следующего события: ")
                                .append(formatDuration(exp.getNextEventTime() - System.currentTimeMillis()));
                    } else if (exp.isWaitingChoice() && exp.getPendingEventTitle() != null) {
                        status.append("\n\n⚠️ Ждёт твоего выбора!\n");
                        status.append("📍 Этап ").append(exp.getStage()).append("/").append(exp.getMaxStages());
                        status.append(" — ").append(exp.getPendingEventTitle()).append("\n");
                        status.append("Ответь цифрой: 1 или 2");
                        if (exp.isInCombat()) {
                            status.append(" (или 3 при битве с боссом)");
                        }
                    }
                    VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender, status.toString());
                    return;
                }
                untrackExpedition(sender);
            }
        }

        showExpeditionMenu(peer, sender, uuid);
    }

    /**
     * Проверяет, активна ли синергия полного сета брони у игрока.
     * Защита (defense) >= 15 означает надетый полный комплект брони.
     */
    public boolean hasArmorSetBonus(UUID uuid) {
        int[] gear = expeditionStorage.loadPlayerGear(uuid);
        return gear[1] >= 15;
    }

    /**
     * Возвращает максимальное число локаций, доступных игроку в меню походов.
     * 4 — базовые локации; 7 — с разблокированными секретными сокровищницами синергии.
     */
    public int getMenuMaxChoice(UUID uuid) {
        return hasArmorSetBonus(uuid) ? 7 : 4;
    }

    /**
     * Показ меню выбора локации.
     */
    private void showExpeditionMenu(int peer, int sender, UUID uuid) {
        Expedition exp = new Expedition(peer, sender, uuid, "menu", 0);
        exp.setWaitingChoice(true);
        trackExpedition(exp);

        boolean hasSetBonus = hasArmorSetBonus(uuid);

        StringBuilder text = new StringBuilder("Выбери локацию для похода:\n" +
                "1. 🌲 Ближний Лес (Безопасно, 3 этапа, вход: 50 реп.)\n" +
                "2. ⛏ Заброшенные Шахты (Опасно, 5 этапов, вход: 150 реп.)\n" +
                "3. 🏰 Проклятый Замок (Смертельно, Босс, 7 этапов, вход: 400 реп.)\n" +
                "4. 🌋 Адские Врата (Смертельно+, Босс, 9 этапов, вход: 800 реп.)");

        if (hasSetBonus) {
            text.append("\n\n✨ [СИНЕРГИЯ СЕТА] Вам открыт доступ к секретным сокровищницам:\n")
                .append("5. 🪙 Царская Казна (6 этапов, сверх-добыча золота/алмазов, вход: 200 реп.)\n")
                .append("6. 📁 Спец-Лаборатория КГБ (6 этапов, секретные технологии, вход: 200 реп.)\n")
                .append("7. 🌿 Сокровищница Волхвов (6 этапов, редкие артефакты, вход: 200 реп.)");
        }

        int maxChoice = hasSetBonus ? 7 : 4;
        sendExpeditionMessage(peer, sender, text.toString(), maxChoice);
    }

    /**
     * Обработка выбора локации.
     */
    public void handleLocationChoice(int sender, int choice) {
        Expedition exp = activeExpeditions.get(sender);
        if (exp == null || !exp.isWaitingChoice() || !"menu".equals(exp.getDungeonType())) {
            return;
        }

        Expedition newExp;
        int cost = 50;
        if (choice == 1) {
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "forest", 3);
            cost = config.getInt("expedition-costs.forest", 50);
        } else if (choice == 2) {
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "mine", 5);
            cost = config.getInt("expedition-costs.mine", 150);
        } else if (choice == 3) {
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "castle", 7);
            cost = config.getInt("expedition-costs.castle", 400);
        } else if (choice == 4) {
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "nether", 9);
            cost = config.getInt("expedition-costs.nether", 800);
        } else if (choice == 5) { // Царская Казна
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "castle", 6);
            newExp.setPendingEventTitle("🪙 Царская Казна");
            cost = 200;
        } else if (choice == 6) { // Спец-Лаборатория КГБ
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "mine", 6);
            newExp.setPendingEventTitle("📁 Спец-Лаборатория КГБ");
            cost = 200;
        } else if (choice == 7) { // Сокровищница Волхвов
            newExp = new Expedition(exp.getPeerId(), sender, exp.getPlayerUuid(), "forest", 6);
            newExp.setPendingEventTitle("🌿 Сокровищница Волхвов");
            cost = 200;
        } else {
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender, "Неверный выбор.");
            return;
        }

        double discountFactor = getDiscountFactor(exp.getPlayerUuid());
        cost = (int) (cost * discountFactor);

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(sender);
        if (currentRep < cost) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                    "❌ Недостаточно репутации! Для этого похода требуется " + cost + " репутации (у тебя: " + currentRep + ").\n" +
                    "Выбери более легкую локацию или накопи репутацию на сменах.");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(sender, cost);

        int[] gear = expeditionStorage.loadPlayerGear(exp.getPlayerUuid());
        newExp.setBaseStats(gear[0], gear[1]);

        int totalMinutes = calculateRandomExpeditionMinutes();
        long expeditionEndTime = System.currentTimeMillis() + (totalMinutes * 60000L);

        newExp.setExpeditionEndTime(expeditionEndTime);
        newExp.setEstimatedTotalMinutes(totalMinutes);
        newExp.setHasPet(hasPet(sender));
        newExp.setPetType(getPet(sender));

        StringBuilder feedMsg = new StringBuilder();
        if (newExp.hasPet()) {
            boolean fed = consumeFoodFromStash(exp.getPlayerUuid());
            newExp.setPetFed(fed);
            if (fed) {
                feedMsg.append("\n🍖 Твой питомец (").append(newExp.getPetType()).append(") был накормлен лакомствами из твоего виртуального тайника! Его баффы активны.");
            } else {
                feedMsg.append("\n⚠️ В твоем тайнике нет еды (нужно 3 ед. еды, например, пшеницы/яблок/картофеля)! Твой питомец (").append(newExp.getPetType()).append(") проголодался и его баффы не будут работать в этом походе.");
            }
        }

        trackExpedition(newExp);
        String mention = getVkMention(sender);
        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                "🔔 " + mention + "\n" +
                "✅ Собраны припасы, оплачена пошлина (" + cost + " реп.) и поход начался!\n" +
                "📍 Локация: " + getDungeonName(newExp.getDungeonType()) + "\n" +
                "⏳ Продолжительность: ~" + totalMinutes + " минут\n" +
                "⏳ До конца похода: " + formatDuration(expeditionEndTime - System.currentTimeMillis()) + "\n" +
                "Ожидаемое завершение: " + formatClockTime(expeditionEndTime) +
                feedMsg.toString());

        // Проверка времени (ночь/день)
        try {
            long time = Bukkit.getWorlds().get(0).getTime();
            newExp.setNight(time > 13000 && time < 23000);
        } catch (Exception ignored) {}

        scheduleNextEvent(newExp, true);
    }

    /**
     * Запланировать следующее событие через задержку.
     */
    private void scheduleNextEvent(Expedition exp) {
        scheduleNextEvent(exp, false);
    }

    private void scheduleNextEvent(Expedition exp, boolean immediate) {
        exp.setWaitingChoice(false);
        exp.setInCombat(false);

        if (exp.isComplete()) {
            finishExpedition(exp, true);
            return;
        }

        int baseSeconds = config.getInt("stage-delay.base-seconds", 8)
                + exp.getStage() * config.getInt("stage-delay.per-stage-seconds", 4);
        if (exp.hasPet()) {
            baseSeconds = Math.max(8, baseSeconds - 5);
        }
        if ("благословение_скорости".equals(exp.getActiveModifier())) {
            baseSeconds = Math.max(5, baseSeconds / 2);
        }

        long delayMillis = immediate ? 0L : baseSeconds * 1000L;
        exp.setNextEventTime(System.currentTimeMillis() + delayMillis);

        if (immediate) {
            deliverPendingEvent(exp);
        } else {
            long waitMillis = exp.getNextEventTime() - System.currentTimeMillis();
            String mention = getVkMention(exp.getSenderId());
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    "🔔 " + mention + "\n" +
                    "📍 Этап " + exp.getStage() + "/" + exp.getMaxStages() + " пройден!\n" +
                    "⏱ Следующее событие через " + formatDuration(waitMillis) + "...");
            persistExpedition(exp);
        }
    }

    private void deliverBossEncounter(Expedition exp) {
        exp.setInCombat(true);
        exp.setCurrentEncounterType("boss");
        exp.setWaitingChoice(true);
        exp.clearPendingEvent();
        
        String bossName = "nether".equals(exp.getDungeonType()) ? "Иссушитель" : "Огромный Страж";
        String bossDesc = "nether".equals(exp.getDungeonType()) ? "Трехголовый летающий скелет испускает адский рев!" : "Огромный механический страж блокирует выход!";
        
        exp.setPendingEventTitle(bossName);
        exp.setPendingEventDescription(bossDesc);

        double discountFactor = getDiscountFactor(exp.getPlayerUuid());
        int baseBribe = 400 + ThreadLocalRandom.current().nextInt(401); // 400-800
        int bribeCost = (int) (baseBribe * discountFactor);
        exp.setBossBribeCost(bribeCost);

        String mention = getVkMention(exp.getSenderId());
        sendExpeditionMessage(exp.getPeerId(), exp.getSenderId(),
                "🔔 " + mention + "\n" +
                buildStageHeader(exp) +
                "🦁 ФИНАЛЬНЫЙ БОСС!\n" +
                bossName + " преграждает путь. Твое здоровье: " + exp.getHp() + "/" + exp.getMaxHp() + " HP.\n\n" +
                "Выбери действие:\n" +
                "1. ⚔ Рискованная атака (Шанс: ~" + getCombatChance(exp, "aggressive") + "%)\n" +
                "2. 🛡 Осторожный удар (Шанс: ~" + getCombatChance(exp, "careful") + "%)\n" +
                "3. 🏃 Попытаться убежать (Шанс: ~" + getCombatChance(exp, "escape") + "%)\n" +
                "4. 💰 Дать взятку Боссу (" + bribeCost + " реп.) [100% победа]", 4);
        persistExpedition(exp);
    }

    /**
     * Генерация и отправка события похода.
     */
    private void deliverPendingEvent(Expedition exp) {
        if (exp.getEndTime() > 0 || exp.isWaitingChoice()) {
            return;
        }

        exp.setNextEventTime(0);

        if (exp.isComplete()) {
            finishExpedition(exp, true);
            return;
        }

        if (exp.getStage() >= exp.getMaxStages() && ("castle".equals(exp.getDungeonType()) || "nether".equals(exp.getDungeonType()))) {
            deliverBossEncounter(exp);
            return;
        }

        EventResult event;
        try {
            event = eventGenerator.generateEvent(exp);
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка генерации события похода: " + ex.getMessage());
            event = EventResult.luckyEvent("Тропа на время стала спокойнее...");
        }

        if (event.getType().contains("riddle") && !riddles.isEmpty()) {
            Riddle riddle = riddles.get(ThreadLocalRandom.current().nextInt(riddles.size()));
            exp.setWaitingChoice(false);
            exp.setWaitingRiddle(true);
            exp.setCurrentRiddleQuestion(riddle.getQuestion());
            exp.setCurrentRiddleAnswers(riddle.getAnswers());
            exp.setRiddleSuccessReward(riddle.getSuccessReward());
            exp.setRiddleFailReward(riddle.getFailReward());
            exp.setCurrentEncounterType("riddle");
            exp.setRiddleExpireTime(System.currentTimeMillis() + 300000L);

            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    "🔔 " + getVkMention(exp.getSenderId()) + "\n" +
                    buildStageHeader(exp) +
                    "❓ ЗАГАДКА СФИНКСА!\n" +
                    riddle.getQuestion() + "\n\n" +
                    "✍️ Напиши правильный ответ прямо в чат!");
            persistExpedition(exp);
            return;
        }

        if (event.getType().equals("pvp")) {
            Map<String, Object> ghost = expeditionStorage.getRandomPlayerGhost(exp.getPlayerUuid());
            if (ghost != null) {
                String ghostName = (String) ghost.get("name");
                int gDamage = (int) ghost.get("damage");
                int gDefense = (int) ghost.get("defense");
                
                exp.setInCombat(true);
                exp.setCurrentEncounterType("pvp");
                exp.setWaitingChoice(true);
                exp.clearPendingEvent();
                exp.setPendingEventTitle("ghost:" + ghostName + ":" + gDamage + ":" + gDefense);
                exp.setPendingEventDescription("Перед тобой материализовался призрак другого исследователя — " + ghostName + "!\n" +
                        "🛡 Защита призрака: " + gDefense + " | ⚔ Урон призрака: " + gDamage + ".\n" +
                        "Он обнажает виртуальное оружие и преграждает путь!");
                
                sendExpeditionMessage(exp.getPeerId(), exp.getSenderId(),
                        "🔔 " + getVkMention(exp.getSenderId()) + "\n" +
                        buildStageHeader(exp) +
                        "👥 ОФЛАЙН-PVP ДУЭЛЬ!\n" +
                        exp.getPendingEventDescription() + "\n\n" +
                        getChoicePrompt("pvp", exp), 2);
                persistExpedition(exp);
                return;
            } else {
                try {
                    event = eventGenerator.generateEvent(exp);
                    if (event.getType().equals("pvp")) {
                        event = EventResult.luckyEvent("Тропа на время стала спокойнее...");
                    }
                } catch (Exception ignored) {}
            }
        }

        if (event.isBoss()) {
            deliverBossEncounter(exp);
            return;
        }

        bindPendingEvent(exp, event);
        exp.setInCombat(false);
        exp.setCurrentEncounterType(getEncounterTypeFromEvent(event));
        exp.setWaitingChoice(true);

        String text = formatEventText(event, exp);
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
        int maxChoice = getMaxChoicesForEncounter(event.getType(), currentRep);
        sendExpeditionMessage(exp.getPeerId(), exp.getSenderId(), 
                "🔔 " + getVkMention(exp.getSenderId()) + "\n" + text, maxChoice);
        persistExpedition(exp);
    }

    private void bindPendingEvent(Expedition exp, EventResult event) {
        pendingEvents.put(exp.getSenderId(), event);
        String[] typeParts = event.getType().split(":", 2);
        exp.setPendingEventTitle(typeParts.length > 1 ? typeParts[1].trim() : event.getType());
        exp.setPendingEventDescription(event.getDescription());
    }

    private String buildStageHeader(Expedition exp) {
        return "━━━━━━━━━━━━━━━━\n" +
                "📍 Этап " + exp.getStage() + " / " + exp.getMaxStages() +
                " • " + getDungeonName(exp.getDungeonType()) + "\n" +
                "❤️ " + exp.getHp() + "/" + exp.getMaxHp() + " HP\n" +
                "━━━━━━━━━━━━━━━━\n\n";
    }

    /**
     * Получить название локации.
     */
    private String getDungeonName(String type) {
        switch (type) {
            case "forest": return "🌲 Ближний Лес";
            case "mine": return "⛏ Заброшенные Шахты";
            case "castle": return "🏰 Проклятый Замок";
            case "nether": return "🌋 Адские Врата";
            default: return "Неизвестная локация";
        }
    }

    /**
     * Форматировать время.
     */
    private String formatTime(long timestamp) {
        long diff = timestamp - System.currentTimeMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        return hours + " ч. " + minutes + " мин.";
    }

    private int calculateRandomExpeditionMinutes() {
        int minMinutes = config.getInt("expedition-time.min", 5);
        int maxMinutes = config.getInt("expedition-time.max", 60);
        if (maxMinutes < minMinutes) {
            maxMinutes = minMinutes;
        }
        return minMinutes + ThreadLocalRandom.current().nextInt(maxMinutes - minMinutes + 1);
    }

    private boolean consumeFoodFromStash(UUID uuid) {
        List<ItemStack> items = plugin.getStashManager().getItems(uuid);
        if (items.isEmpty()) return false;

        List<Material> foodMaterials = Arrays.asList(
                Material.WHEAT, Material.CARROT, Material.POTATO, Material.APPLE,
                Material.BREAD, Material.COOKED_BEEF, Material.PORKCHOP, Material.SWEET_BERRIES
        );

        int needed = 3;
        int found = 0;
        
        for (ItemStack item : items) {
            if (item != null && foodMaterials.contains(item.getType())) {
                found += item.getAmount();
            }
        }

        if (found < needed) {
            return false;
        }

        int remainingToDeduct = needed;
        List<ItemStack> updated = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && foodMaterials.contains(item.getType())) {
                if (remainingToDeduct > 0) {
                    int amt = item.getAmount();
                    if (amt <= remainingToDeduct) {
                        remainingToDeduct -= amt;
                    } else {
                        item.setAmount(amt - remainingToDeduct);
                        remainingToDeduct = 0;
                        updated.add(item);
                    }
                } else {
                    updated.add(item);
                }
            } else {
                updated.add(item);
            }
        }

        plugin.getStashManager().saveItems(uuid, updated);
        return true;
    }

    private int getProgressPercent(Expedition exp) {
        long totalMillis = Math.max(60000L, exp.getEstimatedTotalMinutes() * 60000L);
        long startMillis = exp.getExpeditionEndTime() - totalMillis;
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        int progress = (int) Math.round((elapsedMillis * 100.0) / totalMillis);
        return Math.max(0, Math.min(100, progress));
    }

    private String formatDuration(long millis) {
        if (millis <= 0) return "0 мин.";
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + " ч. " + minutes + " мин.";
        if (minutes > 0) return minutes + " мин. " + secs + " сек.";
        return secs + " сек.";
    }

    private String formatClockTime(long timestamp) {
        Date date = new Date(timestamp);
        return String.format("%02d:%02d", date.getHours(), date.getMinutes());
    }

    /**
     * Обработка выбора действия во время события.
     */
    public void handleActionChoice(int sender, int choice) {
        Expedition exp = activeExpeditions.get(sender);
        if (exp == null || !exp.isWaitingChoice() || exp.getEndTime() > 0 || "menu".equals(exp.getDungeonType())) {
            return;
        }

        if (exp.isInCombat() && "boss".equals(exp.getCurrentEncounterType())) {
            if (choice < 1 || choice > 4) {
                sendInvalidChoice(exp, 4);
                return;
            }
            exp.setWaitingChoice(false);
            handleBossBattle(exp, choice);
            return;
        }

        String type = exp.getCurrentEncounterType();
        int maxChoice = 2;
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(sender);
        if (("enemy".equals(type) && currentRep >= 100) ||
            ("trap".equals(type) && currentRep >= 80) ||
            ("environmental".equals(type) && currentRep >= 60) ||
            "robbery".equals(type)) {
            maxChoice = 3;
        }

        if (choice < 1 || choice > maxChoice) {
            sendInvalidChoice(exp, maxChoice);
            return;
        }

        exp.setWaitingChoice(false);
        if (choice == 3 && !"robbery".equals(type)) {
            handlePaidBypass(exp, type);
        } else {
            handleEncounterAction(exp, choice);
        }
    }

    private void handlePaidBypass(Expedition exp, String type) {
        int cost = 0;
        String bypassMsg = "";
        
        if ("enemy".equals(type)) {
            cost = 100;
            bypassMsg = "💰 Ты заплатил разбойникам выкуп в 100 репутации! Они ухмыльнулись и пропустили тебя без боя.";
        } else if ("trap".equals(type)) {
            cost = 80;
            bypassMsg = "💰 Ты обезвредил ловушку за 80 репутации! Путь стал безопасным.";
        } else if ("environmental".equals(type)) {
            cost = 60;
            bypassMsg = "💰 Ты нанял проводника за 60 репутации! Он провел тебя безопасной тропой в обход разбушевавшейся стихии.";
        }
        
        VKChatPlugin.getInstance().getApi().takeReputation(exp.getSenderId(), cost);
        exp.onVictory(false);
        
        clearPendingEvent(exp);
        exp.setStage(exp.getStage() + 1);
        maybeApplyModifier(exp);
        
        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                bypassMsg + "\n\n" + exp.getStatusText());
        scheduleNextEvent(exp);
    }

    /**
     * Обработка ответа на интерактивную загадку.
     */
    public void handleRiddleAnswer(int sender, String rawAnswer) {
        Expedition exp = activeExpeditions.get(sender);
        if (exp == null || !exp.isWaitingRiddle()) {
            return;
        }

        String answer = rawAnswer.toLowerCase().trim();
        List<String> correctAnswers = exp.getCurrentRiddleAnswers();
        
        boolean success = false;
        for (String correct : correctAnswers) {
            if (answer.contains(correct) || correct.contains(answer)) {
                if (answer.equals(correct) || (answer.length() >= 3 && correct.contains(answer))) {
                    success = true;
                    break;
                }
            }
        }

        exp.setWaitingRiddle(false);

        if (success) {
            exp.onVictory(false);
            
            String rewardsStr = exp.getRiddleSuccessReward();
            List<String> rewardsList = rewardsStr != null && !rewardsStr.isEmpty() 
                    ? Arrays.asList(rewardsStr.split(";"))
                    : new ArrayList<>();
            
            List<ItemStack> items = EventResult.parseItems(rewardsList);
            if (!items.isEmpty()) {
                exp.addItems(items);
            }
            
            int repGain = 50;
            int hpGain = 0;
            for (String reward : rewardsList) {
                if (reward.startsWith("rep:")) {
                    try { repGain = Integer.parseInt(reward.substring(4)); } catch (Exception ignored) {}
                } else if (reward.startsWith("hp:")) {
                    try { hpGain = Integer.parseInt(reward.substring(3)); } catch (Exception ignored) {}
                }
            }
            if (hpGain > 0) exp.heal(hpGain);
            VKChatPlugin.getInstance().getApi().addReputation(sender, repGain);

            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                    "🎉 ПРАВИЛЬНО! Ответ '" + rawAnswer + "' оказался верным!\n" +
                    "🔺 Получено: +" + repGain + " репутации" + (items.isEmpty() ? "" : ", новые предметы отправлены в рюкзак.") + "\n\n" +
                    exp.getStatusText());
        } else {
            String penaltyStr = exp.getRiddleFailReward();
            List<String> penaltyList = penaltyStr != null && !penaltyStr.isEmpty()
                    ? Arrays.asList(penaltyStr.split(";"))
                    : new ArrayList<>();
            
            int hpLoss = 20;
            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(sender);
            int repLoss = (int) Math.ceil(currentRep * 0.05); // 5% репутации
            if (repLoss < 10) repLoss = 10;
            repLoss = Math.min(currentRep, repLoss);

            for (String penalty : penaltyList) {
                if (penalty.startsWith("hp:")) {
                    try { hpLoss = Integer.parseInt(penalty.substring(3)); } catch (Exception ignored) {}
                }
            }
            
            exp.takeDamage(hpLoss, "Неправильный ответ на загадку");
            if (repLoss > 0) {
                VKChatPlugin.getInstance().getApi().takeReputation(sender, repLoss);
            }

            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                    "❌ НЕВЕРНО! Твой ответ '" + rawAnswer + "' оказался ошибочным.\n" +
                    "Штраф Сфинкса: -" + repLoss + " репутации.\n" +
                    "Правильные ответы: " + String.join(" / ", correctAnswers) + ".\n\n" +
                    exp.getStatusText());

            if (exp.isDead()) {
                failExpedition(exp, "Ты сошел с ума или погиб от магии Сфинкса!");
                return;
            }
        }

        exp.setStage(exp.getStage() + 1);
        maybeApplyModifier(exp);
        scheduleNextEvent(exp);
    }

    /**
     * Обработка таймаута на ответ Сфинксу.
     */
    private void handleRiddleTimeout(Expedition exp) {
        if (!exp.isWaitingRiddle()) return;
        exp.setWaitingRiddle(false);

        String penaltyStr = exp.getRiddleFailReward();
        List<String> penaltyList = penaltyStr != null && !penaltyStr.isEmpty()
                ? Arrays.asList(penaltyStr.split(";"))
                : new ArrayList<>();
        
        int hpLoss = 25; // Сфинкс бьет больнее при таймауте
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
        int repLoss = (int) Math.ceil(currentRep * 0.05); // 5% штраф
        if (repLoss < 10) repLoss = 10;
        repLoss = Math.min(currentRep, repLoss);

        for (String penalty : penaltyList) {
            if (penalty.startsWith("hp:")) {
                try { hpLoss = Integer.parseInt(penalty.substring(3)); } catch (Exception ignored) {}
            }
        }
        
        exp.takeDamage(hpLoss, "Время разгадывания загадки истекло");
        if (repLoss > 0) {
            VKChatPlugin.getInstance().getApi().takeReputation(exp.getSenderId(), repLoss);
        }

        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                "⏳ ВРЕМЯ ИСТЕКЛО! Ты не успел ответить на загадку Сфинкса за 5 минут.\n" +
                "Штраф Сфинкса: -" + repLoss + " репутации.\n" +
                "Правильные ответы: " + String.join(" / ", exp.getCurrentRiddleAnswers()) + ".\n\n" +
                exp.getStatusText());

        if (exp.isDead()) {
            failExpedition(exp, "Ты не выдержал долгого молчания и погиб под взглядом Сфинкса!");
            return;
        }

        exp.setStage(exp.getStage() + 1);
        maybeApplyModifier(exp);
        scheduleNextEvent(exp);
    }

    /**
     * Запрос подсказки для текущей загадки.
     */
    public void handleRiddleHintRequest(int sender) {
        Expedition exp = activeExpeditions.get(sender);
        if (exp == null || !exp.isWaitingRiddle()) return;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(sender);
        // Стоимость подсказки: 5% от текущей репутации (минимум 10)
        int cost = (int) Math.ceil(rep * 0.05);
        if (cost < 10) cost = 10;
        cost = Math.min(rep, cost);

        if (rep < cost) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                    "❌ Недостаточно репутации! Подсказка стоит 5% твоей репутации (нужно минимум " + cost + " реп.).");
            return;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(sender, cost);
        
        List<String> answers = exp.getCurrentRiddleAnswers();
        String primary = answers.get(0);
        int len = primary.length();
        char first = primary.charAt(0);
        
        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                "🔮 Сфинкс принимает твою жертву (-" + cost + " реп.) и дает подсказку:\n" +
                "💡 Слово состоит из " + len + " букв. Первая буква: '" + Character.toUpperCase(first) + "'.\n" +
                "⏳ Осталось времени: " + formatDuration(exp.getRiddleExpireTime() - System.currentTimeMillis()));
    }

    private void sendInvalidChoice(Expedition exp, int maxChoice) {
        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                "❌ Неверный выбор! Ответь числом от 1 до " + maxChoice + ".");
    }

    /**
     * Обработка действий в битве с боссом.
     */
    private void handleBossBattle(Expedition exp, int choice) {
        int pLevel = getPlayerLevel(exp.getPlayerUuid());

        if (choice == 1) { // Рискованная атака
            int chance = getCombatChance(exp, "aggressive");
            boolean success = ThreadLocalRandom.current().nextInt(100) < chance;

            if (success) {
                int damage = (exp.getDamage() * 5) + ThreadLocalRandom.current().nextInt(20);
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Ты нанес сокрушительный удар! Босс повержен!\n" +
                        "Урон: " + damage);
                finishExpedition(exp, true);
            } else {
                int damage = 30 + ThreadLocalRandom.current().nextInt(20);
                exp.takeDamage(damage, "Рискованная атака босса");
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Ты промахнулся и получил мощный ответный удар!\n" +
                        exp.getStatusText());

                if (exp.isDead()) {
                    failExpedition(exp, "Ты погиб в битве с боссом!");
                } else {
                    exp.setWaitingChoice(true);
                    persistExpedition(exp);
                }
            }
            return;
        }

        if (choice == 2) { // Осторожный удар
            int chance = getCombatChance(exp, "careful");
            boolean success = ThreadLocalRandom.current().nextInt(100) < chance;

            if (success) {
                int damage = (exp.getDamage() * 3) + ThreadLocalRandom.current().nextInt(15);
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Медленно, но верно ты добил босса!\n" +
                        "Урон: " + damage);
                finishExpedition(exp, true);
            } else {
                int damage = 15 + ThreadLocalRandom.current().nextInt(10);
                exp.takeDamage(damage, "Босс контратаковал");
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Босс оказался хитрее!\n" +
                        exp.getStatusText());

                if (exp.isDead()) {
                    failExpedition(exp, "Ты погиб от ран!");
                } else {
                    exp.setWaitingChoice(true);
                    persistExpedition(exp);
                }
            }
            return;
        }

        if (choice == 3) { // Попытка сбежать
            int chance = getCombatChance(exp, "escape");
            boolean success = ThreadLocalRandom.current().nextInt(100) < chance;

            if (success) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Ты трусливо сбежал, оставив сокровища... Штраф: -100 репутации.");
                failExpedition(exp, "Побег");
            } else {
                int damage = 50;
                exp.takeDamage(damage, "Босс уложил тебя при попытке сбежать");
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "Ты не смог сбежать! Босс нанес сокрушительный удар.\n" +
                        exp.getStatusText());

                if (exp.isDead()) {
                    failExpedition(exp, "Попытка бегства обернулась трагедией!");
                } else {
                    exp.setWaitingChoice(true);
                    persistExpedition(exp);
                }
            }
            return;
        }

        if (choice == 4) { // Взятка Огромному Стражу
            int cost = exp.getBossBribeCost();
            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
            if (currentRep < cost) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "❌ Недостаточно репутации для взятки! Требуется: " + cost + " (У тебя: " + currentRep + ")");
                exp.setWaitingChoice(true);
                return;
            }
            VKChatPlugin.getInstance().getApi().takeReputation(exp.getSenderId(), cost);
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    "💰 Ты дал Огромному Стражу взятку в размере " + cost + " репутации! Механические шестеренки Стража заскрежетали, он тихо отошел в сторону и открыл тебе проход к сокровищам.");
            finishExpedition(exp, true);
            return;
        }
    }

    /**
     * Обработка действий во время обычного события.
     */
    private void handleEncounterAction(Expedition exp, int choice) {
        String type = exp.getCurrentEncounterType();
        EventResult event = pendingEvents.get(exp.getSenderId());
        int pLevel = getPlayerLevel(exp.getPlayerUuid());

        boolean success;
        int damage = 0;

        if ("pvp".equals(type)) {
            String title = exp.getPendingEventTitle();
            String[] parts = title.split(":");
            String ghostName = parts.length > 1 ? parts[1] : "Призрак";
            int gDamage = 5;
            int gDefense = 0;
            try {
                if (parts.length > 2) gDamage = Integer.parseInt(parts[2]);
                if (parts.length > 3) gDefense = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}
            int sender = exp.getSenderId();
            
            if (choice == 1) { // Дуэль
                int playerPower = exp.getDamage() + exp.getDefense();
                int ghostPower = gDamage + gDefense;
                int winChance = 50 + (playerPower - ghostPower) * 4;
                winChance = Math.max(15, Math.min(85, winChance));
                
                success = ThreadLocalRandom.current().nextInt(100) < winChance;
                if (success) {
                    VKChatPlugin.getInstance().getApi().addReputation(sender, 150);
                    exp.addItem(new ItemStack(Material.GOLD_INGOT, 1 + ThreadLocalRandom.current().nextInt(3)));
                    
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "⚔️ ДУЭЛЬ ВЫИГРАНА!\n" +
                            "Ты одолел призрака " + ghostName + " в честном бою!\n" +
                            "🔺 Получено: +150 репутации, новые трофеи отправлены в рюкзак.");
                } else {
                    damage = 25 + ThreadLocalRandom.current().nextInt(20);
                    exp.takeDamage(damage, "Проигранная дуэль с призраком " + ghostName);
                    VKChatPlugin.getInstance().getApi().takeReputation(sender, 50);
                    
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "⚔️ ДУЭЛЬ ПРОИГРАНА!\n" +
                            "Призрак " + ghostName + " оказался сильнее и одолел тебя!\n" +
                            "🔻 Штраф: -50 репутации.\n" +
                            exp.getStatusText());
                    
                    if (exp.isDead()) {
                        clearPendingEvent(exp);
                        failExpedition(exp, "Ты пал от руки призрака " + ghostName + "!");
                        return;
                    }
                }
            } else { // Сбежать
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                if (success) {
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "🏃 Ты благополучно скрылся от дуэли во тьме.");
                } else {
                    damage = 15 + ThreadLocalRandom.current().nextInt(10);
                    exp.takeDamage(damage, "Призрак настиг тебя при бегстве");
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "❌ Призрак " + ghostName + " настиг тебя при попытке сбежать!\n" +
                            exp.getStatusText());
                    
                    if (exp.isDead()) {
                        clearPendingEvent(exp);
                        failExpedition(exp, "Ты погиб от полученных в спину ран!");
                        return;
                    }
                }
            }
            
            clearPendingEvent(exp);
            exp.setStage(exp.getStage() + 1);
            maybeApplyModifier(exp);
            scheduleNextEvent(exp);
            return;
        }

        if ("robbery".equals(type)) {
            int sender = exp.getSenderId();
            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(sender);
            if (choice == 1) { // Сражаться
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "aggressive");
                if (success) {
                    exp.onVictory(false);
                    // Награда за победу над разбойниками
                    VKChatPlugin.getInstance().getApi().addReputation(sender, 150);
                    ItemStack gold = new ItemStack(Material.GOLD_INGOT, 1 + ThreadLocalRandom.current().nextInt(3));
                    exp.addItem(gold);
                    
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "⚔️ РАЗБОЙНИКИ ПОВЕРЖЕНЫ!\n" +
                            "Ты проявил недюжинную отвагу и перебил бандитов в открытом бою!\n" +
                            "🔺 Награда: +150 репутации, золотые слитки добавлены в рюкзак.\n\n" +
                            exp.getStatusText());
                } else {
                    damage = 35 + ThreadLocalRandom.current().nextInt(15);
                    exp.takeDamage(damage, "Тяжелые ранения в бою с разбойниками");
                    
                    // Штраф: потеря 50% накопленного лута (потеря лута / грабеж)
                    int lostLootSize = 0;
                    if (!exp.getInventory().isEmpty()) {
                        lostLootSize = (int) Math.ceil(exp.getInventory().size() * 0.50);
                        for (int k = 0; k < lostLootSize && !exp.getInventory().isEmpty(); k++) {
                            if (exp.getInventory().isEmpty()) break;
                            exp.getInventory().remove(ThreadLocalRandom.current().nextInt(exp.getInventory().size()));
                        }
                    }
                    
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "❌ ТЯЖЕЛОЕ ПОРАЖЕНИЕ!\n" +
                            "Разбойники одолели тебя, избили и жестоко ограбили!\n" +
                            "🎒 Потеряно: " + lostLootSize + " накопленных предметов (50% рюкзака).\n" +
                            "💔 Нанесен урон: -" + damage + " HP.\n\n" +
                            exp.getStatusText());
                    
                    if (exp.isDead()) {
                        clearPendingEvent(exp);
                        failExpedition(exp, "Ты скончался от ран, полученных в схватке с разбойниками!");
                        return;
                    }
                }
            } else if (choice == 2) { // Отдать кошелек и часть добычи
                // Теряет 20% лута и 50 репутации
                int lostLootSize = 0;
                if (!exp.getInventory().isEmpty()) {
                    lostLootSize = (int) Math.ceil(exp.getInventory().size() * 0.20);
                    if (lostLootSize == 0 && exp.getInventory().size() > 0) lostLootSize = 1; // минимум 1, если есть
                    for (int k = 0; k < lostLootSize && !exp.getInventory().isEmpty(); k++) {
                        exp.getInventory().remove(ThreadLocalRandom.current().nextInt(exp.getInventory().size()));
                    }
                }
                int repLoss = Math.min(currentRep, 50);
                if (repLoss > 0) {
                    VKChatPlugin.getInstance().getApi().takeReputation(sender, repLoss);
                }
                
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                        "🎒 МЕНЬШЕЕ ИЗ ЗОЛ:\n" +
                        "Ты покорно отдал разбойникам свой кошелек и часть припасов.\n" +
                        "🔻 Потери: -" + repLoss + " репутации, утеряно предметов: " + lostLootSize + " шт.\n" +
                        "Они ухмыльнулись и скрылись во тьме, оставив тебя в живых.\n\n" +
                        exp.getStatusText());
            } else if (choice == 3) { // Попытаться скрыться в зарослях
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                if (success) {
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "🏃 ЧУДО-ПОБЕГ:\n" +
                            "Ты искусно проскользнул сквозь густые заросли и ушел от погони разбойников невредимым!\n\n" +
                            exp.getStatusText());
                } else {
                    damage = 25 + ThreadLocalRandom.current().nextInt(10);
                    exp.takeDamage(damage, "Засада при попытке побега");
                    
                    // Поломка снаряжения: навсегда снижает урон и защиту на 3
                    int oldDmg = exp.getDamage();
                    int oldDef = exp.getDefense();
                    exp.setDamage(Math.max(5, exp.getDamage() - 3));
                    exp.setDefense(Math.max(0, exp.getDefense() - 3));
                    int dmgDiff = oldDmg - exp.getDamage();
                    int defDiff = oldDef - exp.getDefense();
                    
                    // Грабеж: воруют 30% лута
                    int lostLootSize = 0;
                    if (!exp.getInventory().isEmpty()) {
                        lostLootSize = (int) Math.ceil(exp.getInventory().size() * 0.30);
                        for (int k = 0; k < lostLootSize && !exp.getInventory().isEmpty(); k++) {
                            exp.getInventory().remove(ThreadLocalRandom.current().nextInt(exp.getInventory().size()));
                        }
                    }
                    
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), sender,
                            "❌ ПОЙМАН ЗА ХВОСТ!\n" +
                            "Разбойники настигли тебя при попытке побега, сильно избили, испортили снаряжение и обчистили карманы!\n" +
                            "💔 Нанесен урон: -" + damage + " HP.\n" +
                            "🎒 Потеряно лута: " + lostLootSize + " предметов (30% рюкзака).\n" +
                            "🛠 ПОЛОМКА: Твоя экипировка сильно пострадала! " +
                            (defDiff > 0 ? "Защита навсегда снижена на -" + defDiff + " ед. " : "") +
                            (dmgDiff > 0 ? "Урон навсегда снижен на -" + dmgDiff + " ед." : "") + "\n\n" +
                            exp.getStatusText());
                    
                    if (exp.isDead()) {
                        clearPendingEvent(exp);
                        failExpedition(exp, "Ты погиб от тяжелых ран после поимки разбойниками!");
                        return;
                    }
                }
            }
            
            clearPendingEvent(exp);
            exp.setStage(exp.getStage() + 1);
            maybeApplyModifier(exp);
            scheduleNextEvent(exp);
            return;
        }

        if ("loot".equals(type)) {
            if (choice == 1) {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "careful");
            } else {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                damage = success ? ThreadLocalRandom.current().nextInt(5) : ThreadLocalRandom.current().nextInt(12) + 5;
            }
        } else if ("enemy".equals(type)) {
            if (choice == 1) {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "aggressive");
                damage = success ? 0 : ThreadLocalRandom.current().nextInt(20) + 10;
            } else {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                damage = success ? ThreadLocalRandom.current().nextInt(8) : ThreadLocalRandom.current().nextInt(15) + 10;
            }
        } else if ("trap".equals(type)) {
            if (choice == 1) {
                success = ThreadLocalRandom.current().nextInt(100) < 50 + pLevel;
                damage = success ? 0 : 30;
            } else {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                damage = success ? 0 : 25;
            }
        } else if ("encounter".equals(type)) {
            success = choice == 1 ? ThreadLocalRandom.current().nextInt(100) < 55 + pLevel : ThreadLocalRandom.current().nextInt(100) < 40;
            damage = success ? 0 : ThreadLocalRandom.current().nextInt(10) + 5;
        } else if ("environmental".equals(type)) {
            if (choice == 1) {
                success = ThreadLocalRandom.current().nextInt(100) < 45 + pLevel;
                damage = success ? ThreadLocalRandom.current().nextInt(8) : ThreadLocalRandom.current().nextInt(18) + 8;
            } else {
                success = ThreadLocalRandom.current().nextInt(100) < getCombatChance(exp, "escape");
                damage = success ? 0 : ThreadLocalRandom.current().nextInt(12) + 8;
            }
        } else {
            success = choice == 1 ? ThreadLocalRandom.current().nextBoolean() : ThreadLocalRandom.current().nextInt(100) < 35;
            damage = success ? 0 : ThreadLocalRandom.current().nextInt(15) + 5;
        }

        if (success) {
            exp.onVictory(false);
            applyEventRewards(exp, event);
            if (damage > 0) {
                exp.takeDamage(damage, "Побочный урон");
            }

            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    "✅ Этап " + exp.getStage() + " пройден!\n" + exp.getStatusText());
        } else {
            damage = Math.max(damage, 12);
            exp.takeDamage(damage, "Неудачное действие");

            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    "❌ Этап " + exp.getStage() + " — неудача!\n" + exp.getStatusText());

            if (exp.isDead()) {
                clearPendingEvent(exp);
                failExpedition(exp, "Ты погиб от полученных ран!");
                return;
            }
        }

        clearPendingEvent(exp);
        exp.setStage(exp.getStage() + 1);
        maybeApplyModifier(exp);
        scheduleNextEvent(exp);
    }

    private void applyEventRewards(Expedition exp, EventResult event) {
        if (event == null) {
            int pLevel = getPlayerLevel(exp.getPlayerUuid());
            VKChatPlugin.getInstance().getApi().addReputation(exp.getSenderId(), 15 + pLevel);
            return;
        }

        if (event.getHpGain() > 0) {
            exp.heal(event.getHpGain());
        }
        if (event.getRepGain() > 0) {
            VKChatPlugin.getInstance().getApi().addReputation(exp.getSenderId(), event.getRepGain());
        }
        if (event.getRepLoss() > 0) {
            VKChatPlugin.getInstance().getApi().takeReputation(exp.getSenderId(), event.getRepLoss());
        }
        if (!event.getItems().isEmpty()) {
            exp.addItems(event.getItems());
        }

        int pLevel = getPlayerLevel(exp.getPlayerUuid());
        int repGain = 10 + (pLevel * 2);
        VKChatPlugin.getInstance().getApi().addReputation(exp.getSenderId(), repGain);
    }

    private void clearPendingEvent(Expedition exp) {
        pendingEvents.remove(exp.getSenderId());
        exp.clearPendingEvent();
    }

    /**
     * Определение типа события по результату.
     */
    private String getEncounterTypeFromEvent(EventResult event) {
        String type = event.getType();
        if (type.startsWith("loot")) return "loot";
        if (type.startsWith("danger")) return "enemy";
        if (type.startsWith("trap")) return "trap";
        if (type.startsWith("encounter")) return "encounter";
        if (type.startsWith("environmental")) return "environmental";
        if (type.startsWith("robbery")) return "robbery";
        return "neutral";
    }

    /**
     * Форматирование текста события.
     */
    private String formatEventText(EventResult event, Expedition exp) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildStageHeader(exp));

        String[] typeParts = event.getType().split(":", 2);
        String eventName = typeParts.length > 1 ? typeParts[1].trim() : event.getType();
        sb.append("📝 ").append(eventName).append("\n\n");
        sb.append(event.getDescription()).append("\n\n");

        if (event.getHpLoss() > 0) {
            sb.append("⚠️ Возможный урон: до ").append(event.getHpLoss()).append(" HP\n");
        }
        if (event.getRepGain() > 0) {
            sb.append("🔺 Возможная награда: +").append(event.getRepGain()).append(" реп.\n");
        }
        if (!event.getItems().isEmpty()) {
            sb.append("🎒 Можно найти предметы: ").append(event.getItems().size()).append(" шт.\n");
        }
        if (exp.isModifierActive()) {
            sb.append("🔮 Модификатор: ").append(getModifierName(exp.getActiveModifier())).append("\n");
        }

        String encounterType = getEncounterTypeFromEvent(event);
        sb.append("\n").append(getChoicePrompt(encounterType, exp));
        return sb.toString();
    }

    private String getChoicePrompt(String encounterType, Expedition exp) {
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
        switch (encounterType) {
            case "loot":
                return "Выбери действие:\n1. 🔍 Осторожно обыскать\n2. 🏃 Схватить и бежать";
            case "enemy":
                String enemyPrompt = "Выбери действие:\n1. ⚔ Атаковать\n2. 🛡 Отступить";
                if (currentRep >= 100) {
                    enemyPrompt += "\n3. 💰 Заплатить выкуп (100 реп.) [100% безопасно]";
                }
                return enemyPrompt;
            case "trap":
                String trapPrompt = "Выбери действие:\n1. 🦶 Попытаться пройти\n2. ↩️ Обойти стороной";
                if (currentRep >= 80) {
                    trapPrompt += "\n3. 💰 Обезвредить за репутацию (80 реп.) [100% безопасно]";
                }
                return trapPrompt;
            case "encounter":
                return "Выбери действие:\n1. 💬 Вступить во взаимодействие\n2. 🚶 Пройти мимо";
            case "environmental":
                String envPrompt = "Выбери действие:\n1. 💪 Преодолеть препятствие\n2. 🌿 Искать обходной путь";
                if (currentRep >= 60) {
                    envPrompt += "\n3. 💰 Нанять проводника (60 реп.) [100% безопасно]";
                }
                return envPrompt;
            case "pvp":
                return "Выбери действие:\n1. ⚔ Принять вызов (Дуэль)\n2. 🏃 Сбежать";
            case "robbery":
                return "🎒 ТЕБЯ ГРАБЯТ! Выбери действие:\n" +
                       "1. ⚔️ Сражаться не на жизнь, а на смерть (Шанс: ~" + getCombatChance(exp, "aggressive") + "%)\n" +
                       "2. 🎒 Отдать кошелек и часть добычи (Теряешь 20% лута и 50 репутации) [100% безопасно]\n" +
                       "3. 🏃 Попытаться скрыться в зарослях (Шанс: ~" + getCombatChance(exp, "escape") + "%)\n" +
                       "   ⚠️ ВНИМАНИЕ: При провале бегства разбойники сломают снаряжение и украдут 30% лута!";
            default:
                return "Выбери действие:\n1. ✅ Действовать\n2. ❌ Отступить";
        }
    }

    private String getModifierName(String modifier) {
        switch (modifier) {
            case "благословение_сокровищ": return "✨ Благословение сокровищ";
            case "благословение_скорости": return "✨ Благословение скорости";
            case "проклятие_тьмы": return "💀 Проклятие тьмы";
            case "проклятие_голода": return "💀 Проклятие голода";
            default: return modifier;
        }
    }

    /**
     * Завершение похода.
     */
    private void finishExpedition(Expedition exp, boolean success) {
        clearPendingEvent(exp);
        untrackExpedition(exp.getSenderId());

        String dungeonKey = exp.getDungeonType();
        int baseRep = config.getInt("completion." + dungeonKey + ".base_rep",
                config.getInt("completion.forest.base_rep", 100));

        if (exp.isNight() && config.getBoolean("completion." + dungeonKey + ".night_bonus", true)) {
            baseRep *= 2;
        }

        baseRep += exp.getConsecutiveWins() * 10;

        int estimatedMinutes = exp.getEstimatedTotalMinutes();
        if (estimatedMinutes > 30) {
            baseRep += (estimatedMinutes - 30) / 5;
        }

        VKChatPlugin.getInstance().getApi().addReputation(exp.getSenderId(), baseRep);

        plugin.getOfflineListener().generateLoot(exp, exp.isInCombat());

        StringBuilder msg = new StringBuilder();
        msg.append("✅ Поход успешно завершен!\n");
        msg.append("Репутация: +").append(baseRep).append("\n");
        msg.append(exp.getStatusText());

        int rareChance = config.getInt("completion." + dungeonKey + ".rare_loot_chance", 0);
        if (ThreadLocalRandom.current().nextInt(100) < rareChance) {
            msg.append("\n🎉 Выбит эксклюзивный лут!");
        }

        if (!exp.getInventory().isEmpty()) {
            plugin.getStashManager().addItems(exp.getPlayerUuid(), exp.getInventory());
            msg.append("\n🎒 Все предметы отправлены в твой /stash.");
        }

        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(), msg.toString());

        String joinMsg = "§a[VKChat Offline] Поход в " + getDungeonName(exp.getDungeonType()) + " успешно завершен! Получено +" + baseRep + " репутации. Напиши §e/stash§a, чтобы забрать добычу.";
        expeditionStorage.addNotification(exp.getPlayerUuid(), joinMsg);
    }

    /**
     * Неудачное завершение похода.
     */
    private void failExpedition(Expedition exp, String reason) {
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
        // Штраф за смерть: 10% от текущей репутации + 20 за каждую серию побед (минимум 50)
        int penalty = (int) Math.ceil(currentRep * 0.10) + exp.getConsecutiveWins() * 20;
        if (penalty < 50) penalty = 50;
        penalty = Math.min(currentRep, penalty);

        VKChatPlugin.getInstance().getApi().takeReputation(exp.getSenderId(), penalty);

        int cooldownHours = config.getInt("death-cooldown-hours", 4);
        long cooldown = cooldownHours * 3600000L;
        exp.setEndTime(System.currentTimeMillis() + cooldown);
        exp.setWaitingChoice(false);
        exp.setInCombat(false);
        exp.resetWins();
        clearPendingEvent(exp);

        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                "❌ " + reason + "\n" +
                "Штраф за провал: -" + penalty + " репутации (10% от твоего баланса).\n" +
                "Персонаж доставлен в лазарет на " + cooldownHours + " ч.");
        persistExpedition(exp);

        String failMsg = "§c[VKChat Offline] Ты погиб в походе (" + reason + ")! Снаряжение спасло твою жизнь, но персонаж попал в лазарет на " + cooldownHours + " ч.";
        expeditionStorage.addNotification(exp.getPlayerUuid(), failMsg);
    }

    /**
     * Обработка команды !спасти.
     */
    public void handleRescueCommand(int peer, int sender, String[] args) {
        if (args.length < 2) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "Использование: !спасти @id<номер>");
            return;
        }

        try {
            String targetStr = args[1].replaceAll("[^0-9]", "");
            int targetId = Integer.parseInt(targetStr);

            if (!activeExpeditions.containsKey(targetId)) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender, "Игрок не нуждается в спасении.");
                return;
            }

            Expedition targetExp = activeExpeditions.get(targetId);
            if (targetExp.getEndTime() == 0 || targetExp.getEndTime() < System.currentTimeMillis()) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender, "Игрок не в лазарете.");
                return;
            }

            int cost = 500;
            int rep = VKChatPlugin.getInstance().getApi().getReputation(sender);
            if (rep < cost) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "Недостаточно репутации! Нужно " + cost);
                return;
            }

            VKChatPlugin.getInstance().getApi().takeReputation(sender, cost);
            untrackExpedition(targetId);

            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "✅ Ты вылечил игрока @id" + targetId + " за " + cost + " репутации!");
            VKChatPlugin.getInstance().getVkManager().sendMessage(targetExp.getPeerId(), targetId,
                    "Тебя спас игрок @id" + sender + "! Ты снова можешь ходить в походы.");

        } catch (Exception ex) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender, "Не удалось найти ID игрока.");
        }
    }

    /**
     * Обработка команды !питомец.
     */
    public void handlePetCommand(int peer, int sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("купить")) {
            int cost = 1000;
            if (playerPets.containsKey(sender)) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "У тебя уже есть питомец: " + playerPets.get(sender));
                return;
            }
            if (VKChatPlugin.getInstance().getApi().getReputation(sender) < cost) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "Питомец (Охотничий Сокол) стоит " + cost + " репутации.");
                return;
            }
            VKChatPlugin.getInstance().getApi().takeReputation(sender, cost);
            playerPets.put(sender, "Сокол");
            expeditionStorage.savePet(sender, "Сокол");
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "✅ Ты купил Охотничьего Сокола! Он ускоряет походы и помогает находить лут.");
            return;
        }

        String pet = playerPets.get(sender);
        VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                pet != null ? "Твой питомец: " + pet : "У тебя нет питомца. Купи его: !питомец купить");
    }

    private void maybeApplyModifier(Expedition exp) {
        if (exp.isModifierActive()) {
            exp.decrementModifierDuration();
            
            String mod = exp.getActiveModifier();
            if ("проклятие_тьмы".equals(mod) || "проклятие_голода".equals(mod)) {
                exp.takeDamage(5, "Урон от Проклятия (" + getModifierName(mod) + ")");
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                        "💀 " + getModifierName(mod) + " вытягивает из тебя жизненные силы! (-5 HP)");
            }
        }

        if (ThreadLocalRandom.current().nextInt(100) >= 15) {
            return;
        }

        String[] modifiers = {
                "благословение_сокровищ",
                "благословение_скорости",
                "проклятие_тьмы",
                "проклятие_голода"
        };
        String modifier = modifiers[ThreadLocalRandom.current().nextInt(modifiers.length)];
        exp.applyModifier(modifier, 2);

        VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                "🔮 На тебя действует модификатор: " + getModifierName(modifier));
    }

    private int getCombatChance(Expedition exp, String type) {
        int baseChance = config.getInt("combat." + type, exp.getAttackChance(type));
        if (exp.hasPet()) {
            baseChance += 10;
        }
        if (exp.isNight() && "forest".equals(exp.getDungeonType())) {
            baseChance += 15;
        }
        
        String mod = exp.getActiveModifier();
        if ("проклятие_тьмы".equals(mod) || "проклятие_голода".equals(mod)) {
            baseChance -= 15;
        }
        return Math.min(95, Math.max(5, baseChance + (exp.getLevel() - 1) * 3));
    }

    private void startExpeditionTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkExpeditions, 100L, 100L);
    }

    /**
     * Получение уровня игрока через профессии VKChatJobs.
     */
    private int getPlayerLevel(UUID uuid) {
        try {
            VKChatJobsPlugin jobs = VKChatJobsPlugin.getInstance();
            if (jobs != null && jobs.isEnabled()) {
                int miner = jobs.getJobsDataManager().getLevel(uuid, "miner");
                int wood = jobs.getJobsDataManager().getLevel(uuid, "woodcutter");
                int farmer = jobs.getJobsDataManager().getLevel(uuid, "farmer");
                return Math.max(1, Math.max(miner, Math.max(wood, farmer)));
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    public double getDiscountFactor(UUID uuid) {
        int level = getPlayerLevel(uuid);
        double discount = (level - 1) * 0.03; // 3% скидка за каждый уровень выше 1
        return Math.max(0.70, 1.0 - discount); // Максимальная скидка 30% (фактор 0.70)
    }

    /**
     * Проверка и завершение истекших походов (вызывается таймером).
     */
    public void checkExpeditions() {
        long now = System.currentTimeMillis();

        for (Expedition exp : new ArrayList<>(activeExpeditions.values())) {
            if (exp.isWaitingRiddle() && exp.getRiddleExpireTime() > 0 && now >= exp.getRiddleExpireTime()) {
                Expedition target = exp;
                Bukkit.getScheduler().runTask(plugin, () -> handleRiddleTimeout(target));
                continue;
            }

            if (exp.getEndTime() > 0) {
                if (now >= exp.getEndTime()) {
                    untrackExpedition(exp.getSenderId());
                    VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                            "✅ Лазарет: твой персонаж восстановился! Можно снова ходить в походы.");
                }
                continue;
            }

            if ("menu".equals(exp.getDungeonType())) {
                continue;
            }

            if (exp.getExpeditionEndTime() > 0 && now >= exp.getExpeditionEndTime()) {
                if (exp.isDead()) {
                    failExpedition(exp, "Время похода истекло, а сил уже не осталось...");
                } else {
                    finishExpedition(exp, true);
                }
                continue;
            }

            if (!exp.isWaitingChoice() && exp.getNextEventTime() > 0 && now >= exp.getNextEventTime()) {
                Expedition target = exp;
                Bukkit.getScheduler().runTask(plugin, () -> deliverPendingEvent(target));
            }
        }
    }

    /**
     * Восстановление ожидающих выборов после перезагрузки (pendingEvents в памяти пусты).
     */
    private void restorePendingChoicesAfterLoad() {
        for (Expedition exp : activeExpeditions.values()) {
            if (exp.isWaitingChoice() && exp.getPendingEventTitle() != null && !exp.isInCombat()) {
                int currentRep = VKChatPlugin.getInstance().getApi().getReputation(exp.getSenderId());
                int maxChoice = getMaxChoicesForEncounter(exp.getCurrentEncounterType(), currentRep);
                sendExpeditionMessage(exp.getPeerId(), exp.getSenderId(),
                        buildStageHeader(exp) +
                        "📝 " + exp.getPendingEventTitle() + "\n\n" +
                        (exp.getPendingEventDescription() != null ? exp.getPendingEventDescription() + "\n\n" : "") +
                        getChoicePrompt(exp.getCurrentEncounterType(), exp) + "\n\n" +
                        "⚠️ Поход восстановлен после перезагрузки. Выберите действие:", maxChoice);
            }
        }
    }

    // --- Геттеры и персистентность ---

    private void trackExpedition(Expedition expedition) {
        activeExpeditions.put(expedition.getSenderId(), expedition);
        persistExpedition(expedition);
    }

    private void untrackExpedition(int vkId) {
        activeExpeditions.remove(vkId);
        pendingEvents.remove(vkId);
        expeditionStorage.deleteExpedition(vkId);
    }

    private void persistExpedition(Expedition expedition) {
        if (expedition != null) {
            expeditionStorage.saveExpedition(expedition);
        }
    }

    public void shutdown() {
        expeditionStorage.saveAllExpeditions(activeExpeditions);
        expeditionStorage.saveAllPets(playerPets);
    }

    public void clearHospitalCooldown(int vkId) {
        untrackExpedition(vkId);
    }

    public Map<Integer, Expedition> getActiveExpeditions() {
        return new HashMap<>(activeExpeditions);
    }

    public Map<Integer, String> getPlayerPets() {
        return new HashMap<>(playerPets);
    }

    public void addPet(int vkId, String petName) {
        playerPets.put(vkId, petName);
        expeditionStorage.savePet(vkId, petName);
    }

    public boolean hasPet(int vkId) {
        return playerPets.containsKey(vkId);
    }

    public String getPet(int vkId) {
        return playerPets.get(vkId);
    }

    public String getVkMention(int vkId) {
        try {
            org.json.JSONObject user = VKChatPlugin.getInstance().getVkManager().getUserInfo(vkId);
            if (user != null) {
                return "@id" + vkId + " (" + user.getString("first_name") + ")";
            }
        } catch (Exception ignored) {}
        return "@id" + vkId + " (исследователь)";
    }

    private int getMaxChoicesForEncounter(String encounterType, int currentRep) {
        if ("boss".equals(encounterType)) return 4;
        if ("robbery".equals(encounterType)) return 3;
        if ("enemy".equals(encounterType) && currentRep >= 100) return 3;
        if ("trap".equals(encounterType) && currentRep >= 80) return 3;
        if ("environmental".equals(encounterType) && currentRep >= 60) return 3;
        if ("riddle".equals(encounterType)) return 0;
        return 2;
    }

    private void sendExpeditionMessage(int peer, int sender, String text, int maxChoice) {
        if (maxChoice <= 1) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender, text);
            return;
        }
        String kbJson = buildChoiceKeyboard(maxChoice);
        VKChatPlugin.getInstance().getVkManager().sendKeyboard(peer, text, kbJson);
    }

    /**
     * Строит inline-клавиатуру с числовыми кнопками 1..maxChoice.
     * Кнопки раскладываются по 4 в ряд; секретные локации синергии (5–7) подсвечиваются зелёным.
     */
    private String buildChoiceKeyboard(int maxChoice) {
        StringBuilder buttons = new StringBuilder("[");
        int inRow = 0;
        for (int i = 1; i <= maxChoice; i++) {
            if (inRow == 0) {
                if (buttons.length() > 1) buttons.append(",");
                buttons.append("[");
            } else {
                buttons.append(",");
            }
            // Кнопки 5–7 (секретные сокровищницы) — зелёным, остальные — синим
            String color = (i >= 5) ? "positive" : "primary";
            buttons.append("{\"action\":{\"type\":\"text\",\"label\":\"")
                   .append(i).append("\"},\"color\":\"").append(color).append("\"}");
            inRow++;
            if (inRow >= 4 || i == maxChoice) {
                buttons.append("]");
                inRow = 0;
            }
        }
        buttons.append("]");
        return "{\"inline\":true,\"buttons\":" + buttons + "}";
    }
}
