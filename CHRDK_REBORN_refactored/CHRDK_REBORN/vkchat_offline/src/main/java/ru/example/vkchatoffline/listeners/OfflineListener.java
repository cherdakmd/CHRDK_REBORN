package ru.example.vkchatoffline.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.managers.Keyboards;
import ru.example.vkchatoffline.managers.ShiftManager.ShiftData;

import java.util.List;
import java.util.UUID;

public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;

    public OfflineListener(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.isCancelled()) return;
        String cmd = e.getCommand();
        int vkId = e.getSenderVkId();
        String[] args = e.getArgs();

        if (!cmd.equals("!shift") && !cmd.equals("!шахта") && !cmd.equals("!смена")) return;
        e.setCancelled(true);

        if (args.length <= 1 || args[1].equals("menu")) {
            showMenu(vkId);
            return;
        }

        switch (args[1]) {
            case "start":
                if (args.length > 2) startShift(vkId, args[2]);
                else showMenu(vkId);
                break;
            case "status":
                showStatus(vkId);
                break;
            case "cancel":
                confirmCancel(vkId);
                break;
            case "cancel_confirm":
                cancelShift(vkId);
                break;
            case "claim":
                claimRewards(vkId);
                break;
            default:
                showMenu(vkId);
        }
    }

    private void showMenu(int vkId) {
        if (plugin.getShiftManager().hasActiveShift(vkId)) {
            showStatus(vkId);
            return;
        }
        if (plugin.getShiftManager().hasCompletedShift(vkId)) {
            sendWithKb(vkId, "⛏ У вас есть завершённая смена! Заберите награды.", Keyboards.shiftDone());
            return;
        }
        sendWithKb(vkId, plugin.getShiftManager().getShiftsInfo() + "\nВыбери смену кнопкой:", Keyboards.shiftMenu());
    }

    private void startShift(int vkId, String key) {
        if (plugin.getShiftManager().hasActiveShift(vkId)) {
            sendWithKb(vkId, "❌ У вас уже активная смена! " + plugin.getShiftManager().getShiftStatus(vkId),
                    Keyboards.shiftActive());
            return;
        }
        if (plugin.getShiftManager().hasCompletedShift(vkId)) {
            sendWithKb(vkId, "❌ Сначала заберите награды за прошлую смену!", Keyboards.shiftDone());
            return;
        }
        if (plugin.getShiftManager().startShift(vkId, key)) {
            String name = plugin.getShiftManager().getShiftName(key);
            ShiftData sd = plugin.getShiftManager().getShift(vkId);
            long hrs = (sd.endTime - sd.startTime) / 3600000;
            sendWithKb(vkId, "⛏ Смена '" + name + "' начата!\n⏳ Длительность: " + hrs + " ч\n🔔 Бот уведомит когда завершится.",
                    Keyboards.shiftActive());
        } else {
            sendWithKb(vkId, "❌ Не удалось начать смену.", Keyboards.shiftMenu());
        }
    }

    private void showStatus(int vkId) {
        String status = plugin.getShiftManager().getShiftStatus(vkId);
        ShiftData sd = plugin.getShiftManager().getShift(vkId);
        if (sd != null && sd.completed && !sd.claimed) {
            sendWithKb(vkId, status, Keyboards.shiftDone());
        } else if (sd != null && !sd.completed) {
            sendWithKb(vkId, status, Keyboards.shiftActive());
        } else {
            sendWithKb(vkId, status, Keyboards.shiftMenu());
        }
    }

    private void confirmCancel(int vkId) {
        if (!plugin.getShiftManager().hasActiveShift(vkId)) {
            sendWithKb(vkId, "❌ Нет активной смены для отмены.", Keyboards.shiftMenu());
            return;
        }
        sendWithKb(vkId, "❌ Точно отменить смену? Награды не будут получены.", Keyboards.cancelConfirm());
    }

    private void cancelShift(int vkId) {
        if (plugin.getShiftManager().cancelShift(vkId)) {
            sendWithKb(vkId, "❌ Смена отменена.", Keyboards.shiftMenu());
        } else {
            sendWithKb(vkId, "❌ Не удалось отменить смену.", Keyboards.shiftActive());
        }
    }

    private void claimRewards(int vkId) {
        List<ItemStack> items = plugin.getShiftManager().claimRewards(vkId);
        if (items.isEmpty()) {
            sendWithKb(vkId, "❌ Нет завершённых смен для получения наград.", Keyboards.shiftMenu());
            return;
        }
        UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
        if (uuid != null) {
            plugin.getStashManager().addItems(uuid, items);
        }
        sendWithKb(vkId, "🎁 Ресурсы отправлены в /stash! Открой в игре.", Keyboards.shiftMenu());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(e.getPlayer());
            if (vkId != -1 && !plugin.getStashManager().isEmpty(e.getPlayer().getUniqueId())) {
                e.getPlayer().sendMessage("§a⛏ У вас есть награды в тайнике! /stash");
            }
        } catch (Exception ignored) {}
    }

    private void sendWithKb(int vkId, String text, String kb) {
        try { VKChatPlugin.getInstance().getApi().sendKeyboard(vkId, text, kb); } catch (Exception ignored) {}
    }
}
