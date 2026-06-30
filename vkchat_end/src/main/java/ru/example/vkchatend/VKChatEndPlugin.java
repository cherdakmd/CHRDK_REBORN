package ru.example.vkchatend;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatend.managers.*;
import ru.example.vkchatend.listeners.*;
import ru.example.vkchatend.commands.*;

public class VKChatEndPlugin extends JavaPlugin {
    private static VKChatEndPlugin instance;

    private EndManager endManager;
    private EndBossManager endBossManager;
    private EndCityManager endCityManager;
    private EndOreManager endOreManager;
    private EndCorruptionManager endCorruptionManager;
    private EndRiftManager endRiftManager;
    private EndArtifactManager endArtifactManager;
    private EndPortalManager endPortalManager;
    private EndShulkerManager endShulkerManager;
    private EndEnchantmentManager endEnchantmentManager;
    private EndProgressManager endProgressManager;
    private EndResourceManagers endResourceManagers;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! vkchat_end выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация менеджеров
        endManager = new EndManager(this);
        endBossManager = new EndBossManager(this);
        endCityManager = new EndCityManager(this);
        endOreManager = new EndOreManager(this);
        endCorruptionManager = new EndCorruptionManager(this);
        endRiftManager = new EndRiftManager(this);
        endArtifactManager = new EndArtifactManager(this);
        endPortalManager = new EndPortalManager(this);
        endShulkerManager = new EndShulkerManager(this);
        endEnchantmentManager = new EndEnchantmentManager(this);
        endProgressManager = new EndProgressManager(this);
        endResourceManagers = new EndResourceManagers(this);

        // Регистрация событий
        getServer().getPluginManager().registerEvents(new EndListener(this), this);
        getServer().getPluginManager().registerEvents(endBossManager, this);
        getServer().getPluginManager().registerEvents(endOreManager, this);
        getServer().getPluginManager().registerEvents(endCorruptionManager, this);
        getServer().getPluginManager().registerEvents(endRiftManager, this);
        getServer().getPluginManager().registerEvents(endPortalManager, this);

        // Регистрация команд
        EndCommand endCmd = new EndCommand(this);
        getCommand("end").setExecutor(endCmd);
        getCommand("end").setTabCompleter(endCmd);

        getLogger().info("═══════════════════════════════════════");
        getLogger().info("VKChatEnd v2.0 — 35 обновлений!");
        getLogger().info("Боссы: " + endBossManager.getBossCount());
        getLogger().info("Руды: " + endOreManager.getOreCount());
        getLogger().info("Артефакты: " + endArtifactManager.getArtifactCount());
        getLogger().info("Зачарования: " + endEnchantmentManager.getEnchantmentCount());
        getLogger().info("Зелья: " + endEnchantmentManager.getPotionCount());
        getLogger().info("Рыба: " + endEnchantmentManager.getFishCount());
        getLogger().info("Урожай: " + endEnchantmentManager.getCropCount());
        getLogger().info("Достижения: " + endProgressManager.getAchievementCount());
        getLogger().info("═══════════════════════════════════════");
    }

    @Override
    public void onDisable() {
        if (endBossManager != null) endBossManager.saveAll();
        if (endCorruptionManager != null) endCorruptionManager.saveAll();
        if (endRiftManager != null) endRiftManager.saveAll();
    }

    public static VKChatEndPlugin getInstance() { return instance; }
    public EndManager getEndManager() { return endManager; }
    public EndBossManager getEndBossManager() { return endBossManager; }
    public EndCityManager getEndCityManager() { return endCityManager; }
    public EndOreManager getEndOreManager() { return endOreManager; }
    public EndCorruptionManager getEndCorruptionManager() { return endCorruptionManager; }
    public EndRiftManager getEndRiftManager() { return endRiftManager; }
    public EndArtifactManager getEndArtifactManager() { return endArtifactManager; }
    public EndPortalManager getEndPortalManager() { return endPortalManager; }
    public EndShulkerManager getEndShulkerManager() { return endShulkerManager; }
    public EndEnchantmentManager getEndEnchantmentManager() { return endEnchantmentManager; }
    public EndProgressManager getEndProgressManager() { return endProgressManager; }
    public EndResourceManagers getEndResourceManagers() { return endResourceManagers; }

    public World getEndWorld() {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                .findFirst().orElse(null);
    }
}
