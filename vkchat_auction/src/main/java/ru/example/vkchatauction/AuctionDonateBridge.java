package ru.example.vkchatauction;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatdonate.DonateManager;
import ru.example.vkchatdonate.VKChatDonatePlugin;

public class AuctionDonateBridge {

    private static boolean donateAvailable = false;
    private static VKChatDonatePlugin donatePlugin;

    public static void init() {
        Plugin p = Bukkit.getPluginManager().getPlugin("VKChatDonate");
        donateAvailable = p instanceof VKChatDonatePlugin && p.isEnabled();
        if (donateAvailable) {
            donatePlugin = (VKChatDonatePlugin) p;
        }
    }

    public static boolean isDonateAvailable() { return donateAvailable; }

    public static String getPlayerStatusId(Player player) {
        if (!donateAvailable || donatePlugin == null) return null;
        try {
            DonateManager dm = donatePlugin.getDonateManager();
            if (dm == null) return null;
            var status = dm.getPlayerStatus(player);
            return status != null ? status.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getPlayerStatusName(Player player) {
        if (!donateAvailable || donatePlugin == null) return null;
        try {
            DonateManager dm = donatePlugin.getDonateManager();
            if (dm == null) return null;
            var status = dm.getPlayerStatus(player);
            return status != null ? status.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static double getRepDiscount(Player player) {
        if (!donateAvailable || donatePlugin == null) return 0;
        try {
            return donatePlugin.getDonateManager().getRepDiscount(player);
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean hasPass(Player player) {
        return VKChatBridge.hasPass(player);
    }
}
