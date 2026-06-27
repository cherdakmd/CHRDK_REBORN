package ru.example.vkchatoffline.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatjobs.VKChatJobsPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.ActiveShift;
import ru.example.vkchatoffline.data.Expedition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление рабочими сменами.
 * Поддерживает шахту, лесопилку и ферму с различной добычей.
 */
public class ShiftManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, ActiveShift> activeShifts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ShiftManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        startTask();
    }

    /**
     * Проверка, работает ли игрок на смене.
     */
    public boolean isWorking(int vkId) {
        return activeShifts.containsKey(vkId);
    }

    /**
     * Получение времени окончания смены.
     */
    public long getEndTime(int vkId) {
        ActiveShift shift = activeShifts.get(vkId);
        return shift != null ? shift.getEndTime() : 0;
    }

    /**
     * Получение типа смены.
     */
    public String getShiftType(int vkId) {
        ActiveShift shift = activeShifts.get(vkId);
        return shift != null ? shift.getShiftType() : "";
    }

    /**
     * Обработка команды !смена.
     */
    public void handleCommand(int peerId, int vkId, UUID uuid, String[] args) {
        // Проверка на поход
        if (plugin.getAdventureCommandManager() != null && plugin.getAdventureCommandManager().isInExpedition(vkId)) {
            Expedition exp = plugin.getAdventureCommandManager().getExpedition(vkId);
            if (exp != null && exp.getEndTime() == 0) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                        "❌ Ты уже находишься в походе! Сначала дождись его завершения.");
                return;
            }
            long left = (exp.getEndTime() - System.currentTimeMillis()) / 1000L;
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                    " Твой персонаж восстанавливается в лазарете.\nОсталось: " + (left / 3600) + " ч.");
            return;
        }

        if (activeShifts.containsKey(vkId)) {
            ActiveShift shift = activeShifts.get(vkId);
            long left = (shift.getEndTime() - System.currentTimeMillis()) / 1000L;
            if (left > 0) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                        "⏳ Твой персонаж на смене (" + shift.getShiftType() + "). " +
                        "Осталось: " + (left / 3600) + " ч. " + ((left % 3600) / 60) + " мин.");
            } else {
                finishShift(vkId);
            }
            return;
        }

        if (args.length < 3) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                    "⛏️ Использование: !смена <шахта/лесопилка/ферма> <часы (1-12)>");
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                    "Доступные смены:\n" +
                    " • шахта - руды и драгоценные камни\n" +
                    " • лесопилка - древесина и ягоды\n" +
                    " • ферма - зерно и овощи");
            return;
        }

        String type = args[1].toLowerCase();
        if (!plugin.getConfig().contains("shifts." + type)) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                    "❌ Доступные смены: шахта, лесопилка, ферма.");
            return;
        }

        int hours;
        try {
            hours = Integer.parseInt(args[2]);
            if (hours < 1 || hours > 12) throw new NumberFormatException();
        } catch (Exception e) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                    "❌ Количество часов должно быть от 1 до 12.");
            return;
        }

        long endTime = System.currentTimeMillis() + (hours * 3600000L);
        activeShifts.put(vkId, new ActiveShift(peerId, vkId, uuid, type, hours, endTime));
        VKChatPlugin.getInstance().getVkManager().sendMessage(peerId, vkId,
                "✅ Твой персонаж отправился на смену (" + type + ") на " + hours + " часов.\n" +
                "Возвращайся позже и напиши !смена, чтобы забрать добычу!");
    }

    /**
     * Проверка и завершение истекших смен (вызывается таймером).
     */
    public void checkShifts() {
        long now = System.currentTimeMillis();
        List<Integer> toFinish = new ArrayList<>();
        for (ActiveShift shift : activeShifts.values()) {
            if (now >= shift.getEndTime()) {
                toFinish.add(shift.getVkId());
            }
        }
        for (int vkId : toFinish) {
            finishShift(vkId);
        }
    }

    /**
     * Завершение смены (для вызова извне).
     */
    public void finishShift(int vkId) {
        ActiveShift shift = activeShifts.remove(vkId);
        if (shift == null) return;

        String jobName = plugin.getConfig().getString("shifts." + shift.getShiftType() + ".job");
        int level = 1;
        try {
            VKChatJobsPlugin jobs = VKChatJobsPlugin.getInstance();
            if (jobs != null && jobs.isEnabled()) {
                level = jobs.getJobsDataManager().getLevel(shift.getPlayerUuid(), jobName);
            }
        } catch (Throwable ignored) {}

        List<ItemStack> rewards = new ArrayList<>();
        List<String> itemsCfg = plugin.getConfig().getStringList("shifts." + shift.getShiftType() + ".items");

        int totalRep = 0;

        // Добыча за каждый час
        for (int i = 0; i < shift.getHours(); i++) {
            // Базовая репутация за час
            totalRep += 5 + (level * 2);

            // Генерация предметов
            for (String itemStr : itemsCfg) {
                String[] p = itemStr.split(";");
                if (p.length >= 2) {
                    try {
                        Material m = Material.valueOf(p[0].toUpperCase());
                        int min = Integer.parseInt(p[1]);
                        int max = p.length > 2 ? Integer.parseInt(p[2]) : min;

                        // Множитель от уровня
                        double multiplier = 1.0 + (level * 0.05);
                        int actualMax = (int) (max * multiplier);

                        if (actualMax > min) {
                            int amount = random.nextInt(actualMax - min + 1) + min;
                            if (amount > 0) {
                                rewards.add(new ItemStack(m, amount));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Консолидация предметов
        Map<Material, Integer> consolidated = new HashMap<>();
        for (ItemStack item : rewards) {
            consolidated.put(item.getType(), consolidated.getOrDefault(item.getType(), 0) + item.getAmount());
        }

        List<ItemStack> finalRewards = new ArrayList<>();
        StringBuilder itemsList = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : consolidated.entrySet()) {
            finalRewards.add(new ItemStack(entry.getKey(), entry.getValue()));
            itemsList.append("   ").append(entry.getKey().name()).append(" x").append(entry.getValue()).append("\n");
        }

        // Добавление в тайник
        plugin.getStashManager().addItems(shift.getPlayerUuid(), finalRewards);

        // Выдача репутации
        VKChatPlugin.getInstance().getApi().addReputation(shift.getVkId(), totalRep);

        String msg = " ✅ Смена (" + shift.getShiftType() + ") завершена!\n\n" +
                " Репутация: +" + totalRep + "\n" +
                " Добытые ресурсы:\n" +
                (itemsList.length() > 0 ? itemsList.toString() : "   Ничего ценного не найдено.") +
                "\n\n✅ Все предметы отправлены в твой виртуальный тайник! Введи /stash в игре, чтобы забрать их.";

        VKChatPlugin.getInstance().getVkManager().sendMessage(shift.getPeerId(), shift.getVkId(), msg);

        String joinMsg = "§a[VKChat Offline] Смена (" + shift.getShiftType() + ") успешно завершена! Получено +" + totalRep + " репутации. Напиши §e/stash§a, чтобы забрать добычу.";
        plugin.getAdventureCommandManager().getExpeditionStorage().addNotification(shift.getPlayerUuid(), joinMsg);
    }

    /**
     * Запуск таймера проверки.
     */
    private void startTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkShifts();
        }, 1200L, 1200L); // 1 раз в минуту
    }
}
