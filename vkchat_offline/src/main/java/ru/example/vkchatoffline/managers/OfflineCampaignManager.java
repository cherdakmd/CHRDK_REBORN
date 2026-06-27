package ru.example.vkchatoffline.managers;

import org.bukkit.configuration.file.FileConfiguration;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Кампания и коллекции Offline 2.0.
 *
 * Вынесено из AdventureManager без изменения путей данных:
 * campaign.<vk>.chapter.<n>, collections.<vk>.<id>.
 */
public class OfflineCampaignManager {
    private final VKChatOfflinePlugin plugin;
    private final Supplier<FileConfiguration> data;
    private final BiConsumer<Integer, String> journal;

    public OfflineCampaignManager(VKChatOfflinePlugin plugin, Supplier<FileConfiguration> data, BiConsumer<Integer, String> journal) {
        this.plugin = plugin;
        this.data = data;
        this.journal = journal;
    }

    private FileConfiguration d() { return data.get(); }

    public String chapterForRoute(String route) {
        if (route.equals("forest")) return "1";
        if (route.equals("mine")) return "2";
        if (route.equals("ruins")) return "3";
        if (route.equals("swamp")) return "4";
        if (route.equals("castle")) return "5";
        if (route.equals("nether")) return "6";
        return "";
    }

    public String routeForChapter(String ch) {
        if (ch.equals("1")) return "forest";
        if (ch.equals("2")) return "mine";
        if (ch.equals("3")) return "ruins";
        if (ch.equals("4")) return "swamp";
        if (ch.equals("5")) return "castle";
        if (ch.equals("6")) return "nether";
        return "forest";
    }

    public String chapterName(String ch) {
        switch (ch) {
            case "1": return "I. Зов леса";
            case "2": return "II. Шахты без рассвета";
            case "3": return "III. Руины древних";
            case "4": return "IV. Болота Мораны";
            case "5": return "V. Проклятый замок";
            case "6": return "VI. Адские врата";
            default: return ch;
        }
    }

    public boolean isChapterUnlocked(int vkId, String ch) {
        if (ch.equals("1")) return true;
        try {
            return d().getBoolean("campaign." + vkId + ".chapter." + (Integer.parseInt(ch) - 1), false);
        } catch (Exception e) {
            return false;
        }
    }

    public void completeCampaignForRoute(int vkId, String route) {
        String ch = chapterForRoute(route);
        if (!ch.isEmpty()) {
            d().set("campaign." + vkId + ".chapter." + ch, true);
            journal.accept(vkId, "📖 Кампания: завершена глава " + chapterName(ch));
        }
    }

    public String campaignLine(int vkId) {
        int done = 0;
        for (int i = 1; i <= 6; i++) if (d().getBoolean("campaign." + vkId + ".chapter." + i, false)) done++;
        return done + "/6 глав";
    }

    public String buildCampaignText(int vkId) {
        StringBuilder sb = new StringBuilder("📖 Кампания: Хроники пропавшей экспедиции\n\n");
        for (int i = 1; i <= 6; i++) {
            String ch = String.valueOf(i);
            boolean done = d().getBoolean("campaign." + vkId + ".chapter." + ch, false);
            boolean unlock = isChapterUnlocked(vkId, ch);
            String route = routeForChapter(ch);
            sb.append(done ? "✅ " : (unlock ? "▶ " : "🔒 "))
                    .append(chapterName(ch)).append(" — ")
                    .append(plugin.getConfig().getString("adventures." + route + ".name", route))
                    .append("\n");
        }
        sb.append("\nКоманда: !глава <1-6>. Главы идут по маршрутам и дают историю, коллекции и прогресс.");
        return sb.toString();
    }

    public String[] collectionIds() {
        return new String[]{"forest_totem", "mine_map", "ruins_tablet", "swamp_flower", "castle_seal", "nether_core"};
    }

    public String collectionName(String id) {
        if ("forest_totem".equals(id)) return "Лесной тотем";
        if ("mine_map".equals(id)) return "Карта шахт";
        if ("ruins_tablet".equals(id)) return "Каменная табличка";
        if ("swamp_flower".equals(id)) return "Цветок Мораны";
        if ("castle_seal".equals(id)) return "Печать замка";
        if ("nether_core".equals(id)) return "Адское ядро";
        return id;
    }

    public String collectionForRoute(String route) {
        if (route.equals("forest")) return "forest_totem";
        if (route.equals("mine")) return "mine_map";
        if (route.equals("ruins")) return "ruins_tablet";
        if (route.equals("swamp")) return "swamp_flower";
        if (route.equals("castle")) return "castle_seal";
        if (route.equals("nether")) return "nether_core";
        return "forest_totem";
    }

    public void discoverCollection(int vkId, String route, StringBuilder msg) {
        String id = collectionForRoute(route);
        if (!d().getBoolean("collections." + vkId + "." + id, false)) {
            d().set("collections." + vkId + "." + id, true);
            journal.accept(vkId, "🏺 Найдена коллекция: " + collectionName(id));
            try { VKChatPlugin.getInstance().getApi().addReputation(vkId, plugin.getConfig().getInt("offline2.collections.reward-rep", 75)); } catch (Exception ignored) {}
            if (msg != null) msg.append("🏺 Коллекция открыта: ").append(collectionName(id)).append("\n");
        } else if (msg != null) {
            msg.append("🏺 Найден повтор коллекции: ").append(collectionName(id)).append("\n");
        }
    }

    public String buildCollectionsText(int vkId) {
        StringBuilder sb = new StringBuilder("🏺 Коллекции экспедиции\n\n");
        int found = 0;
        for (String id : collectionIds()) {
            boolean ok = d().getBoolean("collections." + vkId + "." + id, false);
            if (ok) found++;
            sb.append(ok ? "✅ " : "⬜ ").append(collectionName(id)).append("\n");
        }
        sb.append("\nСобрано: ").append(found).append("/").append(collectionIds().length).append(". Коллекции падают в событиях и при завершении глав.");
        return sb.toString();
    }
}
