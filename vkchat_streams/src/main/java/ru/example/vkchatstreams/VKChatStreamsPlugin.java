package ru.example.vkchatstreams;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatstreams.checker.StreamChecker;
import ru.example.vkchat.util.VKChatBridge;

public class VKChatStreamsPlugin extends JavaPlugin {
    private static VKChatStreamsPlugin instance;
    private StreamChecker streamChecker;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ru.example.vkchat.core.ConfigMigrationUtil.migrate(this, "config.yml");
        VKChatBridge.init();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        streamChecker = new StreamChecker(this);
        streamChecker.start();

        StreamsCommand scmd = new StreamsCommand(this);
        if (getCommand("streams") != null) {
            getCommand("streams").setExecutor(scmd);
            getCommand("streams").setTabCompleter(scmd);
        }
        if (getCommand("stream") != null) {
            getCommand("stream").setExecutor(scmd);
            getCommand("stream").setTabCompleter(scmd);
        }

        validateConfig();

        getLogger().info("VKChatStreams запущен!");
    }

    private void validateConfig() {
        boolean configured = false;

        if (getConfig().getBoolean("twitch.enabled", true)) {
            String cid = getConfig().getString("twitch.client-id", "");
            String oauth = getConfig().getString("twitch.oauth-token", "");
            String secret = getConfig().getString("twitch.client-secret", "");
            boolean hasToken = (!oauth.isEmpty() && !oauth.startsWith("YOUR_"))
                    || (!cid.isEmpty() && !cid.startsWith("YOUR_") && !secret.isEmpty() && !secret.startsWith("YOUR_"));
            if (hasToken)
                configured = true;
            else
                getLogger().warning("Twitch: не настроены oauth-token или client-id+client-secret.");
        }

        if (!configured)
            getLogger().warning("Twitch не настроен. Стримы не будут проверяться.");
        else
            getLogger().info("Twitch настроен, мониторинг стримов активен.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (streamChecker != null) streamChecker.stop();
        instance = null;
    }

    public static VKChatStreamsPlugin getInstance() { return instance; }
    public StreamChecker getStreamChecker() { return streamChecker; }
}
