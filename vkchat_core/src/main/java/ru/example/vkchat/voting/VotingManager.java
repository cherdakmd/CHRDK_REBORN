package ru.example.vkchat.voting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VotingManager {

    private final VKChatPlugin plugin;
    private boolean enabled;
    private int repReward;
    private final List<RewardEntry> rewards = new ArrayList<>();
    private int totalWeight = 0;

    private final Map<UUID, Integer> votesToday = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totalVotes = new ConcurrentHashMap<>();
    private long lastResetDay = 0;

    public VotingManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("voting.enabled", false);

        if (!enabled) return;

        repReward = config.getInt("voting.rep-reward", 500);
        rewards.clear();
        totalWeight = 0;

        List<?> items = config.getList("voting.items", new ArrayList<>());
        if (items == null) return;

        for (Object obj : items) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                Object materialObj = map.get("material");
                String materialName = materialObj instanceof String ? (String) materialObj : "DIAMOND";
                int chance = map.containsKey("chance") ? ((Number) map.get("chance")).intValue() : 20;
                int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;

                Material mat = Material.getMaterial(materialName.toUpperCase());
                if (mat == null) {
                    plugin.getLogger().warning("[Voting] Неизвестный материал: " + materialName);
                    continue;
                }
                rewards.add(new RewardEntry(mat, chance, amount));
                totalWeight += chance;
            }
        }
    }

    public void reload() {
        loadConfig();
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isVotifierInstalled() {
        return Bukkit.getPluginManager().getPlugin("Votifier") != null
                || Bukkit.getPluginManager().getPlugin("Votifier Legacy") != null;
    }

    public void onVote(UUID playerUuid, String serviceName) {
        if (!enabled) return;

        checkDayReset();

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;

        int vkId = plugin.getAuthManager().getLinkedVkId(player);
        if (vkId == -1) return;

        plugin.getReputationManager().addPoints(vkId, repReward);

        ItemStack itemReward = rollReward();
        if (itemReward != null) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemReward);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
        }

        votesToday.merge(playerUuid, 1, Integer::sum);
        totalVotes.merge(playerUuid, 1, Integer::sum);

        String broadcast = "§6§l★ §e" + player.getName() + " §6голосовал за сервер! §7(+" + repReward + " реп)";
        if (itemReward != null) {
            String itemName = itemReward.getItemMeta().hasDisplayName()
                    ? itemReward.getItemMeta().getDisplayName()
                    : formatMaterial(itemReward.getType());
            broadcast += " §7+ " + itemReward.getAmount() + "x " + itemName;
        }
        Bukkit.broadcastMessage(broadcast);
    }

    public ItemStack rollReward() {
        if (rewards.isEmpty()) return null;

        int roll = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (RewardEntry entry : rewards) {
            cumulative += entry.chance;
            if (roll < cumulative) {
                return new ItemStack(entry.material, entry.amount);
            }
        }
        return new ItemStack(rewards.get(0).material, rewards.get(0).amount);
    }

    public int getVotesToday(UUID uuid) {
        checkDayReset();
        return votesToday.getOrDefault(uuid, 0);
    }

    public int getTotalVotes(UUID uuid) {
        return totalVotes.getOrDefault(uuid, 0);
    }

    public String getNextRewardPreview() {
        if (rewards.isEmpty()) return "§7Нет доступных наград";
        StringBuilder sb = new StringBuilder();
        for (RewardEntry entry : rewards) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append("§e").append(entry.amount).append("x ").append(formatMaterial(entry.material));
            sb.append(" §7(").append(entry.chance).append("%)");
        }
        return sb.toString();
    }

    private void checkDayReset() {
        long today = System.currentTimeMillis() / 86400000L;
        if (today != lastResetDay) {
            votesToday.clear();
            lastResetDay = today;
        }
    }

    private String formatMaterial(Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    private static class RewardEntry {
        final Material material;
        final int chance;
        final int amount;

        RewardEntry(Material material, int chance, int amount) {
            this.material = material;
            this.chance = chance;
            this.amount = amount;
        }
    }
}
