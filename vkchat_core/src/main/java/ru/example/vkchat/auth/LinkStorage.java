package ru.example.vkchat.auth;

import ru.example.vkchat.VKChatPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище привязок UUID ↔ VK ID в текстовом файле.
 * Формат: uuid=vkId — по одной записи на строку.
 */
public class LinkStorage {
    private final File file;
    private final Map<UUID, Integer> uuidToVk = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> vkToUuid = new ConcurrentHashMap<>();

    public LinkStorage(VKChatPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "links.txt");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (IOException e) {
                plugin().getLogger().warning("Не удалось создать links.txt: " + e.getMessage());
            }
            return;
        }
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
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            plugin().getLogger().warning("Ошибка чтения links.txt: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("# UUID=VK_ID — привязки аккаунтов CHRDK REBORN");
            writer.newLine();
            for (Map.Entry<UUID, Integer> e : uuidToVk.entrySet()) {
                writer.write(e.getKey().toString() + "=" + e.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            plugin().getLogger().warning("Ошибка записи links.txt: " + e.getMessage());
        }
    }

    public void link(UUID uuid, int vkId) {
        // Удалить старые привязки
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
            // Записываем строку с vk_id = -1 для совместимости с БД
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

    private VKChatPlugin plugin() {
        return VKChatPlugin.getInstance();
    }
}
