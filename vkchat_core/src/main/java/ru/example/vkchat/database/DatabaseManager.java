package ru.example.vkchat.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {
    private final VKChatPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            HikariConfig config = new HikariConfig();
            boolean useMysql = plugin.getConfig().getBoolean("database.use-mysql", false);
            
            if (useMysql) {
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String db = plugin.getConfig().getString("database.mysql.database", "vkchat");
                String user = plugin.getConfig().getString("database.mysql.username", "root");
                String pass = plugin.getConfig().getString("database.mysql.password", "");
                String props = plugin.getConfig().getString("database.mysql.properties", "?useSSL=false&autoReconnect=true");

                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + props);
                config.setUsername(user);
                config.setPassword(pass);
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                
                // Оптимизация пула для MySQL
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("useLocalSessionState", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");
            } else {
                // SQLite
                File dbFile = new File(plugin.getDataFolder(), "database.db");
                if (!dbFile.exists()) {
                    dbFile.getParentFile().mkdirs();
                    dbFile.createNewFile();
                }
                config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                config.setDriverClassName("org.sqlite.JDBC");
            }
            
            config.setPoolName("VKChat-Pool");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000); // 30 минут, чтобы предотвратить закрытие соединения MySQL сервером (wait_timeout)

            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Успешно инициализирован пул соединений " + (useMysql ? "MySQL" : "SQLite") + "!");
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось подключиться к базе данных!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        if (dataSource == null) return;
        
        try (Connection conn = getConnection()) {
            // Таблица авторизации
            String authTable = "CREATE TABLE IF NOT EXISTS vkchat_auth (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "vk_id INT DEFAULT -1, " +
                    "password VARCHAR(100), " +
                    "last_ip VARCHAR(50), " +
                    "reg_date BIGINT, " +
                    "is_donut BOOLEAN DEFAULT 0)";
            
            // Таблица репутации
            String repTable = "CREATE TABLE IF NOT EXISTS vkchat_reputation (" +
                    "vk_id INT PRIMARY KEY, " +
                    "points INT DEFAULT 0, " +
                    "last_message BIGINT DEFAULT 0, " +
                    "last_bonus BIGINT DEFAULT 0)";
            
            // Таблица игровой статистики
            String statsTable = "CREATE TABLE IF NOT EXISTS vkchat_stats (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "kills INT DEFAULT 0, " +
                    "deaths INT DEFAULT 0, " +
                    "blocks INT DEFAULT 0, " +
                    "achievements INT DEFAULT 0)";
            
            // Таблица промокодов
            String promoTable = "CREATE TABLE IF NOT EXISTS vkchat_promocodes (" +
                    "code VARCHAR(50) PRIMARY KEY, " +
                    "reward INT NOT NULL, " +
                    "max_uses INT NOT NULL, " +
                    "current_uses INT DEFAULT 0)";
            
            // Таблица активаций промокодов
            String activationsTable = "CREATE TABLE IF NOT EXISTS vkchat_promo_activations (" +
                    "code VARCHAR(50), " +
                    "vk_id INT, " +
                    "PRIMARY KEY (code, vk_id))";

            // Таблица мутов
            String mutesTable = "CREATE TABLE IF NOT EXISTS vkchat_mutes (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "expiry BIGINT, " +
                    "reason TEXT)";

            try (PreparedStatement s1 = conn.prepareStatement(authTable);
                 PreparedStatement s2 = conn.prepareStatement(repTable);
                 PreparedStatement s3 = conn.prepareStatement(statsTable);
                 PreparedStatement s4 = conn.prepareStatement(promoTable);
                 PreparedStatement s5 = conn.prepareStatement(activationsTable);
                 PreparedStatement s6 = conn.prepareStatement(mutesTable)) {
                s1.execute();
                s2.execute();
                s3.execute();
                s4.execute();
                s5.execute();
                s6.execute();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при создании таблиц в БД!");
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            connect();
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
