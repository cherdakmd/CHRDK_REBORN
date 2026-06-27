package ru.example.vkchat.vk;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.example.vkchat.VKChatPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RiddleManager {
    private final VKChatPlugin plugin;
    private File riddlesFile;
    private FileConfiguration riddlesConfig;
    private Map<String, String> riddles = new HashMap<>();
    private volatile String currentAnswer = null;
    private volatile boolean active = false;
    private Random random = new Random();

    public RiddleManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        
        riddlesFile = new File(plugin.getDataFolder(), "riddles.yml");
        if (!riddlesFile.exists()) {
            plugin.saveResource("riddles.yml", false);
        }
        riddlesConfig = YamlConfiguration.loadConfiguration(riddlesFile);
        
        if (riddlesConfig.contains("riddles")) {
            for (String key : riddlesConfig.getConfigurationSection("riddles").getKeys(false)) {
                String question = riddlesConfig.getString("riddles." + key + ".question");
                String answer = riddlesConfig.getString("riddles." + key + ".answer");
                if (question != null && answer != null) {
                    riddles.put(question, answer);
                }
            }
        }

        if (plugin.getConfig().getBoolean("riddles.enabled", true)) {
            int interval = plugin.getConfig().getInt("riddles.interval", 900) * 20; // по умолчанию 15 минут
            new BukkitRunnable() {
                @Override
                public void run() {
                    askRiddle();
                }
            }.runTaskTimerAsynchronously(plugin, interval, interval);
        }
    }

    public void askRiddle() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return; // Не задаем загадки, если на сервере никого нет
        
        boolean isMath = random.nextBoolean(); // 50/50 шанс

        if (isMath) {
            // Математика: Сложение, вычитание, умножение до 100
            int a = random.nextInt(90) + 10; // 10..99
            int b = random.nextInt(90) + 10;
            int op = random.nextInt(3);
            
            String sign = "+";
            int result = 0;
            if (op == 0) {
                sign = "+";
                result = a + b;
            } else if (op == 1) {
                // Чтобы не было отрицательных чисел, сделаем a больше b
                if (a < b) { int temp = a; a = b; b = temp; }
                sign = "-";
                result = a - b;
            } else {
                a = random.nextInt(12) + 2; // для умножения поменьше числа
                b = random.nextInt(12) + 2;
                sign = "*";
                result = a * b;
            }
            
            currentAnswer = String.valueOf(result);
            active = true;
            plugin.getVkManager().sendToMainChat(" Математика!\n\nСколько будет: " + a + " " + sign + " " + b + " ?\n\nПервый ответивший получит небольшую награду!");
            
        } else {
            // Классические загадки
            if (riddles.isEmpty()) return;
            List<String> keys = new ArrayList<>(riddles.keySet());
            String question = keys.get(random.nextInt(keys.size()));
            currentAnswer = riddles.get(question);
            active = true;
            
            plugin.getVkManager().sendToMainChat(" Время загадки!\n\n" + question + "\n\nПервый, кто напишет правильный ответ, получит очки репутации!");
        }
    }

    public boolean checkAnswer(int vkId, String text) {
        if (!active || currentAnswer == null) return false;
        
        if (text.toLowerCase().contains(currentAnswer.toLowerCase())) {
            active = false; // Загадка разгадана
            int reward = plugin.getConfig().getInt("riddles.reward", 5);
            
            plugin.getReputationManager().addPoints(vkId, reward);
            String vkMsg = " Правильно! Пользователь получает " + reward + " очков репутации. Ответ был: " + currentAnswer;
            
            plugin.getVkManager().sendToMainChat(org.bukkit.ChatColor.stripColor(vkMsg));
            return true;
        }
        return false;
    }
}