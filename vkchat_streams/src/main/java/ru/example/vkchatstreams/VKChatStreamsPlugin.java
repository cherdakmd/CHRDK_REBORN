package ru.example.vkchatstreams;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatstreams.checker.StreamChecker;

public class VKChatStreamsPlugin extends JavaPlugin {
    private static VKChatStreamsPlugin instance;
    private StreamChecker streamChecker;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ru.example.vkchat.util.VKChatBridge.init();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        streamChecker = new StreamChecker(this);
        streamChecker.start();

        if (getCommand("streams") != null)
            getCommand("streams").setExecutor(new StreamsCommand(this));

        getLogger().info("VKChatStreams запущен!");
    }

    @Override
    public void onDisable() {
        if (streamChecker != null) streamChecker.stop();
    }

    public static VKChatStreamsPlugin getInstance() { return instance; }
    public StreamChecker getStreamChecker() { return streamChecker; }
}
