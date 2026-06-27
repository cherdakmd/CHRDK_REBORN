package ru.example.vkchatoffline.managers;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Random;

/**
 * Чистая математика оффлайн-событий: риск, урон, награды, XP.
 *
 * Вынесено из AdventureManager без изменения баланса.
 */
public final class OfflineEventMath {
    private OfflineEventMath() {}

    public static int baseRisk(String type, int difficulty, int deaths, int rep) {
        int risk = 8 + difficulty * 4 + deaths * 2;
        if (type.equals("trap")) risk += 14;
        else if (type.equals("ambush")) risk += 12;
        else if (type.equals("curse")) risk += 10;
        else if (type.equals("combat")) risk += 8;
        else if (type.equals("survival")) risk += 6;
        else if (type.equals("treasure")) risk += 4;
        else if (type.equals("boss")) risk += 18;
        else if (isSlavicMyth(type)) risk += 13;
        else if (type.equals("moral")) risk += 8;
        else if (type.equals("oracle") || type.equals("blacksmith")) risk += 5;
        else if (type.equals("tavern")) risk += 6;
        else if (type.equals("nightmare")) risk += 14;
        else if (type.equals("memory")) risk += 5;
        else if (type.equals("companion")) risk += 4;
        else if (type.equals("collection")) risk += 7;
        else if (type.equals("extra")) risk += 9;
        else if (type.equals("gathering")) risk += 2;
        else if (type.equals("camp")) risk -= 8;
        else if (type.equals("merchant") || type.equals("shrine")) risk -= 4;
        if (rep > 2000) risk += 3;
        return risk;
    }

    public static int choiceRiskModifier(String type, int choice) {
        if (isCombatEvent(type)) {
            if (choice == 1) return 6;
            if (choice == 2) return -12;
            if (choice == 3) return 12;
            return type.equals("boss") ? 2 : -6;
        }
        if (choice == 1) return type.equals("treasure") ? 18 : 14;
        if (choice == 2) return -8;
        if (choice == 3) return type.equals("trap") ? 8 : 4;
        return type.equals("ambush") ? -4 : -14;
    }

    public static int damageFor(Random random, String type, int difficulty, int choice) {
        int d = 7 + random.nextInt(9) + difficulty * 3;
        if (type.equals("trap")) d += 10;
        if (type.equals("ambush")) d += 7;
        if (type.equals("curse")) d += 5;
        if (type.equals("survival")) d += 4;
        if (type.equals("boss")) d += 14;
        if (isSlavicMyth(type)) d += 9;
        if (type.equals("moral")) d += 4;
        if (type.equals("oracle")) d += 3;
        if (type.equals("blacksmith")) d += 5;
        if (type.equals("tavern")) d += 4;
        if (type.equals("nightmare")) d += 7;
        if (type.equals("memory")) d += 3;
        if (type.equals("companion")) d += 2;
        if (type.equals("collection")) d += 4;
        if (type.equals("extra")) d += 6;
        if (type.equals("camp")) d = Math.max(2, d - 8);
        if (isCombatEvent(type) && choice == 2) d = Math.max(1, d - 10);
        if (isCombatEvent(type) && choice == 3) d += 6;
        if (choice == 1) d += 4;
        if (choice == 4) d = Math.max(3, d - 6);
        return d;
    }

    public static int lethalChance(FileConfiguration config, String type, int difficulty, int choice) {
        int c = Math.max(0, difficulty - 1);
        if (type.equals("trap")) c += config.getInt("mechanics.traps.lethal-chance", 6);
        if (type.equals("ambush")) c += 4;
        if (type.equals("curse")) c += 3;
        if (type.equals("combat")) c += 2;
        if (type.equals("boss")) c += 10;
        if (isSlavicMyth(type)) c += 6;
        if (type.equals("moral") && choice == 1) c += 3;
        if (type.equals("nightmare")) c += 3;
        if (type.equals("extra")) c += 3;
        if (isCombatEvent(type) && choice == 2) c = Math.max(0, c - 5);
        if (isCombatEvent(type) && choice == 3) c += 3;
        if (choice == 1) c += 4;
        if (choice == 4) c = Math.max(0, c - 3);
        return Math.min(35, c);
    }

    public static int successReward(Random random, String type, int difficulty, int choice) {
        int bonus = 8 + random.nextInt(18) + difficulty * 6;
        if (isCombatEvent(type)) {
            if (choice == 1) bonus += 10;
            if (choice == 2) bonus = Math.max(1, bonus - 5);
            if (choice == 3) bonus += 22;
        }
        if (type.equals("treasure")) bonus += 30;
        if (type.equals("shrine")) bonus += 18;
        if (type.equals("merchant")) bonus += 12;
        if (type.equals("riddle")) bonus += 20;
        if (type.equals("boss")) bonus += 70;
        if (isSlavicMyth(type)) bonus += 45;
        if (type.equals("oracle")) bonus += 20;
        if (type.equals("blacksmith")) bonus += 25;
        if (type.equals("moral")) bonus += choice == 2 ? 25 : 10;
        if (type.equals("tavern")) bonus += 12;
        if (type.equals("nightmare")) bonus += 15;
        if (type.equals("memory")) bonus += 18;
        if (type.equals("companion")) bonus += 12;
        if (type.equals("collection")) bonus += 28;
        if (type.equals("extra")) bonus += 18;
        if (type.equals("gathering")) bonus += 10;
        if (type.equals("camp")) bonus += 5;
        if (choice == 1) bonus += 8;
        if (choice == 3) bonus += 10;
        return bonus;
    }

    public static int xpForEvent(String type, int difficulty, boolean success) {
        int xp = 8 + difficulty * 5;
        if (type.equals("boss")) xp += 45;
        if (type.equals("trap") || type.equals("ambush") || type.equals("curse")) xp += 8;
        if (!success) xp = Math.max(3, xp / 2);
        return xp;
    }

    public static boolean isCombatEvent(String type) {
        return type != null && (type.equals("combat") || type.equals("ambush") || type.equals("duel") || type.equals("boss") || type.equals("bogatyr") || type.equals("perun") || type.equals("zmey") || type.equals("koshchey"));
    }

    public static boolean isSlavicMyth(String type) {
        return Arrays.asList("baba_yaga", "leshy", "rusalka", "domovoi", "perun", "morana", "vodyanoy", "koshchey", "zmey", "bogatyr").contains(type);
    }
}
