package ru.example.vkchatoffline.managers;

import org.bukkit.configuration.file.FileConfiguration;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Уровни, XP, прогресс маршрутов и ежедневки оффлайн-походника.
 * Вынесено из AdventureManager без изменения формата adventures.yml.
 */
public class OfflineProgressManager {
    private final VKChatOfflinePlugin plugin;
    private final Supplier<FileConfiguration> data;
    private final BiConsumer<Integer, String> journal;

    public OfflineProgressManager(VKChatOfflinePlugin plugin, Supplier<FileConfiguration> data, BiConsumer<Integer, String> journal) {
        this.plugin = plugin;
        this.data = data;
        this.journal = journal;
    }

    private FileConfiguration d() { return data.get(); }

    public int getAdvLevel(int vkId) {
        return d().getInt("stats." + vkId + ".level", 1);
    }

    public int getAdvXp(int vkId) {
        return d().getInt("stats." + vkId + ".xp", 0);
    }

    public int xpToNext(int level) {
        return 100 + Math.max(1, level) * 50;
    }

    public int addAdventureXp(int vkId, int amount) {
        int level = getAdvLevel(vkId);
        int xp = getAdvXp(vkId) + Math.max(0, amount);
        int levels = 0;
        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            levels++;
        }
        d().set("stats." + vkId + ".level", level);
        d().set("stats." + vkId + ".xp", xp);
        return levels;
    }

    public int getProgress(int vkId) {
        return d().getInt("stats." + vkId + ".progress", 0);
    }

    public void addProgress(int vkId, String route) {
        d().set("stats." + vkId + ".progress", getProgress(vkId) + 1);
        d().set("stats." + vkId + ".completed." + route, d().getInt("stats." + vkId + ".completed." + route, 0) + 1);
    }

    public String dailyDate() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    public void ensureDaily(int vkId) {
        String today = dailyDate();
        if (today.equals(d().getString("daily." + vkId + ".date"))) return;
        String[] types = {"complete", "relic", "gold"};
        String type = types[ThreadLocalRandom.current().nextInt(types.length)];
        d().set("daily." + vkId + ".date", today);
        d().set("daily." + vkId + ".type", type);
        d().set("daily." + vkId + ".progress", 0);
        d().set("daily." + vkId + ".target", type.equals("complete") ? 1 : type.equals("relic") ? 1 : 100);
        d().set("daily." + vkId + ".claimed", false);
    }

    public String buildDailyText(int vkId) {
        ensureDaily(vkId);
        String type = d().getString("daily." + vkId + ".type", "complete");
        int progress = d().getInt("daily." + vkId + ".progress", 0);
        int target = d().getInt("daily." + vkId + ".target", 1);
        boolean claimed = d().getBoolean("daily." + vkId + ".claimed", false);
        String name = dailyName(type);
        if (!claimed && progress >= target) {
            int reward = plugin.getConfig().getInt("mmorpg.daily.reward-rep", 120);
            VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
            d().set("daily." + vkId + ".claimed", true);
            journal.accept(vkId, "📅 Ежедневка выполнена: " + name + " (+" + reward + " реп.)");
            claimed = true;
        }
        return "📅 Ежедневное задание\n\n" + name + "\nПрогресс: " + Math.min(progress, target) + "/" + target + "\nСтатус: " + (claimed ? "✅ награда получена" : "⏳ в процессе");
    }

    public void progressDaily(int vkId, String type, int amount) {
        ensureDaily(vkId);
        if (!type.equals(d().getString("daily." + vkId + ".type"))) return;
        d().set("daily." + vkId + ".progress", d().getInt("daily." + vkId + ".progress", 0) + amount);
    }

    private String dailyName(String type) {
        return type.equals("complete") ? "Завершить 1 поход" : type.equals("relic") ? "Найти 1 реликвию" : "Собрать 100 золота";
    }
}
