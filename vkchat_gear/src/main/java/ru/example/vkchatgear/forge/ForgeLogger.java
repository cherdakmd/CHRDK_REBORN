package ru.example.vkchatgear.forge;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ForgeLogger — выделенный логгер операций кузни.
 *
 * FIX #3: Логирование вынесено из ForgeCommand (SRP)
 * IMPROVE #3: Структурированные записи с типом операции
 */
public final class ForgeLogger {

    private final File logFile;

    public ForgeLogger(Plugin plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.logFile = new File(plugin.getDataFolder(), "forge-log.log");
    }

    /**
     * Записать операцию в лог.
     *
     * @param player  имя игрока
     * @param action  тип операции (FUSION_START, FUSION_SUCCESS, FUSION_FAIL, REFORGE, CLEANSE, REPAIR, BUY_SCROLL, RUNE_CLEANSING)
     * @param details детали операции
     */
    public void log(String player, String action, String details) {
        try (FileWriter fw = new FileWriter(logFile, true)) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            fw.write(stamp + " | " + action + " | " + player + " | "
                    + details.replace('\n', ' ') + "\n");
        } catch (IOException e) {
            // Тихо игнорируем — лог кузни не должен крашить сервер
        }
    }

    /**
     * Записать операцию с результатом (успех/провал).
     */
    public void log(String player, String action, String details, boolean success) {
        log(player, success ? action + "_OK" : action + "_FAIL", details);
    }

    /**
     * Записать покупку свитка.
     */
    public void logScrollPurchase(String player, String scrollType, int price) {
        log(player, "BUY_SCROLL", scrollType + " price=" + price);
    }

    /**
     * Записать операцию слияния.
     */
    public void logFusion(String player, String fromRarity, String toRarity, int chance, int repCost, boolean success) {
        log(player, success ? "FUSION_SUCCESS" : "FUSION_FAIL",
                fromRarity + "→" + toRarity + " chance=" + chance + "% rep=" + repCost);
    }

    /**
     * Записать операцию заточки.
     */
    public void logUpgrade(String player, String item, int fromLevel, int toLevel, String crystalTier, boolean success) {
        log(player, success ? "UPGRADE_SUCCESS" : "UPGRADE_FAIL",
                item + " +" + fromLevel + "→+" + toLevel + " crystal=" + crystalTier);
    }
}
