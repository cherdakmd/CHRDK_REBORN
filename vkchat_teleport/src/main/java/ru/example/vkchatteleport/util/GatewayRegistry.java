package ru.example.vkchatteleport.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * GatewayRegistry — конфиг-управляемые порталы фракций.
 *
 * Извлечено из TeleportCommand.handleGateway() (хардкод координат 3 фракций).
 * Теперь порталы полностью настраиваются из config.yml.
 */
public class GatewayRegistry {

    private final JavaPlugin plugin;
    private final Map<String, GatewayDef> gateways = new LinkedHashMap<>();
    private final Map<String, String> aliasToId = new HashMap<>();

    public static class GatewayDef {
        private final String id;
        private final String name;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final String[] aliases;
        private final int cost;

        public GatewayDef(String id, String name, String worldName, double x, double y, double z, String[] aliases, int cost) {
            this.id = id;
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.aliases = aliases;
            this.cost = cost;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String[] getAliases() { return aliases; }
        public int getCost() { return cost; }

        public Location toLocation(World fallbackWorld) {
            World w = org.bukkit.Bukkit.getWorld(worldName);
            if (w == null) w = fallbackWorld;
            Location loc = new Location(w, x, y, z);
            loc.setY(w.getHighestBlockYAt(loc) + 1.0);
            return loc;
        }
    }

    public GatewayRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void loadFromConfig() {
        gateways.clear();
        aliasToId.clear();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("teleportation.gateways");
        if (sec == null) {
            registerDefaults();
            return;
        }

        for (String key : sec.getKeys(false)) {
            ConfigurationSection gSec = sec.getConfigurationSection(key);
            if (gSec == null) continue;

            String name = gSec.getString("name", key);
            String world = gSec.getString("world", "world");
            double x = gSec.getDouble("x", 0);
            double y = gSec.getDouble("y", 80);
            double z = gSec.getDouble("z", 0);
            String[] aliases = gSec.getStringList("aliases").toArray(new String[0]);
            int cost = gSec.getInt("cost", 25);

            GatewayDef def = new GatewayDef(key, name, world, x, y, z, aliases, cost);
            gateways.put(key, def);
            for (String alias : aliases) {
                aliasToId.put(alias.toLowerCase(), key);
            }
        }

        if (gateways.isEmpty()) registerDefaults();
        plugin.getLogger().info("[GatewayRegistry] Загружено " + gateways.size() + " порталов фракций");
    }

    private void registerDefaults() {
        register("soviet", "Советский Союз", "world", 100.5, 80, 100.5,
                new String[]{"soviet", "совет"}, 25);
        register("pagan", "Языческий Культ", "world", -500.5, 80, -500.5,
                new String[]{"pagan", "языч"}, 25);
        register("imperial", "Священная Империя", "world", 1000.5, 80, -1000.5,
                new String[]{"imperial", "импер"}, 25);
    }

    private void register(String id, String name, String world, double x, double y, double z,
                          String[] aliases, int cost) {
        GatewayDef def = new GatewayDef(id, name, world, x, y, z, aliases, cost);
        gateways.put(id, def);
        for (String alias : aliases) {
            aliasToId.put(alias.toLowerCase(), id);
        }
    }

    /** Разрешить алиас в GatewayDef */
    public GatewayDef resolve(String input) {
        if (input == null) return null;
        // Сначала точный ID
        GatewayDef direct = gateways.get(input.toLowerCase());
        if (direct != null) return direct;
        // Потом по алиасам
        String id = aliasToId.get(input.toLowerCase());
        if (id != null) return gateways.get(id);
        // Partial match
        for (GatewayDef def : gateways.values()) {
            for (String alias : def.getAliases()) {
                if (alias.contains(input.toLowerCase())) return def;
            }
        }
        return null;
    }

    /** Список доступных фракций для tab-complete */
    public List<String> getAliases() {
        List<String> result = new ArrayList<>();
        for (GatewayDef def : gateways.values()) {
            result.add(def.getAliases()[0]);
        }
        return result;
    }

    public Collection<GatewayDef> getAllGateways() {
        return gateways.values();
    }
}
