package ru.example.vkchatmobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmobs.data.ContractManager;
import ru.example.vkchatmobs.siege.SiegeManager;
import ru.example.vkchatmobs.commands.MobCommand;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatmobs.managers.HardcoreMobManager;
import ru.example.vkchatmobs.managers.MobsEvents2Manager;
import ru.example.vkchatmobs.managers.MobStormManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class VKChatMobsPlugin extends JavaPlugin {
    private static VKChatMobsPlugin instance;
    private ContractManager contractManager;
    private SiegeManager siegeManager;
    private HardcoreMobManager hardcoreMobManager;
    private MobsEvents2Manager events2Manager;
    private MobStormManager mobStormManager;
    private static final Random random = new Random();
    

    private void migrateConfigDefaults() {
        try {
            if (getConfig().getDefaults() == null) return;
            boolean hasMissing = false;
            for (String key : getConfig().getDefaults().getKeys(true)) {
                if (!getConfig().isSet(key)) {
                    hasMissing = true;
                    break;
                }
            }
            if (!hasMissing) return;

            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-before-migration-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Создан бэкап старого config.yml: " + backup.getName());
            }

            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
            getLogger().info("config.yml автоматически обновлён: недостающие ключи добавлены, существующие значения сохранены.");
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить авто-миграцию config.yml: " + e.getMessage());
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaults();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация менеджеров
        contractManager = new ContractManager(this);
        siegeManager = new SiegeManager(this);

        // Регистрация команд
        MobCommand cmd = new MobCommand(this, contractManager);
        getCommand("mobs").setExecutor(cmd);
        getCommand("mobs").setTabCompleter(cmd);
        getCommand("contract").setExecutor(cmd);
        getCommand("contract").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(cmd, this);

        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        hardcoreMobManager = new HardcoreMobManager(this);
        getServer().getPluginManager().registerEvents(hardcoreMobManager, this);
        events2Manager = new MobsEvents2Manager(this);
        getServer().getPluginManager().registerEvents(events2Manager, this);
        mobStormManager = new MobStormManager(this);
        getServer().getPluginManager().registerEvents(mobStormManager, this);
        getLogger().info("VKChatMobs (Hardcore RPG Mobs + Осады + Контракты + Шторм) успешно запущен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("VKChatMobs успешно выключен.");
    }

    public static VKChatMobsPlugin getInstance() {
        return instance;
    }

    public ContractManager getContractManager() {
        return contractManager;
    }

    public SiegeManager getSiegeManager() {
        return siegeManager;
    }

    public HardcoreMobManager getHardcoreMobManager() {
        return hardcoreMobManager;
    }

    public MobsEvents2Manager getEvents2Manager() {
        return events2Manager;
    }

    public MobStormManager getMobStormManager() {
        return mobStormManager;
    }

    public static ItemStack createSetFragment(VKChatMobsPlugin plugin) {
        org.bukkit.plugin.Plugin gear = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gear == null) return null;
        List<String> sets = new ArrayList<>(plugin.getConfig().getStringList("hardcore-mobs.rewards.set-fragments"));
        if (sets.isEmpty()) sets.addAll(Arrays.asList("bogatyr", "sokol", "volhv", "koshchey", "tankist", "udarnik"));
        String set = sets.get(random.nextInt(sets.size()));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String setName = plugin.getConfig().getString("hardcore-mobs.rewards.set-fragment-names." + set, set);
        meta.setDisplayName(ChatColor.GOLD + "Фрагмент сета: " + setName);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Трофей элитной охоты.", ChatColor.GRAY + "Используется при ковке брони в VKChatGear."));
        meta.getPersistentDataContainer().set(new NamespacedKey(gear, "set_fragment"), PersistentDataType.STRING, set);
        item.setItemMeta(meta);
        return item;
    }
}
