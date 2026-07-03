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
        if (getCommand("stream") != null)
            getCommand("stream").setExecutor(new StreamsCommand(this));

        validateConfig();

        getLogger().info("VKChatStreams запущен!");
    }

    private void validateConfig() {
        boolean anyPlatform = false;

        if (getConfig().getBoolean("streams.twitch.enabled", true)) {
            String cid = getConfig().getString("streams.twitch.client-id", "");
            String oauth = getConfig().getString("streams.twitch.oauth-token", "");
            if (!cid.isEmpty() && !cid.startsWith("YOUR_") && !oauth.isEmpty() && !oauth.startsWith("YOUR_"))
                anyPlatform = true;
            else
                getLogger().warning("Twitch: не настроены client-id / oauth-token.");
        }

        if (getConfig().getBoolean("streams.youtube.enabled", true)) {
            String key = getConfig().getString("streams.youtube.api-key", "");
            if (!key.isEmpty() && !key.startsWith("YOUR_"))
                anyPlatform = true;
            else
                getLogger().warning("YouTube: не настроен api-key.");
        }

        if (getConfig().getBoolean("streams.vk.enabled", true)) {
            String token = getConfig().getString("streams.vk.token", "");
            if (!token.isEmpty() && !token.startsWith("YOUR_"))
                anyPlatform = true;
            else
                getLogger().warning("VK: не настроен token.");
        }

        if (!anyPlatform)
            getLogger().warning("Ни одна платформа не настроена. Стримы не будут проверяться.");
        else
            getLogger().info("Платформы настроены, мониторинг стримов активен.");
    }

    @Override
    public void onDisable() {
        if (streamChecker != null) streamChecker.stop();
    }

    public static VKChatStreamsPlugin getInstance() { return instance; }
    public StreamChecker getStreamChecker() { return streamChecker; }
}
