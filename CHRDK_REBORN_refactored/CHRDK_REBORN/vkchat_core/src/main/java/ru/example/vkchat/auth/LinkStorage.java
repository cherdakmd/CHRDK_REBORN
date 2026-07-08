package ru.example.vkchat.auth;

import ru.example.vkchat.VKChatPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище привязок UUID ↔ VK ID в текстовом файле.
 * Формат: uuid=vkId — по одной записи на строку.
 * При первом запуске автоматически мигрирует данные из старой БД.
 * В случае конфликта (VK ID привязан к нескольким UUID) — первый по дате wins.
 */
public class LinkStorage {
    private final VKChatPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> uuidToVk = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> vkToUuid = new ConcurrentHashMap<>();
    private boolean migrated = false;

    public LinkStorage(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.txt");
        load();
    }

    private void load() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            migrateFromDatabase();
            return;
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    try {
                        UUID uuid = UUID.fromString(parts[0].trim());
                        int vkId = Integer.parseInt(parts[1].trim());
                        uuidToVk.put(uuid, vkId);
                        vkToUuid.put(vkId, uuid);
                        count++;
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка чтения links.txt: " + e.getMessage());
        }
        if (count == 0) {
            migrateFromDatabase();
        } else {
            plugin.getLogger().info("Загружено " + count + " привязок из links.txt");
        }
    }

    private void migrateFromDatabase() {
        int imported = 0, duplicates = 0, skipped = 0;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, vk_id FROM vkchat_auth WHERE vk_id > 0 ORDER BY reg_date ASC");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                int vkId = rs.getInt("vk_id");
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (vkToUuid.containsKey(vkId)) {
                        duplicates++;
                        plugin.getLogger().warning("Дубль VK ID " + vkId + ": уже привязан к "
                                + vkToUuid.get(vkId) + ", пропускаем " + uuid);
                        continue;
                    }
                    if (uuidToVk.containsKey(uuid)) {
                        skipped++;
                        continue;
                    }
                    uuidToVk.put(uuid, vkId);
                    vkToUuid.put(vkId, uuid);
                    imported++;
                } catch (IllegalArgumentException e) {
                    skipped++;
                }
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка миграции из БД: " + e.getMessage());
        }

        if (imported > 0) {
            migrated = true;
            plugin.getLogger().info("Миграция привязок: " + imported + " импортировано, "
                    + duplicates + " дублей пропущено, " + skipped + " ошибок.");
        } else {
            plugin.getLogger().info("Миграция: нет записей для переноса из БД.");
        }
        save();
    }

    public synchronized void save() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("# UUID=VK_ID — привязки аккаунтов CHRDK REBORN");
            writer.newLine();
            if (migrated) writer.write("# Мигрировано из старой БД");
            else writer.write("# Создано автоматически");
            writer.newLine();
            for (Map.Entry<UUID, Integer> e : uuidToVk.entrySet()) {
                writer.write(e.getKey().toString() + "=" + e.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка записи links.txt: " + e.getMessage());
        }
    }

    public void link(UUID uuid, int vkId) {
        Integer oldVk = uuidToVk.remove(uuid);
        if (oldVk != null) vkToUuid.remove(oldVk);
        UUID oldUuid = vkToUuid.remove(vkId);
        if (oldUuid != null) uuidToVk.remove(oldUuid);
        uuidToVk.put(uuid, vkId);
        vkToUuid.put(vkId, uuid);
        save();
    }

    public void unlink(UUID uuid) {
        Integer vkId = uuidToVk.remove(uuid);
        if (vkId != null) {
            vkToUuid.remove(vkId);
            save();
        }
    }

    public int getVkId(UUID uuid) {
        Integer vkId = uuidToVk.get(uuid);
        return vkId != null ? vkId : -1;
    }

    public UUID getUuid(int vkId) {
        return vkToUuid.get(vkId);
    }

    public boolean isLinked(UUID uuid) {
        return uuidToVk.containsKey(uuid);
    }

    public boolean isVkLinked(int vkId) {
        return vkToUuid.containsKey(vkId);
    }

    public int getLinkCount() {
        return uuidToVk.size();
    }

    public Map<UUID, Integer> getAllLinks() {
        return new HashMap<>(uuidToVk);
    }
}
