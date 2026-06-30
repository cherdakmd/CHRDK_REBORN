package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ядро системы Энда — телепортация, зоны, уровни доступа
 */
public class EndManager {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey endLevelKey;
    private final NamespacedKey endRepKey;

    // Зоны Энда
    private final Map<String, EndZone> zones = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();

    public enum EndZone {
        SPAWN("Точка входа", 0),
        OUTER_ISLANDS("Внешние острова", 1),
        END_CITY_ZONE("Зона Эндер-городов", 2),
        CHORUS_FOREST("Лес Хоруса", 3),
        VOID_MINES("Бездонные шахты", 4),
        DRAGON_ARENA("Арена Дракона", 5),
        CORRUPTED_WASTES("Заражённые земли", 6),
        PURIFIED_HAVEN("Очищенный рай", 7);

        public final String displayName;
        public final int requiredLevel;

        EndZone(String displayName, int requiredLevel) {
            this.displayName = displayName;
            this.requiredLevel = requiredLevel;
        }
    }

    public EndManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.endLevelKey = new NamespacedKey(plugin, "end_level");
        this.endRepKey = new NamespacedKey(plugin, "end_reputation");
        initZones();
    }

    private void initZones() {
        // Зоны инициализируются из конфига или по умолчанию
        for (EndZone zone : EndZone.values()) {
            zones.put(zone.name(), zone);
        }
    }

    /**
     * Получить уровень игрока в Энде
     */
    public int getEndLevel(Player p) {
        return p.getPersistentDataContainer().getOrDefault(endLevelKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Установить уровень игрока в Энде
     */
    public void setEndLevel(Player p, int level) {
        p.getPersistentDataContainer().set(endLevelKey, PersistentDataType.INTEGER, level);
    }

    /**
     * Получить репутацию в Энде
     */
    public int getEndReputation(Player p) {
        return p.getPersistentDataContainer().getOrDefault(endRepKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Добавить репутацию в Энде
     */
    public void addEndReputation(Player p, int amount) {
        int current = getEndReputation(p);
        p.getPersistentDataContainer().set(endRepKey, PersistentDataType.INTEGER, current + amount);

        // Проверка повышения уровня
        checkLevelUp(p);
    }

    /**
     * Проверить повышение уровня
     */
    private void checkLevelUp(Player p) {
        int currentLevel = getEndLevel(p);
        int rep = getEndReputation(p);
        int requiredRep = getRequiredRepForLevel(currentLevel + 1);

        if (rep >= requiredRep && currentLevel < getMaxLevel()) {
            setEndLevel(p, currentLevel + 1);
            p.sendMessage(ChatColor.DARK_PURPLE + "⬆ Уровень Энда повышен: " + ChatColor.LIGHT_PURPLE + (currentLevel + 1));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);

            // Уведомление в ВК
            try {
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatPlugin.getInstance().getApi().addReputation(vkId, 100);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Получить необходимую репутацию для уровня
     */
    public int getRequiredRepForLevel(int level) {
        return level * level * 500; // 500, 2000, 4500, 8000, 12500, ...
    }

    /**
     * Получить максимальный уровень
     */
    public int getMaxLevel() {
        return plugin.getConfig().getInt("end.max-level", 10);
    }

    /**
     * Проверить доступ к зоне
     */
    public boolean canAccessZone(Player p, EndZone zone) {
        return getEndLevel(p) >= zone.requiredLevel;
    }

    /**
     * Телепортировать в Энд
     */
    public boolean teleportToEnd(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink) для доступа к Энду!");
            return false;
        }

        int cost = plugin.getConfig().getInt("end.teleport-cost", 500);
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
            return false;
        }

        // Кулдаун
        long cooldown = plugin.getConfig().getLong("end.teleport-cooldown", 300) * 1000;
        Long lastTeleport = teleportCooldowns.get(p.getUniqueId());
        if (lastTeleport != null && System.currentTimeMillis() - lastTeleport < cooldown) {
            long remaining = (cooldown - (System.currentTimeMillis() - lastTeleport)) / 1000;
            p.sendMessage(ChatColor.RED + "Подожди " + remaining + " сек.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        teleportCooldowns.put(p.getUniqueId(), System.currentTimeMillis());

        World endWorld = plugin.getEndWorld();
        if (endWorld == null) {
            p.sendMessage(ChatColor.RED + "Мир Энда не найден!");
            return false;
        }

        Location spawn = endWorld.getSpawnLocation();
        p.teleport(spawn.add(0.5, 1, 0.5));
        p.sendMessage(ChatColor.DARK_PURPLE + "✦ Добро пожаловать в Энд! Стоимость: " + cost + " реп.");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);

        return true;
    }

    /**
     * Получить информацию о прогрессе игрока
     */
    public String getPlayerInfo(Player p) {
        int level = getEndLevel(p);
        int rep = getEndReputation(p);
        int nextRep = getRequiredRepForLevel(level + 1);

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ 🐉 ЭНД — Профиль ═══\n\n");
        sb.append(ChatColor.LIGHT_PURPLE).append("Уровень: ").append(ChatColor.WHITE).append(level).append("/").append(getMaxLevel()).append("\n");
        sb.append(ChatColor.LIGHT_PURPLE).append("Репутация: ").append(ChatColor.WHITE).append(rep).append("/").append(nextRep).append("\n");

        sb.append("\n").append(ChatColor.DARK_PURPLE).append("Доступные зоны:\n");
        for (EndZone zone : EndZone.values()) {
            boolean hasAccess = canAccessZone(p, zone);
            sb.append(hasAccess ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ");
            sb.append(ChatColor.GRAY).append(zone.displayName);
            if (zone.requiredLevel > 0) {
                sb.append(ChatColor.DARK_GRAY).append(" (ур. ").append(zone.requiredLevel).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
