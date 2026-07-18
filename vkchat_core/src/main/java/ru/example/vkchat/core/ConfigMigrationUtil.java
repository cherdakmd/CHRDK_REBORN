package ru.example.vkchat.core;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Shared utility for config migration logic used across all vkchat modules.
 */
public final class ConfigMigrationUtil {

    private ConfigMigrationUtil() {}

    /**
     * Migrate config defaults: if the config-version key in the bundled defaults
     * is greater than the one stored in the user's config, merge defaults in
     * (adding missing keys while preserving existing user values).
     *
     * @param config    the live FileConfiguration (from JavaPlugin.getConfig())
     * @param configFile the File object pointing to config.yml on disk
     * @param versionKey the key used to store/compare the config version (e.g. "config-version");
     *                   if null, migration always runs when keys are missing
     * @param logger    logger for backup/info messages
     */
    public static void migrateDefaults(FileConfiguration config, File configFile, String versionKey, Logger logger) {
        try {
            Configuration defaults = config.getDefaults();
            if (defaults == null) return;

            boolean needsMigration = false;
            if (versionKey != null && config.isSet(versionKey) && defaults.isSet(versionKey)) {
                int currentVersion = config.getInt(versionKey);
                int latestVersion = defaults.getInt(versionKey);
                if (currentVersion < latestVersion) {
                    needsMigration = true;
                }
            }
            if (!needsMigration) {
                for (String key : defaults.getKeys(true)) {
                    if (!config.isSet(key)) {
                        needsMigration = true;
                        break;
                    }
                }
            }
            if (!needsMigration) return;

            if (configFile.exists()) {
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File backup = new File(configFile.getParentFile(), configFile.getName() + ".bak-before-migration-" + stamp);
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Создан бэкап старого " + configFile.getName() + ": " + backup.getName());
            }

            config.options().copyDefaults(true);
            saveAndReload(config, configFile, logger);
            logger.info(configFile.getName() + " автоматически обновлён: недостающие ключи добавлены, существующие значения сохранены.");
        } catch (Exception e) {
            logger.warning("Не удалось выполнить авто-миграцию " + configFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Convenience overload that always runs when keys are missing (no version check).
     */
    public static void migrateDefaults(FileConfiguration config, File configFile, Logger logger) {
        migrateDefaults(config, configFile, null, logger);
    }

    /**
     * Rename a config key: copies the value from oldKey to newKey, then removes oldKey.
     * No-op if oldKey doesn't exist or newKey already has a value.
     *
     * @return true if a migration was performed
     */
    public static boolean migrateKey(FileConfiguration config, String oldKey, String newKey) {
        if (!config.isSet(oldKey) || config.isSet(newKey)) return false;
        config.set(newKey, config.get(oldKey));
        config.set(oldKey, null);
        return true;
    }

    /**
     * Move an entire configuration section from one path to another.
     * Creates the destination section with all values from source, then removes source.
     * No-op if source doesn't exist or destination already has values.
     *
     * @return true if a migration was performed
     */
    public static boolean migrateSection(FileConfiguration config, String fromSection, String toSection) {
        if (!config.isConfigurationSection(fromSection)) return false;
        if (config.isConfigurationSection(toSection) && !config.getConfigurationSection(toSection).getKeys(false).isEmpty()) {
            return false;
        }
        ConfigurationSection source = config.getConfigurationSection(fromSection);
        config.createSection(toSection, source.getValues(true));
        config.set(fromSection, null);
        return true;
    }

    /**
     * Add any keys present in defaults but missing in config.
     * Existing user values are preserved.
     *
     * @return true if any keys were added
     */
    public static boolean addMissingKeys(FileConfiguration config, FileConfiguration defaults) {
        if (defaults == null) return false;
        boolean added = false;
        for (String key : defaults.getKeys(true)) {
            if (!config.isSet(key)) {
                config.set(key, defaults.get(key));
                added = true;
            }
        }
        return added;
    }

    private static void saveAndReload(FileConfiguration config, File configFile, Logger logger) {
        try {
            if (config instanceof YamlConfiguration) {
                ((YamlConfiguration) config).save(configFile);
            }
        } catch (IOException e) {
            logger.warning("Не удалось сохранить " + configFile.getName() + ": " + e.getMessage());
        }
    }
}
