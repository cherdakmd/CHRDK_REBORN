package ru.example.vkchatteleport;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatteleport.commands.TeleportCommand;
import ru.example.vkchatteleport.listeners.TeleportListener;
import ru.example.vkchatteleport.manager.TeleportManager;

import java.text.SimpleDateFormat;

public class VKChatTeleportPlugin extends JavaPlugin {
    private static VKChatTeleportPlugin instance;
    private TeleportManager teleportManager;
    private static final int CONFIG_VERSION = 2;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        teleportManager = new TeleportManager(this);

        TeleportCommand commandExecutor = new TeleportCommand(this);
        getCommand("rtp").setExecutor(commandExecutor);
        getCommand("tpa").setExecutor(commandExecutor);
        getCommand("tpa").setTabCompleter(commandExecutor);
        getCommand("tpaccept").setExecutor(commandExecutor);
        getCommand("tpdeny").setExecutor(commandExecutor);
        getCommand("sethome").setExecutor(commandExecutor);
        getCommand("home").setExecutor(commandExecutor);
        getCommand("home").setTabCompleter(commandExecutor);
        getCommand("homes").setExecutor(commandExecutor);
        getCommand("delhome").setExecutor(commandExecutor);
        getCommand("delhome").setTabCompleter(commandExecutor);
        getCommand("gateway").setExecutor(commandExecutor);
        getCommand("gateway").setTabCompleter(commandExecutor);

        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getLogger().info("VKChatTeleport успешно запущен!");
    }

    private void migrateConfig() {
        try {
            int currentVersion = getConfig().getInt("config-version", 0);
            if (currentVersion >= CONFIG_VERSION) return;

            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Принудительно обновляем критические значения
            java.io.InputStream defStream = getResource("config.yml");
            if (defStream != null) {
                org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                
                String[] forceKeys = {
                    "teleportation.rtp.cost",
                    "teleportation.home.cost", 
                    "teleportation.tpa.cost",
                    "teleportation.rtp.cooldown",
                    "teleportation.home.cooldown",
                    "teleportation.tpa.cooldown"
                };
                
                for (String key : forceKeys) {
                    if (defConfig.isSet(key)) {
                        getConfig().set(key, defConfig.get(key));
                    }
                }
            }

            getConfig().set("config-version", CONFIG_VERSION);
            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
            getLogger().info("Конфиг телепортации обновлён до v" + CONFIG_VERSION);
        } catch (Exception e) {
            getLogger().warning("Ошибка миграции конфига телепортации: " + e.getMessage());
        }
    }

    public static VKChatTeleportPlugin getInstance() {
        return instance;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}
