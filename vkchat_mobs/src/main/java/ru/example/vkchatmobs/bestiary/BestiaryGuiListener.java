package ru.example.vkchatmobs.bestiary;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.util.List;

public class BestiaryGuiListener implements Listener {

    private final VKChatMobsPlugin plugin;

    public BestiaryGuiListener(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith("§8▸ §a§lБЕСТИАРИЙ") && !title.contains("§8◂")) return;
        if (!title.contains("БЕСТИАРИЙ") && !title.contains("§a§l")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();
        BestiaryManager manager = plugin.getBestiaryManager();

        if (title.endsWith("§8◂ §7Энциклопедия")) {
            handleMainMenuClick(player, event.getSlot(), event.getCurrentItem());
        } else if (title.contains("§8◂") && !title.contains("Энциклопедия") && !title.contains("ОХОТА")) {
            handleDetailClick(player, title, event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("БЕСТИАРИЙ")) event.setCancelled(true);
    }

    private void handleMainMenuClick(Player player, int slot, org.bukkit.inventory.ItemStack current) {
        if (slot == 50) {
            player.performCommand("mobs");
            return;
        }
        if (slot == 49) return;
        if (slot >= 45) return;
        if (slot >= 0 && slot < BestiaryGUI.getTrackedMobs().size()) {
            EntityType type = BestiaryGUI.getTrackedMobs().get(slot);
            String key = type.name();
            BestiaryManager manager = plugin.getBestiaryManager();
            List<Integer> avail = manager.getAvailableMilestones(player, key);
            if (!avail.isEmpty()) {
                for (int t : avail) {
                    manager.claimMilestone(player, key, t);
                }
                player.sendMessage("§a✓ Награды за '" + BestiaryGUI.formatTypeName(type) + "' получены!");
            }
            new BestiaryGUI(plugin, player).openDetail(type);
        }
    }

    private void handleDetailClick(Player player, String title, int slot) {
        if (slot == 15) {
            new BestiaryGUI(plugin, player).openMainMenu();
            return;
        }
        if (slot == 11) {
            String mobName = title.replace("§8▸ §a§l", "").replace(" §8◂", "").trim();
            EntityType type = findEntityType(mobName);
            if (type != null) {
                String key = type.name();
                BestiaryManager manager = plugin.getBestiaryManager();
                List<Integer> avail = manager.getAvailableMilestones(player, key);
                int claimed = 0;
                for (int t : avail) {
                    if (manager.claimMilestone(player, key, t)) claimed++;
                }
                if (claimed > 0) {
                    player.sendMessage("§a✓ Получено наград: §e" + claimed);
                }
                new BestiaryGUI(plugin, player).openDetail(type);
            }
        }
    }

    private EntityType findEntityType(String displayName) {
        for (EntityType t : BestiaryGUI.getTrackedMobs()) {
            if (BestiaryGUI.formatTypeName(t).equalsIgnoreCase(displayName)) return t;
        }
        return null;
    }
}
