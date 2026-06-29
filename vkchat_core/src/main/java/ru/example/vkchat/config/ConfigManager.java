package ru.example.vkchat.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.example.vkchat.VKChatPlugin;

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
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    private final VKChatPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private FileConfiguration badwords;
    private File badwordsFile;
    private FileConfiguration donutConfig;
    private File donutFile;
    private FileConfiguration eventsConfig;
    private File eventsFile;
    private FileConfiguration hardcoreConfig;
    private File hardcoreFile;
    private final Pattern hexPattern = Pattern.compile("&#[a-fA-F0-9]{6}");

    public ConfigManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        updateMainConfigWithDefaults();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try { plugin.saveResource("messages.yml", false); } catch (Exception ex) {
                plugin.getLogger().warning("messages.yml не найден в JAR, создаю дефолтный.");
                try { messagesFile.createNewFile(); } catch (Exception ignored) {}
            }
        }
        updateYamlWithDefaults(messagesFile, "messages.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        badwordsFile = new File(plugin.getDataFolder(), "badwords.yml");
        if (!badwordsFile.exists()) {
            try { plugin.saveResource("badwords.yml", false); } catch (Exception ex) {
                plugin.getLogger().warning("badwords.yml не найден в JAR, создаю дефолтный.");
                try { badwordsFile.createNewFile(); } catch (Exception ignored) {}
            }
        }
        updateYamlWithDefaults(badwordsFile, "badwords.yml");
        badwords = YamlConfiguration.loadConfiguration(badwordsFile);

        donutFile = new File(plugin.getDataFolder(), "vkdonut.yml");
        if (!donutFile.exists()) {
            try { plugin.saveResource("vkdonut.yml", false); } catch (Exception ex) {
                plugin.getLogger().warning("vkdonut.yml не найден в JAR, создаю дефолтный.");
                try { donutFile.createNewFile(); } catch (Exception ignored) {}
            }
        }
        updateYamlWithDefaults(donutFile, "vkdonut.yml");
        donutConfig = YamlConfiguration.loadConfiguration(donutFile);

        eventsFile = new File(plugin.getDataFolder(), "events.yml");
        if (!eventsFile.exists()) {
            try {
                plugin.saveResource("events.yml", false);
            } catch (Exception ex) {
                plugin.getLogger().warning("events.yml не найден в JAR, создаю дефолтный.");
                try { eventsFile.createNewFile(); } catch (Exception ignored) {}
            }
        }
        updateYamlWithDefaults(eventsFile, "events.yml");
        eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);

        hardcoreFile = new File(plugin.getDataFolder(), "hardcore.yml");
        if (!hardcoreFile.exists()) {
            try {
                plugin.saveResource("hardcore.yml", false);
            } catch (Exception ex) {
                plugin.getLogger().warning("hardcore.yml не найден в JAR, создаю дефолтный.");
                try { hardcoreFile.createNewFile(); } catch (Exception ignored) {}
            }
        }
        updateYamlWithDefaults(hardcoreFile, "hardcore.yml");
        hardcoreConfig = YamlConfiguration.loadConfiguration(hardcoreFile);
    }

    private void updateMainConfigWithDefaults() {
        FileConfiguration config = plugin.getConfig();
        InputStream defStream = plugin.getResource("config.yml");
        if (defStream == null) return;
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        config.setDefaults(defConfig);
        boolean hasMissing = hasMissingKeys(config, defConfig);
        if (hasMissing) backupConfigFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml");
        config.options().copyDefaults(true);
        plugin.saveConfig();
        if (hasMissing) plugin.getLogger().info("config.yml автоматически обновлён: недостающие ключи добавлены, старые значения сохранены.");
    }

    private void updateYamlWithDefaults(File file, String resourceName) {
        InputStream defStream = plugin.getResource(resourceName);
        if (defStream == null) return;
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(file);
        userConfig.setDefaults(defConfig);
        boolean hasMissing = hasMissingKeys(userConfig, defConfig);
        boolean hasObsolete = hasObsoleteKeys(userConfig, defConfig);
        if (hasMissing || hasObsolete) backupConfigFile(file, resourceName);
        userConfig.options().copyDefaults(true);
        if (hasObsolete) {
            removeObsoleteKeys(userConfig, defConfig);
            plugin.getLogger().info(resourceName + ": удалены устаревшие ключи.");
        }
        try {
            userConfig.save(file);
            if (hasMissing) plugin.getLogger().info(resourceName + " автоматически обновлён: недостающие ключи добавлены.");
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить обновленный конфиг " + resourceName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean hasObsoleteKeys(FileConfiguration userConfig, YamlConfiguration defConfig) {
        for (String key : userConfig.getKeys(true)) {
            if (!defConfig.isSet(key) && !key.equals("config-version")) return true;
        }
        return false;
    }

    private void removeObsoleteKeys(FileConfiguration userConfig, YamlConfiguration defConfig) {
        List<String> toRemove = new ArrayList<>();
        for (String key : userConfig.getKeys(true)) {
            if (!defConfig.isSet(key) && !key.equals("config-version")) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            userConfig.set(key, null);
        }
        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("Удалено устаревших ключей: " + toRemove.size());
        }
    }

    private boolean hasMissingKeys(FileConfiguration userConfig, YamlConfiguration defConfig) {
        for (String key : defConfig.getKeys(true)) {
            if (!userConfig.isSet(key)) return true;
        }
        return false;
    }

    private void backupConfigFile(File file, String resourceName) {
        try {
            if (file == null || !file.exists()) return;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backup = new File(file.getParentFile(), resourceName + ".bak-before-migration-" + stamp);
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Создан бэкап старого " + resourceName + ": " + backup.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось создать бэкап " + resourceName + ": " + e.getMessage());
        }
    }

    public FileConfiguration getDonutConfig() {
        return donutConfig;
    }
    
    public FileConfiguration getEventsConfig() {
        return eventsConfig;
    }
    
    public FileConfiguration getHardcoreConfig() {
        return hardcoreConfig;
    }

    public java.util.List<String> getBadWords() {
        return badwords.getStringList("words");
    }

    public String getMessage(String path) {
        String msg = messages.getString(path, "Missing message: " + path);
        return formatColor(msg);
    }
    
    public String getPrefix() {
        return getMessage("prefix");
    }

    public String formatColor(String msg) {
        if (msg == null) return null;
        try {
            Matcher matcher = hexPattern.matcher(msg);
            while (matcher.find()) {
                String color = msg.substring(matcher.start(), matcher.end());
                msg = msg.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)) + "");
                matcher = hexPattern.matcher(msg);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Фолбэк для старых версий (до 1.16), где нет метода of(String)
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}