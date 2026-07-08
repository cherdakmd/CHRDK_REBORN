package ru.example.vkchatstarter.listeners;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatstarter.VKChatStarterPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик квестов обучения нового игрока.
 * Отслеживает действия игрока и выдаёт награды за выполнение заданий.
 */
public class QuestListener implements Listener {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progKey;
    private final NamespacedKey deathKey;
    private final NamespacedKey startTimeKey;

    private final Map<Integer, QuestStage> stages = new HashMap<>();
    private int rewardPerStage = 75;
    private int finalRewardRep = 1500;
    private List<String> finalRewardItems = new ArrayList<>();

    // Шаблоны сообщений
    private String msgProgress = "&7{quest}: &e{progress}/{target}";
    private String msgComplete = "&a✔ Квест выполнен! &7Следующее: &e{next}";
    private String msgFinal = "&6🎉 ОБУЧЕНИЕ ЗАВЕРШЕНО! &e+{reward} репутации ВК!";
    private String titleComplete = "&aКвест выполнен!";
    private String titleFinal = "&6🌟 ОБУЧЕНИЕ ЗАВЕРШЕНО 🌟";

    public QuestListener(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        this.progKey = new NamespacedKey(plugin, "starter_quest_progress");
        this.deathKey = new NamespacedKey(plugin, "starter_quest_deaths");
        this.startTimeKey = new NamespacedKey(plugin, "starter_quest_start_time");
        loadConfig();
    }

    /**
     * Загружает конфигурацию квестов.
     */
    public void loadConfig() {
        stages.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("quest");
        if (sec == null) return;

        rewardPerStage = sec.getInt("reward-per-stage", 75);
        finalRewardRep = sec.getInt("final-reward-rep", 1500);
        finalRewardItems = sec.getStringList("final-reward-items");

        List<Map<?, ?>> stageList = sec.getMapList("stages");
        for (Map<?, ?> map : stageList) {
            int id = (int) map.get("id");
            String type = (String) map.get("type");
            String target = (String) map.get("target");
            int amount = (int) map.get("amount");
            String name = (String) map.get("name");
            String message = map.containsKey("message") ? (String) map.get("message") : null;
            String rewardMsg = map.containsKey("reward-message") ? (String) map.get("reward-message") : null;
            stages.put(id, new QuestStage(id, type, target, amount, name, message, rewardMsg));
        }

        // Шаблоны уведомлений
        ConfigurationSection notifSec = plugin.getConfig().getConfigurationSection("notifications");
        if (notifSec != null) {
            msgProgress = notifSec.getString("progress-message", msgProgress);
            msgComplete = notifSec.getString("complete-message", msgComplete);
            msgFinal = notifSec.getString("final-message", msgFinal);
            titleComplete = notifSec.getString("title-complete", titleComplete);
            titleFinal = notifSec.getString("title-final", titleFinal);
        }
    }

    /**
     * Получает текущий этап квеста игрока.
     */
    private int getStage(Player p) {
        return p.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Получает прогресс текущего этапа.
     */
    private int getProgress(Player p) {
        return p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Проверяет, пропустил ли игрок квест (по конфигу или PDC).
     */
    private boolean isSkipped(Player p) {
        return p.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(plugin, "starter_quest_skipped"),
                PersistentDataType.INTEGER, 0) == 1;
    }

    /**
     * Выполняет промежуточный этап квеста.
     */
    private void completeStage(Player p, String nextGoal) {
        int current = getStage(p);
        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, current + 1);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);

        // Награда за этап
        int vkId = getVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, rewardPerStage);
        }

        // Уведомление
        String completeMsg = ChatColor.translateAlternateColorCodes('&', msgComplete)
                .replace("{next}", nextGoal)
                .replace("{reward}", String.valueOf(rewardPerStage));
        p.sendMessage(completeMsg);
        p.sendTitle(
                ChatColor.translateAlternateColorCodes('&', titleComplete),
                ChatColor.YELLOW + "Следующее: " + nextGoal,
                10, 70, 20
        );
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /**
     * Выполняет финальный этап квеста.
     */
    private void completeFinalQuest(Player p) {
        int current = getStage(p);
        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, current + 1);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);

        // Финальная награда
        int vkId = getVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, finalRewardRep);
        }

        // Предметы
        for (String itemStr : finalRewardItems) {
            String[] parts = itemStr.split(";");
            if (parts.length >= 2) {
                try {
                    org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    p.getInventory().addItem(new ItemStack(mat, amount));
                } catch (Exception ignored) {}
            }
        }

        // Уведомление
        String finalMsg = ChatColor.translateAlternateColorCodes('&', msgFinal)
                .replace("{reward}", String.valueOf(finalRewardRep));
        p.sendMessage(finalMsg);
        p.sendTitle(
                ChatColor.translateAlternateColorCodes('&', titleFinal),
                ChatColor.GREEN + "+" + finalRewardRep + " репутации ВК!",
                20, 100, 20
        );
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.8f);

        // Проверка достижений
        checkAchievements(p);
    }

    /**
     * Проверяет и выдаёт достижения.
     */
    private void checkAchievements(Player p) {
        ConfigurationSection achSec = plugin.getConfig().getConfigurationSection("achievements");
        if (achSec == null || !achSec.getBoolean("enabled", true)) return;

        int vkId = getVkId(p);
        if (vkId == -1) return;

        // Выпускник
        unlockAchievement(p, vkId, "quest_complete");

        // Спринтер (за 10 минут)
        long startTime = p.getPersistentDataContainer().getOrDefault(startTimeKey, PersistentDataType.LONG, 0L);
        if (startTime > 0 && System.currentTimeMillis() - startTime < 600000) {
            unlockAchievement(p, vkId, "quest_speedrun");
        }

        // Безупречный (без смертей)
        int deaths = p.getPersistentDataContainer().getOrDefault(deathKey, PersistentDataType.INTEGER, 0);
        if (deaths == 0) {
            unlockAchievement(p, vkId, "quest_no_death");
        }
    }

    /**
     * Разблокирует достижение и выдаёт награду.
     */
    private void unlockAchievement(Player p, int vkId, String achievementId) {
        NamespacedKey achKey = new NamespacedKey(plugin, "ach_" + achievementId);
        if (p.getPersistentDataContainer().has(achKey, PersistentDataType.INTEGER)) return;

        p.getPersistentDataContainer().set(achKey, PersistentDataType.INTEGER, 1);

        // Награда из конфига
        ConfigurationSection achSec = plugin.getConfig().getConfigurationSection("achievements");
        if (achSec != null) {
            List<Map<?, ?>> list = achSec.getMapList("list");
            for (Map<?, ?> ach : list) {
                if (achievementId.equals(ach.get("id"))) {
                    int rep = ach.containsKey("reward-rep") ? ((Number) ach.get("reward-rep")).intValue() : 0;
                    if (rep > 0) {
                        VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
                    }
                    String name = ach.containsKey("name") ? (String) ach.get("name") : achievementId;
                    p.sendMessage(ChatColor.GOLD + "🏆 Достижение: " + name + " (+" + rep + " реп.)");
                    break;
                }
            }
        }
    }

    /**
     * Проверяет совпадение цели.
     */
    private boolean matchesTarget(QuestStage stage, String typeName) {
        if (stage.target.startsWith("_")) {
            return typeName.contains(stage.target.substring(1));
        }
        return typeName.equals(stage.target);
    }

    /**
     * Обрабатывает прогресс квеста.
     */
    private void handleProgress(Player p, QuestStage stage, int currentProg) {
        int newProg = currentProg + 1;
        if (newProg >= stage.amount) {
            // Этап завершён
            int nextStage = stage.id + 1;
            QuestStage next = stages.get(nextStage);
            String nextName = next != null ? next.name : "Финал!";
            if (next != null && next.id == stages.size() - 1) {
                completeFinalQuest(p);
            } else {
                completeStage(p, nextName);
            }
        } else {
            p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, newProg);
            // Прогресс-сообщение
            if (stage.message != null) {
                String msg = ChatColor.translateAlternateColorCodes('&', stage.message)
                        .replace("{progress}", String.valueOf(newProg))
                        .replace("{target}", String.valueOf(stage.amount));
                p.sendMessage(msg);
            }
        }
    }

    /**
     * Получает VK ID игрока.
     */
    private int getVkId(Player p) {
        try {
            return VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        } catch (Exception e) {
            return -1;
        }
    }

    // ==================== ОБРАБОТЧИКИ СОБЫТИЙ ====================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int vkId = getVkId(p);
        if (vkId == -1) return;
        int stage = getStage(p);
        if (stage >= 0 && stage < stages.size()) {
            QuestStage current = stages.get(stage);
            p.sendMessage(ChatColor.GOLD + "Квест обучения: " + ChatColor.WHITE + current.name);
            p.sendMessage(ChatColor.GRAY + "Прогресс этапа " + (stage + 1) + "/" + stages.size() + ". Награда за этап: +" + rewardPerStage + " реп.");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (isSkipped(p)) return;

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("break")) return;

        if (matchesTarget(stage, e.getBlock().getType().name())) {
            handleProgress(p, stage, getProgress(p));
        }

        if (e.getBlock().getType().name().contains("ORE")) {
            int vkId = getVkId(p);
            if (vkId != -1) unlockAchievement(p, vkId, "first_mine");
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        if (isSkipped(p)) return;

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("craft")) return;

        if (matchesTarget(stage, e.getCurrentItem().getType().name())) {
            handleProgress(p, stage, getProgress(p));
        }

        int vkId = getVkId(p);
        if (vkId != -1) unlockAchievement(p, vkId, "first_craft");
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) return;
        if (isSkipped(p)) return;

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("kill")) return;

        if (matchesTarget(stage, e.getEntity().getType().name())) {
            handleProgress(p, stage, getProgress(p));
        }

        int vkId = getVkId(p);
        if (vkId != -1) unlockAchievement(p, vkId, "first_kill");
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (isSkipped(p)) return;

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("pickup")) return;

        if (matchesTarget(stage, e.getItem().getItemStack().getType().name())) {
            handleProgress(p, stage, getProgress(p));
        }
    }

    @EventHandler
    public void onFurnaceClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getInventory() == null) return;

        // Проверяем, что это печка и клик по слоту результата (слот 2)
        String invType = e.getInventory().getType().name();
        if (!invType.equals("FURNACE") && !invType.equals("BLAST_FURNACE") && !invType.equals("SMOKER")) return;
        if (e.getRawSlot() != 2) return; // Слот выхода печки

        Player p = (Player) e.getWhoClicked();
        if (isSkipped(p)) return;

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("smelt")) return;

        if (e.getCurrentItem() != null && matchesTarget(stage, e.getCurrentItem().getType().name())) {
            handleProgress(p, stage, getProgress(p));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        // Считаем смерти для достижения "Безупречный"
        if (p.getPersistentDataContainer().has(stageKey, PersistentDataType.INTEGER)) {
            int deaths = p.getPersistentDataContainer().getOrDefault(deathKey, PersistentDataType.INTEGER, 0);
            p.getPersistentDataContainer().set(deathKey, PersistentDataType.INTEGER, deaths + 1);
        }
    }

    // ==================== ВНУТРЕННИЙ КЛАСС ====================

    public static class QuestStage {
        public final int id;
        public final String type;
        public final String target;
        public final int amount;
        public final String name;
        public final String message;
        public final String rewardMessage;

        public QuestStage(int id, String type, String target, int amount, String name, String message, String rewardMessage) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.name = name;
            this.message = message;
            this.rewardMessage = rewardMessage;
        }
    }
}
