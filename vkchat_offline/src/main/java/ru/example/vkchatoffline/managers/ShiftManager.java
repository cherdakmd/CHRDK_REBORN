package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShiftManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, ActiveShift> activeShifts = new ConcurrentHashMap<>();

    public static class ActiveShift {
        public final int peerId, vkId;
        public final UUID playerUuid;
        public final String shiftType;
        public final int hours;
        public final long endTime;
        public ActiveShift(int peerId, int vkId, UUID playerUuid, String shiftType, int hours) {
            this.peerId = peerId; this.vkId = vkId; this.playerUuid = playerUuid;
            this.shiftType = shiftType; this.hours = hours;
            this.endTime = System.currentTimeMillis() + (hours * 3600000L);
        }
        public long getEndTime() { return endTime; }
        public String getShiftType() { return shiftType; }
        public int getVkId() { return vkId; }
        public int getPeerId() { return peerId; }
        public UUID getPlayerUuid() { return playerUuid; }
    }

    public ShiftManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        startTask();
    }

    public boolean isWorking(int vkId) { return activeShifts.containsKey(vkId); }

    public void handleCommand(int peerId, int vkId, UUID uuid, String[] args) {
        if (plugin.getAdventureManager() != null && plugin.getAdventureManager().isActiveAdventure(vkId)) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "❌ Ты уже в походе!");
            return;
        }
        if (activeShifts.containsKey(vkId)) {
            ActiveShift shift = activeShifts.get(vkId);
            long left = (shift.getEndTime() - System.currentTimeMillis()) / 1000;
            if (left > 0) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "⏳ На смене (" + shift.getShiftType() + "). Осталось: " + (left / 3600) + " ч.");
            } else {
                finishShift(vkId);
            }
            return;
        }
        if (args.length < 3) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "⛏ Использование: !смена <шахта/лесопилка/ферма> <часы (1-12)>");
            return;
        }
        String type = args[1].toLowerCase();
        int hours;
        try { hours = Integer.parseInt(args[2]); } catch (Exception e) { VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "❌ Неверное число часов!"); return; }
        if (hours < 1 || hours > 12) { VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "❌ От 1 до 12 часов!"); return; }

        activeShifts.put(vkId, new ActiveShift(peerId, vkId, uuid, type, hours));
        VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId, "⛏ Смена начата: " + type + " на " + hours + " ч.");
    }

    private void finishShift(int vkId) {
        ActiveShift shift = activeShifts.remove(vkId);
        if (shift == null) return;

        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();
        int baseAmount = shift.hours * 3;

        switch (shift.getShiftType()) {
            case "шахта":
                loot.add(new ItemStack(Material.IRON_ORE, baseAmount + rand.nextInt(baseAmount)));
                loot.add(new ItemStack(Material.GOLD_ORE, rand.nextInt(baseAmount / 2)));
                if (rand.nextInt(100) < 15) loot.add(new ItemStack(Material.DIAMOND, 1 + rand.nextInt(2)));
                break;
            case "лесопилка":
                loot.add(new ItemStack(Material.OAK_LOG, baseAmount * 2 + rand.nextInt(baseAmount)));
                loot.add(new ItemStack(Material.APPLE, rand.nextInt(baseAmount / 2)));
                break;
            case "ферма":
                loot.add(new ItemStack(Material.WHEAT, baseAmount * 2 + rand.nextInt(baseAmount)));
                loot.add(new ItemStack(Material.POTATO, baseAmount + rand.nextInt(baseAmount)));
                loot.add(new ItemStack(Material.CARROT, baseAmount + rand.nextInt(baseAmount)));
                break;
        }

        int rep = 5 + (shift.hours * 2);
        try {
            int uuid_int = shift.getVkId();
            VKChatPlugin.getInstance().getApi().addReputation(uuid_int, rep);
            VKChatPlugin.getInstance().getVkManager().sendMessage(shift.getPeerId(), shift.getVkId(), "⛏ Смена завершена! +" + rep + " реп. Забери добычу: /stash");
            UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(shift.getVkId());
            if (uuid != null) plugin.getStashManager().addItems(uuid, loot);
        } catch (Exception ignored) {}
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<Integer, ActiveShift> entry : new ArrayList<>(activeShifts.entrySet())) {
                if (now >= entry.getValue().getEndTime()) finishShift(entry.getKey());
            }
        }, 1200L, 1200L);
    }
}
