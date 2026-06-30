package ru.example.vkchatoffline.campaign;

import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер кампании (13 глав)
 */
public class CampaignManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, CampaignProgress> progress = new ConcurrentHashMap<>();

    // Главы кампании
    public static final CampaignChapter[] CHAPTERS = {
        new CampaignChapter(1, "Зов леса", "Лес", "Волчий Вожак", 1, 5,
                "Вы отправляетесь в лес, чтобы найти пропавшую экспедицию...",
                "Редкий меч", 100),
        new CampaignChapter(2, "Тени шахт", "Шахты", "Каменный Голем", 6, 10,
                "Следы ведут в глубокие шахты, где таится каменный голем...",
                "Эпическая кирка", 200),
        new CampaignChapter(3, "Древние руины", "Руины", "Страж Руин", 11, 15,
                "Руины древней цивилизации хранят множество тайн...",
                "Легендарный артефакт", 350),
        new CampaignChapter(4, "Болотная ведьма", "Болота", "Морана", 16, 20,
                "На болотах хозяйствует могущественная ведьма...",
                "Мифическое зелье", 500),
        new CampaignChapter(5, "Проклятый замок", "Замок", "Нежить-Рыцарь", 21, 25,
                "Замок окутан проклятием, внутри бродит нежить...",
                "Эпическая броня", 700),
        new CampaignChapter(6, "Адские врата", "Незер", "Ифрит", 26, 30,
                "Врата в Незер открыты, ифрит страждет вход...",
                "Легендарный тотем", 1000),
        new CampaignChapter(7, "Бездна", "Энд", "Эндер Страж", 31, 35,
                "Бездна поглощает всё, эндер страж не дремлет...",
                "Мифический артефакт", 1500),
        new CampaignChapter(8, "Хроники времени", "Локации", "Хронос", 36, 40,
                "Хронос, повелитель времени, вмешивается в дела смертных...",
                "Легендарные часы", 2000),
        new CampaignChapter(9, "Кровавая луна", "Ночь", "Кровавый Лорд", 41, 45,
                "Кровавая луна восходит, и начинается охота...",
                "Мифический меч", 2500),
        new CampaignChapter(10, "Подземелье теней", "Пещеры", "Теневой Дракон", 46, 50,
                "В глубинах пещер спит теневой дракон...",
                "Легендарная броня", 3000),
        new CampaignChapter(11, "Небесный храм", "Высота", "Ангел Смерти", 51, 60,
                "На вершине мира находится небесный храм...",
                "Мифические крылья", 4000),
        new CampaignChapter(12, "Хаос", "Смешение", "Демон Хаоса", 61, 75,
                "Хаос threatens to consume everything...",
                "Легендарный набор", 5000),
        new CampaignChapter(13, "Пробуждение", "Финал", "Древний Бог", 76, 88,
                "Древний бог пробуждается, и только вы можете его остановить...",
                "Мифический набор", 10000),
    };

    // Прогресс кампании
    public static class CampaignProgress {
        public int currentChapter;
        public Map<Integer, Boolean> completedChapters;
        public Map<Integer, Long> completionTimes;

        public CampaignProgress() {
            this.currentChapter = 1;
            this.completedChapters = new ConcurrentHashMap<>();
            this.completionTimes = new ConcurrentHashMap<>();
        }
    }

    // Глава кампании
    public static class CampaignChapter {
        public final int id;
        public final String name;
        public final String route;
        public final String bossName;
        public final int minLevel;
        public final int maxLevel;
        public final String description;
        public final String reward;
        public final int rewardRep;

        public CampaignChapter(int id, String name, String route, String bossName,
                              int minLevel, int maxLevel, String description,
                              String reward, int rewardRep) {
            this.id = id;
            this.name = name;
            this.route = route;
            this.bossName = bossName;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.description = description;
            this.reward = reward;
            this.rewardRep = rewardRep;
        }
    }

    public CampaignManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить прогресс кампании
     */
    public CampaignProgress getProgress(int vkId) {
        return progress.computeIfAbsent(vkId, k -> new CampaignProgress());
    }

    /**
     * Получить текущую главу
     */
    public CampaignChapter getCurrentChapter(int vkId) {
        CampaignProgress prog = getProgress(vkId);
        if (prog.currentChapter > CHAPTERS.length) return null;
        return CHAPTERS[prog.currentChapter - 1];
    }

    /**
     * Проверить, доступна ли глава
     */
    public boolean isChapterAvailable(int vkId, int chapterId) {
        CampaignProgress prog = getProgress(vkId);
        if (chapterId == 1) return true;
        return prog.completedChapters.getOrDefault(chapterId - 1, false);
    }

    /**
     * Завершить главу
     */
    public boolean completeChapter(int vkId, int chapterId) {
        CampaignProgress prog = getProgress(vkId);
        if (chapterId != prog.currentChapter) return false;

        prog.completedChapters.put(chapterId, true);
        prog.completionTimes.put(chapterId, System.currentTimeMillis());
        prog.currentChapter = chapterId + 1;

        return true;
    }

    /**
     * Получить информацию о кампании
     */
    public String getCampaignInfo(int vkId) {
        CampaignProgress prog = getProgress(vkId);

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("📖 КАМПАНИЯ: ХРОНИКИ ПРОПАВШЕЙ ЭКСПЕДИЦИИ\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append("Прогресс: ").append(prog.currentChapter - 1).append("/13 глав\n\n");

        for (CampaignChapter chapter : CHAPTERS) {
            boolean completed = prog.completedChapters.getOrDefault(chapter.id, false);
            boolean available = isChapterAvailable(vkId, chapter.id);
            boolean current = chapter.id == prog.currentChapter;

            if (completed) {
                sb.append("✅ ");
            } else if (current) {
                sb.append("▶ ");
            } else if (available) {
                sb.append("🔓 ");
            } else {
                sb.append("🔒 ");
            }

            sb.append("Глава ").append(chapter.id).append(": ").append(chapter.name);
            sb.append(" [Ур. ").append(chapter.minLevel).append("-").append(chapter.maxLevel).append("]");
            sb.append("\n   Босс: ").append(chapter.bossName);
            sb.append(" | Награда: ").append(chapter.reward).append("\n");
        }

        return sb.toString();
    }

    /**
     * Получить информацию о главе
     */
    public String getChapterInfo(CampaignChapter chapter) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("📖 ГЛАВА ").append(chapter.id).append(": ").append(chapter.name).append("\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append(chapter.description).append("\n\n");
        sb.append("📍 Маршрут: ").append(chapter.route).append("\n");
        sb.append("👹 Босс: ").append(chapter.bossName).append("\n");
        sb.append("📊 Уровень: ").append(chapter.minLevel).append("-").append(chapter.maxLevel).append("\n");
        sb.append("🎁 Награда: ").append(chapter.reward).append("\n");
        sb.append("⭐ Репутация: +").append(chapter.rewardRep).append("\n");

        return sb.toString();
    }

    /**
     * Получить количество завершённых глав
     */
    public int getCompletedChapters(int vkId) {
        return getProgress(vkId).completedChapters.size();
    }

    /**
     * Получить количество глав
     */
    public int getChapterCount() {
        return CHAPTERS.length;
    }
}
