package ru.example.vkchatannouncer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.Random;

public class QuizListener implements Listener {
    private final VKChatAnnouncerPlugin plugin;
    private final Random random = new Random();

    private static String activeQuestion = null;
    private static String activeAnswer = null;

    private static final String[][] QUESTIONS = {
        {"Сколько репутации стоит малый блок привата? (Назовите число)", "100"},
        {"Какая максимальная заточка предметов на сервере? (Назовите число)", "20"},
        {"Как теперь называется короткая нация Богатырей?", "Русь"},
        {"Какая руна спасает от смерти ценой разрушения брони?", "Второе Дыхание"},
        {"С какого уровня кузнеца открывается легендарное снаряжение? (Назовите число)", "25"},
        {"Какое прозвище носит первый космонавт, чей сет дает прыгучесть IV?", "Гагарин"},
        {"Какой командой открыть главное меню сервера?", "/menu"},
        {"Какая руна наносит двойной урон и иссушение при шансе 5%?", "Распад"},
        {"Какая руна позволяет затачивать редким кристаллом? (Назовите тир)", "Редкий"},
        {"Сколько прочности ежедневно тратит блок привата? (Назовите число)", "2"}
    };

    public QuizListener(VKChatAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }

    public static void askQuestion() {
        int index = new Random().nextInt(QUESTIONS.length);
        activeQuestion = QUESTIONS[index][0];
        activeAnswer = QUESTIONS[index][1];

        String prefix = ChatColor.GOLD + "✨ [ВИКТОРИНА] ";
        String text = ChatColor.YELLOW + "Внимание! Вопрос: " + ChatColor.WHITE + activeQuestion + 
                     "\n" + ChatColor.YELLOW + "Напишите правильный ответ первым в чат, чтобы получить " + ChatColor.GOLD + "+50 репутации ВК" + ChatColor.YELLOW + "!";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(" ");
            p.sendMessage(prefix + text);
            p.sendMessage(" ");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (activeAnswer == null) return;

        String msg = e.getMessage().trim();
        if (msg.equalsIgnoreCase(activeAnswer)) {
            // Игрок угадал!
            e.setCancelled(true);
            Player p = e.getPlayer();
            
            String answer = activeAnswer;
            activeQuestion = null;
            activeAnswer = null;

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, 50);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                String winMsg = ChatColor.GREEN + "🎉 Игрок " + ChatColor.YELLOW + p.getName() + 
                               ChatColor.GREEN + " первым ответил правильно! Ответ: " + ChatColor.GOLD + answer + 
                               ChatColor.GREEN + ". Он получает " + ChatColor.GOLD + "+50 репутации ВК" + ChatColor.GREEN + "!";
                
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(ChatColor.GOLD + "✨ [ВИКТОРИНА] " + winMsg);
                    online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                }
            });
        }
    }
}
