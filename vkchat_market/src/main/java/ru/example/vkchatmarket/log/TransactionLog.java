package ru.example.vkchatmarket.log;

import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public class TransactionLog {
    private final Path logDir;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TransactionLog(File dataFolder) {
        this.logDir = dataFolder.toPath().resolve("logs");
        try { Files.createDirectories(logDir); } catch (IOException ignored) {}
    }

    public void log(Player player, String action, String itemId, int amount, int price, String extra) {
        String line = String.format("[%s] %s | %s | %s | %d x %d реп. | %s",
                LocalDateTime.now().format(fmt),
                player.getName(),
                player.getUniqueId(),
                action,
                amount,
                price,
                itemId,
                extra != null ? extra : ""
        );
        writeLine(line);
    }

    public void logSystem(String message) {
        String line = String.format("[%s] SYSTEM | %s", LocalDateTime.now().format(fmt), message);
        writeLine(line);
    }

    private void writeLine(String line) {
        Path file = logDir.resolve(FILE_FMT.format(LocalDateTime.now()) + ".log");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            java.util.logging.Logger.getLogger("VKChatMarket").log(Level.WARNING, "Failed to write transaction log", e);
        }
    }
}
