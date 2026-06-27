package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestManager implements Listener {
    private final VKChatEventsPlugin plugin;
    // UUID -> (QuestKey -> Progress)
    private final Map<UUID, Map<String, Integer>> playerProgress = new HashMap<>();

    public QuestManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Integer> getPlayerQuestProgress(UUID uuid) {
        return playerProgress.getOrDefault(uuid, java.util.Collections.emptyMap());
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            checkQuestProgress(p, e.getEntityType().name(), 1);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        
        ItemStack recipeResult = e.getRecipe().getResult();
        if (recipeResult == null || recipeResult.getType().isAir()) return;

        int amount = recipeResult.getAmount();

        if (e.isShiftClick()) {
            int maxCraftable = getMaxCraftable(e);
            int fits = getFits(p, recipeResult);
            int actualCrafted = Math.min(maxCraftable, fits);
            if (actualCrafted > 0) {
                amount *= actualCrafted;
            }
        }

        checkQuestProgress(p, recipeResult.getType().name(), amount);
    }

    private int getMaxCraftable(CraftItemEvent e) {
        int max = Integer.MAX_VALUE;
        for (ItemStack item : e.getInventory().getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                max = Math.min(max, item.getAmount());
            }
        }
        return max == Integer.MAX_VALUE ? 1 : max;
    }

    private int getFits(Player p, ItemStack item) {
        int fits = 0;
        for (ItemStack invItem : p.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                fits += item.getType().getMaxStackSize();
            } else if (invItem.isSimilar(item)) {
                fits += (item.getType().getMaxStackSize() - invItem.getAmount());
            }
        }
        return fits / item.getAmount();
    }

    private void checkQuestProgress(Player p, String target, int amount) {
        ConfigurationSection chains = plugin.getConfig().getConfigurationSection("quests.chains");
        if (chains == null) return;

        for (String qKey : chains.getKeys(false)) {
            ConfigurationSection q = chains.getConfigurationSection(qKey);
            if (q.getString("target").equalsIgnoreCase(target)) {
                
                // Проверка профессии с защитой от NPE и ClassNotFound
                if (q.contains("req_job")) {
                    String reqJob = q.getString("req_job");
                    int reqLvl = q.getInt("req_lvl", 1);
                    
                    try {
                        org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
                        if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                            Object dm = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                            int pLvl = (int) dm.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dm, p.getUniqueId(), reqJob);
                            if (pLvl < reqLvl) continue; // Не дорос
                        } else {
                            continue; // Плагин джобсов не запущен
                        }
                    } catch (Throwable ex) {
                        ex.printStackTrace(); // Напечатаем ошибку для отладки
                        continue;
                    }
                }

                playerProgress.putIfAbsent(p.getUniqueId(), new HashMap<>());
                int current = playerProgress.get(p.getUniqueId()).getOrDefault(qKey, 0) + amount;
                int required = q.getInt("amount");
                
                if (current >= required) {
                    playerProgress.get(p.getUniqueId()).remove(qKey);
                    int rep = q.getInt("reward_rep");
                    
                    int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                    if (vkId != -1) {
                        VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
                        p.sendMessage(ChatColor.GREEN + " Вы завершили сюжетный квест: " + q.getString("desc") + "!");
                        p.sendMessage(ChatColor.YELLOW + "Награда: +" + rep + " ВК Репутации");
                    }
                } else {
                    playerProgress.get(p.getUniqueId()).put(qKey, current);
                }
            }
        }
    }
}
