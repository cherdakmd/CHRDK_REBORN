package ru.example.vkchatnations;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatnations.data.NationManager;
import ru.example.vkchatnations.listeners.NationGuiListener;
import ru.example.vkchatnations.listeners.NationListener;
import ru.example.vkchatnations.listeners.ClaimDefenseListener;
import ru.example.vkchatnations.gui.MapGui;
import ru.example.vkchatnations.gui.ClaimGui;
import ru.example.vkchatnations.tasks.TaxTask;
import ru.example.vkchatnations.listeners.PreventListener;
import ru.example.vkchatnations.commands.NationCommand;
import ru.example.vkchatnations.commands.ClaimCommand;

public class VKChatNationsPlugin extends JavaPlugin {
    private static VKChatNationsPlugin instance;
    private NationManager nationManager;
    private MapGui mapGui;
    private ClaimGui claimGui;
    private NationGuiListener guiListener;


    private void migrateConfigDefaultsAndNationNames() {
        try {
            reloadConfig();
            java.io.InputStream defStream = getResource("config.yml");
            if (defStream != null) {
                org.bukkit.configuration.file.YamlConfiguration def = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                getConfig().setDefaults(def);
                getConfig().options().copyDefaults(true);
            }
            boolean changed = false;
            changed |= replaceIfLegacy("nations.soviet_light.name", "&cСветлый Совет (Пролетариат)", "&cСовет");
            changed |= replaceIfLegacy("nations.soviet_light.prefix", "&c[Труд]", "&c[Совет]");
            changed |= replaceIfLegacy("nations.soviet_light.description", "Светлая советская нация. Ускоренная добыча.", "Советская нация. Ускоренная добыча.");
            changed |= replaceIfLegacy("nations.soviet_dark.name", "&4Темный Совет (КГБ)", "&4КГБ");
            changed |= replaceIfLegacy("nations.soviet_dark.description", "Темная советская нация. Скорость и скрытность.", "Скрытная нация. Скорость и невидимость.");
            changed |= replaceIfLegacy("nations.pagan_light.name", "&aСветлые Язычники (Волхвы)", "&aВолхвы");
            changed |= replaceIfLegacy("nations.pagan_light.description", "Светлая языческая нация. Регенерация.", "Природная нация. Регенерация.");
            changed |= replaceIfLegacy("nations.pagan_dark.name", "&2Темные Язычники (Культ)", "&2Культ");
            changed |= replaceIfLegacy("nations.pagan_dark.description", "Темная языческая нация. Жестокость.", "Культовая нация. Сила в бою.");
            changed |= replaceIfLegacy("nations.imperial_light.name", "&eСветлая Империя (Богатыри)", "&eРусь");
            changed |= replaceIfLegacy("nations.imperial_light.description", "Светлая нация Царя. Защитники.", "Защитная нация. Стойкость.");
            changed |= replaceIfLegacy("nations.imperial_dark.name", "&6Темная Империя (Опричники)", "&6Опричнина");
            changed |= replaceIfLegacy("nations.imperial_dark.prefix", "&6[Опричник]", "&6[Оприч]");
            changed |= replaceIfLegacy("nations.imperial_dark.description", "Темная нация Царя. Каратели.", "Карательная нация. Прыгучесть.");
            if (changed) {
                java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
                if (configFile.exists()) {
                    String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                    java.nio.file.Files.copy(configFile.toPath(), new java.io.File(getDataFolder(), "config.yml.bak-before-nation-rename-" + stamp).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            saveConfig();
            reloadConfig();
            if (changed) getLogger().info("Названия наций автоматически сокращены. Старый config.yml сохранён в backup.");
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить миграцию названий наций: " + e.getMessage());
        }
    }

    private boolean replaceIfLegacy(String path, String legacy, String updated) {
        String current = getConfig().getString(path, null);
        if (legacy.equals(current)) {
            getConfig().set(path, updated);
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaultsAndNationNames();

        if (getServer().getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        nationManager = new NationManager(this);
        mapGui = new MapGui(this);
        claimGui = new ClaimGui(this);
        new TaxTask(this).runTaskTimer(this, 1200L, 72000L);
        guiListener = new NationGuiListener(this);

        getServer().getPluginManager().registerEvents(new NationListener(this), this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new PreventListener(this), this);
        getServer().getPluginManager().registerEvents(new ClaimDefenseListener(this), this);
        
        NationCommand nationCmd = new NationCommand(this);
        getCommand("nation").setExecutor(nationCmd);
        getCommand("nation").setTabCompleter(nationCmd);
        ClaimCommand cc = new ClaimCommand(this);
        getCommand("claim").setExecutor(cc);
        getCommand("claim").setTabCompleter(cc);

        // TaxTask уже запускает processDailyTaxes синхронно. Не запускаем второй async tax-task,
        // чтобы не было гонок сохранения YAML и Bukkit API из async-потока.
        // Автосейв защищает выбор наций/приваты от потерь при аварийных рестартах.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (nationManager != null) nationManager.saveAll();
        }, 6000L, 6000L); // каждые 5 минут

        getLogger().info("VKChatNations успешно запущен!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        if (nationManager != null) nationManager.saveAll();
    }

    public static VKChatNationsPlugin getInstance() { return instance; }
    public NationManager getNationManager() { return nationManager; }
    public MapGui getMapGui() { return mapGui; }
    public ClaimGui getClaimGui() { return claimGui; }
    public NationGuiListener getGuiListener() { return guiListener; }
}
