package ru.example.vkchatoffline.managers;

import ru.example.vkchatoffline.managers.ZoneData.*;
import ru.example.vkchatoffline.managers.AdventureManager.AdventureState;

import java.util.*;

/**
 * Боевая система — пошаговые бои с навыками, эффектами и критами
 */
public class CombatManager {

    public void showCombatUI(AdventureManager mgr, int vkId) {
        showCombatUI(mgr, vkId, null);
    }

    public void showCombatUI(AdventureManager mgr, int vkId, String prefix) {
        AdventureState s = mgr.getState(vkId);
        if (s == null || s.enemy == null) return;

        String hpBar = getBar(s.hp, s.maxHp, 10);
        String eHpBar = getBar(s.enemyHp, s.enemyMaxHp, 10);

        String msg = (prefix != null ? prefix + "\n\n" : "")
                + "═══════════════════════════\n"
                + "⚔ " + s.enemy.icon + " " + s.enemy.name + "\n"
                + "═══════════════════════════\n\n"
                + "❤ Вы: " + s.hp + "/" + s.maxHp + " " + hpBar + "\n"
                + "❤ Враг: " + s.enemyHp + "/" + s.enemyMaxHp + " " + eHpBar + "\n"
                + "⚡ Энергия: " + s.energy + "/" + s.maxEnergy + "\n\n"
                + "Раунд: " + (s.combatRound + 1) + "\n"
                + "[1] ⚔ Атака  [2] 🛡 Защита\n"
                + "[3] 💥 Навык  [4] 🧪 Зелье\n"
                + "[5] 🏃 Побег";

        if (s.effectType != null) {
            msg += "\n\n" + s.effectType + " (" + s.effectTurns + " ходов)";
        }

        mgr.sendWithKb(vkId, msg, Keyboards.combatActions(true, 4));
    }

    public void handleAction(AdventureManager mgr, int vkId, int action, String... extra) {
        AdventureState s = mgr.getState(vkId);
        if (s == null || s.state != AdventureManager.State.COMBAT) return;
        Random rnd = new Random();

        if (s.skillCooldown > 0) s.skillCooldown--;

        s.combatRound++;
        boolean playerDefending = false;
        StringBuilder result = new StringBuilder();
        result.append("⚔ Раунд ").append(s.combatRound).append("\n\n");

        // === ХОД ИГРОКА ===
        switch (action) {
            case 1: // Атака
                int dmg = Math.max(1, (s.baseAtk + rnd.nextInt(6)) - (s.enemy.def / 2));
                if (rnd.nextInt(100) < 15) { dmg *= 2; result.append("💥 КРИТ! "); }
                s.enemyHp -= dmg;
                result.append("⚔ Ваша атака! → ").append(dmg).append(" урона\n");
                break;

            case 2: // Защита
                playerDefending = true;
                s.defending = true;
                result.append("🛡 Вы в защите! Урон снижен.\n");
                break;

            case 3: { // Навык
                String skillIndex = extra.length > 0 ? extra[0] : "1";
                int idx = Integer.parseInt(skillIndex) - 1;
                Map<String, List<Skill>> skills = ZoneData.getSkills();
                List<Skill> classSkills = skills.get(s.className);
                if (classSkills != null && idx >= 0 && idx < classSkills.size()) {
                    Skill skill = classSkills.get(idx);
                    int level = mgr.getPlayerData().getLevel(vkId);
                    if (level < skill.requiredLevel) {
                        result.append("❌ Нужен уровень ").append(skill.requiredLevel).append("\n");
                    } else if (s.energy < skill.energyCost) {
                        result.append("❌ Недостаточно энергии! (").append(skill.energyCost).append(")\n");
                    } else {
                        s.energy -= skill.energyCost;
                        int skillDmg = (int)(s.baseAtk * 1.8) + rnd.nextInt(10);
                        if (rnd.nextInt(100) < 20) { skillDmg *= 2; result.append("💥 СУПЕР-КРИТ! "); }
                        s.enemyHp -= Math.max(1, skillDmg - s.enemy.def);
                        result.append("💥 ").append(skill.name).append("! → ").append(skillDmg).append(" урона\n");
                        s.skillCooldown = 2;
                    }
                } else {
                    result.append("❌ Навык недоступен.\n");
                }
                break;
            }

            case 4: // Зелье
                if (s.supplies > 0) {
                    s.supplies--;
                    int heal = s.maxHp / 4 + rnd.nextInt(10);
                    s.hp = Math.min(s.maxHp, s.hp + heal);
                    result.append("🧪 Зелье! → +").append(heal).append(" HP\n");
                } else {
                    result.append("❌ Нет припасов!\n");
                }
                break;

            case 5: // Побег
                if (rnd.nextInt(100) < 25) {
                    result.append("🏃 Побег удался!\n");
                    s.state = AdventureManager.State.RESULT;
                    mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatWin());
                    return;
                }
                result.append("🏃 Побег не удался!\n");
                break;
        }

        // Эффекты
        if (s.effectType != null && s.effectTurns > 0) {
            if (s.effectType.equals("poison")) {
                int poison = s.maxHp / 20;
                s.hp -= poison;
                result.append("☠ Яд! -").append(poison).append(" HP\n");
            } else if (s.effectType.equals("regen")) {
                int regen = s.maxHp / 15;
                s.hp = Math.min(s.maxHp, s.hp + regen);
                result.append("💚 Реген! +").append(regen).append(" HP\n");
            }
            s.effectTurns--;
            if (s.effectTurns <= 0) s.effectType = null;
        }

        // Проверка победы
        if (s.enemyHp <= 0) {
            s.state = AdventureManager.State.RESULT;
            int xp = s.zone.difficulty * 15 + (s.enemy.name.contains("BOSS") || s.enemy.name.contains("Лорд") ? 50 : 0);
            int rep = s.enemy.atk * 2 + s.zone.difficulty * 10;
            int gold = 2 + rnd.nextInt(5 + s.zone.difficulty * 2);

            s.repEarned += rep;
            s.gold += gold;
            mgr.getPlayerData().addXp(vkId, xp);
            mgr.getPlayerData().addKill(vkId);

            boolean isBoss = s.enemy.name.contains("BOSS") || s.enemy.name.contains("Лорд")
                    || s.enemy.name.contains("Дракон") || s.enemy.name.contains("Владыка");
            if (isBoss) mgr.getPlayerData().addBoss(vkId);

            result.append("\n☠ ").append(s.enemy.name).append(" повержен!\n");
            result.append("⭐ +").append(rep).append(" реп | ✨ +").append(xp).append(" XP\n");
            result.append("💰 +").append(gold).append(" золота");

            mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatWin());
            return;
        }

        // Проверка смерти от эффектов
        if (s.hp <= 0) {
            s.state = AdventureManager.State.DEAD;
            result.append("\n💀 Вы погибли!");
            mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatLose());
            return;
        }

        // === ХОД ВРАГА ===
        int enemyDmg = Math.max(1, (s.enemy.atk + rnd.nextInt(5)) - (s.baseDef + (playerDefending ? 3 : 0)) / 2);
        if (rnd.nextDouble() < 0.1) {
            result.append("💨 Вы уклонились!\n");
        } else {
            s.hp -= enemyDmg;
            result.append("💥 ").append(s.enemy.name).append(" атакует! → ").append(enemyDmg).append(" урона\n");
        }

        if (s.hp <= 0) {
            s.state = AdventureManager.State.DEAD;
            result.append("\n💀 Вы погибли!");
            mgr.sendWithKb(vkId, result.toString(), Keyboards.afterCombatLose());
            return;
        }

        if (!playerDefending) s.defending = false;
        s.energy = Math.min(s.maxEnergy, s.energy + 5);

        showCombatUI(mgr, vkId, result.toString());
    }

    private String getBar(int cur, int max, int len) {
        int filled = (int)((double) cur / max * len);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }
}
