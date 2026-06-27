package ru.example.vkchatoffline.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import ru.example.vkchatoffline.data.ActiveAdventure;

/**
 * Форматирование текстов оффлайн-походов: карточки, события, итоги, бары, иконки.
 * Вынесено из AdventureManager без изменения итоговых сообщений.
 */
public final class OfflineTextFactory {
    private OfflineTextFactory() {}

    public static String routeCard(FileConfiguration config, String key, boolean unlocked, String cleanKeyName) {
        String path = "adventures." + key + ".";
        int stages = config.getInt(path + "stages", 3);
        int cost = config.getInt(path + "cost", 0);
        int death = config.getInt(path + "death-chance", 5);
        int difficulty = config.getInt(path + "difficulty", 1);
        StringBuilder sb = new StringBuilder();
        sb.append(unlocked ? "✅ " : "🔒 ").append(routeEmoji(key)).append(" ")
                .append(config.getString(path + "name", key)).append("\n");
        sb.append("   ").append(difficultyStars(difficulty)).append(" | ").append(stages).append(" эт.")
                .append(" | ").append(cost).append(" реп.")
                .append(" | смерть ").append(death).append("%\n");
        if (!unlocked) sb.append("   ключ: ").append(cleanKeyName).append("\n");
        return sb.toString();
    }

    public static String buildEventMessage(FileConfiguration config, ActiveAdventure adv, String blessingText) {
        long left = Math.max(0, (adv.choiceDeadline - System.currentTimeMillis()) / 1000L);
        return "⚠ Выбор в походе\n\n" +
                routeEmoji(adv.route) + " " + config.getString("adventures." + adv.route + ".name", adv.route) + "\n" +
                "📍 Этап: " + (adv.stage + 1) + "/" + adv.maxStages + "\n" +
                "❤️ HP: " + adv.hp + "/" + adv.maxHp + "\n" +
                "🥫 " + adv.supplies + "   🧠 " + adv.morale + "%   🧩 " + adv.sanity + "%\n" +
                "🪙 " + adv.gold + "   🏺 " + adv.relics + "   " + blessingText + "\n" +
                "🎲 " + eventIcon(adv.pendingType) + " " + adv.pendingTitle + "\n" +
                "⏳ Ответ: ~" + left + " сек.\n\n" +
                "Выбери действие кнопкой. Если не ответишь — выбор будет случайным.";
    }

    public static String buildFinishMessage(FileConfiguration config, ActiveAdventure adv, int rep, int itemCount, String campaignLine) {
        return "🏆 Поход завершён\n\n" +
                routeEmoji(adv.route) + " " + config.getString("adventures." + adv.route + ".name", adv.route) + "\n" +
                "📍 Этапы: " + adv.maxStages + "/" + adv.maxStages + "\n" +
                "💰 Репутация: +" + rep + "\n" +
                "🪙 Золото конвертировано: " + adv.gold + "\n" +
                "🏺 Реликвии: " + adv.relics + " x75 реп.\n" +
                "⭐ XP похода: +" + adv.xpGained + "\n" +
                "🎒 Предметов в тайник: " + itemCount + "\n" +
                "📖 Кампания: " + campaignLine + "\n" +
                "📦 Проверить: !тайник 1\n" +
                "🎮 Забрать: /stash";
    }

    public static String hpBar(int hp, int max) {
        int filled = (int) Math.round(Math.max(0, Math.min(1.0, hp / (double) Math.max(1, max))) * 10.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "♥" : "♡");
        return sb.toString();
    }

    public static String stageBar(int stage, int max) {
        int filled = (int) Math.round(Math.max(0, Math.min(1.0, stage / (double) Math.max(1, max))) * 10.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "▰" : "▱");
        return sb.toString();
    }

    public static String difficultyStars(int diff) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, Math.max(1, diff)); i++) sb.append("★");
        return sb.toString();
    }

    public static String routeEmoji(String route) {
        if (route.equals("forest")) return "🌲";
        if (route.equals("mine")) return "⛏";
        if (route.equals("ruins")) return "🏛";
        if (route.equals("swamp")) return "☠";
        if (route.equals("castle")) return "🏰";
        if (route.equals("nether")) return "🌋";
        return "🗺";
    }

    public static String eventIcon(String type) {
        if ("combat".equals(type)) return "⚔";
        if ("trap".equals(type)) return "🪤";
        if ("ambush".equals(type)) return "🏹";
        if ("curse".equals(type)) return "🧿";
        if ("treasure".equals(type)) return "📦";
        if ("merchant".equals(type)) return "🛒";
        if ("shrine".equals(type)) return "🕯";
        if ("riddle".equals(type)) return "🧩";
        if ("survival".equals(type)) return "⚠";
        if ("gathering".equals(type)) return "⛏";
        if ("camp".equals(type)) return "🔥";
        if ("mimic".equals(type)) return "📦";
        if ("puzzle".equals(type)) return "🧩";
        if ("duel".equals(type)) return "🏟";
        if ("portal".equals(type)) return "🌀";
        if ("artifact".equals(type)) return "🏺";
        if ("patron".equals(type)) return "🌟";
        if ("heist".equals(type)) return "🗝";
        if ("disease".equals(type)) return "🦠";
        if ("baba_yaga".equals(type)) return "🧙";
        if ("leshy".equals(type)) return "🌲";
        if ("rusalka".equals(type)) return "🌊";
        if ("domovoi".equals(type)) return "🏚";
        if ("perun".equals(type)) return "⚡";
        if ("morana".equals(type)) return "❄";
        if ("vodyanoy".equals(type)) return "💧";
        if ("koshchey".equals(type)) return "☠";
        if ("zmey".equals(type)) return "🐉";
        if ("bogatyr".equals(type)) return "🛡";
        if ("oracle".equals(type)) return "🔮";
        if ("tavern".equals(type)) return "🍻";
        if ("blacksmith".equals(type)) return "🔨";
        if ("moral".equals(type)) return "⚖";
        if ("nightmare".equals(type)) return "🧠";
        if ("memory".equals(type)) return "📜";
        if ("companion".equals(type)) return "🤝";
        if ("collection".equals(type)) return "🏺";
        if ("extra".equals(type)) return "🎲";
        if ("boss".equals(type)) return "👑";
        return "✨";
    }

    public static String cleanKeyName(String keyName) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', keyName));
    }
}
