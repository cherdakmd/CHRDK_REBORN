package ru.example.vkchatstarter.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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

public class QuestListener implements Listener {
    private final VKChatStarterPlugin plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progKey;

    public QuestListener(VKChatStarterPlugin plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "starter_quest_stage");
        this.progKey = new NamespacedKey(plugin, "starter_quest_progress");
    }

    private int getStage(Player p) {
        return p.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
    }

    private void completeQuest(Player p, String nextGoal) {
        int current = getStage(p);
        p.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, current + 1);
        p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 0);
        
        p.sendTitle(org.bukkit.ChatColor.GREEN + "Квест выполнен!", org.bukkit.ChatColor.YELLOW + "Новое задание: " + nextGoal, 10, 70, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId != -1) {
            VKChatPlugin.getInstance().getApi().addReputation(vkId, 50);
            p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Награда: +50 репутации ВКонтакте!");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        int stage = getStage(p);
        
        if (stage == 0 && e.getBlock().getType().name().endsWith("_LOG")) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 5) completeQuest(p, "Скрафтить Верстак");
            else {
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
                p.sendMessage(org.bukkit.ChatColor.GRAY + "Дерево: " + prog + "/5");
            }
        }
        else if (stage == 3 && e.getBlock().getType().name().equals("STONE")) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 10) completeQuest(p, "Скрафтить Каменный Меч");
            else p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
        }
        else if (stage == 6 && e.getBlock().getType().name().equals("COAL_ORE")) {
            completeQuest(p, "Скрафтить Факелы");
        }
        else if (stage == 8 && e.getBlock().getType().name().contains("IRON_ORE")) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 3) completeQuest(p, "Переплавить Железный Слиток");
            else p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
        }
        else if (stage == 12 && e.getBlock().getType().name().contains("GOLD_ORE")) {
            completeQuest(p, "Выплавить Золотой Слиток");
        }
        else if (stage == 14 && e.getBlock().getType().name().contains("DIAMOND_ORE")) {
            completeQuest(p, "ПОБЕДА! Обучение завершено.");
            p.sendMessage(org.bukkit.ChatColor.GOLD + " Поздравляем! Вы прошли начальное обучение сервера!");
            
            // Выдаем эпические награды!
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 1000); // 1000 реп!
                p.sendMessage(org.bukkit.ChatColor.GREEN + "🎉 За прохождение всего обучения вам начислено +1000 репутации ВК!");
            }
            
            // Выдаем Жетоны и Осколки!
            p.getInventory().addItem(ru.example.vkchatmobs.listeners.MobListener.getRuneToken());
            p.getInventory().addItem(ru.example.vkchatmobs.listeners.MobListener.getArtifactShard());
            
            p.sendTitle(org.bukkit.ChatColor.GOLD + "🌟 ОБУЧЕНИЕ ЗАВЕРШЕНО 🌟", org.bukkit.ChatColor.GREEN + "Вы получили Древние Жетоны и +1000 реп. ВК!", 10, 100, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.8f);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null) return;
        String type = e.getCurrentItem().getType().name();
        
        int stage = getStage(p);
        if (stage == 1 && type.equals("CRAFTING_TABLE")) completeQuest(p, "Скрафтить Деревянную Кирку");
        else if (stage == 2 && type.equals("WOODEN_PICKAXE")) completeQuest(p, "Добыть 10 Камня");
        else if (stage == 4 && type.equals("STONE_SWORD")) completeQuest(p, "Убить Зомби");
        else if (stage == 7 && type.equals("TORCH")) completeQuest(p, "Добыть 3 Железной Руды");
        else if (stage == 11 && type.equals("SHIELD")) completeQuest(p, "Добыть Золотую Руду");
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            int stage = getStage(p);
            String type = e.getEntity().getType().name();
            
            if (stage == 5 && type.equals("ZOMBIE")) completeQuest(p, "Добыть Уголь");
            else if (stage == 10 && type.equals("SKELETON")) completeQuest(p, "Скрафтить Щит");
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        int stage = getStage(p);
        String type = e.getItem().getItemStack().getType().name();
        
        if (stage == 9 && type.equals("IRON_INGOT")) completeQuest(p, "Убить Скелета");
        else if (stage == 13 && type.equals("GOLD_INGOT")) completeQuest(p, "Найти и добыть Алмаз");
    }
}
