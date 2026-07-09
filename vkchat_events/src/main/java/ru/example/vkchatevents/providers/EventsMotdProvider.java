package ru.example.vkchatevents.providers;

import ru.example.vkchat.api.MotdProvider;
import ru.example.vkchatevents.VKChatEventsPlugin;

/**
 * Провайдер MOTD для модуля событий (Wrath, Cataclysm).
 */
public class EventsMotdProvider implements MotdProvider {
    private final VKChatEventsPlugin plugin;

    public EventsMotdProvider(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean hasActiveEvent() {
        var wrath = plugin.getWrathManager();
        if (wrath == null) return false;
        return wrath.isActive() || wrath.getActiveCataclysm() != null;
    }

    @Override
    public String getMotdLine() {
        var wrath = plugin.getWrathManager();
        if (wrath == null) return null;

        if (wrath.isActive()) {
            return "&c&l🚨 БОСС: Аватар Гнева Богов заспавнился в мире! Спеши на битву!";
        }

        String cataclysm = wrath.getActiveCataclysm();
        if (cataclysm != null) {
            return "&e&l⛈️ КАТАКЛИЗМ: На сервере бушует " + getCataclysmDisplayName(cataclysm) + "!";
        }

        return null;
    }

    private String getCataclysmDisplayName(String type) {
        return switch (type) {
            case "acid_rain" -> "Кислотный Дождь";
            case "earthquake" -> "Землетрясение";
            case "tempest" -> "Грозовой Шторм";
            case "meteor_shower" -> "Метеоритный Дождь";
            case "blizzard" -> "Снежный Буран";
            case "eclipse" -> "Солнечное Затмение";
            case "reputation_bloom" -> "Золотой Век";
            case "angelic_grace" -> "Ангельская Благодать";
            case "star_shower" -> "Звездопад Желаний";
            case "geysers" -> "Раскаленные Гейзеры";
            case "blood_moon_hunt" -> "Кровавая Луна";
            case "treasure_comet" -> "Комета Сокровищ";
            case "station_fall" -> "Падение Космической Станции";
            case "fog_shadows" -> "Туман Теней";
            case "plasma_storm" -> "Плазменный Шторм";
            case "gravity_anomaly" -> "Извращение Гравитации";
            default -> type;
        };
    }
}
