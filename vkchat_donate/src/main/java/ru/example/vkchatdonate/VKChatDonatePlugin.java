package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.api.events.VKPlayerLinkEvent;
import ru.example.vkchat.core.ConfigMigrationUtil;
import ru.example.vkchatdonate.luckperms.LuckPermsHelper;
import ru.example.vkchatdonate.pass.PassCommand;
import ru.example.vkchatdonate.pass.PassManager;

/**
 * VKChatDonate v3.1 — полная переработка с выделенным PassManager.
 *
 * - Инициализация LuckPermsHelper при запуске
 * - Инициализация PassManager при запуске
 * - Проверка истёкших статусов и проходок при входе
 * - IMPROVE #3: Автоконвертация проходка → ВК при привязке
 * - Статистика в логе запуска
 */
public class VKChatDonatePlugin extends JavaPlugin {
    private static VKChatDonatePlugin instance;
    private DonateManager donateManager;
    private PassManager passManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigMigrationUtil.migrate(this, "config.yml");

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Плагин выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация LuckPerms API
        boolean lpAvailable = LuckPermsHelper.initialize();
        if (lpAvailable) {
            getLogger().info("LuckPerms API подключён!");
        } else {
            getLogger().warning("LuckPerms не найден — используются команды /lp (fallback)");
        }

        // Инициализация PassManager (до DonateManager!)
        passManager = new PassManager(this);
        getLogger().info("PassManager инициализирован");

        // Инициализация DonateManager
        donateManager = new DonateManager(this);

        // Регистрация /donate
        if (getCommand("donate") != null) {
            DonateCommand dc = new DonateCommand(this);
            getCommand("donate").setExecutor(dc);
            getCommand("donate").setTabCompleter(dc);
        }

        // IMPROVE #4: Регистрация /pass
        if (getCommand("pass") != null) {
            PassCommand pc = new PassCommand(this);
            getCommand("pass").setExecutor(pc);
            getCommand("pass").setTabCompleter(pc);
        }

        // Регистрация событий
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                if (donateManager != null) {
                    donateManager.addPlayerToFundraiser(e.getPlayer());
                    // Проверка истёкших донат-статусов
                    donateManager.checkExpiredStatus(e.getPlayer());
                }
                // FIX #2: Проверка истёкших проходок
                if (passManager != null) {
                    passManager.checkPassExpiry(e.getPlayer());
                }
            }
        }, this);

        // IMPROVE #3: Слушатель привязки ВК → автоконвертация проходки
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onVkLink(VKPlayerLinkEvent e) {
                if (passManager == null) return;
                if (passManager.hasPass(e.getPlayer())) {
                    // Задержка 1 секунду — чтобы привязка точно сохранилась
                    Bukkit.getScheduler().runTaskLater(instance, () -> {
                        passManager.convertPassToVk(e.getPlayer(), e.getVkId());
                    }, 20L);
                }
            }
        }, this);

        // Статистика при запуске
        var statusSec = getConfig().getConfigurationSection("statuses");
        int statusCount = statusSec != null ? statusSec.getKeys(false).size() : 0;
        getLogger().info("═══════════════════════════════════");
        getLogger().info("VKChatDonate v3.1 запущен!");
        getLogger().info("Статусов: " + statusCount);
        getLogger().info("LP API: " + (lpAvailable ? "✅" : "❌"));
        getLogger().info("Донатеров: " + donateManager.getDonorCount());
        getLogger().info("Всего пожертвовано: " + String.format("%.0f", donateManager.getTotalDonatedAll()) + "₽");
        getLogger().info("Проходок активно: " + passManager.getActivePassCount());
        getLogger().info("Проходок конвертировано: " + passManager.getTotalConverted());
        getLogger().info("═══════════════════════════════════");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (passManager != null) passManager.shutdown();
        if (donateManager != null) donateManager.shutdown();
        instance = null;
    }

    public static VKChatDonatePlugin getInstance() { return instance; }
    public DonateManager getDonateManager() { return donateManager; }
    public PassManager getPassManager() { return passManager; }
}
