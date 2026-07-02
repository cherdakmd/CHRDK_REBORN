package ru.example.vkchatoffline.managers;

import ru.example.vkchatoffline.managers.ZoneData.*;
import ru.example.vkchatoffline.managers.AdventureManager.AdventureState;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Боевая система v2.0 — статус-эффекты, способности врагов, криты, фазы боссов
 */
public class CombatManager {

    private static final String[] CRIT_MSGS = {"💥 КРИТ!", "⚡ СОКРУШИТЕЛЬНЫЙ УДАР!", "🔥 КРИТИЧЕСКОЕ ПОПАДАНИЕ!"};
    private static final String[] DODGE_MSGS = {"💨 Уклонение!", "🌀 Промах!", "👻 Мимо!"};
    private static final String[] BLOCK_MSGS = {"🛡 Заблокировано!", "🛡 Отражено!", "🛡 Щит выдержал!"};

    public void showCombatUI(AdventureManager mgr, int vkId) {
        showCombatUI(mgr, vkId, null);
    }

    public void showCombatUI(AdventureManager mgr, int vkId, String prefix) {
        AdventureState s = mgr.getState(vkId);
        if (s == null || s.enemy == null) return;

        String hpBar = getBar(s.hp, s.maxHp, 10);
        String eHpBar = getBar(s.enemyHp, s.enemyMaxHp, 10);

        boolean isBoss = isBossEnemy(s.enemy);
        String enemyLabel = isBoss ? "☠ БОСС" : "⚔ Враг";

        String msg = (prefix != null ? prefix + "\n\n" : "")
                + "══════════════════════\n"
                + enemyLabel + ": " + s.enemy.icon + " " + s.enemy.name + "\n"
                + "══════════════════════\n\n"
                + "❤ Вы: " + s.hp + "/" + s.maxHp + " " + hpBar + "\n"
                + "❤ " + s.enemy.name + ": " + s.enemyHp + "/" + s.enemyMaxHp + " " + eHpBar + "\n"
                + "⚡ Энергия: " + s.energy + "/" + s.maxEnergy + "\n";

        if (isBoss) {
            double pct = (double) s.enemyHp / s.enemyMaxHp;
            String phase;
            if (pct > 0.6) phase = "😤 Ярость";
            else if (pct > 0.3) phase = "😈 Берсерк";
            else phase = "💀 Агония";
            msg += "🔥 Фаза: " + phase + "\n";
        }

        msg += "\n⚔ [Атака] 🛡 [Защита] 💥 [Навык]\n🧪 [Зелье] 🏃 [Побег]";

        if (s.statusEffects != null && !s.statusEffects.isEmpty()) {
            msg += "\n\n📌 Эффекты:";
            for (Map.Entry<String, Integer> eff : s.statusEffects.entrySet()) {
                msg += " " + getStatusIcon(eff.getKey()) + "(" + eff.getValue() + ")";
            }
        }

        mgr.sendWithKb(vkId, msg, Keyboards.combatActions(true, 4));
    }

    public void handleAction(AdventureManager mgr, int vkId, int action, String... extra) {
        AdventureState s = mgr.getState(vkId);
        if (s == null || s.state != AdventureManager.State.COMBAT) return;
        Random rnd = ThreadLocalRandom.current();

        if (s.skillCooldown > 0) s.skillCooldown--;
        processStatusEffects(s);

        s.combatRound++;
        boolean playerDefending = false;
        StringBuilder result = new StringBuilder();
        result.append("⚔ Раунд ").append(s.combatRound);
        if (!s.comboCount.isEmpty()) result.append(" | 🔥 Комбо: x").append(s.comboCount.size());
        result.append("\n\n");

        // === ХОД ИГРОКА ===
        boolean stunned = hasStatus(s, "stun");
        if (stunned) {
            result.append("😵 Вы оглушены! Пропуск хода.\n");
        } else {
            switch (action) {
                case 1 -> { // Атака
                    int dmg = Math.max(1, (s.baseAtk + rnd.nextInt(8)) - (s.enemy.def / 2));
                    boolean crit = rnd.nextInt(100) < 15 + getComboBonus(s);
                    if (crit) {
                        dmg = (int)(dmg * 2.2);
                        result.append(CRIT_MSGS[rnd.nextInt(CRIT_MSGS.length)]).append(" ");
                    }
                    dmg = applyBurnBonus(s, dmg);
                    s.enemyHp -= dmg;
                    result.append("⚔ Атака! → ").append(dmg).append(" урона\n");
                    addStatus(s, "combo", 3);
                    s.comboCount.add(System.currentTimeMillis());
                }
                case 2 -> { // Защита
                    playerDefending = true;
                    s.defending = true;
                    s.defenseBonus = 5;
                    result.append("🛡 Защита! +5 ЗАЩ, снижен урон.\n");
                }
                case 3 -> { // Навык
                    handleSkillUse(mgr, vkId, s, extra, result, rnd);
                }
                case 4 -> { // Зелье
                    if (s.supplies > 0) {
                        s.supplies--;
                        int heal = s.maxHp / 3 + rnd.nextInt(15);
                        s.hp = Math.min(s.maxHp, s.hp + heal);
                        result.append("🧪 Зелье! → +").append(heal).append(" HP");
                        if (rnd.nextInt(100) < 30) {
                            s.statusEffects.remove("poison");
                            s.statusEffects.remove("burn");
                            result.append(" + снятие яда/огня");
                        }
                        result.append("\n");
                    } else {
                        result.append("❌ Нет припасов!\n");
                    }
                }
                case 5 -> { // Побег
                    boolean isBoss = isBossEnemy(s.enemy);
                    if (isBoss) {
                        result.append("❌ От босса не сбежать!\n");
                    } else if (rnd.nextInt(100) < 30) {
                        result.append("🏃 Побег удался!\n");
                        s.state = AdventureManager.State.RESULT;
                        mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatWin());
                        return;
                    } else {
                        result.append("🏃 Побег не удался!\n");
                    }
                }
            }
        }

        // Обработка статус-эффектов после хода
        applyStatusDamage(s, result);

        // Проверка победы
        if (s.enemyHp <= 0) {
            victory(mgr, vkId, s, result);
            return;
        }

        // Проверка смерти от статусов
        if (s.hp <= 0) {
            death(mgr, vkId, s, result);
            return;
        }

        // === ХОД ВРАГА ===
        if (!stunned && enemyAlive(s)) {
            enemyTurn(s, result, rnd, playerDefending);
        }

        // Финальные проверки
        if (s.hp <= 0) {
            death(mgr, vkId, s, result);
            return;
        }

        // Очистка временных эффектов
        s.defenseBonus = 0;
        s.energy = Math.min(s.maxEnergy, s.energy + 8);
        s.comboCount.removeIf(ts -> System.currentTimeMillis() - ts > 30000);

        showCombatUI(mgr, vkId, result.toString());
    }

    private void handleSkillUse(AdventureManager mgr, int vkId, AdventureState s,
                                 String[] extra, StringBuilder result, Random rnd) {
        String skillIndex = extra.length > 0 ? extra[0] : "1";
        int idx;
        try { idx = Integer.parseInt(skillIndex) - 1; } catch (NumberFormatException e) { idx = 0; }

        Map<String, List<Skill>> skills = ZoneData.getSkills();
        List<Skill> classSkills = skills.get(s.className);
        if (classSkills == null || idx < 0 || idx >= classSkills.size()) {
            result.append("❌ Навык недоступен.\n");
            return;
        }

        Skill skill = classSkills.get(idx);
        int level = mgr.getPlayerData().getLevel(vkId);
        if (level < skill.requiredLevel) {
            result.append("❌ Нужен уровень ").append(skill.requiredLevel).append(" (ваш: ").append(level).append(")\n");
            return;
        }
        if (s.energy < skill.energyCost) {
            result.append("❌ Мало энергии! (нужно ").append(skill.energyCost).append(")\n");
            return;
        }

        s.energy -= skill.energyCost;
        double baseMult = 1.8;

        switch (s.className) {
            case "WARRIOR" -> {
                switch (idx) {
                    case 0 -> { // Мощный удар
                        int dmg = (int)(s.baseAtk * 2.0) + rnd.nextInt(10) - s.enemy.def;
                        s.hp -= 2; // self damage
                        s.enemyHp -= Math.max(1, dmg);
                        result.append("💥 Мощный удар! → ").append(dmg).append(" урона (-2 HP себе)\n");
                    }
                    case 1 -> { // Берсерк
                        addStatus(s, "berserk", 3);
                        addStatus(s, "vulnerable", 3);
                        result.append("😤 Берсерк! +50% атаки, -5 защиты на 3 хода\n");
                    }
                    case 2 -> { // Круговой удар
                        int dmg = (int)(s.baseAtk * 1.5) + rnd.nextInt(5);
                        s.enemyHp -= Math.max(1, dmg);
                        result.append("🌀 Круговой удар! → ").append(dmg).append(" урона\n");
                    }
                    case 3 -> { // Казнь
                        if ((double) s.enemyHp / s.enemyMaxHp < 0.2) {
                            s.enemyHp = 0;
                            result.append("☠ КАЗНЬ! Мгновенное убийство!\n");
                        } else {
                            int dmg = (int)(s.baseAtk * 1.2);
                            s.enemyHp -= Math.max(1, dmg);
                            result.append("⚔ Казнь → ").append(dmg).append(" урона (HP врага > 20%)\n");
                        }
                    }
                }
            }
            case "RANGER" -> {
                switch (idx) {
                    case 0 -> { // Меткий
                        int dmg = Math.max(1, (int)(s.baseAtk * 1.5) - (s.enemy.def / 4));
                        s.enemyHp -= dmg;
                        result.append("🎯 Меткий выстрел! → ").append(dmg).append(" урона (игнор 50% защиты)\n");
                    }
                    case 1 -> { // Град стрел
                        for (int i = 0; i < 3; i++) {
                            int dmg = Math.max(1, (int)(s.baseAtk * 0.6) + rnd.nextInt(3));
                            s.enemyHp -= dmg;
                            if (i == 0) result.append("🏹 Град стрел: ").append(dmg);
                            else result.append(" + ").append(dmg);
                        }
                        result.append(" урона\n");
                    }
                    case 2 -> { // Ядовитая
                        int dmg = (int)(s.baseAtk * 1.3) + rnd.nextInt(5);
                        s.enemyHp -= Math.max(1, dmg);
                        addStatus(s, "poison", 3);
                        result.append("🐍 Ядовитая стрела! → ").append(dmg).append(" урона + яд на 3 хода\n");
                    }
                    case 3 -> { // Выстрел в голову
                        if (rnd.nextInt(100) < 60) {
                            int dmg = (int)(s.baseAtk * 3.0);
                            s.enemyHp -= dmg;
                            result.append("💀 ВЫСТРЕЛ В ГОЛОВУ! → ").append(dmg).append(" урона (x3)!\n");
                        } else {
                            result.append("💨 Промах! Пропуск хода.\n");
                            addStatus(s, "stun", 1);
                        }
                    }
                }
            }
            case "MAGE" -> {
                switch (idx) {
                    case 0 -> { // Огненный шар
                        int dmg = (int)(s.baseAtk * 1.5) + rnd.nextInt(10);
                        s.enemyHp -= Math.max(1, dmg);
                        addStatus(s, "burn_enemy", 2);
                        result.append("🔥 Огненный шар! → ").append(dmg).append(" урона + горение\n");
                    }
                    case 1 -> { // Ледяная стрела
                        int dmg = s.baseAtk + rnd.nextInt(8);
                        s.enemyHp -= Math.max(1, dmg);
                        addStatus(s, "freeze_enemy", 2);
                        result.append("❄ Ледяная стрела! → ").append(dmg).append(" урона + замедление\n");
                    }
                    case 2 -> { // Цепная молния
                        int dmg = (int)(s.baseAtk * 1.8) + rnd.nextInt(15);
                        s.enemyHp -= Math.max(1, dmg);
                        if (rnd.nextInt(100) < 25) addStatus(s, "stun_enemy", 1);
                        result.append("⚡ Цепная молния! → ").append(dmg).append(" урона");
                        if (hasStatus(s, "stun_enemy")) result.append(" + оглушение!");
                        result.append("\n");
                        s.energy -= 5;
                    }
                    case 3 -> { // Метеор
                        s.skillCooldown = 3;
                        int dmg = (int)(s.baseAtk * 3.0) + rnd.nextInt(20);
                        s.enemyHp -= Math.max(1, dmg);
                        result.append("☄ МЕТЕОР! → ").append(dmg).append(" урона (кулдаун 3 хода)\n");
                    }
                }
            }
            case "PALADIN" -> {
                switch (idx) {
                    case 0 -> { // Исцеление
                        int heal = s.maxHp / 3 + rnd.nextInt(10);
                        s.hp = Math.min(s.maxHp, s.hp + heal);
                        result.append("✨ Святое исцеление! → +").append(heal).append(" HP\n");
                    }
                    case 1 -> { // Щит
                        addStatus(s, "shield", 2);
                        result.append("🛡 Божественный щит! Блокирует урон на 2 хода\n");
                    }
                    case 2 -> { // Кара
                        int dmg = (int)(s.baseAtk * 2.0) + rnd.nextInt(12);
                        s.enemyHp -= Math.max(1, dmg);
                        result.append("⚡ Кара небес! → ").append(dmg).append(" урона\n");
                    }
                    case 3 -> { // Аура
                        addStatus(s, "regen", 5);
                        result.append("☀ Аура света! Реген 5% HP на 5 ходов\n");
                    }
                }
            }
            case "ASSASSIN" -> {
                switch (idx) {
                    case 0 -> { // Удар в спину
                        if (s.combatRound <= 1) {
                            int dmg = (int)(s.baseAtk * 2.5) + rnd.nextInt(15);
                            s.enemyHp -= dmg;
                            result.append("🗡 УДАР В СПИНУ! → ").append(dmg).append(" урона (x2.5 первый ход)!\n");
                        } else {
                            int dmg = (int)(s.baseAtk * 1.2);
                            s.enemyHp -= Math.max(1, dmg);
                            result.append("🗡 Удар → ").append(dmg).append(" урона\n");
                        }
                    }
                    case 1 -> { // Ядовитый клинок
                        int dmg = (int)(s.baseAtk * 1.4) + rnd.nextInt(5);
                        s.enemyHp -= Math.max(1, dmg);
                        addStatus(s, "poison_enemy", 3);
                        result.append("☠ Ядовитый клинок! → ").append(dmg).append(" урона + яд 3 хода\n");
                    }
                    case 2 -> { // Тень
                        addStatus(s, "dodge", 1);
                        result.append("🌑 Шаг в тень! Уклонение на 1 ход, след. атака x2\n");
                    }
                    case 3 -> { // Смертельный
                        if (hasStatus(s, "poison_enemy") || hasStatus(s, "burn_enemy")) {
                            int dmg = (int)(s.baseAtk * 2.5) + rnd.nextInt(15);
                            s.enemyHp -= dmg;
                            result.append("💀 СМЕРТЕЛЬНЫЙ УДАР! → ").append(dmg).append(" урона (цель отравлена)!\n");
                        } else {
                            int dmg = (int)(s.baseAtk * 1.1);
                            s.enemyHp -= Math.max(1, dmg);
                            result.append("🗡 Удар → ").append(dmg).append(" урона (цель не отравлена)\n");
                        }
                    }
                }
            }
        }
    }

    // === ХОД ВРАГА ===
    private void enemyTurn(AdventureState s, StringBuilder result, Random rnd, boolean playerDefending) {
        boolean isBoss = isBossEnemy(s.enemy);
        boolean frozen = hasStatus(s, "freeze_enemy");

        if (frozen) {
            result.append("❄ ").append(s.enemy.name).append(" заморожен! Пропуск хода.\n");
            decrementStatus(s, "freeze_enemy");
            return;
        }

        // Босс: 30% шанс на спецспособность
        if (isBoss && rnd.nextInt(100) < 30) {
            bossSpecialAbility(s, result, rnd);
            return;
        }

        // Обычная атака
        int rawDmg = s.enemy.atk + rnd.nextInt(6);
        int def = s.baseDef + s.defenseBonus;
        if (playerDefending) def += 3;

        // Проверка уклонения
        if (hasStatus(s, "dodge") || rnd.nextInt(100) < 5) {
            result.append(DODGE_MSGS[rnd.nextInt(DODGE_MSGS.length)]).append("\n");
            decrementStatus(s, "dodge");
            return;
        }

        // Проверка щита
        if (hasStatus(s, "shield")) {
            rawDmg = rawDmg / 4;
            result.append(BLOCK_MSGS[rnd.nextInt(BLOCK_MSGS.length)]).append(" ");
        }

        int enemyDmg = Math.max(1, rawDmg - def / 2);

        // Статусные модификаторы
        if (hasStatus(s, "vulnerable")) enemyDmg = (int)(enemyDmg * 1.5);
        if (hasStatus(s, "burn_enemy")) enemyDmg = (int)(enemyDmg * 0.8);

        s.hp -= enemyDmg;
        result.append("💥 ").append(s.enemy.name).append(" атакует! → ").append(enemyDmg).append(" урона");

        // Доп. эффекты от врага
        if (s.enemy == EnemyType.SPIDER || s.enemy == EnemyType.CAVE_SPIDER) {
            if (rnd.nextInt(100) < 15) { addStatus(s, "poison", 3); result.append(" + яд!"); }
        }
        if (s.enemy == EnemyType.BLAZE || s.enemy == EnemyType.NETHER_LORD) {
            if (rnd.nextInt(100) < 20) { addStatus(s, "burn", 2); result.append(" + горение!"); }
        }
        if (s.enemy == EnemyType.FROST_WYRM || s.enemy == EnemyType.FROST_DRAGON) {
            if (rnd.nextInt(100) < 15) { addStatus(s, "freeze", 1); result.append(" + заморозка!"); }
        }
        result.append("\n");
    }

    private void bossSpecialAbility(AdventureState s, StringBuilder result, Random rnd) {
        String bossName = s.enemy.name;
        switch (s.enemy) {
            case TREANT_BOSS -> {
                int heal = s.enemyMaxHp / 5;
                s.enemyHp = Math.min(s.enemyMaxHp, s.enemyHp + heal);
                result.append("🌳 ").append(bossName).append(" исцеляется! → +").append(heal).append(" HP\n");
            }
            case WORM_BOSS -> {
                addStatus(s, "stun", 1);
                int dmg = s.enemy.atk + rnd.nextInt(5);
                s.hp -= dmg;
                result.append("🪱 ").append(bossName).append(" сотрясает землю! → ").append(dmg).append(" урона + оглушение\n");
            }
            case LICH_BOSS -> {
                addStatus(s, "curse", 3);
                result.append("💀 ").append(bossName).append(" накладывает проклятие! -20% урона на 3 хода\n");
            }
            case NETHER_LORD -> {
                int dmg = s.enemy.atk * 2 + rnd.nextInt(8);
                s.hp -= dmg;
                addStatus(s, "burn", 3);
                result.append("🔥 ").append(bossName).append(": ОГНЕННЫЙ ШТОРМ! → ").append(dmg).append(" урона + горение\n");
            }
            case FROST_DRAGON -> {
                int dmg = s.enemy.atk + rnd.nextInt(5);
                s.hp -= dmg;
                addStatus(s, "freeze", 2);
                result.append("❄ ").append(bossName).append(": ЛЕДЯНОЕ ДЫХАНИЕ! → ").append(dmg).append(" урона + заморозка\n");
            }
            case VOID_LORD -> {
                int dmg = (int)(s.hp * 0.15);
                s.hp -= dmg;
                result.append("🌀 ").append(bossName).append(": ВЫТЯГИВАНИЕ ДУШИ! → ").append(dmg).append(" урона (15% HP)\n");
            }
            default -> {
                int dmg = (int)(s.enemy.atk * 1.5) + rnd.nextInt(8);
                s.hp -= Math.max(1, dmg);
                result.append("💢 ").append(bossName).append(": ЯРОСТНЫЙ УДАР! → ").append(dmg).append(" урона\n");
            }
        }
    }

    // === ПОБЕДА ===
    private void victory(AdventureManager mgr, int vkId, AdventureState s, StringBuilder result) {
        s.state = AdventureManager.State.RESULT;
        Random rnd = ThreadLocalRandom.current();
        boolean isBoss = isBossEnemy(s.enemy);

        int baseXp = s.zone.difficulty * 15;
        int xp = isBoss ? baseXp * 3 + 50 : baseXp;
        int rep = s.enemy.atk * 2 + s.zone.difficulty * 10;
        if (isBoss) rep *= 3;
        int gold = 2 + rnd.nextInt(5 + s.zone.difficulty * 2);
        if (isBoss) gold = gold * 4 + rnd.nextInt(20);

        s.repEarned += rep;
        s.gold += gold;
        mgr.getPlayerData().addXp(vkId, xp);
        mgr.getPlayerData().addKill(vkId);
        if (isBoss) mgr.getPlayerData().addBoss(vkId);

        result.append("\n☠ ").append(s.enemy.name).append(" ПОВЕРЖЕН!\n");
        if (isBoss) result.append("👑 БОСС УНИЧТОЖЕН!\n");
        result.append("⭐ +").append(rep).append(" реп | ✨ +").append(xp).append(" XP\n");
        result.append("💰 +").append(gold).append(" золота");

        // Доп. награда за босса
        if (isBoss && rnd.nextInt(100) < 50) {
            addStatus(s, "boss_reward", 1);
            result.append("\n🎁 Бонус: шанс на редкий ресурс!");
        }

        mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatWin());
    }

    // === СМЕРТЬ ===
    private void death(AdventureManager mgr, int vkId, AdventureState s, StringBuilder result) {
        s.state = AdventureManager.State.DEAD;
        result.append("\n\n💀 ВЫ ПОГИБЛИ!\n");
        result.append("⚰ ").append(s.enemy.name).append(" оказался сильнее...");
        mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatLose());
    }

    // === СТАТУС-ЭФФЕКТЫ ===
    private void processStatusEffects(AdventureState s) {
        if (s.statusEffects == null) s.statusEffects = new LinkedHashMap<>();
        s.statusEffects.entrySet().removeIf(e -> e.getValue() <= 0);
        List<String> toDecrement = new ArrayList<>(s.statusEffects.keySet());
        // Уменьшаем только эффекты с duration
        for (String key : toDecrement) {
            if (!key.equals("combo")) {
                s.statusEffects.merge(key, -1, Integer::sum);
            }
        }
    }

    private void applyStatusDamage(AdventureState s, StringBuilder result) {
        if (hasStatus(s, "poison")) {
            int dmg = Math.max(1, s.maxHp / 20);
            s.hp -= dmg;
            result.append("☠ Яд! -").append(dmg).append(" HP\n");
            checkAndRemoveStatus(s, "poison");
        }
        if (hasStatus(s, "burn")) {
            int dmg = Math.max(1, s.maxHp / 15);
            s.hp -= dmg;
            result.append("🔥 Горение! -").append(dmg).append(" HP\n");
            checkAndRemoveStatus(s, "burn");
        }
        if (hasStatus(s, "regen")) {
            int heal = Math.max(1, s.maxHp / 15);
            s.hp = Math.min(s.maxHp, s.hp + heal);
            result.append("💚 Реген! +").append(heal).append(" HP\n");
            checkAndRemoveStatus(s, "regen");
        }
        if (hasStatus(s, "poison_enemy") && enemyAlive(s)) {
            int dmg = Math.max(1, s.enemyMaxHp / 25);
            s.enemyHp -= dmg;
            result.append("☠ Яд на враге! -").append(dmg).append(" HP\n");
        }
        if (hasStatus(s, "burn_enemy") && enemyAlive(s)) {
            int dmg = Math.max(1, s.enemyMaxHp / 20);
            s.enemyHp -= dmg;
            result.append("🔥 Горение врага! -").append(dmg).append(" HP\n");
        }
        decrementTimedStatuses(s);
    }

    private void decrementTimedStatuses(AdventureState s) {
        for (String key : new ArrayList<>(s.statusEffects.keySet())) {
            if (!key.equals("combo") && !key.equals("boss_reward")) {
                s.statusEffects.merge(key, -1, Integer::sum);
            }
        }
        s.statusEffects.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    private void addStatus(AdventureState s, String effect, int duration) {
        if (s.statusEffects == null) s.statusEffects = new LinkedHashMap<>();
        // Не добавляем если уже есть более сильный
        int current = s.statusEffects.getOrDefault(effect, 0);
        if (duration > current) s.statusEffects.put(effect, duration);
    }

    private boolean hasStatus(AdventureState s, String effect) {
        return s.statusEffects != null && s.statusEffects.getOrDefault(effect, 0) > 0;
    }

    private void decrementStatus(AdventureState s, String effect) {
        if (s.statusEffects != null) s.statusEffects.merge(effect, -1, Integer::sum);
    }

    private void checkAndRemoveStatus(AdventureState s, String effect) {
        if (s.statusEffects != null && s.statusEffects.getOrDefault(effect, 0) <= 1) {
            s.statusEffects.remove(effect);
        }
    }

    private int applyBurnBonus(AdventureState s, int dmg) {
        if (hasStatus(s, "berserk")) return (int)(dmg * 1.5);
        if (hasStatus(s, "curse")) return (int)(dmg * 0.8);
        return dmg;
    }

    private int getComboBonus(AdventureState s) {
        s.comboCount.removeIf(ts -> System.currentTimeMillis() - ts > 30000);
        int count = s.comboCount.size();
        return Math.min(15, count * 3);
    }

    private boolean enemyAlive(AdventureState s) {
        return s.enemyHp > 0;
    }

    private boolean isBossEnemy(EnemyType enemy) {
        return enemy == EnemyType.TREANT_BOSS || enemy == EnemyType.WORM_BOSS
            || enemy == EnemyType.LICH_BOSS || enemy == EnemyType.NETHER_LORD
            || enemy == EnemyType.FROST_DRAGON || enemy == EnemyType.VOID_LORD;
    }

    private String getStatusIcon(String effect) {
        return switch (effect) {
            case "poison", "poison_enemy" -> "☠";
            case "burn", "burn_enemy" -> "🔥";
            case "freeze", "freeze_enemy" -> "❄";
            case "stun", "stun_enemy" -> "😵";
            case "regen" -> "💚";
            case "shield" -> "🛡";
            case "berserk" -> "😤";
            case "curse" -> "💀";
            case "dodge" -> "🌑";
            case "vulnerable" -> "⚠";
            default -> "📌";
        };
    }

    private String getBar(int cur, int max, int len) {
        int filled = (int)((double) cur / max * len);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }
}
