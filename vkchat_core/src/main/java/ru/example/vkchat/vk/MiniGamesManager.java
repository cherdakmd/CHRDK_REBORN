package ru.example.vkchat.vk;

import ru.example.vkchat.VKChatPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;

public class MiniGamesManager {
    private final VKChatPlugin plugin;
    private final Random random = new Random();
    
    // Рулетка
    private long lastRouletteTime = 0;
    
    // Кулдауны на ответы в загадках
    private final Map<Integer, Long> riddleCooldowns = new ConcurrentHashMap<>();

    public MiniGamesManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // --- РУССКАЯ РУЛЕТКА ---
    public void playRoulette(int vkId, int peer) {
        int cooldown = plugin.getConfig().getInt("riddles.roulette-cooldown", 300);
        long now = System.currentTimeMillis();
        
        if (now - lastRouletteTime < cooldown * 1000L) {
            long left = (cooldown * 1000L - (now - lastRouletteTime)) / 1000L;
            plugin.getVkManager().sendMessage(peer, "⏳ Барабан револьвера еще остывает... Подожди " + left + " сек.");
            return;
        }

        lastRouletteTime = now;
        int chance = random.nextInt(6); // 1 из 6 патронов

        int currentRep = plugin.getReputationManager().getPoints(vkId);

        if (chance == 0) {
            // Выстрел! Игрок проиграл 10% репутации
            int lost = (int) Math.ceil(currentRep * 0.10); // 10%
            if (lost < 5) lost = 5; // Минимум 5
            lost = Math.min(currentRep, lost);

            plugin.getReputationManager().deductPoints(vkId, lost);
            plugin.getVkManager().sendMessage(peer, "💥 БАМ! Тебе не повезло...\nТы получаешь виртуальное пулевое ранение и теряешь 10% своей репутации (-" + lost + " реп.)! 🩸\nТвой баланс: " + plugin.getReputationManager().getPoints(vkId));
        } else {
            // Выжил - получает 5% репутации
            int reward = (int) Math.ceil(currentRep * 0.05); // 5%
            if (reward < 10) reward = 10; // Минимум 10

            plugin.getReputationManager().addPoints(vkId, reward);
            plugin.getVkManager().sendMessage(peer, "🔫 Щелк... Пусто!\nТебе крупно повезло! За свою смелость ты получаешь 5% бонусной репутации (+" + reward + " реп.)! 🎉\nТвой баланс: " + plugin.getReputationManager().getPoints(vkId));
        }
    }

    // --- КУЛДАУН НА ЗАГАДКИ ---
    public boolean checkRiddleCooldown(int vkId) {
        int cooldown = plugin.getConfig().getInt("riddles.riddles-cooldown", 60);
        long now = System.currentTimeMillis();
        
        if (riddleCooldowns.containsKey(vkId)) {
            long lastAnswer = riddleCooldowns.get(vkId);
            if (now - lastAnswer < cooldown * 1000L) {
                return false; // Кулдаун еще идет
            }
        }
        
        riddleCooldowns.put(vkId, now);
        return true;
    }
}