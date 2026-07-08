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

    private static volatile String activeQuestion = null;
    private static volatile String activeAnswer = null;

    private static final String[][] QUESTIONS = {
        {"Какая команда открывает главное меню сервера?", "/menu"},
        {"Какой командой привязать ВК аккаунт?", "/vklink"},
        {"Какая максимальная заточка предметов на сервере? (Назовите число)", "25"},
        {"Сколько стоит обычный артефакт в магазине? (Назовите число)", "3750"},
        {"Какая команда для покупки блока привата?", "/nation buyclaim"},
        {"Какой командой открыть рынок?", "/market"},
        {"Какой командой открыть кузню?", "/forge"},
        {"Сколько проходка даёт доступ без ВК? (Сумма в рублях, только число)", "500"},
        {"Какая команда показывает баланс репутации?", "/rep"},
        {"Сколько стоит мифическая реликвия в магазине? (Назовите число)", "11250"}
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
