package ru.example.vkchatmarket.playerShop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerShopManager {
    private final VKChatMarketPlugin plugin;
    private final Map<String, PlayerShop> shopsByLocation = new LinkedHashMap<>();
    private final Map<UUID, List<PlayerShop>> shopsByOwner = new HashMap<>();
    private File shopFile;
    private FileConfiguration shopConfig;

    private boolean enabled;
    private int maxPerPlayer;
    private int createCost;
    private double commissionPercent;
    private int maxItemsPerShop;

    public PlayerShopManager(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        var settings = plugin.getSettingsConfig().getConfigurationSection("player-shops");
        if (settings == null) {
            enabled = false;
            return;
        }
        enabled = settings.getBoolean("enabled", true);
        maxPerPlayer = settings.getInt("max-per-player", 3);
        createCost = settings.getInt("create-cost", 1000);
        commissionPercent = settings.getDouble("commission-percent", 5);
        maxItemsPerShop = settings.getInt("max-items-per-shop", 9);
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxPerPlayer() { return maxPerPlayer; }
    public int getCreateCost() { return createCost; }
    public double getCommissionPercent() { return commissionPercent; }
    public int getMaxItemsPerShop() { return maxItemsPerShop; }

    public void load() {
        shopsByLocation.clear();
        shopsByOwner.clear();
        shopFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopFile.exists()) {
            try {
                shopFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать shops.yml: " + e.getMessage());
            }
            shopConfig = YamlConfiguration.loadConfiguration(shopFile);
            return;
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        ConfigurationSection sec = shopConfig.getConfigurationSection("shops");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            String[] parts = key.split(",");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            int x, y, z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) { continue; }
            Location loc = new Location(world, x, y, z);
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(s.getString("owner-uuid"));
            } catch (Exception e) { continue; }
            String ownerName = s.getString("owner-name", "Unknown");
            int price = s.getInt("price", 100);
            PlayerShop shop = new PlayerShop(ownerUuid, ownerName, price, loc);
            List<?> rawItems = s.getList("items");
            if (rawItems != null) {
                for (Object obj : rawItems) {
                    if (obj instanceof Map) {
                        try {
                            ItemStack item = ItemStack.deserialize((Map<String, Object>) obj);
                            if (item != null && item.getType() != Material.AIR) {
                                shop.getItems().add(item);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            shopsByLocation.put(key, shop);
            shopsByOwner.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(shop);
        }
        plugin.getLogger().info("Загружено игровых магазинов: " + shopsByLocation.size());
    }

    public void save() {
        if (shopConfig == null) return;
        shopConfig.set("shops", null);
        ConfigurationSection sec = shopConfig.createSection("shops");
        for (Map.Entry<String, PlayerShop> e : shopsByLocation.entrySet()) {
            PlayerShop shop = e.getValue();
            ConfigurationSection s = sec.createSection(e.getKey());
            s.set("owner-uuid", shop.getOwnerUuid().toString());
            s.set("owner-name", shop.getOwnerName());
            s.set("price", shop.getPrice());
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (ItemStack item : shop.getItems()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemMaps.add(item.serialize());
                }
            }
            s.set("items", itemMaps);
        }
        try {
            shopConfig.save(shopFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Ошибка сохранения shops.yml: " + ex.getMessage());
        }
    }

    public List<PlayerShop> getPlayerShops(UUID playerUuid) {
        return shopsByOwner.getOrDefault(playerUuid, Collections.emptyList());
    }

    public List<PlayerShop> getAllShops() {
        return new ArrayList<>(shopsByLocation.values());
    }

    public PlayerShop getShopAt(Location loc) {
        return shopsByLocation.get(PlayerShop.locationKey(loc));
    }

    public PlayerShop getShopAt(Block block) {
        return getShopAt(block.getLocation());
    }

    public PlayerShop getShopByLocationKey(String locKey) {
        return shopsByLocation.get(locKey);
    }

    public boolean createShop(Player player, int price) {
        if (price <= 0) {
            player.sendMessage("§cЦена должна быть больше 0!");
            return false;
        }
        int vkId = VKChatBridge.getLinkedVkId(player);
        boolean hasVk = vkId != -1;
        boolean hasPass = VKChatBridge.hasPass(player);
        if (!hasVk && !hasPass) {
            player.sendMessage("§c❌ Привяжи ВК: /vklink");
            return false;
        }
        int rep = hasVk ? VKChatBridge.getReputation(vkId) : VKChatBridge.getLocalReputation(player);
        if (rep < createCost) {
            player.sendMessage("§c❌ Недостаточно репутации! Нужно: §e" + createCost + " реп.");
            return false;
        }
        List<PlayerShop> existing = getPlayerShops(player.getUniqueId());
        if (existing.size() >= maxPerPlayer) {
            player.sendMessage("§c❌ Максимум магазинов: §e" + maxPerPlayer);
            return false;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage("§c❌ Смотри на блок!");
            return false;
        }
        Block signBlock = target.getRelative(BlockFace.UP);
        if (signBlock.getType() != Material.AIR) {
            player.sendMessage("§c❌ Место над блоком занято!");
            return false;
        }
        signBlock.setType(Material.OAK_SIGN);
        if (!(signBlock.getState() instanceof Sign)) {
            signBlock.setType(Material.AIR);
            player.sendMessage("§c❌ Не удалось разместить табличку!");
            return false;
        }
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(0, "§6[ТОРГОВЕЦ]");
        sign.setLine(1, "§f" + player.getName());
        sign.setLine(2, "§aКликни");
        sign.setLine(3, "§7(/shop)");
        sign.update(true);
        if (hasVk) {
            VKChatBridge.takeReputation(vkId, createCost);
        } else {
            VKChatBridge.takeLocalReputation(player, createCost);
        }
        PlayerShop shop = new PlayerShop(player.getUniqueId(), player.getName(), price, signBlock.getLocation());
        shopsByLocation.put(shop.locationKey(), shop);
        shopsByOwner.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(shop);
        save();
        player.sendMessage("§a✓ Магазин создан! Цена: §e" + price + " реп. §aза предмет.");
        return true;
    }

    public boolean removeShop(Player player, PlayerShop shop) {
        if (!shop.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cЭто не твой магазин!");
            return false;
        }
        removeShopInternal(shop);
        player.sendMessage("§a✓ Магазин удалён.");
        return true;
    }

    public void removeShopInternal(PlayerShop shop) {
        Location loc = shop.getLocation();
        Block block = loc.getBlock();
        if (block.getState() instanceof Sign) {
            block.setType(Material.AIR);
        }
        String key = shop.locationKey();
        shopsByLocation.remove(key);
        List<PlayerShop> ownerShops = shopsByOwner.get(shop.getOwnerUuid());
        if (ownerShops != null) {
            ownerShops.remove(shop);
            if (ownerShops.isEmpty()) shopsByOwner.remove(shop.getOwnerUuid());
        }
        save();
    }

    public boolean adminRemovePlayerShops(UUID targetUuid) {
        List<PlayerShop> shops = getPlayerShops(targetUuid);
        if (shops.isEmpty()) return false;
        for (PlayerShop shop : new ArrayList<>(shops)) {
            removeShopInternal(shop);
        }
        return true;
    }

    private boolean isShopSign(Block block) {
        if (!(block.getState() instanceof Sign)) return false;
        Sign sign = (Sign) block.getState();
        String line0 = sign.getLine(0);
        return line0 != null && line0.equals("§6[ТОРГОВЕЦ]");
    }

    public boolean isShopBlock(Block block) {
        return isShopSign(block);
    }

    public void updateSign(PlayerShop shop) {
        Block block = shop.getLocation().getBlock();
        if (!(block.getState() instanceof Sign)) return;
        Sign sign = (Sign) block.getState();
        sign.setLine(0, "§6[ТОРГОВЕЦ]");
        sign.setLine(1, "§f" + shop.getOwnerName());
        sign.setLine(2, "§aКликни");
        sign.setLine(3, "§7(/shop)");
        sign.update(true);
    }
}
