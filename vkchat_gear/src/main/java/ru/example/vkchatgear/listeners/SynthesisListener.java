package ru.example.vkchatgear.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.ArrayList;
import java.util.List;

public class SynthesisListener implements Listener {
    private final VKChatGearPlugin plugin;

    public SynthesisListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemStack offhand = p.getInventory().getItemInOffHand();

        if (hand == null || !hand.hasItemMeta()) return;
        ItemMeta handMeta = hand.getItemMeta();
        
        String scrollName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("synthesis.scroll_name", "&d&l Свиток Синтеза"));
        
        if (hand.getType() == Material.PAPER && handMeta.hasDisplayName() && handMeta.getDisplayName().equals(scrollName)) {
            e.setCancelled(true);
            
            if (offhand == null || offhand.getType() == Material.AIR || !plugin.getGearManager().isGear(offhand.getType())) {
                p.sendMessage(ChatColor.RED + "Положите предмет-основу в левую руку!");
                return;
            }
            
            ItemStack sacrifice = null;
            int sacrificeSlot = -1;
            
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == offhand.getType() && !item.equals(offhand) && !item.equals(hand)) {
                    if (item.hasItemMeta() && item.getItemMeta().hasLore() && item.getItemMeta().getLore().toString().contains("Редкость:")) {
                        sacrifice = item;
                        sacrificeSlot = i;
                        break;
                    }
                }
            }
            
            if (sacrifice == null) {
                p.sendMessage(ChatColor.RED + "В вашем инвентаре нет подходящего предмета-жертвы (такого же типа)!");
                return;
            }

            // Начинаем синтез (переносим чары с жертвы на основу)
            ItemMeta offMeta = offhand.getItemMeta();
            ItemMeta sacMeta = sacrifice.getItemMeta();
            
            boolean changed = false;
            
            // Перенос ванильных чар (если уровень выше или чара нет)
            for (Enchantment enc : sacMeta.getEnchants().keySet()) {
                int sacLvl = sacMeta.getEnchantLevel(enc);
                int offLvl = offMeta.getEnchantLevel(enc);
                if (sacLvl > offLvl) {
                    offMeta.addEnchant(enc, sacLvl, true);
                    changed = true;
                }
            }
            
            // Перенос кастомных чар (поиск по лору)
            List<String> offLore = offMeta.hasLore() ? offMeta.getLore() : new ArrayList<>();
            List<String> sacLore = sacMeta.hasLore() ? sacMeta.getLore() : new ArrayList<>();
            
            for (String line : sacLore) {
                String pure = ChatColor.stripColor(line);
                if (pure.isEmpty() || pure.startsWith("Редкость:") || pure.startsWith("Создано") || pure.startsWith("Заточка:") || pure.startsWith("Убито") || pure.startsWith("Часть сета") || pure.startsWith("Испорчено") || pure.startsWith("Удача")) continue;
                
                // Проверяем, есть ли такой чар уже на основе
                boolean hasIt = false;
                for (String offLine : offLore) {
                    if (ChatColor.stripColor(offLine).equals(pure)) {
                        hasIt = true;
                        break;
                    }
                }
                
                if (!hasIt) {
                    // Простая вставка кастомного чара
                    offLore.add(line);
                    changed = true;
                }
            }
            
            if (changed) {
                offMeta.setLore(offLore);
                offhand.setItemMeta(offMeta);
                
                hand.setAmount(hand.getAmount() - 1);
                p.getInventory().setItem(sacrificeSlot, null); // Уничтожаем жертву
                
                p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 100, 0.5, 0.5, 0.5, 0.1);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Синтез прошел успешно! Предмет-основа впитал силу жертвы!");
            } else {
                p.sendMessage(ChatColor.YELLOW + "Оба предмета имеют одинаковые или лучшие чары. Синтез не требуется.");
            }
        }
    }
}
