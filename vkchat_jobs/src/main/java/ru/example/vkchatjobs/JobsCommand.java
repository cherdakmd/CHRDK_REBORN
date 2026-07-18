package ru.example.vkchatjobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JobsCommand implements CommandExecutor, TabCompleter {
    public static final String MAIN_TITLE = "§8▸ §e§lПРОФЕССИИ §8◂ §7Меню";
    private final VKChatJobsPlugin plugin;

    public JobsCommand(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("daily") || sub.equals("дейлик")) { sendDailyText(p); return true; }
            if (sub.equals("top") || sub.equals("топ")) { sendTop(p); return true; }
            if (sub.equals("info") || sub.equals("инфо")) { sendJobInfo(p); return true; }
            if (sub.equals("claim") || sub.equals("забрать")) {
                if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Используй: /jobs claim <job>"); return true; }
                if (!plugin.getJobsDataManager().claimDaily(p, args[1].toLowerCase())) p.sendMessage(ChatColor.RED + "Ежедневка не готова или уже забрана.");
                return true;
            }
            if (sub.equals("spec") || sub.equals("спек")) {
                if (args.length < 3) { sendSpecsHelp(p); return true; }
                String job = args[1].toLowerCase();
                String spec = normalizeSpec(args[2].toLowerCase());
                if (plugin.getJobsDataManager().setSpecialization(p, job, spec)) {
                    p.sendMessage(ChatColor.GREEN + "Специализация выбрана: " + job + " / " + specName(spec));
                } else {
                    p.sendMessage(ChatColor.RED + "Не удалось выбрать специализацию. Нужно 20 уровень, правильный job/spec и специализация не должна быть выбрана ранее.");
                }
                return true;
            }
            if (sub.equals("weekly") || sub.equals("неделя")) {
                if (args.length >= 2) {
                    String sub2 = args[1].toLowerCase();
                    if (sub2.equals("claim") || sub2.equals("забрать")) {
                        if (args.length < 3) { p.sendMessage(ChatColor.YELLOW + "Используй: /jobs weekly claim <mine|kill|craft|fish|build>"); return true; }
                        String taskType = args[2].toLowerCase();
                        if (!plugin.getWeeklyTaskManager().claimReward(p, taskType)) {
                            p.sendMessage(ChatColor.RED + "Задание не выполнено или уже забрано.");
                        }
                        return true;
                    }
                }
                sendWeeklyText(p);
                return true;
            }
            if (sub.equals("rank") || sub.equals("рейтинг")) {
                sendRanking(p);
                return true;
            }
            if (sub.equals("help") || sub.equals("помощь")) {
                sendHelp(p);
                return true;
            }
            if (sub.equals("stats") || sub.equals("стата")) {
                if (args.length >= 2 && p.hasPermission("vkchatjobs.admin")) {
                    Player target = org.bukkit.Bukkit.getPlayer(args[1]);
                    if (target != null) sendStats(p, target);
                    else p.sendMessage(ChatColor.RED + "Игрок не найден.");
                } else {
                    sendStats(p, p);
                }
                return true;
            }
        }
        openMain(p);
        return true;
    }

    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MAIN_TITLE);
        fill(inv);

        String[] jobsList = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        Material[] mats = {Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_HOE, Material.BREWING_STAND, Material.ANVIL, Material.BONE, Material.FISHING_ROD};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};

        int totalLvl = 0;
        for (String j : jobsList) totalLvl += plugin.getJobsDataManager().getLevel(p.getUniqueId(), j);

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Профиль профессий",
                ChatColor.GRAY + "Суммарный уровень: " + ChatColor.YELLOW + totalLvl,
                ChatColor.GRAY + "Усталость: " + fatigueLine(p),
                "",
                ChatColor.AQUA + "Прокачка стала глубже:",
                ChatColor.GRAY + "• ранги профессий",
                ChatColor.GRAY + "• навыки и ветки",
                ChatColor.GRAY + "• интеграции с Gear/Market/Offline",
                "",
                ChatColor.YELLOW + "/jobs daily " + ChatColor.GRAY + "— ежедневки",
                ChatColor.YELLOW + "/jobs spec <job> <xp|stamina|reward>"));

        for (int i = 0; i < jobsList.length; i++) {
            String j = jobsList[i];
            String name = plugin.getConfig().getString("jobs." + j + ".name", j);
            int lvl = plugin.getJobsDataManager().getLevel(p.getUniqueId(), j);
            int exp = plugin.getJobsDataManager().getExp(p.getUniqueId(), j);
            int req = Math.max(1, lvl * 1000);
            int pts = plugin.getJobsDataManager().getSkillPoints(p.getUniqueId(), j);
            inv.setItem(slots[i], item(mats[i], ChatColor.translateAlternateColorCodes('&', name),
                    ChatColor.GRAY + "Ранг: " + rankName(lvl),
                    ChatColor.GRAY + "Уровень: " + ChatColor.AQUA + lvl,
                    ChatColor.GRAY + "Опыт: " + ChatColor.GREEN + exp + " / " + req,
                    progressBar(exp, req),
                    ChatColor.GRAY + "Очки навыков: " + ChatColor.YELLOW + pts,
                    ChatColor.GRAY + "Заработано репутации: " + ChatColor.GREEN + plugin.getJobsDataManager().getRepEarned(p.getUniqueId(), j),
                    ChatColor.GRAY + "Специализация: " + ChatColor.LIGHT_PURPLE + (plugin.getJobsDataManager().getSpecialization(p.getUniqueId(), j).isEmpty() ? "не выбрана" : specName(plugin.getJobsDataManager().getSpecialization(p.getUniqueId(), j))),
                    "",
                    integrationLore(j),
                    "",
                    ChatColor.YELLOW + "▶ Нажмите, чтобы открыть навыки"));
        }

        inv.setItem(28, item(Material.WRITABLE_BOOK, ChatColor.AQUA + "Ежедневные проф-задачи",
                ChatColor.GRAY + "Каждая профессия имеет дневную цель.",
                ChatColor.GRAY + "Награда: ВК-репутация + ванильный предмет.",
                ChatColor.YELLOW + "Команда: /jobs daily",
                ChatColor.GREEN + "Нажми, чтобы открыть GUI ежедневок"));
        inv.setItem(29, item(Material.COMPASS, ChatColor.LIGHT_PURPLE + "Специализации",
                ChatColor.GRAY + "С 20 уровня можно выбрать одну ветку:",
                ChatColor.GREEN + "XP " + ChatColor.GRAY + "— +15% опыта профессии",
                ChatColor.AQUA + "Stamina " + ChatColor.GRAY + "— -25% усталости",
                ChatColor.GOLD + "Reward " + ChatColor.GRAY + "— +20% наград ежедневок",
                ChatColor.YELLOW + "/jobs spec <job> <xp|stamina|reward>"));
        inv.setItem(30, item(Material.EMERALD, ChatColor.GREEN + "Экономика профессий",
                ChatColor.GRAY + "Профессии будут мягко связываться с рынком.",
                ChatColor.GRAY + "Цены одинаковые для всех, но профессии дают прогресс и доступы."));
        inv.setItem(32, item(Material.ANVIL, ChatColor.RED + "Gear-интеграция",
                ChatColor.GRAY + "Кузнец влияет на скидки и качество ковки.",
                ChatColor.GRAY + "Алхимик полезен против ядов/проклятий."));

        ItemStack fatigueItem = item(Material.CLOCK, ChatColor.RED + "Усталость", fatigueLine(p), ChatColor.GRAY + "Отдыхайте, чтобы снизить усталость.");
        inv.setItem(49, fatigueItem);
        p.openInventory(inv);
    }


    private void sendTop(Player p) {
        java.util.List<java.util.UUID> players = new java.util.ArrayList<>(plugin.getJobsDataManager().getKnownPlayers());
        players.sort((a, b) -> Integer.compare(plugin.getJobsDataManager().getTotalLevel(b), plugin.getJobsDataManager().getTotalLevel(a)));
        p.sendMessage(ChatColor.GOLD + "🏆 Топ профессий по суммарному уровню:");
        int n = 0;
        for (java.util.UUID uuid : players) {
            int total = plugin.getJobsDataManager().getTotalLevel(uuid);
            if (total <= 7) continue;
            String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            String topJob = plugin.getJobsDataManager().getTopJob(uuid);
            p.sendMessage(ChatColor.YELLOW + "#" + (++n) + " " + ChatColor.WHITE + name + ChatColor.GRAY + " — " + ChatColor.AQUA + total
                    + ChatColor.GRAY + " [" + topJob + "]");
            if (n >= 10) break;
        }
        if (n == 0) p.sendMessage(ChatColor.GRAY + "Пока нет данных для топа.");
    }

    private void sendDailyText(Player p) {
        String[] jobs = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        StringBuilder sb = new StringBuilder(ChatColor.AQUA + "Ежедневные задания профессий\n");
        for (String job : jobs) {
            int prog = plugin.getJobsDataManager().getDailyProgress(p.getUniqueId(), job);
            int target = plugin.getJobsDataManager().getDailyTarget(job);
            boolean claimed = plugin.getJobsDataManager().isDailyClaimed(p.getUniqueId(), job);
            sb.append(ChatColor.GRAY).append(job).append(": ").append(ChatColor.YELLOW).append(prog).append("/").append(target)
                    .append(claimed ? ChatColor.GREEN + " получено" : (prog >= target ? ChatColor.GOLD + " можно забрать: /jobs claim " + job : ""))
                    .append("\n");
        }
        p.sendMessage(sb.toString());
    }

    private void sendJobInfo(Player p) {
        String[] jobs = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        String[] names = {"Шахтёр", "Лесоруб", "Фермер", "Алхимик", "Кузнец", "Охотник", "Рыбак"};

        boolean hasAny = false;
        for (String job : jobs) {
            if (plugin.getJobsDataManager().getLevel(p.getUniqueId(), job) > 1) { hasAny = true; break; }
        }
        if (!hasAny) {
            int totalLvl = 0;
            for (String job : jobs) totalLvl += plugin.getJobsDataManager().getLevel(p.getUniqueId(), job);
            if (totalLvl <= jobs.length) {
                p.sendMessage("§8▸ §e§lИНФО §8◂ §7Профили");
                p.sendMessage("");
                p.sendMessage("§7У тебя пока нет профессий.");
                p.sendMessage("§e▸ §7Используй §f/jobs list §7чтобы выбрать!");
                return;
            }
        }

        p.sendMessage("§8▸ §e§lИНФО §8◂ §7Профили");
        p.sendMessage("");

        int fatigue = plugin.getJobsDataManager().getFatigue(p.getUniqueId());
        int maxF = plugin.getConfig().getInt("fatigue.max-fatigue", 1000);
        p.sendMessage("§8▸ §c§lУСТАЛОСТЬ §8◂ §7Текущий статус");
        p.sendMessage("   §7" + fatigue + "§8/§7" + maxF + " " + progressBar(maxF - fatigue, maxF));
        p.sendMessage("");

        int rankPos = plugin.getRankingManager().getPlayerRank(p.getUniqueId());
        int weeklyRep = plugin.getRankingManager().getWeeklyRep(p.getUniqueId());
        p.sendMessage("§8▸ §6§lРЕЙТИНГ §8◂ §7Позиция за неделю");
        p.sendMessage("   §7Позиция: " + (rankPos > 0 ? "§e#" + rankPos : "§8не в топе") + " §8| §7Репутация: §a" + weeklyRep);
        p.sendMessage("");

        for (int i = 0; i < jobs.length; i++) {
            String job = jobs[i];
            java.util.UUID uuid = p.getUniqueId();
            int lvl = plugin.getJobsDataManager().getLevel(uuid, job);
            int exp = plugin.getJobsDataManager().getExp(uuid, job);
            int req = Math.max(1, lvl * 1000);
            String spec = plugin.getJobsDataManager().getSpecialization(uuid, job);
            java.util.List<String> skills = plugin.getJobsDataManager().getUnlockedSkills(uuid, job);

            p.sendMessage("§8▸ §e§l" + names[i].toUpperCase() + " §8◂ §7" + rankName(lvl) + " §8| §7ур. §f" + lvl);
            p.sendMessage("   §7Опыт: §a" + exp + "§8/§7" + req + " " + progressBar(exp, req));

            if (!spec.isEmpty()) {
                p.sendMessage("   §7Специализация: §d" + specName(spec));
            }

            if (!skills.isEmpty()) {
                StringBuilder sb = new StringBuilder("   §7Навыки: ");
                for (int si = 0; si < skills.size(); si++) {
                    String skillId = skills.get(si);
                    String skillName = getSkillName(job, skillId);
                    sb.append("§a").append(skillName);
                    if (si < skills.size() - 1) sb.append("§7, ");
                }
                p.sendMessage(sb.toString());
            } else {
                p.sendMessage("   §7Навыки: §8нет");
            }

            p.sendMessage("");
        }
    }

    private String getSkillName(String job, String skillId) {
        for (SkillManager.SkillDef sd : plugin.getSkillManager().getSkillsForJob(job)) {
            if (sd.id.equals(skillId)) return sd.name;
        }
        return skillId;
    }

    private void sendSpecsHelp(Player p) {
        p.sendMessage(ChatColor.LIGHT_PURPLE + "Специализации Jobs");
        p.sendMessage(ChatColor.GRAY + "С 20 уровня профессии выбери одну навсегда:");
        p.sendMessage(ChatColor.GREEN + "xp" + ChatColor.GRAY + " — +15% опыта профессии");
        p.sendMessage(ChatColor.AQUA + "stamina" + ChatColor.GRAY + " — -25% усталости за действия");
        p.sendMessage(ChatColor.GOLD + "reward" + ChatColor.GRAY + " — +20% наград ежедневок");
        p.sendMessage(ChatColor.YELLOW + "Пример: /jobs spec miner xp");
    }

    private void sendWeeklyText(Player p) {
        String[] tasks = {"mine", "kill", "craft", "fish", "build"};
        String[] desc = {"Добыть 500 блоков", "Убить 100 мобов", "Скрафтить 50 предметов", "Поймать 30 рыб", "Поставить 200 блоков"};
        int[] targets = {500, 100, 50, 30, 200};
        int completed = plugin.getWeeklyTaskManager().getCompletedCount(p.getUniqueId());
        int required = plugin.getConfig().getInt("weekly-tasks.tasks-per-week", 3);

        p.sendMessage(ChatColor.AQUA + "═══ Еженедельные задания (" + completed + "/" + required + ") ═══");
        for (int i = 0; i < tasks.length; i++) {
            int prog = plugin.getWeeklyTaskManager().getProgress(p.getUniqueId(), tasks[i]);
            boolean claimed = plugin.getWeeklyTaskManager().isClaimed(p.getUniqueId(), tasks[i]);
            p.sendMessage(ChatColor.GRAY + desc[i] + ": " + ChatColor.YELLOW + prog + "/" + targets[i]
                    + (claimed ? ChatColor.GREEN + " получено" : (prog >= targets[i] ? ChatColor.GOLD + " — /jobs weekly claim " + tasks[i] : "")));
        }
        if (completed >= required) {
            p.sendMessage(ChatColor.GOLD + "🏆 Бонус за все задания: +" + plugin.getConfig().getInt("weekly-tasks.bonus-rep", 500) + " репутации!");
        }
    }

    private void sendRanking(Player p) {
        List<java.util.UUID> top = plugin.getRankingManager().getTopPlayers(10);
        p.sendMessage(ChatColor.GOLD + "═══ Рейтинг Профессий (неделя) ═══");
        if (top.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Пока нет данных за эту неделю.");
            return;
        }
        int n = 0;
        for (java.util.UUID uuid : top) {
            int rep = plugin.getRankingManager().getWeeklyRep(uuid);
            if (rep <= 0) continue;
            String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            ChatColor color = n == 0 ? ChatColor.GOLD : n == 1 ? ChatColor.WHITE : n == 2 ? ChatColor.YELLOW : ChatColor.GRAY;
            String medal = n == 0 ? "🥇" : n == 1 ? "🥈" : n == 2 ? "🥉" : "  ";
            p.sendMessage(color + medal + " #" + (n + 1) + " " + name + ChatColor.GRAY + " — " + ChatColor.AQUA + rep + " реп.");
            n++;
        }
    }

    private String normalizeSpec(String spec) {
        if (spec.equals("опыт") || spec.equals("exp")) return "xp";
        if (spec.equals("выносливость") || spec.equals("усталость")) return "stamina";
        if (spec.equals("награда") || spec.equals("награды")) return "reward";
        return spec;
    }

    private String specName(String spec) {
        if (spec.equals("xp")) return "Опыт";
        if (spec.equals("stamina")) return "Выносливость";
        if (spec.equals("reward")) return "Награды";
        return spec;
    }

    private String fatigueLine(Player p) {
        int fatigue = plugin.getJobsDataManager().getFatigue(p.getUniqueId());
        int maxF = plugin.getConfig().getInt("fatigue.max-fatigue", 1000);
        return ChatColor.YELLOW + String.valueOf(fatigue) + ChatColor.GRAY + " / " + maxF + " " + progressBar(maxF - fatigue, maxF);
    }

    private String rankName(int lvl) {
        if (lvl >= 50) return ChatColor.GOLD + "Легенда";
        if (lvl >= 40) return ChatColor.LIGHT_PURPLE + "Грандмастер";
        if (lvl >= 30) return ChatColor.AQUA + "Мастер";
        if (lvl >= 20) return ChatColor.GREEN + "Специалист";
        if (lvl >= 10) return ChatColor.YELLOW + "Подмастерье";
        return ChatColor.GRAY + "Новичок";
    }

    private String progressBar(int value, int max) {
        int filled = (int) Math.round(Math.max(0, Math.min(1.0, value / (double) Math.max(1, max))) * 10.0);
        StringBuilder sb = new StringBuilder(ChatColor.DARK_GRAY + "[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? ChatColor.GREEN + "■" : ChatColor.DARK_GRAY + "□");
        sb.append(ChatColor.DARK_GRAY + "]");
        return sb.toString();
    }

    private String integrationLore(String job) {
        switch (job) {
            case "miner": return ChatColor.DARK_AQUA + "Интеграция: шахты, рынок руд, пещеры";
            case "woodcutter": return ChatColor.DARK_AQUA + "Интеграция: дерево, рынок, лесные события";
            case "farmer": return ChatColor.DARK_AQUA + "Интеграция: припасы, еда, зелья";
            case "alchemist": return ChatColor.DARK_AQUA + "Интеграция: зелья, яды, проклятия, артефакты";
            case "blacksmith": return ChatColor.DARK_AQUA + "Интеграция: Gear, ковка, редкость, скидки";
            case "hunter": return ChatColor.DARK_AQUA + "Интеграция: мобы, боссы, фрагменты сетов";
            case "fisherman": return ChatColor.DARK_AQUA + "Интеграция: редкие уловы и ресурсы";
            default: return ChatColor.DARK_AQUA + "Интеграция: серверная экономика";
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "═══ /jobs Помощь ═══");
        p.sendMessage(ChatColor.YELLOW + "/jobs" + ChatColor.GRAY + " — открыть GUI профессий");
        p.sendMessage(ChatColor.YELLOW + "/jobs info" + ChatColor.GRAY + " — инфо о профессиях и навыках");
        p.sendMessage(ChatColor.YELLOW + "/jobs top" + ChatColor.GRAY + " — топ игроков по уровням");
        p.sendMessage(ChatColor.YELLOW + "/jobs stats [игрок]" + ChatColor.GRAY + " — детальная статистика");
        p.sendMessage(ChatColor.YELLOW + "/jobs spec <job> <xp|stamina|reward>" + ChatColor.GRAY + " — выбрать специализацию");
        p.sendMessage(ChatColor.YELLOW + "/jobs daily" + ChatColor.GRAY + " — прогресс ежедневок");
        p.sendMessage(ChatColor.YELLOW + "/jobs claim <job>" + ChatColor.GRAY + " — забрать ежедневку");
        p.sendMessage(ChatColor.YELLOW + "/jobs weekly" + ChatColor.GRAY + " — еженедельные задания");
        p.sendMessage(ChatColor.YELLOW + "/jobs weekly claim <type>" + ChatColor.GRAY + " — забрать недельное");
        p.sendMessage(ChatColor.YELLOW + "/jobs rank" + ChatColor.GRAY + " — рейтинг профессий");
        p.sendMessage("");
        p.sendMessage(ChatColor.AQUA + "Доступные профессии:");
        p.sendMessage(ChatColor.GRAY + "miner — Шахтёр (добыча руд)");
        p.sendMessage(ChatColor.GRAY + "woodcutter — Лесоруб (рубка дерева)");
        p.sendMessage(ChatColor.GRAY + "farmer — Фермер (сбор урожая)");
        p.sendMessage(ChatColor.GRAY + "alchemist — Алхимик (варка зелий)");
        p.sendMessage(ChatColor.GRAY + "blacksmith — Кузнец (крафт брони/оружия)");
        p.sendMessage(ChatColor.GRAY + "hunter — Охотник (убийство мобов)");
        p.sendMessage(ChatColor.GRAY + "fisherman — Рыбак (рыбалка)");
    }

    private void sendStats(Player sender, Player target) {
        String[] jobs = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        String[] names = {"Шахтёр", "Лесоруб", "Фермер", "Алхимик", "Кузнец", "Охотник", "Рыбак"};
        sender.sendMessage(ChatColor.GOLD + "═══ Статистика: " + target.getName() + " ═══");
        int total = 0;
        for (int i = 0; i < jobs.length; i++) {
            int lvl = plugin.getJobsDataManager().getLevel(target.getUniqueId(), jobs[i]);
            int exp = plugin.getJobsDataManager().getExp(target.getUniqueId(), jobs[i]);
            int rep = plugin.getJobsDataManager().getRepEarned(target.getUniqueId(), jobs[i]);
            String spec = plugin.getJobsDataManager().getSpecialization(target.getUniqueId(), jobs[i]);
            total += lvl;
            sender.sendMessage(ChatColor.GRAY + names[i] + ": " + ChatColor.AQUA + "ур." + lvl + ChatColor.GRAY
                    + " | exp: " + exp + "/" + (lvl * 1000) + " | реп: " + rep
                    + (spec.isEmpty() ? "" : " | спец: " + specName(spec)));
        }
        sender.sendMessage(ChatColor.YELLOW + "Суммарный уровень: " + ChatColor.AQUA + total);
        sender.sendMessage(ChatColor.YELLOW + "Усталость: " + fatigueLine(target));
        sender.sendMessage(ChatColor.YELLOW + "Контрактов выполнено: " + ChatColor.AQUA + plugin.getJobsDataManager().getCompletedContracts(target.getUniqueId()));
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("daily", "дейлик", "top", "топ", "info", "инфо", "claim", "забрать", "spec", "спек", "weekly", "неделя", "rank", "рейтинг", "help", "помощь", "stats", "стата"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spec") || sub.equals("спек")) {
                completions.addAll(Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"));
            } else if (sub.equals("claim") || sub.equals("забрать")) {
                completions.addAll(Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"));
            } else if (sub.equals("weekly") || sub.equals("неделя")) {
                completions.addAll(Arrays.asList("claim", "забрать"));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spec") || sub.equals("спек")) {
                completions.addAll(Arrays.asList("xp", "stamina", "reward"));
            } else if (sub.equals("weekly") || sub.equals("неделя")) {
                completions.addAll(Arrays.asList("mine", "kill", "craft", "fish", "build"));
            }
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
