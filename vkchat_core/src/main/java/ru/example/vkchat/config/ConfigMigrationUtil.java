package ru.example.vkchat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Утилита для миграции конфигов всех плагинов.
 * Добавляет недостающие ключи и удаляет устаревшие.
 */
public class ConfigMigrationUtil {

    /**
     * Мигрировать конфиг плагина: добавить новые ключи, удалить старые, сделать бэкап.
     * 
     * @param plugin плагин
     * @param resourceName имя ресурса (например "config.yml")
     * @param obsoleteKeys список ключей для принудительного удаления (можно пустой)
     */
    public static void migrate(JavaPlugin plugin, String resourceName, String... obsoleteKeys) {
        Logger logger = plugin.getLogger();
        File configFile = new File(plugin.getDataFolder(), resourceName);
        
        // Загружаем дефолтный конфиг из ресурсов
        InputStream defStream = plugin.getResource(resourceName);
        if (defStream == null) return;
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream, StandardCharsets.UTF_8));
        
        // Загружаем пользовательский конфиг
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);
        
        // Проверяем что нужно обновить
        boolean hasMissing = hasMissingKeys(userConfig, defConfig);
        
        if (!hasMissing) return;
        
        // Создаём бэкап
        try {
            if (configFile.exists()) {
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File backup = new File(plugin.getDataFolder(), resourceName + ".bak-" + stamp);
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Бэкап конфига: " + backup.getName());
            }
        } catch (IOException e) {
            logger.warning("Не удалось создать бэкап: " + e.getMessage());
        }
        
        // ── Миграция: ТОЛЬКО добавляем недостающие ключи ──
        // Удаление ключей ОТКЛЮЧЕНО — оно опасное и ломает пользовательские конфиги.
        // Принудительное удаление obsoleteKeys тоже отключено.
        // Устаревшие ключи просто игнорируются кодом, удаление — через ручное редактирование.

        if (hasMissing) {
            userConfig.setDefaults(defConfig);
            userConfig.options().copyDefaults(true);
            logger.info(resourceName + ": добавлены недостающие ключи.");
        }
        
        // Сохраняем
        try {
            userConfig.save(configFile);
            plugin.reloadConfig();
            logger.info(resourceName + " обновлён.");
        } catch (IOException e) {
            logger.warning("Не удалось сохранить " + resourceName + ": " + e.getMessage());
        }
    }

    /**
     * Проверить есть ли недостающие ключи
     */
    private static boolean hasMissingKeys(FileConfiguration userConfig, YamlConfiguration defConfig) {
        for (String key : defConfig.getKeys(true)) {
            if (!userConfig.isSet(key)) return true;
        }
        return false;
    }

    /**
     * Проверить есть ли устаревшие ключи
     */
    private static boolean hasObsoleteKeys(FileConfiguration userConfig, YamlConfiguration defConfig) {
        for (String key : userConfig.getKeys(true)) {
            if (!defConfig.isSet(key) && !key.equals("config-version")) return true;
        }
        return false;
    }

    /**
     * Удалить устаревшие ключи
     * 
     * @return список удалённых ключей
     */
    private static List<String> removeObsoleteKeys(YamlConfiguration userConfig, YamlConfiguration defConfig, String[] forceRemove) {
        List<String> removed = new ArrayList<>();
        
        // Удаляем ключи, которых нет в дефолте
        for (String key : userConfig.getKeys(true)) {
            if (!defConfig.isSet(key) && !key.equals("config-version")) {
                userConfig.set(key, null);
                removed.add(key);
            }
        }
        
        // Удаляем принудительно указанные ключи
        for (String key : forceRemove) {
            if (userConfig.isSet(key)) {
                userConfig.set(key, null);
                if (!removed.contains(key)) removed.add(key);
            }
        }
        
        return removed;
    }
}
