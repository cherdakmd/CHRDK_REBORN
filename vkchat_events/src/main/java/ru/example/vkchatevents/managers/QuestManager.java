package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.util.JobsBridge;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class QuestManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
    private final File dataFile;

    public QuestManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "quest_progress.yml");
        loadProgress();
    }

    public Map<String, Integer> getPlayerQuestProgress(UUID uuid) {
        return playerProgress.getOrDefault(uuid, java.util.Collections.emptyMap());
    }

    // --- Persistence ---
    private void loadProgress() {
        if (!dataFile.exists()) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            for (String uuidStr : cfg.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection sec = cfg.getConfigurationSection(uuidStr);
                    if (sec == null) continue;
                    Map<String, Integer> quests = new HashMap<>();
                    for (String qKey : sec.getKeys(false)) {
                        quests.put(qKey, sec.getInt(qKey));
                    }
                    playerProgress.put(uuid, quests);
                } catch (IllegalArgumentException ignored) {}
            }
            plugin.getLogger().info("Загружен прогресс квестов для " + playerProgress.size() + " игроков.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка загрузки quest_progress.yml: " + e.getMessage());
        }
    }

    public void saveProgress() {
        try {
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Integer>> entry : playerProgress.entrySet()) {
                ConfigurationSection sec = cfg.createSection(entry.getKey().toString());
                for (Map.Entry<String, Integer> q : entry.getValue().entrySet()) {
                    sec.set(q.getKey(), q.getValue());
                }
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения quest_progress.yml: " + e.getMessage());
        }
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
            String qTarget = q.getString("target", "");
            if (qTarget.isEmpty() || !qTarget.equalsIgnoreCase(target)) continue;

            // Проверка профессии с защитой от NPE и ClassNotFound
            if (q.contains("req_job")) {
                    String reqJob = q.getString("req_job");
                    int reqLvl = q.getInt("req_lvl", 1);
                    
                    try {
                        org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
                        if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                            int pLvl = JobsBridge.getLevel(p, reqJob);
                            if (pLvl < reqLvl) continue; // Не дорос
                        } else {
                            continue; // Плагин джобсов не запущен
                        }
                    } catch (Throwable ex) {
                        ex.printStackTrace(); // Напечатаем ошибку для отладки
                        continue;
                    }
                }

                playerProgress.putIfAbsent(p.getUniqueId(), new ConcurrentHashMap<>());
                int current = playerProgress.get(p.getUniqueId()).getOrDefault(qKey, 0) + amount;
                int required = q.getInt("amount");
                
                if (current >= required) {
                    int rep = q.getInt("reward_rep");
                    
                    int vkId = VKChatBridge.getLinkedVkId(p);
                    if (vkId != -1) {
                        VKChatBridge.addPoints(vkId, rep);
                        playerProgress.get(p.getUniqueId()).remove(qKey);
                        saveProgress();
                        p.sendMessage(ChatColor.GREEN + " Вы завершили сюжетный квест: " + q.getString("desc") + "!");
                        p.sendMessage(ChatColor.YELLOW + "Награда: +" + rep + " ВК Репутации");
                    }
                } else {
                    playerProgress.get(p.getUniqueId()).put(qKey, current);
                    saveProgress();
                }
        }
    }
}
