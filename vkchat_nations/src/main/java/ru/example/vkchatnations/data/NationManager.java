package ru.example.vkchatnations.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NationManager {
    private final VKChatNationsPlugin plugin;
    private File file;
    private FileConfiguration data;

    private final Map<UUID, String> playerNations = new ConcurrentHashMap<>();
    private final Map<String, ChunkClaim> nationClaims = new ConcurrentHashMap<>();
    private final Map<String, Integer> nationBank = new ConcurrentHashMap<>();
    private final Set<UUID> autoClaimPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> unlockedMutations = new ConcurrentHashMap<>();
    private final Map<String, Integer> nationExp = new ConcurrentHashMap<>();
    private final Map<String, Integer> nationLevels = new ConcurrentHashMap<>();

    // Ожидание ввода от игрока: переименование / добавление доверенного
    private final Map<UUID, ChunkClaim> renameQueue = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkClaim> addTrustedQueue = new ConcurrentHashMap<>();

    public NationManager(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        load();
        // Авто-продление прочности каждые 10 минут
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (ChunkClaim claim : nationClaims.values()) {
                if (!claim.isAutoPayEnabled()) continue;
                if (claim.getDurability() >= claim.getMaxDurability() * 0.2) continue;
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(claim.getOwner());
                if (vkId == -1) continue;
                int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
                int need = Math.min(claim.getMaxDurability() - claim.getDurability(), 50);
                int cost = need / 2;
                if (rep >= cost && cost > 0) {
                    VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                    claim.addDurability(need);
                    int fNeed = need;
                    int fCost = cost;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player owner = Bukkit.getPlayer(claim.getOwner());
                        if (owner != null && owner.isOnline())
                            owner.sendMessage(ChatColor.GREEN + "♻ Авто-продление: +" + fNeed + " прочности за " + fCost + " реп.");
                    });
                }
            }
        }, 12000L, 12000L); // Каждые 10 минут (6000 тиков = 5 мин, 12000 = 10 мин)
    }

    private NamespacedKey playerNationKey() {
        return new NamespacedKey(plugin, "player_nation");
    }

    private synchronized void load() {
        playerNations.clear();
        nationClaims.clear();
        nationBank.clear();
        unlockedMutations.clear();
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "nations_data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        // Если основной файл оказался пустым/обрезанным после старых версий или аварийного рестарта,
        // пробуем восстановить хотя бы последнюю хорошую копию.
        File lastGood = new File(plugin.getDataFolder(), "nations_data.yml.bak-last-good");
        if ((!data.contains("players") || data.getConfigurationSection("players") == null || data.getConfigurationSection("players").getKeys(false).isEmpty()) && lastGood.exists() && lastGood.length() > 0) {
            YamlConfiguration backup = YamlConfiguration.loadConfiguration(lastGood);
            if (backup.contains("players") && backup.getConfigurationSection("players") != null && !backup.getConfigurationSection("players").getKeys(false).isEmpty()) {
                plugin.getLogger().warning("nations_data.yml не содержит players, загружаю backup nations_data.yml.bak-last-good");
                data = backup;
            }
        }

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    playerNations.put(UUID.fromString(uuidStr), data.getString("players." + uuidStr));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (data.contains("nations")) {
            for (String nation : data.getConfigurationSection("nations").getKeys(false)) {
                try {
                    nationBank.put(nation, data.getInt("nations." + nation + ".bank", 0));
                    nationLevels.put(nation, data.getInt("nations." + nation + ".level", 1));
                    nationExp.put(nation, data.getInt("nations." + nation + ".exp", 0));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (data.contains("claims")) {
            for (String key : data.getConfigurationSection("claims").getKeys(false)) {
                try {
                    String ownerStr = data.getString("claims." + key + ".owner");
                    String claimNation = data.getString("claims." + key + ".nation");
                    int durability = data.getInt("claims." + key + ".durability", 100);
                    int level = data.getInt("claims." + key + ".level", 1);
                    int radius = data.getInt("claims." + key + ".radius", 8);
                    List<String> trustedStrs = data.getStringList("claims." + key + ".trusted");

                    List<UUID> trusted = new ArrayList<>();
                    for (String t : trustedStrs) {
                        try { trusted.add(UUID.fromString(t)); } catch (Exception ignored) {}
                    }

                    double hX = data.getDouble("claims." + key + ".home_x", 0);
                    double hY = data.getDouble("claims." + key + ".home_y", 0);
                    double hZ = data.getDouble("claims." + key + ".home_z", 0);
                    boolean hasH = data.getBoolean("claims." + key + ".has_home", false);

                    String[] parts = key.split(";");
                    if (parts.length >= 4) {
                        String world = parts[0];
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        
                        boolean fireProt = !data.contains("claims." + key + ".fire-protection") || data.getBoolean("claims." + key + ".fire-protection");
                        boolean explosionProt = !data.contains("claims." + key + ".explosion-protection") || data.getBoolean("claims." + key + ".explosion-protection");
                        boolean noSpawnProt = !data.contains("claims." + key + ".no-spawn-protection") || data.getBoolean("claims." + key + ".no-spawn-protection");
                        boolean pvpProt = !data.contains("claims." + key + ".pvp-protection") || data.getBoolean("claims." + key + ".pvp-protection");
                        boolean autoPay = data.getBoolean("claims." + key + ".auto-pay", false);
                        int extraRadius = data.getInt("claims." + key + ".extra-radius", 0);
                        String claimName = data.getString("claims." + key + ".name", "");
                        
                        nationClaims.put(key, new ChunkClaim(world, x, y, z, radius, UUID.fromString(ownerStr), claimNation, trusted, durability, level, explosionProt, noSpawnProt, fireProt, pvpProt, autoPay, extraRadius, claimName, hX, hY, hZ, hasH));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (data.contains("mutations")) {
            for (String uuidStr : data.getConfigurationSection("mutations").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> list = data.getStringList("mutations." + uuidStr);
                    unlockedMutations.put(uuid, ConcurrentHashMap.newKeySet());
                    unlockedMutations.get(uuid).addAll(list);
                } catch (Exception ignored) {}
            }
        }
    }

    // ═══ Ожидание ввода от игрока ═══
    public void setRenameClaim(UUID uuid, ChunkClaim claim) { renameQueue.put(uuid, claim); }
    public ChunkClaim pollRenameClaim(UUID uuid) { return renameQueue.remove(uuid); }
    public boolean isAwaitingRename(UUID uuid) { return renameQueue.containsKey(uuid); }

    public void setAddingTrusted(UUID uuid, ChunkClaim claim) { addTrustedQueue.put(uuid, claim); }
    public ChunkClaim pollAddingTrusted(UUID uuid) { return addTrustedQueue.remove(uuid); }
    public boolean isAwaitingTrustedAdd(UUID uuid) { return addTrustedQueue.containsKey(uuid); }

    public synchronized void saveAll() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (file == null) file = new File(plugin.getDataFolder(), "nations_data.yml");

            YamlConfiguration out = new YamlConfiguration();

            for (Map.Entry<UUID, String> entry : playerNations.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    out.set("players." + entry.getKey().toString(), entry.getValue());
                }
            }

            if (plugin.getConfig().getConfigurationSection("nations") != null) {
                for (String nation : plugin.getConfig().getConfigurationSection("nations").getKeys(false)) {
                    out.set("nations." + nation + ".bank", nationBank.getOrDefault(nation, 0));
                    out.set("nations." + nation + ".level", nationLevels.getOrDefault(nation, 1));
                    out.set("nations." + nation + ".exp", nationExp.getOrDefault(nation, 0));
                }
            }

            for (Map.Entry<String, ChunkClaim> entry : nationClaims.entrySet()) {
                String key = entry.getKey();
                ChunkClaim c = entry.getValue();
                out.set("claims." + key + ".owner", c.getOwner().toString());
                out.set("claims." + key + ".nation", c.getNation());
                out.set("claims." + key + ".durability", c.getDurability());
                out.set("claims." + key + ".level", c.getLevel());
                out.set("claims." + key + ".radius", c.getRadius());
                out.set("claims." + key + ".home_x", c.getHomeX());
                out.set("claims." + key + ".home_y", c.getHomeY());
                out.set("claims." + key + ".home_z", c.getHomeZ());
                out.set("claims." + key + ".has_home", c.hasHome());
                out.set("claims." + key + ".fire-protection", c.isFireProtectionEnabled());
                out.set("claims." + key + ".explosion-protection", c.isExplosionProtectionEnabled());
                out.set("claims." + key + ".no-spawn-protection", c.isNoSpawnProtectionEnabled());
                out.set("claims." + key + ".pvp-protection", c.isPvpProtectionEnabled());
                out.set("claims." + key + ".auto-pay", c.isAutoPayEnabled());
                out.set("claims." + key + ".extra-radius", c.getExtraRadius());
                out.set("claims." + key + ".name", c.getName());

                List<String> tList = new ArrayList<>();
                for (UUID t : c.getTrusted()) tList.add(t.toString());
                out.set("claims." + key + ".trusted", tList);
            }

            for (Map.Entry<UUID, Set<String>> entry : unlockedMutations.entrySet()) {
                out.set("mutations." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }

            // Atomic-ish save: first write temp, then replace real file. This avoids half-written YAML after crash/restart.
            File tmp = new File(plugin.getDataFolder(), "nations_data.yml.tmp");
            out.save(tmp);
            if (file.exists() && file.length() > 0) {
                java.nio.file.Files.copy(file.toPath(), new File(plugin.getDataFolder(), "nations_data.yml.bak-last-good").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            data = out;
        } catch (java.nio.file.AtomicMoveNotSupportedException atomicUnsupported) {
            try {
                File tmp = new File(plugin.getDataFolder(), "nations_data.yml.tmp");
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                data = YamlConfiguration.loadConfiguration(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось сохранить nations_data.yml после fallback move: " + e.getMessage());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить nations_data.yml: " + e.getMessage());
        }
    }

    public boolean hasMutation(UUID uuid, String mutationId) {
        Set<String> set = unlockedMutations.get(uuid);
        return set != null && set.contains(mutationId);
    }

    public boolean hasMutation(Player p, String mutationId) {
        return hasMutation(p.getUniqueId(), mutationId);
    }

    public void unlockMutation(UUID uuid, String mutationId) {
        unlockedMutations.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(mutationId);
        saveAll();
    }

    public ChunkClaim getPlayerHomeClaim(UUID uuid) {
        for (ChunkClaim claim : nationClaims.values()) {
            if (claim.getOwner().equals(uuid) && claim.hasHome()) {
                return claim;
            }
        }
        return null;
    }

    public String getPlayerNation(Player p) {
        String nation = playerNations.get(p.getUniqueId());
        if (nation == null && p != null) {
            try {
                nation = p.getPersistentDataContainer().get(playerNationKey(), PersistentDataType.STRING);
                if (nation != null && !nation.trim().isEmpty()) {
                    playerNations.put(p.getUniqueId(), nation);
                    saveAll();
                    plugin.getLogger().info("Восстановлен выбор нации игрока " + p.getName() + " из player.dat: " + nation);
                }
            } catch (Throwable ignored) {}
        }
        return nation;
    }

    public String getPlayerNation(UUID uuid) {
        return playerNations.get(uuid);
    }

    public void setPlayerNation(Player p, String nationId) {
        if (p == null || nationId == null || nationId.trim().isEmpty()) return;
        playerNations.put(p.getUniqueId(), nationId);
        try { p.getPersistentDataContainer().set(playerNationKey(), PersistentDataType.STRING, nationId); } catch (Throwable ignored) {}
        saveAll(); // Сохраняем сразу и дополнительно дублируем в player.dat через PDC.
    }

    public void removePlayerNation(UUID uuid) {
        playerNations.remove(uuid);
        unlockedMutations.remove(uuid); // Сбрасываем мутации игрока при смене нации
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            try { p.getPersistentDataContainer().remove(playerNationKey()); } catch (Throwable ignored) {}
        }
        saveAll();
    }

    public boolean hasNation(Player p) {
        return getPlayerNation(p) != null;
    }

    // ==========================================
    // НОВАЯ СИСТЕМА ПРИВАТОВ ЧЕРЕЗ БЛОКИ (BLOCK CLAIMS)
    // ==========================================
    
    public ChunkClaim getClaimAt(Location loc) {
        if (loc == null) return null;
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        
        for (ChunkClaim claim : nationClaims.values()) {
            if (world.equals(claim.getWorldName())) {
                if (Math.abs(x - claim.getX()) <= claim.getRadius() && Math.abs(z - claim.getZ()) <= claim.getRadius()) {
                    return claim;
                }
            }
        }
        return null;
    }

    public ChunkClaim getChunkClaim(Chunk chunk) {
        return getClaimAt(chunk.getBlock(8, 64, 8).getLocation());
    }

    public ChunkClaim getChunkClaim(String worldName, int x, int z) {
        org.bukkit.World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return getClaimAt(new Location(w, x * 16 + 8, 64, z * 16 + 8));
    }

    public Map<String, ChunkClaim> getNationClaims() {
        return nationClaims;
    }

    public java.util.List<ChunkClaim> getClaimsByOwner(java.util.UUID owner) {
        java.util.List<ChunkClaim> result = new java.util.ArrayList<>();
        for (ChunkClaim c : nationClaims.values()) {
            if (c.getOwner().equals(owner)) result.add(c);
        }
        return result;
    }

    public Map<UUID, String> getPlayerNations() {
        return playerNations;
    }

    public String getChunkOwner(Chunk chunk) {
        ChunkClaim claim = getChunkClaim(chunk);
        return claim != null ? claim.getNation() : null;
    }

    public String getChunkOwnerUUID(Chunk chunk) {
        ChunkClaim claim = getChunkClaim(chunk);
        return claim != null ? claim.getOwner().toString() : null;
    }

    public boolean checkOverlap(String world, int x, int z, int radius) {
        for (ChunkClaim claim : nationClaims.values()) {
            if (world.equals(claim.getWorldName())) {
                if (Math.abs(x - claim.getX()) <= radius + claim.getRadius() &&
                    Math.abs(z - claim.getZ()) <= radius + claim.getRadius()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean registerClaim(Player p, org.bukkit.block.Block block, int radius) {
        String nation = getPlayerNation(p);
        if (nation == null) {
            p.sendMessage(ChatColor.RED + "⚠️ Сначала выберите Нацию! (/nation)");
            return false;
        }

        // Проверяем лимит 5 блоков привата на игрока!
        int currentCount = 0;
        for (ChunkClaim c : nationClaims.values()) {
            if (c.getOwner().equals(p.getUniqueId())) {
                currentCount++;
            }
        }
        if (currentCount >= 5) {
            p.sendMessage(ChatColor.RED + "❌ Лимит приватов! Вы не можете установить более 5 блоков привата (У вас: " + currentCount + "/5).");
            return false;
        }

        String key = block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
        ChunkClaim claim = new ChunkClaim(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), radius, p.getUniqueId(), nation);
        nationClaims.put(key, claim);
        saveAll();
        
        p.sendMessage(ChatColor.GREEN + "✓ Блок привата успешно установлен! Территория в радиусе " + radius + " блоков защищена.");
        broadcastToNationWithPrefix(nation, ChatColor.GREEN + "✓ " + ChatColor.WHITE + p.getName() + ChatColor.GREEN + " установил блок привата радиусом " + radius + " блоков!");
        return true;
    }

    // Блоки привата (Малый, Средний, Большой)
    public ItemStack getSmallClaimBlockItem() {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Малый блок привата (8 блоков)");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + "8 блоков");
        lore.add(ChatColor.GRAY + "Тип блока: " + ChatColor.GOLD + "Золотой блок");
        lore.add("");
        lore.add(ChatColor.GRAY + "Поставьте этот блок в мире,");
        lore.add(ChatColor.GRAY + "чтобы создать защищенную зону.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "claim_block_radius"), PersistentDataType.INTEGER, 8);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getMediumClaimBlockItem() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Средний блок привата (16 блоков)");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + "16 блоков");
        lore.add(ChatColor.GRAY + "Тип блока: " + ChatColor.GREEN + "Изумрудный блок");
        lore.add("");
        lore.add(ChatColor.GRAY + "Поставьте этот блок в мире,");
        lore.add(ChatColor.GRAY + "чтобы создать защищенную зону.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "claim_block_radius"), PersistentDataType.INTEGER, 16);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getLargeClaimBlockItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Большой блок привата (32 блоков)");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + "32 блоков");
        lore.add(ChatColor.GRAY + "Тип блока: " + ChatColor.AQUA + "Алмазный блок");
        lore.add("");
        lore.add(ChatColor.GRAY + "Поставьте этот блок в мире,");
        lore.add(ChatColor.GRAY + "чтобы создать защищенную зону.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "claim_block_radius"), PersistentDataType.INTEGER, 32);
        item.setItemMeta(meta);
        return item;
    }

    private void broadcastToNation(String nationId, String message) {
        if (nationId == null) return;

        for (Map.Entry<UUID, String> entry : playerNations.entrySet()) {
            if (nationId.equals(entry.getValue())) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        }
    }

    public void broadcastToNationWithPrefix(String nationId, String message) {
        String nationName = getNationName(nationId);
        String prefix = getNationPrefix(nationId);
        String coloredPrefix = prefix != null ? prefix : ChatColor.DARK_PURPLE + "[Нация]";
        broadcastToNation(nationId, coloredPrefix + " " + message);
    }

    private String getNationName(String nationId) {
        if (nationId == null) return "";
        return plugin.getConfig().getString("nations." + nationId + ".name", nationId);
    }

    private String getNationPrefix(String nationId) {
        if (nationId == null) return "";
        return plugin.getConfig().getString("nations." + nationId + ".prefix", "");
    }

    public String getNationNamePublic(String nationId) {
        return getNationName(nationId);
    }

    public String getNationPrefixPublic(String nationId) {
        return getNationPrefix(nationId);
    }

    // ==========================================
    // УПРАЗДНЕННЫЕ МЕТОДЫ ПРИВАТА ЧАНКАМИ (ОБРАТНАЯ СОВМЕСТИМОСТЬ)
    // ==========================================
    public void claimChunk(Player p, Chunk chunk) {
        p.sendMessage(ChatColor.RED + "❌ Приваты чанками упразднены! Теперь приваты ставятся через блоки привата.");
        p.sendMessage(ChatColor.GRAY + "Купить блоки привата можно через команду: " + ChatColor.YELLOW + "/n buyclaim");
    }

    public void claimChunk(Player p) {
        p.sendMessage(ChatColor.RED + "❌ Приваты чанками упразднены! Теперь приваты ставятся через блоки привата.");
        p.sendMessage(ChatColor.GRAY + "Купить блоки привата можно через команду: " + ChatColor.YELLOW + "/n buyclaim");
    }

    public void unclaimChunk(Player p, Chunk chunk) {
        p.sendMessage(ChatColor.RED + "❌ Приваты чанками упразднены! Снимите приват, просто сломав установленный блок привата.");
    }

    public void unclaimChunk(Player p) {
        p.sendMessage(ChatColor.RED + "❌ Приваты чанками упразднены! Снимите приват, просто сломав установленный блок привата.");
    }

    // ==========================================
    // REPUTATION CHARGE - УПРАЗДНЕНО (ПОЖЕРТВОВАНИЯ ОТКЛЮЧЕНЫ)
    // ==========================================
    public void chargeWithReputation(Player p, int vkId) {
        p.sendMessage(ChatColor.RED + "❌ Пожертвования репутации в казну Нации отключены!");
    }

    public long getFestivalEndTime(String nation) {
        return data.getLong("festivals." + nation, 0L);
    }

    public void setFestivalEndTime(String nation, long time) {
        data.set("festivals." + nation, time);
        saveAll();
    }

    // ==========================================
    // TAXES - ПРОВОКАЦИЯ ПРИ СПИСАНИИ НАЛОГОВ
    // ==========================================
    public void depositReputation(String nation, int amount) {
        nationBank.put(nation, Math.max(0, getBank(nation) + amount));
        if (amount > 0) addNationExp(nation, amount);
        saveAll();
    }

    public int getBank(String nation) {
        return nationBank.getOrDefault(nation, 0);
    }

    public int getMemberCount(String nation) {
        int count = 0;
        for (String n : playerNations.values()) {
            if (nation.equals(n)) count++;
        }
        return count;
    }

    public int getNationLevel(String nation) {
        return nationLevels.getOrDefault(nation, 1);
    }

    public int getNationExp(String nation) {
        return nationExp.getOrDefault(nation, 0);
    }

    public int getNationExpToNextLevel(String nation) {
        int level = getNationLevel(nation);
        return level * 5000;
    }

    public void addNationExp(String nation, int exp) {
        int current = nationExp.getOrDefault(nation, 0) + exp;
        int level = nationLevels.getOrDefault(nation, 1);
        int needed = level * 5000;
        while (current >= needed) {
            current -= needed;
            level++;
            needed = level * 5000;
            nationLevels.put(nation, level);
            broadcastToNationWithPrefix(nation,
                    ChatColor.GOLD + "★ Нация достигла уровня " + level + "! ★");
        }
        nationExp.put(nation, current);
        saveAll();
    }

    public String getNationProgressBar(String nation) {
        int level = getNationLevel(nation);
        int exp = getNationExp(nation);
        int needed = getNationExpToNextLevel(nation);
        int bars = (int) ((double) exp / needed * 10);
        StringBuilder sb = new StringBuilder(ChatColor.GREEN + "" + ChatColor.BOLD + "Ур." + level + " ");
        sb.append(ChatColor.DARK_GREEN);
        for (int i = 0; i < 10; i++) {
            sb.append(i < bars ? "■" : "□");
        }
        sb.append(ChatColor.GRAY).append(" ").append(exp).append("/").append(needed).append(" EXP");
        return sb.toString();
    }

    public void processDailyTaxes() {
        ru.example.vkchat.api.VKChatAPI api = VKChatPlugin.getInstance().getApi();
        
        // --- 1. ЕЖЕДНЕВНЫЙ СБОР НАЛОГОВ С ГРАЖДАН - УДАЛЕН ---
        
        // --- 2. АВТОПРОДЛЕНИЕ ЭНЕРГИИ ПРИВАТОВ - УДАЛЕНО ---

        // --- 3. ВАНИЛЬНОЕ ОБЕСПЕЧЕНИЕ ПРОЧНОСТИ ЧАНКОВ (Оригинальная логика) ---
        List<String> toRemove = new ArrayList<>();
        int totalTaxCollected = 0;

        for (Map.Entry<String, ChunkClaim> entry : nationClaims.entrySet()) {
            String key = entry.getKey();
            ChunkClaim claim = entry.getValue();

            // Владелец платит налог прочностью привата ежедневно (-2 DUR)
            claim.setDurability(claim.getDurability() - 2);

            String nation = claim.getNation();

            if (claim.getDurability() <= 0) {
                toRemove.add(key);

                Player ownerP = Bukkit.getPlayer(claim.getOwner());
                if (ownerP != null) {
                    ownerP.sendMessage(ChatColor.RED + "⚠️ Один из ваших приватов разрушен из-за износа!");
                    // Пытаемся физически удалить блок привата из мира на основном потоке
                    final ChunkClaim finalClaim = claim;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.World w = Bukkit.getWorld(finalClaim.getWorldName());
                        if (w != null) {
                            org.bukkit.block.Block b = w.getBlockAt(finalClaim.getX(), finalClaim.getY(), finalClaim.getZ());
                            if (b.getType() == Material.GOLD_BLOCK || b.getType() == Material.EMERALD_BLOCK || b.getType() == Material.DIAMOND_BLOCK) {
                                b.setType(Material.AIR);
                            }
                        }
                    });
                }

                // Сообщение всей нации
                broadcastToNationWithPrefix(nation,
                        ChatColor.RED + "⚠️ Блок привата разрушен из-за износа!");

                continue;
            }

            // Доверенные игроки платят репутацией ВЛАДЕЛЬЦУ
            List<UUID> toUntrust = new ArrayList<>();
            for (UUID trustedId : claim.getTrusted()) {
                Player tPlayer = Bukkit.getPlayer(trustedId);
                if (tPlayer != null) {
                    int vkid = api.getLinkedVkId(tPlayer);
                    if (vkid != -1 && api.getReputation(vkid) >= 2) {
                        api.takeReputation(vkid, 2);

                        Player ownerP = Bukkit.getPlayer(claim.getOwner());
                        if (ownerP != null) {
                            int oVk = api.getLinkedVkId(ownerP);
                            if (oVk != -1) api.addReputation(oVk, 2);

                            ownerP.sendMessage(ChatColor.YELLOW + "💰 " + tPlayer.getName() + " оплатил налог: 2 реп.");
                            tPlayer.sendMessage(ChatColor.GREEN + "✓ Налог оплачен: -2 реп.");
                        }
                        totalTaxCollected += 2;
                    } else {
                        toUntrust.add(trustedId);
                        tPlayer.sendMessage(ChatColor.RED + "⚠️ Вы выселены из привата за неуплату!");
                    }
                }
            }

            for (UUID u : toUntrust) {
                claim.removeTrusted(u);
            }
        }

        for (String key : toRemove) {
            nationClaims.remove(key);
        }

        if (totalTaxCollected > 0) {
            for (String n : plugin.getConfig().getConfigurationSection("nations").getKeys(false)) {
                int memberCount = getMemberCount(n);
                int expGain = memberCount > 0 ? totalTaxCollected / 2 : 0;
                if (expGain > 0) {
                    addNationExp(n, expGain);
                }
            }
        }

        saveAll();
    }

    public boolean isAutoClaimEnabled(Player p) {
        return autoClaimPlayers.contains(p.getUniqueId());
    }

    public boolean toggleAutoClaim(Player p) {
        if (autoClaimPlayers.contains(p.getUniqueId())) {
            autoClaimPlayers.remove(p.getUniqueId());
            return false;
        } else {
            autoClaimPlayers.add(p.getUniqueId());
            return true;
        }
    }

    public void removeAutoClaim(Player p) {
        autoClaimPlayers.remove(p.getUniqueId());
    }
}
