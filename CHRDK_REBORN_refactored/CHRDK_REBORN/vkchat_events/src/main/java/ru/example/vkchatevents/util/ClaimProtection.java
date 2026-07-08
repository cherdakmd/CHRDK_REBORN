package ru.example.vkchatevents.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Общая защита ивентов от приватных территорий.
 *
 * Сейчас напрямую поддерживается VKChatNations через reflection, чтобы модуль events
 * не получал жёсткую compile-зависимость от nations. Проверка идёт именно по
 * блочным приватам (getClaimAt), а не только по чанку — иначе большие/малые
 * приваты могли определяться неточно.
 */
public final class ClaimProtection {
    private ClaimProtection() {}

    public static boolean isLocationClaimed(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Plugin nationsPlugin = Bukkit.getPluginManager().getPlugin("VKChatNations");
        if (nationsPlugin != null && nationsPlugin.isEnabled()) {
            try {
                Object nationManager = nationsPlugin.getClass().getMethod("getNationManager").invoke(nationsPlugin);
                if (nationManager != null) {
                    try {
                        Object claim = nationManager.getClass().getMethod("getClaimAt", Location.class).invoke(nationManager, loc);
                        if (claim != null) return true;
                    } catch (NoSuchMethodException ignored) {
                        Object claim = nationManager.getClass().getMethod("getChunkClaim", org.bukkit.Chunk.class).invoke(nationManager, loc.getChunk());
                        if (claim != null) return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    public static boolean isAreaClaimed(Location center, int radius) {
        if (center == null || center.getWorld() == null) return false;
        int step = Math.max(4, Math.min(16, Math.max(1, radius)));
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (isLocationClaimed(center.clone().add(dx, 0, dz))) return true;
            }
        }
        return isLocationClaimed(center);
    }

    public static Location findSafeWildernessLocation(World world, int radius, int protectedRadius, int attempts) {
        if (world == null) return null;
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x, y + 1, z);
            if (!isAreaClaimed(loc, protectedRadius)) {
                return loc;
            }
        }
        return null;
    }
}
