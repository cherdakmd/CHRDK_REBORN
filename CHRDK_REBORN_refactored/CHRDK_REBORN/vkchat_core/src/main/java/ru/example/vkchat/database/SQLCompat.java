package ru.example.vkchat.database;

import ru.example.vkchat.VKChatPlugin;

/**
 * Утилита для совместимости SQL запросов между SQLite и MySQL
 */
public class SQLCompat {
    private final VKChatPlugin plugin;
    private final boolean isMySQL;

    public SQLCompat(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.isMySQL = plugin.getConfig().getBoolean("database.use-mysql", false);
    }

    /**
     * INSERT OR UPDATE запрос
     * SQLite: INSERT OR REPLACE
     * MySQL: INSERT ... ON DUPLICATE KEY UPDATE
     */
    public String upsert(String table, String columns, String values, String updateSet) {
        if (isMySQL) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + updateSet;
        } else {
            return "INSERT OR REPLACE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        }
    }

    /**
     * INSERT OR INCREMENT запрос
     * SQLite: INSERT OR REPLACE INTO ... VALUES (?, ?+1)
     * MySQL: INSERT INTO ... ON DUPLICATE KEY UPDATE col = col + ?
     */
    public String insertOrIncrement(String table, String keyCol, String valCol) {
        if (isMySQL) {
            return "INSERT INTO " + table + " (" + keyCol + ", " + valCol + ") VALUES (?, ?) " +
                   "ON DUPLICATE KEY UPDATE " + valCol + " = " + valCol + " + ?";
        } else {
            return "INSERT OR REPLACE INTO " + table + " (" + keyCol + ", " + valCol + ") VALUES (?, " +
                   "COALESCE((SELECT " + valCol + " FROM " + table + " WHERE " + keyCol + " = ?), 0) + ?)";
        }
    }

    /**
     * Проверка использования MySQL
     */
    public boolean isMySQL() {
        return isMySQL;
    }
}
