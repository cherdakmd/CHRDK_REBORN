package ru.example.vkchatoffline.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.utils.Base64Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Персистентное хранение активных походов и питомцев в БД VKChat.
 */
public class ExpeditionStorage {

    public ExpeditionStorage() {
        createTables();
    }

    private void createTables() {
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS vkchat_offline_expeditions (" +
                            "vk_id INT PRIMARY KEY, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "data TEXT NOT NULL)")) {
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS vkchat_offline_pets (" +
                            "vk_id INT PRIMARY KEY, " +
                            "pet_name VARCHAR(64) NOT NULL)")) {
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS vkchat_offline_gear (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY, " +
                            "damage INT NOT NULL, " +
                            "defense INT NOT NULL)")) {
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS vkchat_offline_notifications (" +
                            "player_uuid VARCHAR(36), " +
                            "message TEXT NOT NULL)")) {
                ps.execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addNotification(UUID uuid, String message) {
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO vkchat_offline_notifications (player_uuid, message) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, message);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> popNotifications(UUID uuid) {
        List<String> notifications = new ArrayList<>();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT message FROM vkchat_offline_notifications WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        notifications.add(rs.getString("message"));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM vkchat_offline_notifications WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return notifications;
    }

    public void savePlayerGear(UUID uuid, int damage, int defense) {
        boolean useMysql = useMysql();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection()) {
            PreparedStatement ps;
            if (useMysql) {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_gear (player_uuid, damage, defense) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE damage = ?, defense = ?");
            } else {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_gear (player_uuid, damage, defense) VALUES (?, ?, ?) " +
                                "ON CONFLICT(player_uuid) DO UPDATE SET damage = excluded.damage, defense = excluded.defense");
            }
            ps.setString(1, uuid.toString());
            ps.setInt(2, damage);
            ps.setInt(3, defense);
            if (useMysql) {
                ps.setInt(4, damage);
                ps.setInt(5, defense);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int[] loadPlayerGear(UUID uuid) {
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT damage, defense FROM vkchat_offline_gear WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt("damage"), rs.getInt("defense")};
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new int[]{5, 0}; // Default damage: 5, default defense: 0
    }

    public Map<Integer, Expedition> loadAllExpeditions() {
        Map<Integer, Expedition> result = new HashMap<>();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT vk_id, player_uuid, data FROM vkchat_offline_expeditions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int vkId = rs.getInt("vk_id");
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                Expedition expedition = deserializeExpedition(vkId, uuid, rs.getString("data"));
                if (expedition != null) {
                    result.put(vkId, expedition);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public Map<Integer, String> loadAllPets() {
        Map<Integer, String> result = new HashMap<>();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT vk_id, pet_name FROM vkchat_offline_pets");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("vk_id"), rs.getString("pet_name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public void saveExpedition(Expedition expedition) {
        if (expedition == null) {
            return;
        }

        String data = serializeExpedition(expedition);
        boolean useMysql = useMysql();

        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection()) {
            PreparedStatement ps;
            if (useMysql) {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_expeditions (vk_id, player_uuid, data) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE player_uuid = ?, data = ?");
            } else {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_expeditions (vk_id, player_uuid, data) VALUES (?, ?, ?) " +
                                "ON CONFLICT(vk_id) DO UPDATE SET player_uuid = excluded.player_uuid, data = excluded.data");
            }
            ps.setInt(1, expedition.getSenderId());
            ps.setString(2, expedition.getPlayerUuid().toString());
            ps.setString(3, data);
            if (useMysql) {
                ps.setString(4, expedition.getPlayerUuid().toString());
                ps.setString(5, data);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteExpedition(int vkId) {
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM vkchat_offline_expeditions WHERE vk_id = ?")) {
            ps.setInt(1, vkId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void savePet(int vkId, String petName) {
        boolean useMysql = useMysql();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection()) {
            PreparedStatement ps;
            if (useMysql) {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_pets (vk_id, pet_name) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE pet_name = ?");
            } else {
                ps = conn.prepareStatement(
                        "INSERT INTO vkchat_offline_pets (vk_id, pet_name) VALUES (?, ?) " +
                                "ON CONFLICT(vk_id) DO UPDATE SET pet_name = excluded.pet_name");
            }
            ps.setInt(1, vkId);
            ps.setString(2, petName);
            if (useMysql) {
                ps.setString(3, petName);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deletePet(int vkId) {
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM vkchat_offline_pets WHERE vk_id = ?")) {
            ps.setInt(1, vkId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllExpeditions(Map<Integer, Expedition> expeditions) {
        for (Expedition expedition : expeditions.values()) {
            saveExpedition(expedition);
        }
    }

    public void saveAllPets(Map<Integer, String> pets) {
        for (Map.Entry<Integer, String> entry : pets.entrySet()) {
            savePet(entry.getKey(), entry.getValue());
        }
    }

    private String serializeExpedition(Expedition expedition) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("peerId", expedition.getPeerId());
        yaml.set("dungeonType", expedition.getDungeonType());
        yaml.set("stage", expedition.getStage());
        yaml.set("maxStages", expedition.getMaxStages());
        yaml.set("nextEventTime", expedition.getNextEventTime());
        yaml.set("waitingChoice", expedition.isWaitingChoice());
        yaml.set("endTime", expedition.getEndTime());
        yaml.set("expeditionEndTime", expedition.getExpeditionEndTime());
        yaml.set("estimatedTotalMinutes", expedition.getEstimatedTotalMinutes());
        yaml.set("hp", expedition.getHp());
        yaml.set("maxHp", expedition.getMaxHp());
        yaml.set("level", expedition.getLevel());
        yaml.set("damage", expedition.getDamage());
        yaml.set("defense", expedition.getDefense());
        yaml.set("hasPet", expedition.hasPet());
        yaml.set("petType", expedition.getPetType());
        yaml.set("isPetFed", expedition.isPetFed());
        yaml.set("isNight", expedition.isNight());
        yaml.set("consecutiveWins", expedition.getConsecutiveWins());
        yaml.set("damageTakenTotal", expedition.getDamageTakenTotal());
        yaml.set("activeModifier", expedition.getActiveModifier());
        yaml.set("modifierDuration", expedition.getModifierDuration());
        yaml.set("currentEncounterType", expedition.getCurrentEncounterType());
        yaml.set("inCombat", expedition.isInCombat());
        yaml.set("pendingEventTitle", expedition.getPendingEventTitle());
        yaml.set("pendingEventDescription", expedition.getPendingEventDescription());
        yaml.set("inventory", Base64Util.toBase64(expedition.getInventory()));
        yaml.set("messages", expedition.getMessages());
        yaml.set("baseDamage", expedition.getBaseDamage());
        yaml.set("baseDefense", expedition.getBaseDefense());
        yaml.set("waitingRiddle", expedition.isWaitingRiddle());
        yaml.set("currentRiddleQuestion", expedition.getCurrentRiddleQuestion());
        yaml.set("currentRiddleAnswers", expedition.getCurrentRiddleAnswers());
        yaml.set("riddleSuccessReward", expedition.getRiddleSuccessReward());
        yaml.set("riddleFailReward", expedition.getRiddleFailReward());
        yaml.set("riddleExpireTime", expedition.getRiddleExpireTime());
        yaml.set("bossBribeCost", expedition.getBossBribeCost());
        return yaml.saveToString();
    }

    private Expedition deserializeExpedition(int vkId, UUID playerUuid, String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(data);

            int peerId = yaml.getInt("peerId");
            String dungeonType = yaml.getString("dungeonType", "forest");
            int maxStages = yaml.getInt("maxStages", 3);

            Expedition expedition = new Expedition(peerId, vkId, playerUuid, dungeonType, maxStages);
            expedition.applyPersistedState(
                    dungeonType,
                    yaml.getInt("stage", 1),
                    maxStages,
                    yaml.getLong("nextEventTime", System.currentTimeMillis()),
                    yaml.getBoolean("waitingChoice", false),
                    yaml.getLong("endTime", 0),
                    yaml.getLong("expeditionEndTime", 0),
                    yaml.getInt("estimatedTotalMinutes", 0),
                    yaml.getInt("hp", 100),
                    yaml.getInt("maxHp", 100),
                    yaml.getInt("level", 1),
                    yaml.getInt("damage", 5),
                    yaml.getInt("defense", 0),
                    yaml.getBoolean("hasPet", false),
                    yaml.getString("petType", yaml.getBoolean("hasPet", false) ? "Сокол" : null),
                    yaml.getBoolean("isPetFed", true),
                    yaml.getBoolean("isNight", false),
                    yaml.getInt("consecutiveWins", 0),
                    yaml.getInt("damageTakenTotal", 0),
                    yaml.getString("activeModifier"),
                    yaml.getInt("modifierDuration", 0),
                    yaml.getString("currentEncounterType"),
                    yaml.getBoolean("inCombat", false),
                    yaml.getString("pendingEventTitle"),
                    yaml.getString("pendingEventDescription"),
                    Base64Util.fromBase64(yaml.getString("inventory", "")),
                    yaml.getStringList("messages"),
                    yaml.getInt("baseDamage", 5),
                    yaml.getInt("baseDefense", 0),
                    yaml.getBoolean("waitingRiddle", false),
                    yaml.getString("currentRiddleQuestion"),
                    yaml.getStringList("currentRiddleAnswers"),
                    yaml.getString("riddleSuccessReward"),
                    yaml.getString("riddleFailReward"),
                    yaml.getLong("riddleExpireTime", 0),
                    yaml.getInt("bossBribeCost", 0)
            );
            return expedition;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, Object> getRandomPlayerGhost(UUID excludeUuid) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = VKChatPlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT player_uuid, damage, defense FROM vkchat_offline_gear WHERE player_uuid != ?")) {
            ps.setString(1, excludeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) name = "Призрак Исследователя";
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("uuid", uuid);
                    map.put("damage", rs.getInt("damage"));
                    map.put("defense", rs.getInt("defense"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (list.isEmpty()) return null;
        return list.get(new java.util.Random().nextInt(list.size()));
    }

    private boolean useMysql() {
        return VKChatPlugin.getInstance().getConfig().getBoolean("database.use-mysql", false);
    }
}
