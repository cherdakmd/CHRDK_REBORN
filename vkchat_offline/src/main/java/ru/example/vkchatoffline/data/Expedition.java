package ru.example.vkchatoffline.data;

import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Данные об одном походе игрока.
 * Поддерживает рогалик-систему с этапами, HP, лутом и прогрессией.
 */
public class Expedition {
    private final int peerId;
    private final int senderId;
    private final UUID playerUuid;

    private String dungeonType;
    private int stage;
    private int maxStages;
    private long nextEventTime;
    private boolean waitingChoice;
    private long endTime; // Death cooldown
    private long expeditionEndTime; // Общее время похода
    private int estimatedTotalMinutes; // Оценочная длительность в минутах

    // Roguelike stats
    private int hp;
    private int maxHp;
    private int level;
    private int damage;
    private int defense;
    private int baseDamage = 5;
    private int baseDefense = 0;

    // Interactive Riddle state
    private boolean waitingRiddle = false;
    private String currentRiddleQuestion = null;
    private List<String> currentRiddleAnswers = new ArrayList<>();
    private String riddleSuccessReward = null;
    private String riddleFailReward = null;
    private long riddleExpireTime = 0;
    private int bossBribeCost = 0;

    // Inventory
    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    // State flags
    private boolean hasPet;
    private String petType = null;
    private boolean isNight;
    private int consecutiveWins;
    private int damageTakenTotal;

    // Active modifiers (curse/blessing)
    private String activeModifier = null;
    private int modifierDuration = 0;

    public void applyModifier(String modifier, int duration) {
        this.activeModifier = modifier;
        this.modifierDuration = duration;
    }

    public void decrementModifierDuration() {
        if (modifierDuration > 0) {
            modifierDuration--;
            if (modifierDuration == 0) {
                activeModifier = null;
            }
        }
    }

    public String getActiveModifier() {
        return activeModifier;
    }

    public boolean isModifierActive() {
        return activeModifier != null;
    }

    public int getModifierDuration() {
        return modifierDuration;
    }

    // Encounter data
    private String currentEncounterType;
    private boolean inCombat;
    private String pendingEventTitle;
    private String pendingEventDescription;

    public Expedition(int peerId, int senderId, UUID playerUuid, String dungeonType, int maxStages) {
        this.peerId = peerId;
        this.senderId = senderId;
        this.playerUuid = playerUuid;
        this.dungeonType = dungeonType;
        this.stage = 1;
        this.maxStages = maxStages;
        this.waitingChoice = false;
        this.hp = 100;
        this.maxHp = 100;
        this.level = 1;
        this.damage = 5;
        this.defense = 0;
        this.consecutiveWins = 0;
        this.damageTakenTotal = 0;
        this.nextEventTime = System.currentTimeMillis();
    }

    // --- Basic getters/setters ---

    public int getPeerId() { return peerId; }
    public int getSenderId() { return senderId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getDungeonType() { return dungeonType; }
    public void setDungeonType(String dungeonType) { this.dungeonType = dungeonType; }

    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public int getMaxStages() { return maxStages; }

    public long getNextEventTime() { return nextEventTime; }
    public void setNextEventTime(long nextEventTime) { this.nextEventTime = nextEventTime; }

    public boolean isWaitingChoice() { return waitingChoice; }
    public void setWaitingChoice(boolean waitingChoice) { this.waitingChoice = waitingChoice; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getExpeditionEndTime() { return expeditionEndTime; }
    public void setExpeditionEndTime(long expeditionEndTime) { this.expeditionEndTime = expeditionEndTime; }

    public int getEstimatedTotalMinutes() { return estimatedTotalMinutes; }
    public void setEstimatedTotalMinutes(int estimatedTotalMinutes) { this.estimatedTotalMinutes = estimatedTotalMinutes; }

    public int getHp() { return hp; }
    public void setHp(int hp) {
        this.hp = Math.max(0, Math.min(maxHp, hp));
    }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    // --- Roguelike stats ---

    public int getLevel() { return level; }
    public void setLevel(int level) {
        this.level = Math.max(1, level);
        // Увеличение статов с уровнем
        this.maxHp = 100 + (level - 1) * 10;
        this.damage = baseDamage + (level - 1) * 2;
        this.defense = baseDefense + (level - 1) / 2;
    }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = Math.max(1, damage); }

    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = Math.max(0, defense); }

    public int getBaseDamage() { return baseDamage; }
    public int getBaseDefense() { return baseDefense; }
    public void setBaseStats(int baseDamage, int baseDefense) {
        this.baseDamage = baseDamage;
        this.baseDefense = baseDefense;
        setLevel(this.level);
    }

    // --- Interactive Riddle Getters/Setters ---

    public boolean isWaitingRiddle() { return waitingRiddle; }
    public void setWaitingRiddle(boolean waitingRiddle) { this.waitingRiddle = waitingRiddle; }

    public String getCurrentRiddleQuestion() { return currentRiddleQuestion; }
    public void setCurrentRiddleQuestion(String currentRiddleQuestion) { this.currentRiddleQuestion = currentRiddleQuestion; }

    public List<String> getCurrentRiddleAnswers() { return currentRiddleAnswers; }
    public void setCurrentRiddleAnswers(List<String> currentRiddleAnswers) { this.currentRiddleAnswers = currentRiddleAnswers != null ? currentRiddleAnswers : new ArrayList<>(); }

    public String getRiddleSuccessReward() { return riddleSuccessReward; }
    public void setRiddleSuccessReward(String riddleSuccessReward) { this.riddleSuccessReward = riddleSuccessReward; }

    public String getRiddleFailReward() { return riddleFailReward; }
    public void setRiddleFailReward(String riddleFailReward) { this.riddleFailReward = riddleFailReward; }

    public long getRiddleExpireTime() { return riddleExpireTime; }
    public void setRiddleExpireTime(long riddleExpireTime) { this.riddleExpireTime = riddleExpireTime; }

    public int getBossBribeCost() { return bossBribeCost; }
    public void setBossBribeCost(int bossBribeCost) { this.bossBribeCost = bossBribeCost; }

    // --- Inventory ---

    public List<ItemStack> getInventory() { return inventory; }
    public void clearInventory() { inventory.clear(); }
    public void addItem(ItemStack item) {
        inventory.add(item);
    }
    public void addItems(List<ItemStack> items) {
        inventory.addAll(items);
    }
    public boolean removeItem(ItemStack item) {
        return inventory.remove(item);
    }
    public int getInventorySize() { return inventory.size(); }
    public boolean hasInventorySpace() { return inventory.size() < 36; }

    // --- State flags ---

    public boolean hasPet() { return (hasPet || petType != null) && isPetFed; }
    public void setHasPet(boolean hasPet) { this.hasPet = hasPet; }

    private boolean isPetFed = false;
    public boolean isPetFed() { return isPetFed; }
    public void setPetFed(boolean fed) { this.isPetFed = fed; }

    public String getPetType() { return petType; }
    public void setPetType(String petType) { this.petType = petType; }

    public boolean isNight() { return isNight; }
    public void setNight(boolean night) { isNight = night; }

    public int getConsecutiveWins() { return consecutiveWins; }
    public void setConsecutiveWins(int consecutiveWins) {
        this.consecutiveWins = Math.max(0, consecutiveWins);
    }
    public void incrementWins() { consecutiveWins++; }
    public void resetWins() { consecutiveWins = 0; }

    public int getDamageTakenTotal() { return damageTakenTotal; }
    public void addDamageTaken(int amount) { damageTakenTotal += amount; }

    // --- Encounter data ---

    public String getCurrentEncounterType() { return currentEncounterType; }
    public void setCurrentEncounterType(String currentEncounterType) { this.currentEncounterType = currentEncounterType; }

    public boolean isInCombat() { return inCombat; }
    public void setInCombat(boolean inCombat) { this.inCombat = inCombat; }

    public String getPendingEventTitle() { return pendingEventTitle; }
    public void setPendingEventTitle(String pendingEventTitle) { this.pendingEventTitle = pendingEventTitle; }

    public String getPendingEventDescription() { return pendingEventDescription; }
    public void setPendingEventDescription(String pendingEventDescription) {
        this.pendingEventDescription = pendingEventDescription;
    }

    public void clearPendingEvent() {
        this.pendingEventTitle = null;
        this.pendingEventDescription = null;
    }

    // --- Messages log ---

    public void addMessage(String msg) {
        messages.add(msg);
        if (messages.size() > 10) messages.remove(0);
    }
    public List<String> getMessages() { return new ArrayList<>(messages); }
    public void restoreMessages(List<String> restored) {
        messages.clear();
        if (restored != null) {
            messages.addAll(restored);
        }
    }

    /**
     * Восстановление полного состояния похода из хранилища.
     */
    public void applyPersistedState(
            String dungeonType,
            int stage,
            int maxStages,
            long nextEventTime,
            boolean waitingChoice,
            long endTime,
            long expeditionEndTime,
            int estimatedTotalMinutes,
            int hp,
            int maxHp,
            int level,
            int damage,
            int defense,
            boolean hasPet,
            String petType,
            boolean isPetFed,
            boolean isNight,
            int consecutiveWins,
            int damageTakenTotal,
            String activeModifier,
            int modifierDuration,
            String currentEncounterType,
            boolean inCombat,
            String pendingEventTitle,
            String pendingEventDescription,
            List<ItemStack> restoredInventory,
            List<String> restoredMessages,
            int baseDamage,
            int baseDefense,
            boolean waitingRiddle,
            String currentRiddleQuestion,
            List<String> currentRiddleAnswers,
            String riddleSuccessReward,
            String riddleFailReward,
            long riddleExpireTime,
            int bossBribeCost
    ) {
        this.dungeonType = dungeonType;
        this.stage = stage;
        this.maxStages = maxStages;
        this.nextEventTime = nextEventTime;
        this.waitingChoice = waitingChoice;
        this.endTime = endTime;
        this.expeditionEndTime = expeditionEndTime;
        this.estimatedTotalMinutes = estimatedTotalMinutes;
        this.hp = hp;
        this.maxHp = maxHp;
        this.level = level;
        this.damage = damage;
        this.defense = defense;
        this.hasPet = hasPet;
        this.petType = petType;
        this.isPetFed = isPetFed;
        this.isNight = isNight;
        this.consecutiveWins = consecutiveWins;
        this.damageTakenTotal = damageTakenTotal;
        this.activeModifier = activeModifier;
        this.modifierDuration = modifierDuration;
        this.currentEncounterType = currentEncounterType;
        this.inCombat = inCombat;
        this.pendingEventTitle = pendingEventTitle;
        this.pendingEventDescription = pendingEventDescription;
        inventory.clear();
        if (restoredInventory != null) {
            inventory.addAll(restoredInventory);
        }
        restoreMessages(restoredMessages);
        this.baseDamage = baseDamage;
        this.baseDefense = baseDefense;
        this.waitingRiddle = waitingRiddle;
        this.currentRiddleQuestion = currentRiddleQuestion;
        this.currentRiddleAnswers = currentRiddleAnswers != null ? currentRiddleAnswers : new ArrayList<>();
        this.riddleSuccessReward = riddleSuccessReward;
        this.riddleFailReward = riddleFailReward;
        this.riddleExpireTime = riddleExpireTime;
        this.bossBribeCost = bossBribeCost;
    }

    // --- Utility methods ---

    /**
     * Шанс успеха атаки (в процентах).
     */
    public int getAttackChance(String type) {
        int baseChance;
        switch (type) {
            case "aggressive": baseChance = 40; break;  // Рискованная атака
            case "careful": baseChance = 75; break;    // Осторожный удар
            case "escape": baseChance = 60; break;     // Попытка сбежать
            default: baseChance = 50;
        }

        // Модификаторы
        if (hasPet) baseChance += 10;         // Питомец улучшает шансы
        if (isNight && dungeonType.equals("forest")) baseChance += 15; // Лес ночью

        return Math.min(95, Math.max(5, baseChance + (level - 1) * 3));
    }

    /**
     * Расчет урона при получении.
     */
    public int calculateDamageTaken(int baseDamage) {
        int actualDamage = Math.max(1, baseDamage - defense);
        addDamageTaken(actualDamage);
        return actualDamage;
    }

    /**
     * Получение награды за победу.
     */
    public void onVictory(boolean isBoss) {
        incrementWins();
        setLevel(level + (isBoss ? 2 : 1)); // Повышение уровня

        // Бонус за серию побед
        if (consecutiveWins >= 3) {
            hp = Math.min(maxHp, hp + 15); // Лечение
        }
    }

    /**
     * Получение урона.
     */
    public void takeDamage(int damage, String reason) {
        int actualDamage = calculateDamageTaken(damage);
        if (isNight) {
            actualDamage = (int) (actualDamage * 1.20); // 20% больше урона ночью
        }
        int nextHp = hp - actualDamage;
        if (nextHp <= 0 && "Кошка".equalsIgnoreCase(petType) && isPetFed) {
            setHp(15);
            this.petType = null;
            addMessage("🐱 Кошка пожертвовала девятой жизнью, чтобы спасти тебя!");
            return;
        }
        setHp(nextHp);
        addMessage("⚠️ " + reason + " (-" + actualDamage + " HP)" + (isNight ? " 🌙 (Ночная ярость: +20% урона)" : ""));
    }

    /**
     * Лечение.
     */
    public void heal(int amount) {
        int oldHp = hp;
        setHp(hp + amount);
        if (hp > oldHp) {
            addMessage("❤️ Лечение: +" + (hp - oldHp) + " HP");
        }
    }

    /**
     * Проверка, выигран ли поход.
     */
    public boolean isComplete() {
        return stage > maxStages;
    }

    /**
     * Проверка, проигран ли поход.
     */
    public boolean isDead() {
        return hp <= 0;
    }

    /**
     * Форматирование статуса похода.
     */
    public String getStatusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("📍 Этап: ").append(stage).append("/").append(maxStages).append("\n");
        sb.append("❤️ Здоровье: ").append(hp).append("/").append(maxHp).append("\n");
        sb.append("⚔️ Урон: ").append(damage).append(" | 🛡️ Защита: ").append(defense).append("\n");
        sb.append("🏆 Серия: ").append(consecutiveWins).append(" побед подряд").append("\n");
        if (hasPet) sb.append("🐾 Питомец: есть\n");
        if (isNight) sb.append("🌙 Ночь (бонус к луту!)\n");
        return sb.toString();
    }

    /**
     * Клонирование экземпляра (для сохранения состояния).
     */
    public Expedition clone() {
        Expedition clone = new Expedition(peerId, senderId, playerUuid, dungeonType, maxStages);
        clone.stage = this.stage;
        clone.nextEventTime = this.nextEventTime;
        clone.waitingChoice = this.waitingChoice;
        clone.endTime = this.endTime;
        clone.expeditionEndTime = this.expeditionEndTime;
        clone.estimatedTotalMinutes = this.estimatedTotalMinutes;
        clone.activeModifier = this.activeModifier;
        clone.modifierDuration = this.modifierDuration;
        clone.hp = this.hp;
        clone.maxHp = this.maxHp;
        clone.level = this.level;
        clone.damage = this.damage;
        clone.defense = this.defense;
        clone.hasPet = this.hasPet;
        clone.petType = this.petType;
        clone.isPetFed = this.isPetFed;
        clone.isNight = this.isNight;
        clone.consecutiveWins = this.consecutiveWins;
        clone.damageTakenTotal = this.damageTakenTotal;
        clone.currentEncounterType = this.currentEncounterType;
        clone.inCombat = this.inCombat;
        clone.pendingEventTitle = this.pendingEventTitle;
        clone.pendingEventDescription = this.pendingEventDescription;
        clone.inventory.addAll(this.inventory);
        clone.messages.addAll(this.messages);
        clone.baseDamage = this.baseDamage;
        clone.baseDefense = this.baseDefense;
        clone.waitingRiddle = this.waitingRiddle;
        clone.currentRiddleQuestion = this.currentRiddleQuestion;
        clone.currentRiddleAnswers.addAll(this.currentRiddleAnswers);
        clone.riddleSuccessReward = this.riddleSuccessReward;
        clone.riddleFailReward = this.riddleFailReward;
        clone.riddleExpireTime = this.riddleExpireTime;
        clone.bossBribeCost = this.bossBribeCost;
        return clone;
    }
}
