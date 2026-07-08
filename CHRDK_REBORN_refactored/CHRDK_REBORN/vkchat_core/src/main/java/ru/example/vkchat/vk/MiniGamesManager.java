package ru.example.vkchat.vk;

import ru.example.vkchat.VKChatPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;

public class MiniGamesManager {
    private final VKChatPlugin plugin;
    private final Random random = new Random();
    
    // Кулдауны на ответы в загадках
    private final Map<Integer, Long> riddleCooldowns = new ConcurrentHashMap<>();

    public MiniGamesManager(VKChatPlugin plugin) {
        this.plugin = plugin;
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