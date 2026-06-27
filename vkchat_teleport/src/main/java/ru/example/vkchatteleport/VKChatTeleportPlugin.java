package ru.example.vkchatteleport;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatteleport.commands.TeleportCommand;
import ru.example.vkchatteleport.listeners.TeleportListener;
import ru.example.vkchatteleport.manager.TeleportManager;

public class VKChatTeleportPlugin extends JavaPlugin {
    private static VKChatTeleportPlugin instance;
    private TeleportManager teleportManager;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getLogger().info("VKChatTeleport успешно запущен и инициализирован!");
    }

    public static VKChatTeleportPlugin getInstance() {
        return instance;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}
