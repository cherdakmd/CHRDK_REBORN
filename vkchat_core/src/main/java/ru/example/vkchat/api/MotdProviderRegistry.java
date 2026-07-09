package ru.example.vkchat.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Реестр провайдеров MOTD. Модули регистрируют свои провайдеры при загрузке.
 */
public class MotdProviderRegistry {
    private static final List<MotdProvider> providers = new CopyOnWriteArrayList<>();

    public static void register(MotdProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(MotdProvider::getPriority).reversed());
    }

    public static void unregister(MotdProvider provider) {
        providers.remove(provider);
    }

    /**
     * @return Первая активная строка MOTD от провайдера с наивысшим приоритетом, или null
     */
    public static String getActiveMotdLine() {
        for (MotdProvider p : providers) {
            if (p.hasActiveEvent()) {
                String line = p.getMotdLine();
                if (line != null && !line.isEmpty()) {
                    return line;
                }
            }
        }
        return null;
    }

    public static List<MotdProvider> getAll() {
        return new ArrayList<>(providers);
    }
}
