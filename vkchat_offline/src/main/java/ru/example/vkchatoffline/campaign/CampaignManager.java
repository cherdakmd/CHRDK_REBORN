package ru.example.vkchatoffline.campaign;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CampaignManager {
    private final Map<Integer, CampaignProgress> progress = new ConcurrentHashMap<>();

    public static final CampaignChapter[] CHAPTERS = {
        new CampaignChapter(1, "Зов леса", "Лес", "Волчий Вожак", 1, 5, "Вы отправляетесь в лес...", "Редкий меч", 100),
        new CampaignChapter(2, "Тени шахт", "Шахты", "Каменный Голем", 6, 10, "Следы ведут в шахты...", "Эпическая кирка", 200),
        new CampaignChapter(3, "Древние руины", "Руины", "Страж Руин", 11, 15, "Руины хранят тайны...", "Легендарный артефакт", 350),
        new CampaignChapter(4, "Болотная ведьма", "Болота", "Морана", 16, 20, "На болотах ведьма...", "Мифическое зелье", 500),
        new CampaignChapter(5, "Проклятый замок", "Замок", "Нежить-Рыцарь", 21, 25, "Замок окутан проклятием...", "Эпическая броня", 700),
        new CampaignChapter(6, "Адские врата", "Незер", "Ифрит", 26, 30, "Врата в Незер открыты...", "Легендарный тотем", 1000),
        new CampaignChapter(7, "Бездна", "Энд", "Эндер Страж", 31, 35, "Бездна поглощает всё...", "Мифический артефакт", 1500),
        new CampaignChapter(8, "Хроники времени", "Локации", "Хронос", 36, 40, "Хронос вмешивается...", "Легендарные часы", 2000),
        new CampaignChapter(9, "Кровавая луна", "Ночь", "Кровавый Лорд", 41, 45, "Кровавая луна восходит...", "Мифический меч", 2500),
        new CampaignChapter(10, "Подземелье теней", "Пещеры", "Теневой Дракон", 46, 50, "В глубинах дракон...", "Легендарная броня", 3000),
        new CampaignChapter(11, "Небесный храм", "Высота", "Ангел Смерти", 51, 60, "На вершине храм...", "Мифические крылья", 4000),
        new CampaignChapter(12, "Хаос", "Смешение", "Демон Хаоса", 61, 75, "Хаос угрожает всему...", "Легендарный набор", 5000),
        new CampaignChapter(13, "Пробуждение", "Финал", "Древний Бог", 76, 88, "Древний бог пробуждается...", "Мифический набор", 10000),
    };

    public static class CampaignProgress {
        public int currentChapter = 1;
        public Map<Integer, Boolean> completedChapters = new ConcurrentHashMap<>();
    }

    public static class CampaignChapter {
        public final int id, minLevel, maxLevel, rewardRep;
        public final String name, route, bossName, description, reward;
        public CampaignChapter(int id, String name, String route, String bossName, int minLevel, int maxLevel, String description, String reward, int rewardRep) {
            this.id = id; this.name = name; this.route = route; this.bossName = bossName;
            this.minLevel = minLevel; this.maxLevel = maxLevel; this.description = description;
            this.reward = reward; this.rewardRep = rewardRep;
        }
    }

    public CampaignProgress getProgress(int vkId) { return progress.computeIfAbsent(vkId, k -> new CampaignProgress()); }

    public CampaignChapter getCurrentChapter(int vkId) {
        CampaignProgress prog = getProgress(vkId);
        return prog.currentChapter > CHAPTERS.length ? null : CHAPTERS[prog.currentChapter - 1];
    }

    public boolean completeChapter(int vkId, int chapterId) {
        CampaignProgress prog = getProgress(vkId);
        if (chapterId != prog.currentChapter) return false;
        prog.completedChapters.put(chapterId, true);
        prog.currentChapter = chapterId + 1;
        return true;
    }

    public String getCampaignInfo(int vkId) {
        CampaignProgress prog = getProgress(vkId);
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("📖 КАМПАНИЯ: ХРОНИКИ ПРОПАВШЕЙ ЭКСПЕДИЦИИ\n");
        sb.append("═══════════════════════════════════════\n\n");
        sb.append("Прогресс: ").append(prog.currentChapter - 1).append("/13 глав\n\n");
        for (CampaignChapter ch : CHAPTERS) {
            boolean completed = prog.completedChapters.getOrDefault(ch.id, false);
            boolean current = ch.id == prog.currentChapter;
            sb.append(completed ? "✅ " : (current ? "▶ " : "🔒 "));
            sb.append("Глава ").append(ch.id).append(": ").append(ch.name);
            sb.append(" [Ур. ").append(ch.minLevel).append("-").append(ch.maxLevel).append("]\n");
            sb.append("   Босс: ").append(ch.bossName).append(" | Награда: ").append(ch.reward).append("\n");
        }
        return sb.toString();
    }

    public int getCompletedChapters(int vkId) { return getProgress(vkId).completedChapters.size(); }
    public int getChapterCount() { return CHAPTERS.length; }
}
