package ru.example.vkchatstreams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;

import java.io.File;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StreamManager extends BukkitRunnable implements Listener {
    public static class StreamerProfile {
        public int vkId;
        public String platform;
        public String channel;
        public String link;
        public String description;
        public String schedule;
        public String contacts;
        public boolean approved;
        public long lastAnnounce;
        public int warnings;
        public long manualCooldownUntil;
    }

    public static class LiveStream {
        public int vkId;
        public String title;
        public String category;
        public long startedAt;
        public boolean announced;
        public Set<Integer> paidMilestones = new HashSet<>();
        public String currentVote;
        public long voteEndsAt;
        public Map<Integer, Integer> votes = new HashMap<>();
    }

    private final VKChatStreamsPlugin plugin;
    private final HttpClient httpClient;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<Integer, StreamerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Integer, LiveStream> live = new ConcurrentHashMap<>();
    private final Map<String, Boolean> usedPromos = new ConcurrentHashMap<>();

    public StreamManager(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().build();
        this.dataFile = new File(plugin.getDataFolder(), "streamers.yml");
        loadData();
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;
        tickManualLives();
        // Гибридный режим: если API токены будут настроены, сюда можно добавить polling.
        // Ручной режим уже полностью рабочий для Twitch/VK/YouTube/manual approved-стримеров.
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand();
        if (!cmd.equals("!стрим") && !cmd.equals("!stream") && !cmd.equals("!streams")) return;
        e.setCancelled(true);
        int peer = e.getPeerId();
        int vk = e.getSenderVkId();
        String[] args = e.getArgs();
        if (args.length < 2) { sendHelp(peer, vk); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("заявка") || sub.equals("apply")) apply(peer, vk, args);
        else if (sub.equals("live") || sub.equals("старт") || sub.equals("start")) startManual(peer, vk, args);
        else if (sub.equals("stop") || sub.equals("стоп")) stopManual(peer, vk);
        else if (sub.equals("список") || sub.equals("list")) sendActiveList(peer);
        else if (sub.equals("promo") || sub.equals("промо")) claimPromo(peer, vk, args);
        else if (sub.equals("vote") || sub.equals("голос")) vote(peer, vk, args);
        else if (sub.equals("approve") || sub.equals("одобрить")) approve(peer, vk, args);
        else if (sub.equals("reject") || sub.equals("отклонить")) reject(peer, vk, args);
        else if (sub.equals("warn")) warn(peer, vk, args);
        else sendHelp(peer, vk);
    }

    private void sendHelp(int peer, int vk) {
        String text = "🎥 VKChatStreams\n" +
                "!стрим заявка <twitch|vk|youtube|manual> <канал> <ссылка> | описание\n" +
                "!стрим live <название> — начать ручной лайв (approved)\n" +
                "!стрим stop — закончить лайв\n" +
                "!стрим список — активные стримы\n" +
                "!стрим промо <код> — забрать награду зрителя\n" +
                "!стрим голос <1-4> — голосование зрителей\n\n" +
                "Админ: !стрим approve <vk>, reject <vk>, warn <vk>";
        api().sendMessage(peer, text);
    }

    private void apply(int peer, int vk, String[] args) {
        if (args.length < 5) {
            api().sendMessage(peer, "❌ Формат: !стрим заявка <platform> <channel> <link> | описание/расписание/контакты");
            return;
        }
        StreamerProfile sp = profiles.getOrDefault(vk, new StreamerProfile());
        sp.vkId = vk;
        sp.platform = args[2].toLowerCase(Locale.ROOT);
        sp.channel = args[3];
        sp.link = args[4];
        String rest = join(args, 5);
        sp.description = rest.isEmpty() ? "Описание не указано" : rest;
        sp.schedule = "уточняется";
        sp.contacts = "VK id " + vk;
        sp.approved = plugin.getConfig().getBoolean("approval.auto-approve", false);
        profiles.put(vk, sp);
        saveData();
        api().sendMessage(peer, "✅ Заявка стримера отправлена. Платформа: " + sp.platform + ", канал: " + sp.channel + "\nОжидай подтверждения VK-админа.");
        notifyAdmins("📝 Новая заявка стримера\nVK: " + vk + "\nПлатформа: " + sp.platform + "\nКанал: " + sp.channel + "\nСсылка: " + sp.link + "\nОдобрить: !стрим approve " + vk);
    }

    private void startManual(int peer, int vk, String[] args) {
        StreamerProfile sp = profiles.get(vk);
        if (sp == null || !sp.approved) { api().sendMessage(peer, "❌ Ручной режим доступен только подтверждённым стримерам."); return; }
        if (sp.manualCooldownUntil > System.currentTimeMillis()) { api().sendMessage(peer, "⏳ Ручной режим временно на кулдауне."); return; }
        if (live.containsKey(vk)) { api().sendMessage(peer, "Ты уже в live-статусе. Остановить: !стрим stop"); return; }
        long cd = plugin.getConfig().getLong("anti-spam.announce-cooldown-minutes", 30) * 60000L;
        if (System.currentTimeMillis() - sp.lastAnnounce < cd) {
            api().sendMessage(peer, "⏳ Анонс недавно уже был. Подожди кулдаун.");
            return;
        }
        LiveStream ls = new LiveStream();
        ls.vkId = vk;
        ls.title = args.length >= 3 ? join(args, 2) : "Стрим CHRDK REBORN";
        ls.category = sp.platform;
        ls.startedAt = System.currentTimeMillis();
        live.put(vk, ls);
        sp.lastAnnounce = System.currentTimeMillis();
        saveData();
        announce(sp, ls);
        api().sendMessage(peer, "🟢 Live-статус включён. Награды начнутся после " + plugin.getConfig().getInt("rewards.min-live-minutes", 30) + " минут. Остановить: !стрим stop");
    }

    private void stopManual(int peer, int vk) {
        LiveStream ls = live.remove(vk);
        if (ls == null) { api().sendMessage(peer, "Активного стрима нет."); return; }
        long mins = (System.currentTimeMillis() - ls.startedAt) / 60000L;
        saveData();
        api().sendMessage(peer, "🔴 Стрим завершён. Длительность: " + mins + " мин.");
        StreamerProfile profile = profiles.get(vk);
        try { VKChatPlugin.getInstance().getApi().sendToMainChat("🔴 Стрим " + (profile != null ? profile.channel : "unknown") + " завершён. Спасибо за эфир!"); } catch (Throwable ignored) {}
    }

    private void tickManualLives() {
        for (LiveStream ls : new ArrayList<>(live.values())) {
            StreamerProfile sp = profiles.get(ls.vkId);
            if (sp == null) continue;
            long mins = (System.currentTimeMillis() - ls.startedAt) / 60000L;
            for (int m : plugin.getConfig().getIntegerList("rewards.milestones")) {
                if (mins >= m && !ls.paidMilestones.contains(m)) {
                    ls.paidMilestones.add(m);
                    payStreamerMilestone(sp, ls, m);
                    createMilestonePromo(sp, ls, m);
                    if (plugin.getConfig().getBoolean("interactions.auto-vote-on-milestone", true)) startVote(sp, ls, m);
                    saveData();
                }
            }
            if (ls.voteEndsAt > 0 && System.currentTimeMillis() >= ls.voteEndsAt) finishVote(sp, ls);
        }
    }

    private void payStreamerMilestone(StreamerProfile sp, LiveStream ls, int minutes) {
        int rep = plugin.getConfig().getInt("rewards.streamer.milestone-" + minutes, plugin.getConfig().getInt("rewards.streamer.default", 50));
        try { api().addReputation(sp.vkId, rep); } catch (Throwable ignored) {}
        api().sendMessage(sp.vkId, "🏆 Milestone стрима: " + minutes + " мин. Получено +" + rep + " репутации ВК.");
    }

    private void createMilestonePromo(StreamerProfile sp, LiveStream ls, int minutes) {
        String code = ("STREAM" + sp.vkId + "M" + minutes + randomSuffix()).toUpperCase(Locale.ROOT);
        data.set("promos." + code + ".streamer", sp.vkId);
        data.set("promos." + code + ".reward", plugin.getConfig().getInt("rewards.viewers.promo-rep", 25));
        data.set("promos." + code + ".uses", plugin.getConfig().getInt("rewards.viewers.promo-uses", 25));
        data.set("promos." + code + ".used", new ArrayList<Integer>());
        saveData();
        String msg = "🎁 Промокод для зрителей стрима " + sp.channel + ": " + code + "\nВвести в ЛС/беседе: !стрим промо " + code;
        api().sendToMainChat(msg);
        api().sendMessage(sp.vkId, msg);
    }

    private void claimPromo(int peer, int vk, String[] args) {
        if (args.length < 3) { api().sendMessage(peer, "Использование: !стрим промо <код>"); return; }
        String code = args[2].toUpperCase(Locale.ROOT);
        if (!data.contains("promos." + code)) { api().sendMessage(peer, "❌ Промокод не найден."); return; }
        List<Integer> used = data.getIntegerList("promos." + code + ".used");
        int uses = data.getInt("promos." + code + ".uses", 0);
        if (used.contains(vk)) { api().sendMessage(peer, "❌ Ты уже активировал этот промокод."); return; }
        if (used.size() >= uses) { api().sendMessage(peer, "❌ Лимит активаций промокода исчерпан."); return; }
        int rep = data.getInt("promos." + code + ".reward", 25);
        api().addReputation(vk, rep);
        used.add(vk);
        data.set("promos." + code + ".used", used);
        saveData();
        String bonus = "";
        if (new Random().nextInt(100) < plugin.getConfig().getInt("rewards.viewers.lottery-chance", 10)) bonus = "\n🎲 Бонус-лотерея: тебе выпал редкий шанс! Забери приз у администрации/на ивенте.";
        api().sendMessage(peer, "✅ Промокод активирован: +" + rep + " репутации ВК." + bonus);
    }

    private void startVote(StreamerProfile sp, LiveStream ls, int milestone) {
        ls.currentVote = "milestone_" + milestone;
        ls.voteEndsAt = System.currentTimeMillis() + plugin.getConfig().getLong("interactions.vote-duration-seconds", 90) * 1000L;
        ls.votes.clear();
        api().sendToMainChat("🗳 Голосование для стрима " + sp.channel + "!\n" +
                "1 — Бафф стримеру\n2 — PvE-челлендж\n3 — Экономический челлендж\n4 — Социальный челлендж\nГолосуй: !стрим голос <1-4>");
    }

    private void vote(int peer, int vk, String[] args) {
        if (args.length < 3) { api().sendMessage(peer, "Использование: !стрим голос <1-4>"); return; }
        int choice;
        try { choice = Integer.parseInt(args[2]); } catch (Exception e) { api().sendMessage(peer, "Нужно число 1-4."); return; }
        if (choice < 1 || choice > 4) { api().sendMessage(peer, "Нужно число 1-4."); return; }
        LiveStream target = null;
        for (LiveStream ls : live.values()) if (ls.voteEndsAt > System.currentTimeMillis()) { target = ls; break; }
        if (target == null) { api().sendMessage(peer, "Сейчас нет активного голосования."); return; }
        target.votes.put(vk, choice);
        api().sendMessage(peer, "✅ Голос принят: " + choice);
    }

    private void finishVote(StreamerProfile sp, LiveStream ls) {
        int[] counts = new int[5];
        for (int c : ls.votes.values()) if (c >= 1 && c <= 4) counts[c]++;
        int best = 1;
        for (int i = 2; i <= 4; i++) if (counts[i] > counts[best]) best = i;
        String result;
        if (best == 1) result = "Бафф стримеру: зрители выбрали поддержку!";
        else if (best == 2) result = "PvE-челлендж: убить элитного моба или закрыть контракт.";
        else if (best == 3) result = "Экономический челлендж: добыть/продать ресурсы или сковать предмет.";
        else result = "Социальный челлендж: помочь новичку или провести экскурсию.";
        api().sendToMainChat("🗳 Итог голосования стрима " + sp.channel + ": " + result);
        api().sendMessage(sp.vkId, "🗳 Итог голосования: " + result);
        ls.currentVote = null;
        ls.voteEndsAt = 0;
        ls.votes.clear();
        saveData();
    }

    private void approve(int peer, int adminVk, String[] args) {
        if (!isVkAdmin(adminVk)) { api().sendMessage(peer, "⛔ Нет доступа."); return; }
        if (args.length < 3) { api().sendMessage(peer, "Использование: !стрим approve <vk>"); return; }
        int target = Integer.parseInt(args[2]);
        StreamerProfile sp = profiles.get(target);
        if (sp == null) { api().sendMessage(peer, "Заявка не найдена."); return; }
        sp.approved = true;
        saveData();
        api().sendMessage(peer, "✅ Стример approved: " + target);
        api().sendMessage(target, "✅ Твоя заявка стримера одобрена. Теперь доступен ручной режим: !стрим live <название>");
    }

    private void reject(int peer, int adminVk, String[] args) {
        if (!isVkAdmin(adminVk)) { api().sendMessage(peer, "⛔ Нет доступа."); return; }
        if (args.length < 3) { api().sendMessage(peer, "Использование: !стрим reject <vk>"); return; }
        int target = Integer.parseInt(args[2]);
        profiles.remove(target);
        saveData();
        api().sendMessage(peer, "❌ Заявка отклонена: " + target);
        api().sendMessage(target, "❌ Твоя заявка стримера отклонена администрацией.");
    }

    private void warn(int peer, int adminVk, String[] args) {
        if (!isVkAdmin(adminVk)) { api().sendMessage(peer, "⛔ Нет доступа."); return; }
        if (args.length < 3) { api().sendMessage(peer, "Использование: !стрим warn <vk>"); return; }
        int target = Integer.parseInt(args[2]);
        StreamerProfile sp = profiles.get(target);
        if (sp == null) { api().sendMessage(peer, "Стример не найден."); return; }
        sp.warnings++;
        if (sp.warnings >= plugin.getConfig().getInt("abuse.max-warnings-before-unapprove", 3)) sp.approved = false;
        sp.manualCooldownUntil = System.currentTimeMillis() + plugin.getConfig().getLong("abuse.manual-cooldown-minutes", 120) * 60000L;
        saveData();
        api().sendMessage(target, "⚠ Предупреждение за нарушение ручного режима стримов. Кулдаун включён.");
        api().sendMessage(peer, "Предупреждение выдано. warnings=" + sp.warnings + ", approved=" + sp.approved);
    }


    public boolean submitApplicationFromMinecraft(Player p, String platform, String channel, String link, String description) {
        int vk = api().getLinkedVkId(p);
        if (vk == -1) {
            p.sendMessage(ChatColor.RED + "❌ Для заявки стримера нужно привязать ВК: /vklink");
            return false;
        }
        StreamerProfile sp = profiles.getOrDefault(vk, new StreamerProfile());
        sp.vkId = vk;
        sp.platform = platform.toLowerCase(Locale.ROOT);
        sp.channel = channel;
        sp.link = link;
        sp.description = description == null || description.trim().isEmpty() ? "Заявка из Minecraft от " + p.getName() : description;
        sp.schedule = "уточняется";
        sp.contacts = "Minecraft: " + p.getName() + ", VK id " + vk;
        sp.approved = plugin.getConfig().getBoolean("approval.auto-approve", false);
        profiles.put(vk, sp);
        saveData();
        p.sendMessage(ChatColor.GREEN + "✅ Заявка стримера отправлена: " + sp.platform + " / " + sp.channel);
        p.sendMessage(ChatColor.GRAY + "Ожидай подтверждения VK-админа. После approval будет доступен ручной live-режим.");
        notifyAdmins("📝 Новая заявка стримера из Minecraft\nИгрок: " + p.getName() + "\nVK: " + vk + "\nПлатформа: " + sp.platform + "\nКанал: " + sp.channel + "\nСсылка: " + sp.link + "\nОписание: " + sp.description + "\nОдобрить: !стрим approve " + vk);
        return true;
    }

    public StreamerProfile getProfileByPlayer(Player p) {
        int vk = api().getLinkedVkId(p);
        if (vk == -1) return null;
        return profiles.get(vk);
    }

    public List<LiveStream> getActiveStreams() { return new ArrayList<>(live.values()); }
    public StreamerProfile getProfile(int vkId) { return profiles.get(vkId); }

    public void sendActiveList(int peer) {
        if (live.isEmpty()) { api().sendMessage(peer, "Сейчас активных стримов нет."); return; }
        StringBuilder sb = new StringBuilder("🟢 Активные стримы:\n");
        for (LiveStream ls : live.values()) {
            StreamerProfile sp = profiles.get(ls.vkId);
            if (sp == null) continue;
            long mins = (System.currentTimeMillis() - ls.startedAt) / 60000L;
            sb.append("\n").append(sp.platform.toUpperCase()).append(" — ").append(sp.channel).append("\n")
              .append(ls.title).append("\n")
              .append("⏱ ").append(mins).append(" мин. | ").append(sp.link).append("\n");
        }
        api().sendMessage(peer, sb.toString());
    }

    private void announce(StreamerProfile sp, LiveStream ls) {
        String msg = plugin.getConfig().getString("announcements.template",
                "🎥 {streamer} LIVE на {platform}!\n{title}\n▶ {link}\nНаграды зрителям по промокодам milestones!")
                .replace("{streamer}", sp.channel)
                .replace("{platform}", sp.platform.toUpperCase(Locale.ROOT))
                .replace("{title}", ls.title)
                .replace("{link}", sp.link);
        if (plugin.getConfig().getBoolean("announcements.minecraft", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "🎥 " + ChatColor.GOLD + sp.channel + ChatColor.WHITE + " начал стрим: " + ChatColor.YELLOW + ls.title);
                    p.sendMessage(ChatColor.AQUA + sp.link);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                }
            });
        }
        if (plugin.getConfig().getBoolean("announcements.vk", true)) api().sendToMainChat(msg);
    }

    private VKChatPlugin core() { return VKChatPlugin.getInstance(); }
    private ru.example.vkchat.api.VKChatAPI api() { return VKChatPlugin.getInstance().getApi(); }
    private boolean isVkAdmin(int vkId) { return plugin.getConfig().getIntegerList("vk-admins").contains(vkId); }
    private void notifyAdmins(String text) { for (int id : plugin.getConfig().getIntegerList("vk-admins")) api().sendMessage(id, text); }
    private String join(String[] a, int from) { StringBuilder sb = new StringBuilder(); for (int i = from; i < a.length; i++) { if (i > from) sb.append(' '); sb.append(a[i]); } return sb.toString(); }
    private String randomSuffix() { return Integer.toHexString(new Random().nextInt(0xFFFF)); }

    public void loadData() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        profiles.clear();
        ConfigurationSection sec = data.getConfigurationSection("streamers");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                try {
                    int vk = Integer.parseInt(id);
                    String b = "streamers." + id + ".";
                    StreamerProfile sp = new StreamerProfile();
                    sp.vkId = vk;
                    sp.platform = data.getString(b + "platform", "manual");
                    sp.channel = data.getString(b + "channel", id);
                    sp.link = data.getString(b + "link", "https://vk.com");
                    sp.description = data.getString(b + "description", "");
                    sp.schedule = data.getString(b + "schedule", "");
                    sp.contacts = data.getString(b + "contacts", "");
                    sp.approved = data.getBoolean(b + "approved", false);
                    sp.lastAnnounce = data.getLong(b + "lastAnnounce", 0L);
                    sp.warnings = data.getInt(b + "warnings", 0);
                    sp.manualCooldownUntil = data.getLong(b + "manualCooldownUntil", 0L);
                    profiles.put(vk, sp);
                } catch (Exception ignored) {}
            }
        }
    }

    public void saveData() {
        data.set("streamers", null);
        for (StreamerProfile sp : profiles.values()) {
            String b = "streamers." + sp.vkId + ".";
            data.set(b + "platform", sp.platform);
            data.set(b + "channel", sp.channel);
            data.set(b + "link", sp.link);
            data.set(b + "description", sp.description);
            data.set(b + "schedule", sp.schedule);
            data.set(b + "contacts", sp.contacts);
            data.set(b + "approved", sp.approved);
            data.set(b + "lastAnnounce", sp.lastAnnounce);
            data.set(b + "warnings", sp.warnings);
            data.set(b + "manualCooldownUntil", sp.manualCooldownUntil);
        }
        try { data.save(dataFile); } catch (Exception e) { plugin.getLogger().warning("Не удалось сохранить streamers.yml: " + e.getMessage()); }
    }
}
