package ru.example.vkchatmarket.data;

import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TradeLogger — выделенный логгер транзакций и истории рынка.
 * Отделяет логику записи от бизнес-логики MarketManager.
 */
public class TradeLogger {

    private final VKChatMarketPlugin plugin;
    private final List<String> history = new CopyOnWriteArrayList<>();
    private int maxHistoryEntries = 100;

    public TradeLogger(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Загрузить историю из YAML.
     */
    public void loadFrom(org.bukkit.configuration.file.FileConfiguration data) {
        maxHistoryEntries = plugin.getConfig().getInt("market2.history.max-entries", 100);
        history.clear();
        history.addAll(data.getStringList("history"));
    }

    /**
     * Сохранить историю в YAML.
     */
    public void saveTo(org.bukkit.configuration.file.FileConfiguration data) {
        while (history.size() > maxHistoryEntries) {
            history.remove(0);
        }
        data.set("history", history);
    }

    /**
     * Добавить запись в историю рынка.
     */
    public void addHistoryEntry(String line) {
        String stamp = new SimpleDateFormat("dd.MM HH:mm").format(new Date());
        history.add(stamp + " — " + line);
        while (history.size() > maxHistoryEntries) {
            history.remove(0);
        }
    }

    /**
     * Получить последние N записей.
     */
    public List<String> getHistoryTail(int limit) {
        int from = Math.max(0, history.size() - Math.max(1, limit));
        return new ArrayList<>(history.subList(from, history.size()));
    }

    /**
     * Записать транзакцию в файл transactions.log.
     */
    public void logTransaction(String player, String itemId, int amount,
                               String type, double price, int rep) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = stamp + " | " + player + " | " + type + " | "
                + itemId + " x" + amount + " | цена: "
                + String.format("%.2f", price) + " | реп: " + rep;
        appendToFile("transactions.log", line);
    }

    /**
     * Записать торговый объём (для логов крупных сделок).
     */
    public void logTradeVolume(String itemId, int amount, String type, String trendLabel) {
        int threshold = plugin.getConfig().getInt("market2.history.min-volume-log", 16);
        if (amount >= threshold) {
            addHistoryEntry((type.equals("sell") ? "📤" : "📥") + " " + itemId
                    + " x" + amount + " " + trendLabel);
        }
    }

    /**
     * Дописать строку в файл.
     */
    private void appendToFile(String fileName, String line) {
        File logFile = new File(plugin.getDataFolder(), fileName);
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter out = new PrintWriter(fw)) {
            out.println(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось записать в " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Полный размер истории.
     */
    public int size() {
        return history.size();
    }
}
