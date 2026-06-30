package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [21] Система достижений Энда
 * [22] Таблица лидеров
 * [23] Ежедневные квесты
 * [24] Еженедельные испытания
 * [25] Ежемесячные события
 * [26] Гильдии Энда
 * [27] Контроль территорий
 */
public class EndProgressManager {
    private final VKChatEndPlugin plugin;

    // Достижения
    public static final String[][] END_ACHIEVEMENTS = {
        {"first_portal", "Первый портал", "Создать первый портал в Энд", "500"},
        {"dragon_slayer", "Убийца драконов", "Убить 10 драконов", "2000"},
        {"city_explorer", "Исследователь", "Найти 5 эндер-городов", "1000"},
        {"ore_master", "Мастер добычи", "Добыть 100 эндер-руд", "1500"},
        {"corruption_cleanser", "Очиститель", "Очистить 10 зон коррупции", "2000"},
        {"rift_walker", "Странник разломов", "Использовать 20 разломов", "800"},
        {"artifact_collector", "Коллекционер", "Собрать 10 артефактов", "3000"},
        {"end_level_10", "Мастер Энда", "Достичь 10 уровня Энда", "5000"},
        {"shulker_lord", "Повелитель шалкеров", "Улучшить шалкер до максимума", "2500"},
        {"elytra_master", "Мастер полётов", "Улучшить элитры до максимума", "3000"},
    };

    // Ежедневные квесты
    private final Map<UUID, DailyQuest> dailyQuests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastQuestReset = new ConcurrentHashMap<>();

    private static class DailyQuest {
        String type;
        String target;
        int required;
        int progress;
        int reward;

        DailyQuest(String type, String target, int required, int reward) {
            this.type = type;
            this.target = target;
            this.required = required;
            this.progress = 0;
            this.reward = reward;
        }
    }

    // Гильдии
    private final Map<String, GuildData> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGuilds = new ConcurrentHashMap<>();

    private static class GuildData {
        String name;
        UUID leader;
        Set<UUID> members;
        int level;
        int reputation;
        long creationTime;

        GuildData(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members = new HashSet<>();
            this.members.add(leader);
            this.level = 1;
            this.reputation = 0;
            this.creationTime = System.currentTimeMillis();
        }
    }

    // Территории
    private final Map<String, TerritoryData> territories = new ConcurrentHashMap<>();

    private static class TerritoryData {
        String guildName;
        Location center;
        int radius;
        long captureTime;

        TerritoryData(String guildName, Location center, int radius) {
            this.guildName = guildName;
            this.center = center;
            this.radius = radius;
            this.captureTime = System.currentTimeMillis();
        }
    }

    public EndProgressManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        startDailyReset();
    }

    /**
     * Сброс ежедневных квестов
     */
    private void startDailyReset() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : lastQuestReset.entrySet()) {
                if (now - entry.getValue() > 86400000) { // 24 часа
                    dailyQuests.remove(entry.getKey());
                    lastQuestReset.remove(entry.getKey());
                }
            }
        }, 72000L, 72000L); // Каждый час
    }

    /**
     * Получить ежедневный квест
     */
    public DailyQuest getDailyQuest(Player p) {
        UUID uuid = p.getUniqueId();
        DailyQuest quest = dailyQuests.get(uuid);
        if (quest == null) {
            quest = generateDailyQuest();
            dailyQuests.put(uuid, quest);
            lastQuestReset.put(uuid, System.currentTimeMillis());
        }
        return quest;
    }

    /**
     * Сгенерировать случайный квест
     */
    private DailyQuest generateDailyQuest() {
        String[] types = {"kill", "mine", "explore", "craft"};
        String type = types[new Random().nextInt(types.length)];

        switch (type) {
            case "kill":
                return new DailyQuest("kill", "ENDERMAN", 25, 300);
            case "mine":
                return new DailyQuest("mine", "ENDER_CRYSTAL_ORE", 10, 250);
            case "explore":
                return new DailyQuest("explore", "END_CITY", 2, 400);
            case "craft":
                return new DailyQuest("craft", "END_PORTAL", 1, 500);
            default:
                return new DailyQuest("kill", "ENDERMAN", 25, 300);
        }
    }

    /**
     * Обновить прогресс квеста
     */
    public void updateQuestProgress(Player p, String type, String target, int amount) {
        DailyQuest quest = getDailyQuest(p);
        if (quest.type.equals(type) && quest.target.equals(target)) {
            quest.progress += amount;
            if (quest.progress >= quest.required) {
                completeDailyQuest(p, quest);
            } else {
                p.sendMessage(ChatColor.GRAY + "📋 Квест: " + quest.progress + "/" + quest.required);
            }
        }
    }

    /**
     * Завершить квест
     */
    private void completeDailyQuest(Player p, DailyQuest quest) {
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, quest.reward);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(p, quest.reward / 2);
        p.sendMessage(ChatColor.GOLD + "📋 Квест выполнен! +" + quest.reward + " реп. ВК");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // Сброс квеста
        dailyQuests.remove(p.getUniqueId());
    }

    /**
     * Создать гильдию
     */
    public boolean createGuild(Player p, String guildName) {
        if (playerGuilds.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Ты уже в гильдии!");
            return false;
        }

        if (guilds.containsKey(guildName)) {
            p.sendMessage(ChatColor.RED + "Гильдия с таким именем уже существует!");
            return false;
        }

        int cost = plugin.getConfig().getInt("end.guilds.creation-cost", 5000);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        guilds.put(guildName, new GuildData(guildName, p.getUniqueId()));
        playerGuilds.put(p.getUniqueId(), guildName);

        p.sendMessage(ChatColor.GOLD + "✦ Гильдия '" + guildName + "' создана! Стоимость: " + cost + " реп.");
        return true;
    }

    /**
     * Присоединиться к гильдии
     */
    public boolean joinGuild(Player p, String guildName) {
        if (playerGuilds.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Ты уже в гильдии!");
            return false;
        }

        GuildData guild = guilds.get(guildName);
        if (guild == null) {
            p.sendMessage(ChatColor.RED + "Гильдия не найдена!");
            return false;
        }

        guild.members.add(p.getUniqueId());
        playerGuilds.put(p.getUniqueId(), guildName);

        p.sendMessage(ChatColor.GREEN + "✦ Ты вступил в гильдию '" + guildName + "'!");
        return true;
    }

    /**
     * Захватить территорию
     */
    public boolean captureTerritory(Player p, Location center, int radius) {
        String guildName = playerGuilds.get(p.getUniqueId());
        if (guildName == null) {
            p.sendMessage(ChatColor.RED + "Ты не в гильдии!");
            return false;
        }

        GuildData guild = guilds.get(guildName);
        if (guild == null) return false;

        // Проверка на пересечение территорий
        for (TerritoryData territory : territories.values()) {
            if (territory.center.getWorld().equals(center.getWorld())) {
                double distance = territory.center.distance(center);
                if (distance < territory.radius + radius) {
                    p.sendMessage(ChatColor.RED + "Территория пересекается с другой!");
                    return false;
                }
            }
        }

        int cost = plugin.getConfig().getInt("end.territories.capture-cost", 2000);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        String territoryKey = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        territories.put(territoryKey, new TerritoryData(guildName, center, radius));

        p.sendMessage(ChatColor.GOLD + "✦ Территория захвачена гильдией '" + guildName + "'!");
        return true;
    }

    /**
     * Получить достижения игрока
     */
    public String getAchievementsInfo(Player p) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ✦ Достижения Энда ═══\n");

        for (String[] achievement : END_ACHIEVEMENTS) {
            boolean unlocked = hasAchievement(p, achievement[0]);
            sb.append(unlocked ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ");
            sb.append(ChatColor.GRAY).append(achievement[1]);
            sb.append(ChatColor.DARK_GRAY).append(" — ").append(achievement[2]);
            if (unlocked) sb.append(ChatColor.GREEN).append(" (+").append(achievement[3]).append(" реп.)");
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean hasAchievement(Player p, String achievementId) {
        return p.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "end_ach_" + achievementId), PersistentDataType.INTEGER);
    }

    public void unlockAchievement(Player p, String achievementId) {
        NamespacedKey key = new NamespacedKey(plugin, "end_ach_" + achievementId);
        if (p.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) return;

        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);

        // Награда
        for (String[] achievement : END_ACHIEVEMENTS) {
            if (achievement[0].equals(achievementId)) {
                int reward = Integer.parseInt(achievement[3]);
                try {
                    int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                    if (vkId != -1) {
                        VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
                    }
                } catch (Exception ignored) {}
                plugin.getEndManager().addEndReputation(p, reward / 2);

                p.sendMessage(ChatColor.GOLD + "✦ Достижение: " + achievement[1] + " (+" + reward + " реп.)");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                break;
            }
        }
    }

    public int getAchievementCount() { return END_ACHIEVEMENTS.length; }
    public int getGuildCount() { return guilds.size(); }
    public int getTerritoryCount() { return territories.size(); }
}
