package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import ru.example.vkchat.VKChatPlugin;

public class MotdListener implements Listener {
    private final VKChatPlugin plugin;

    public MotdListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }

        String line1 = plugin.getConfig().getString("motd.line-1", "&6&l★ CHRDK REBORN ★ &7» &fMMO-Выживание с ВК Ботом!");
        String line2 = plugin.getConfig().getString("motd.line-2-default", "&eЗаходи и развивай своего персонажа!");
        String defaultLine2 = line2;

        // 1. Проверяем активные мировые катаклизмы и аирдропы в vkchat_events
        try {
            org.bukkit.plugin.Plugin eventsPlugin = Bukkit.getPluginManager().getPlugin("VKChatEvents");
            if (eventsPlugin != null && eventsPlugin.isEnabled()) {
                // Проверка Босса Wrath
                Object wrathMgr = eventsPlugin.getClass().getMethod("getWrathManager").invoke(eventsPlugin);
                boolean bossActive = (boolean) wrathMgr.getClass().getMethod("isActive").invoke(wrathMgr);
                
                // Проверка активного катаклизма
                java.lang.reflect.Field catNameField = wrathMgr.getClass().getDeclaredField("activeCataclysm");
                catNameField.setAccessible(true);
                String catName = (String) catNameField.get(wrathMgr);
                
                // Проверка Аирдропа
                Object airMgr = eventsPlugin.getClass().getMethod("getAirdropManager").invoke(eventsPlugin);
                boolean airActive = (boolean) airMgr.getClass().getMethod("isActive").invoke(airMgr);
                
                if (bossActive) {
                    line2 = "&c&l🚨 БОСС: Аватар Гнева Богов заспавнился в мире! Спеши на битву!";
                } else if (catName != null) {
                    String catLabel = getCataclysmDisplayName(catName);
                    line2 = "&e&l⛈️ КАТАКЛИЗМ: На сервере бушует " + catLabel + "!";
                } else if (airActive) {
                    String tierName = (String) airMgr.getClass().getMethod("getActiveTierName").invoke(airMgr);
                    line2 = "&e&l📦 АИРДРОП: Сброшен " + tierName + " &e&lв мире! Захватывай зону!";
                }
            }
        } catch (Exception ignored) {}

        // 2. Если мировых событий нет, проверяем активные кризисы на бирже в vkchat_market
        if (line2.equals(defaultLine2)) {
            try {
                org.bukkit.plugin.Plugin marketPlugin = Bukkit.getPluginManager().getPlugin("VKChatMarket");
                if (marketPlugin != null && marketPlugin.isEnabled()) {
                    Object marketMgr = marketPlugin.getClass().getMethod("getMarketManager").invoke(marketPlugin);
                    String evtName = (String) marketMgr.getClass().getMethod("getActiveEventName").invoke(marketMgr);
                    long expTime = (long) marketMgr.getClass().getMethod("getActiveEventExpireTime").invoke(marketMgr);
                    
                    if (evtName != null && System.currentTimeMillis() < expTime) {
                        line2 = "&a&l📈 БИРЖА: На динамическом рынке началось событие " + evtName + "!";
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Если кризисов нет, проверяем активные магические распродажи/коллапсы в vkchat_gear
        if (line2.equals(defaultLine2)) {
            try {
                org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
                if (gearPlugin != null && gearPlugin.isEnabled()) {
                    java.lang.reflect.Field evtNameField = gearPlugin.getClass().getDeclaredField("activeMagicEventName");
                    java.lang.reflect.Field expTimeField = gearPlugin.getClass().getDeclaredField("activeMagicEventExpireTime");
                    evtNameField.setAccessible(true);
                    expTimeField.setAccessible(true);
                    String evtName = (String) evtNameField.get(gearPlugin);
                    long expTime = (long) expTimeField.get(gearPlugin);
                    
                    if (evtName != null && System.currentTimeMillis() < expTime) {
                        line2 = "&5&l🔮 МАГИЯ: В магазине рун активировано событие &d&l" + evtName + "&5&l!";
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. По умолчанию показываем самого богатого игрока беседы ВК
        if (line2.equals(defaultLine2)) {
            try {
                String topRep = plugin.getReputationManager().getTopOnePlayerName();
                if (topRep != null && !topRep.isEmpty()) {
                    line2 = "&e🏆 Топ-Богач чата: &b" + topRep + " &eрепутации! Присоединяйся!";
                }
            } catch (Exception ignored) {}
        }

        String motd = ChatColor.translateAlternateColorCodes('&', line1 + "\n" + line2);
        e.setMotd(motd);
    }

    private String getCataclysmDisplayName(String type) {
        if (type.equals("acid_rain")) return "Кислотный Дождь";
        if (type.equals("earthquake")) return "Землетрясение";
        if (type.equals("tempest")) return "Грозовой Шторм";
        if (type.equals("meteor_shower")) return "Метеоритный Дождь";
        if (type.equals("blizzard")) return "Снежный Буран";
        if (type.equals("eclipse")) return "Солнечное Затмение";
        if (type.equals("reputation_bloom")) return "Золотой Век";
        if (type.equals("angelic_grace")) return "Ангельская Благодать";
        if (type.equals("star_shower")) return "Звездопад Желаний";
        if (type.equals("geysers")) return "Раскаленные Гейзеры";
        if (type.equals("blood_moon_hunt")) return "Кровавая Луна";
        if (type.equals("treasure_comet")) return "Комета Сокровищ";
        if (type.equals("station_fall")) return "Падение Космической Станции";
        if (type.equals("fog_shadows")) return "Туман Теней";
        if (type.equals("plasma_storm")) return "Плазменный Шторм";
        if (type.equals("gravity_anomaly")) return "Извращение Гравитации";
        return type;
    }
}
