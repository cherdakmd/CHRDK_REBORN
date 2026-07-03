package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер анонсов: смерти, убийства, ранги, интеграции с плагинами
 */
public class BroadcastManager implements Listener {
    private final VKChatChatPlugin plugin;
    private final Random rnd = new Random();
    private final ConcurrentHashMap<UUID, Integer> lastJobLevel = new ConcurrentHashMap<>();

    public BroadcastManager(VKChatChatPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startJobLevelCheck();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        String vkStatus = getStatus(victim);
        String originalDeathMsg = e.getDeathMessage();
        String cause = originalDeathMsg != null
                ? ChatColor.stripColor(originalDeathMsg).replace(victim.getName(), "").trim()
                : "погиб при загадочных обстоятельствах";

        if (killer != null) {
            String kStatus = getStatus(killer);
            List<String> msgs = plugin.getConfig().getStringList("broadcasts.kill-messages");
            if (msgs.isEmpty()) msgs = generateKillMsgs();
            String msg = msgs.get(rnd.nextInt(msgs.size()));
            msg = msg.replace("{killer}", kStatus + " &r" + killer.getName())
                     .replace("{victim}", vkStatus + " &r" + victim.getName())
                     .replace("{cause}", cause);
            broadcast(ChatColor.translateAlternateColorCodes('&', msg));
        } else {
            List<String> msgs = plugin.getConfig().getStringList("broadcasts.death-messages");
            if (msgs.isEmpty()) msgs = generateDeathMsgs();
            String msg = msgs.get(rnd.nextInt(msgs.size()));
            msg = msg.replace("{player}", vkStatus + " &r" + victim.getName())
                     .replace("{cause}", cause);
            broadcast(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Треккинг уровня Jobs для анонсов
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkJobLevel(p), 40L);
    }

    private void startJobLevelCheck() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkJobLevel(p);
        }, 200L, 200L);
    }

    private void checkJobLevel(Player p) {
        try {
            org.bukkit.plugin.Plugin jobs = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobs == null || !jobs.isEnabled()) return;
            Object dataMgr = jobs.getClass().getMethod("getJobsDataManager").invoke(jobs);
            if (dataMgr == null) return;
            int totalLvl = (int) dataMgr.getClass().getMethod("getTotalLevel", Player.class).invoke(dataMgr, p);
            int prev = lastJobLevel.getOrDefault(p.getUniqueId(), 0);
            if (totalLvl > prev && prev > 0) {
                List<String> msgs = plugin.getConfig().getStringList("broadcasts.job-level-messages");
                if (msgs.isEmpty()) msgs = generateJobMsgs();
                String msg = msgs.get(rnd.nextInt(msgs.size()));
                msg = msg.replace("{player}", getStatus(p) + " &r" + p.getName())
                         .replace("{level}", String.valueOf(totalLvl));
                broadcast(ChatColor.translateAlternateColorCodes('&', msg));
            }
            lastJobLevel.put(p.getUniqueId(), totalLvl);
        } catch (Exception ignored) {}
    }

    /**
     * Вызвать анонс из любого плагина
     */
    public void announce(String message) {
        broadcast(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Вызвать анонс с вариациями (случайный выбор из списка)
     */
    public void announceRandom(List<String> messages, String player, String extra) {
        if (messages.isEmpty()) return;
        String msg = messages.get(rnd.nextInt(messages.size()));
        msg = msg.replace("{player}", getStatus(Bukkit.getPlayer(player)) + " &r" + player);
        if (extra != null) msg = msg.replace("{extra}", extra);
        broadcast(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        if (plugin.getConfig().getBoolean("broadcasts.send-to-vk", true)) {
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(msg)); } catch (Exception ignored) {}
        }
    }

    private String getStatus(Player p) {
        if (p == null) return "&7";
        if (p.hasPermission("vkchat.donate.overlord")) return "&d&lВЛАСТЕЛИН";
        if (p.hasPermission("vkchat.donate.legend")) return "&5&lЛЕГЕНДА";
        if (p.hasPermission("vkchat.donate.star")) return "&e&lЗВЕЗДА";
        if (p.hasPermission("vkchat.donate.flame")) return "&6&lПЛАМЯ";
        if (p.hasPermission("vkchat.donate.spark")) return "&b&lИСКРА";
        return "&7";
    }

    // ═══ ГЕНЕРАТОРЫ ВАРИАНТОВ (по ~100 на категорию) ═══
    private static final String[] KILL_ACTION = {"убил","прикончил","зарезал","казнил","разорвал","уничтожил","ликвидировал","замочил","отправил на тот свет","зарубил","прихлопнул","завалил","вынес","грохнул","закопал","убрал","стёр в порошок","растоптал","размазал","потушил"};
    private static final String[] KILL_WEAPON = {"мечом","топором","луком","голыми руками","киркой","лопатой","трезубцем","булавой","арбалетом","заклинанием","кулаком","взглядом","словом","молотом","косой","дубиной","кинжалом","огнём","льдом","молнией"};
    private static final String[] KILL_STYLE = {"в жестокой схватке","без шансов","одним ударом","с холодным расчётом","в честном бою","эффектно","с улыбкой","молниеносно","в неравной битве","красивым комбо"};

    private static final String[] DEATH_VERB = {"погиб","скончался","ушёл в мир иной","отправился к праотцам","пал","испустил дух","покинул этот мир","встретил свою смерть","нашёл свой конец","стал жертвой"};
    private static final String[] DEATH_NOUN = {"при падении с высоты","в пламени","в лаве","от голода","от удушья","в битве","от магии","от взрыва","в бездне","под землёй","на воде","в пещере","на равнине","в лесу","в пустыне","в аду","в небесах","во тьме","на стене","под обстрелом"};

    private static final String[] JOB_VERB = {"достиг","прокачался до","получил","заработал","взял","апнул","пробил","забрал","выбил","заслужил"};
    private static final String[] JOB_NOUN = {"уровня профессий","ранга мастерства","ступени развития","левела","квалификации","грейда","тира","этапа","вехи","рубежа"};
    private static final String[] JOB_EMOJI = {"&e⭐","&e🌟","&e✨","&e💫","&e🔝","&e📈","&e🏆","&e🎖","&e💎","&e👑","&e🎯","&e⚡","&e🔥","&e💪","&e🆙","&e📊","&e🏅","&e🎓","&e🔰","&e⬆"};
    private static final String[] JOB_SUFFIX = {"Так держать!","Продолжай в том же духе!","Ещё немного!","Вперёд к новым высотам!","Не останавливайся!","Крутое достижение!","Сервер гордится!","Путь мастера!","Легенда растёт!","Топ уже близко!"};

    private List<String> generateKillMsgs() {
        List<String> list = new ArrayList<>();
        for (String a : KILL_ACTION) for (String w : KILL_WEAPON) for (String s : KILL_STYLE)
            list.add("&c☠ {killer} &7" + a + " &c{victim} &7" + w + " " + s + " &8[{cause}]");
        return list;
    }

    private List<String> generateDeathMsgs() {
        List<String> list = new ArrayList<>();
        for (String v : DEATH_VERB) for (String n : DEATH_NOUN)
            list.add("&7☠ {player} &7" + v + " " + n + " &8[{cause}]");
        return list;
    }

    private List<String> generateJobMsgs() {
        List<String> list = new ArrayList<>();
        for (String e : JOB_EMOJI) for (String v : JOB_VERB) for (String n : JOB_NOUN) for (String s : JOB_SUFFIX)
            list.add(e + " {player} &7" + v + " &e{level} &7" + n + "! &8" + s);
        return list;
    }
}
