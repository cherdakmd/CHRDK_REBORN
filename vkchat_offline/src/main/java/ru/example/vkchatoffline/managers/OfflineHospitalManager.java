package ru.example.vkchatoffline.managers;

import org.bukkit.configuration.file.FileConfiguration;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.ActiveAdventure;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Психика, фобии, травмы и госпиталь Offline 2.0.
 *
 * Вынесено из AdventureManager без изменения формата adventures.yml:
 * stats.<vk>.sanity, stats.<vk>.traumas, stats.<vk>.phobia.
 */
public class OfflineHospitalManager {
    private final VKChatOfflinePlugin plugin;
    private final Supplier<FileConfiguration> data;
    private final Map<Integer, ActiveAdventure> active;
    private final BiConsumer<Integer, String> journal;
    private final Runnable saveAll;
    private final Random random = new Random();

    public OfflineHospitalManager(VKChatOfflinePlugin plugin,
                                  Supplier<FileConfiguration> data,
                                  Map<Integer, ActiveAdventure> active,
                                  BiConsumer<Integer, String> journal,
                                  Runnable saveAll) {
        this.plugin = plugin;
        this.data = data;
        this.active = active;
        this.journal = journal;
        this.saveAll = saveAll;
    }

    private FileConfiguration d() { return data.get(); }

    public int getSanity(int vkId) {
        return Math.max(0, Math.min(100, d().getInt("stats." + vkId + ".sanity", 100)));
    }

    public void setSanity(int vkId, int value) {
        d().set("stats." + vkId + ".sanity", Math.max(0, Math.min(100, value)));
    }

    public void changeSanity(int vkId, int delta, StringBuilder msg) {
        int now = Math.max(0, Math.min(100, getSanity(vkId) + delta));
        setSanity(vkId, now);
        ActiveAdventure adv = active.get(vkId);
        if (adv != null) adv.sanity = now;
        if (msg != null && delta != 0) msg.append("🧠 Рассудок: ").append(delta > 0 ? "+" : "").append(delta).append("% (").append(now).append("%)\n");
        if (now <= 20 && random.nextInt(100) < 20) addTrauma(vkId, "nightmares");
    }

    public List<String> getTraumas(int vkId) {
        return d().getStringList("stats." + vkId + ".traumas");
    }

    public boolean hasTrauma(int vkId, String id) {
        return getTraumas(vkId).contains(id);
    }

    public void addTrauma(int vkId, String id) {
        List<String> list = getTraumas(vkId);
        if (!list.contains(id)) {
            list.add(id);
            d().set("stats." + vkId + ".traumas", list);
            journal.accept(vkId, "🩹 Новая травма: " + traumaName(id));
        }
    }

    public void addRandomTrauma(int vkId) {
        String[] pool = {"deep_wound", "nightmares", "shaky_hands", "bad_omen"};
        addTrauma(vkId, pool[random.nextInt(pool.length)]);
    }

    public String traumaName(String id) {
        if ("deep_wound".equals(id)) return "Глубокая рана (-12 max HP в походе)";
        if ("nightmares".equals(id)) return "Кошмары (-1 вдохновение)";
        if ("shaky_hands".equals(id)) return "Дрожащие руки (сложнее ловушки/сокровища)";
        if ("bad_omen".equals(id)) return "Дурное знамение (хуже мистика)";
        return id;
    }

    public String traumaLine(int vkId) {
        List<String> t = getTraumas(vkId);
        if (t.isEmpty()) return "нет";
        StringBuilder sb = new StringBuilder();
        for (String id : t) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(traumaName(id));
        }
        return sb.toString();
    }

    public String getPhobia(int vkId) {
        return d().getString("stats." + vkId + ".phobia", "none");
    }

    public void setPhobia(int vkId, String id) {
        if (id != null && !id.equals("none")) {
            d().set("stats." + vkId + ".phobia", id);
            journal.accept(vkId, "🧠 Новая фобия: " + phobiaName(id));
        }
    }

    public boolean hasPhobia(int vkId, String type, boolean combatEvent, boolean slavicMyth) {
        String p = getPhobia(vkId);
        return ("dark".equals(p) && (type.equals("curse") || type.equals("nightmare") || type.equals("ambush"))) ||
                ("blood".equals(p) && combatEvent) ||
                ("depth".equals(p) && (type.equals("trap") || type.equals("survival"))) ||
                ("spirits".equals(p) && (type.equals("curse") || slavicMyth || type.equals("shrine")));
    }

    public String phobiaForRoute(String route) {
        if (route.equals("mine") || route.equals("nether")) return "depth";
        if (route.equals("castle") || route.equals("ruins")) return "spirits";
        if (route.equals("swamp")) return "dark";
        return "blood";
    }

    public void maybeAddPhobia(int vkId, String route, String type, StringBuilder msg) {
        if (!"none".equals(getPhobia(vkId))) return;
        if (random.nextInt(100) < 25) {
            String ph = phobiaForRoute(route);
            setPhobia(vkId, ph);
            if (msg != null) msg.append("🧠 Появилась фобия: ").append(phobiaName(ph)).append("\n");
        }
    }

    public String phobiaName(String id) {
        if (id == null || id.equals("none")) return "нет";
        if ("dark".equals(id)) return "Тьма";
        if ("blood".equals(id)) return "Кровь и бой";
        if ("depth".equals(id)) return "Глубина и ловушки";
        if ("spirits".equals(id)) return "Духи и проклятия";
        return id;
    }

    public String buildPsycheText(int vkId) {
        return "🧠 Психика походника\n\n" +
                "Рассудок: " + getSanity(vkId) + "%\n" +
                "Фобия: " + phobiaName(getPhobia(vkId)) + "\n" +
                "Травмы: " + traumaLine(vkId) + "\n\n" +
                "Низкий рассудок повышает DC проверок. Фобии опасны в связанных событиях. Травмы лечатся в госпитале.";
    }

    public String buildHospitalText(int vkId) {
        int sanityCost = plugin.getConfig().getInt("offline2.hospital.sanity-cost", 250);
        int traumaCost = plugin.getConfig().getInt("offline2.hospital.trauma-cost", 700);
        int fearCost = plugin.getConfig().getInt("offline2.hospital.fear-cost", 900);
        return "🏥 Госпиталь / Приют / Исповедальня\n\n" +
                "Баланс: " + VKChatPlugin.getInstance().getApi().getReputation(vkId) + " реп.\n" +
                "🧠 Терапия рассудка: +35% за " + sanityCost + " реп.\n" +
                "✅ Лечение травм: снять 1 травму за " + traumaCost + " реп.\n" +
                "🕯 Снять фобию: " + fearCost + " реп.\n\n" +
                "Команды: !госпиталь sanity | trauma | fear";
    }

    public String useHospital(int vkId, String mode) {
        mode = mode == null ? "" : mode.toLowerCase(Locale.ROOT);
        if (mode.equals("травма")) mode = "trauma";
        if (mode.equals("рассудок")) mode = "sanity";
        if (mode.equals("фобия")) mode = "fear";
        int cost = mode.equals("trauma") ? plugin.getConfig().getInt("offline2.hospital.trauma-cost", 700) :
                mode.equals("fear") ? plugin.getConfig().getInt("offline2.hospital.fear-cost", 900) :
                        plugin.getConfig().getInt("offline2.hospital.sanity-cost", 250);
        if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < cost) return "❌ Недостаточно репутации. Нужно: " + cost;

        if (mode.equals("trauma")) {
            List<String> t = getTraumas(vkId);
            if (t.isEmpty()) return "Травм нет.";
            String removed = t.remove(0);
            d().set("stats." + vkId + ".traumas", t);
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            journal.accept(vkId, "🏥 Вылечена травма: " + traumaName(removed));
            saveAll.run();
            return "✅ Травма вылечена: " + traumaName(removed);
        }
        if (mode.equals("fear")) {
            if ("none".equals(getPhobia(vkId))) return "Фобии нет.";
            String old = getPhobia(vkId);
            d().set("stats." + vkId + ".phobia", "none");
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            journal.accept(vkId, "🕯 Снята фобия: " + phobiaName(old));
            saveAll.run();
            return "✅ Фобия снята: " + phobiaName(old);
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        changeSanity(vkId, 35, null);
        journal.accept(vkId, "🧠 Терапия рассудка в госпитале");
        saveAll.run();
        return "✅ Рассудок восстановлен до " + getSanity(vkId) + "%";
    }
}
