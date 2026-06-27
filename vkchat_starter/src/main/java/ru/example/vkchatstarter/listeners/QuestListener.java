package ru.example.vkchatstarter.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatstarter.VKChatStarterPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestListener implements Listener {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progKey;

    private final Map<Integer, QuestStage> stages = new HashMap<>();
    private int rewardPerStage = 50;
    private int finalRewardRep = 1000;
    private List<String> finalRewardItems;
    private String msgProgress;
    private String msgComplete;
    private String msgFinal;
    private String titleComplete;
    private String titleFinal;

    public QuestListener(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        this.progKey = new NamespacedKey(plugin, "starter_quest_progress");
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection quest = plugin.getConfig().getConfigurationSection("quest");
        if (quest == null) return;

        rewardPerStage = quest.getInt("reward-per-stage", 50);
        finalRewardRep = quest.getInt("final-reward-rep", 1000);
        finalRewardItems = quest.getStringList("final-reward-items");

        ConfigurationSection notif = plugin.getConfig().getConfigurationSection("notifications");
        if (notif != null) {
            msgProgress = notif.getString("progress-message", "&7{quest}: {progress}/{target}");
            msgComplete = notif.getString("complete-message", "&a✔ Квест выполнен! Следующее задание: {next}");
            msgFinal = notif.getString("final-message", "&6🎉 ОБУЧЕНИЕ ЗАВЕРШЕНО! +{reward} репутации ВК!");
            titleComplete = notif.getString("title-complete", "&aКвест выполнен!");
            titleFinal = notif.getString("title-final", "&6🌟 ОБУЧЕНИЕ ЗАВЕРШЕНО 🌟");
        }

        List<Map<?, ?>> stageList = quest.getMapList("stages");
        for (Map<?, ?> raw : stageList) {
            int id = (int) raw.get("id");
            String type = (String) raw.get("type");
            String target = (String) raw.get("target");
            int amount = (int) raw.get("amount");
            String name = (String) raw.get("name");
            String message = raw.containsKey("message") ? (String) raw.get("message") : null;
            stages.put(id, new QuestStage(id, type, target, amount, name, message));
        }
    }

    public void reloadConfig() {
        stages.clear();
        loadConfig();
    }

    private int getStage(Player p) {
        return p.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
    }

    private void completeQuest(Player p, String nextGoal) {
        int current = getStage(p);
        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, current + 1);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);

        p.sendTitle(
            org.bukkit.ChatColor.translateAlternateColorCodes('&', titleComplete),
            org.bukkit.ChatColor.YELLOW + "Новое задание: " + nextGoal,
            10, 70, 20
        );
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, rewardPerStage);
            p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Награда: +" + rewardPerStage + " репутации ВКонтакте!");
        }
    }

    private void completeFinalQuest(Player p) {
        int current = getStage(p);
        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, current + 1);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);

        p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
            msgFinal.replace("{reward}", String.valueOf(finalRewardRep))));

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, finalRewardRep);
        }

        for (String itemEntry : finalRewardItems) {
            String[] parts = itemEntry.split(";");
            if (parts.length == 2) {
                try {
                    Class<?> mobListener = Class.forName("ru.example.vkchatmobs.listeners.MobListener");
                    org.bukkit.inventory.ItemStack rewardItem = null;
                    if (parts[0].equals("RUNE_TOKEN")) {
                        rewardItem = (org.bukkit.inventory.ItemStack) mobListener.getMethod("getRuneToken").invoke(null);
                    } else if (parts[0].equals("ARTIFACT_SHARD")) {
                        rewardItem = (org.bukkit.inventory.ItemStack) mobListener.getMethod("getArtifactShard").invoke(null);
                    }
                    if (rewardItem != null) {
                        rewardItem.setAmount(Integer.parseInt(parts[1]));
                        p.getInventory().addItem(rewardItem);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("VKChatMobs not available for quest reward: " + parts[0]);
                }
            }
        }

        p.sendTitle(
            org.bukkit.ChatColor.translateAlternateColorCodes('&', titleFinal),
            org.bukkit.ChatColor.GREEN + "Вы получили награды за обучение!",
            10, 100, 20
        );
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.8f);
    }

    private boolean matchesTarget(QuestStage stage, String typeName) {
        if (stage.target.startsWith("_")) {
            return typeName.contains(stage.target);
        }
        return typeName.equals(stage.target);
    }

    private void handleProgress(Player p, QuestStage stage, int currentProg) {
        int newProg = currentProg + 1;
        if (newProg >= stage.amount) {
            int nextId = stage.id + 1;
            QuestStage next = stages.get(nextId);
            if (next != null) {
                completeQuest(p, next.name);
            } else {
                completeFinalQuest(p);
            }
        } else {
            p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, newProg);
            if (stage.message != null) {
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    stage.message.replace("{progress}", String.valueOf(newProg)).replace("{target}", String.valueOf(stage.amount))));
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("break")) return;

        if (matchesTarget(stage, e.getBlock().getType().name())) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);
            handleProgress(p, stage, prog);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null) return;
        String type = e.getCurrentItem().getType().name();

        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("craft")) return;

        if (matchesTarget(stage, type)) {
            completeQuest(p, stages.containsKey(stageId + 1) ? stages.get(stageId + 1).name : "Обучение завершено!");
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("kill")) return;

        if (matchesTarget(stage, e.getEntity().getType().name())) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);
            handleProgress(p, stage, prog);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        int stageId = getStage(p);
        QuestStage stage = stages.get(stageId);
        if (stage == null || !stage.type.equals("pickup")) return;

        if (matchesTarget(stage, e.getItem().getItemStack().getType().name())) {
            completeQuest(p, stages.containsKey(stageId + 1) ? stages.get(stageId + 1).name : "Обучение завершено!");
        }
    }

    private static class QuestStage {
        final int id;
        final String type;
        final String target;
        final int amount;
        final String name;
        final String message;

        QuestStage(int id, String type, String target, int amount, String name, String message) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.name = name;
            this.message = message;
        }
    }
}
