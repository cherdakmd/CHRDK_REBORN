package ru.example.vkchatteleport;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatteleport.commands.TeleportCommand;
import ru.example.vkchatteleport.features.TeleportFeatures;
import ru.example.vkchatteleport.listeners.TeleportListener;
import ru.example.vkchatteleport.manager.TeleportManager;

import java.text.SimpleDateFormat;

public class VKChatTeleportPlugin extends JavaPlugin {
    private static VKChatTeleportPlugin instance;
    private TeleportManager teleportManager;
    private TeleportFeatures teleportFeatures;
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
        teleportFeatures = new TeleportFeatures(this);
        teleportManager.setFeatures(teleportFeatures);

        TeleportCommand commandExecutor = new TeleportCommand(this);
        getCommand("rtp").setExecutor(commandExecutor);
        getCommand("rtp").setTabCompleter(commandExecutor);
        getCommand("tpa").setExecutor(commandExecutor);
        getCommand("tpa").setTabCompleter(commandExecutor);
        getCommand("tpaccept").setExecutor(commandExecutor);
        getCommand("tpaccept").setTabCompleter(commandExecutor);
        getCommand("tpdeny").setExecutor(commandExecutor);
        getCommand("tpdeny").setTabCompleter(commandExecutor);
        getCommand("sethome").setExecutor(commandExecutor);
        getCommand("sethome").setTabCompleter(commandExecutor);
        getCommand("home").setExecutor(commandExecutor);
        getCommand("home").setTabCompleter(commandExecutor);
        getCommand("homes").setExecutor(commandExecutor);
        getCommand("homes").setTabCompleter(commandExecutor);
        getCommand("delhome").setExecutor(commandExecutor);
        getCommand("delhome").setTabCompleter(commandExecutor);
        getCommand("gateway").setExecutor(commandExecutor);
        getCommand("gateway").setTabCompleter(commandExecutor);
        getCommand("tpahere").setExecutor(commandExecutor);
        getCommand("tpahere").setTabCompleter(commandExecutor);
        getCommand("back").setExecutor(commandExecutor);
        getCommand("tphistory").setExecutor(commandExecutor);

        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getLogger().info("VKChatTeleport успешно запущен!");
    }

    private void migrateConfig() {
        // Принудительно обновляемые ключи
        String[] forceKeys = {
            "teleportation.rtp.cost",
            "teleportation.home.cost",
            "teleportation.tpa.cost",
            "teleportation.tpahere.cost",
            "teleportation.back.cost",
            "teleportation.rtp.cooldown",
            "teleportation.home.cooldown",
            "teleportation.tpa.cooldown",
            "teleportation.tpahere.cooldown",
            "teleportation.back.cooldown"
        };
        ru.example.vkchat.config.ConfigMigrationUtil.migrate(this, "config.yml", forceKeys);
    }

    public static VKChatTeleportPlugin getInstance() {
        return instance;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TeleportFeatures getTeleportFeatures() {
        return teleportFeatures;
    }
}
