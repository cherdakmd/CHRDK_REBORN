package ru.example.vkchat.vk;

import ru.example.vkchat.VKChatPlugin;
import java.util.Random;
import java.util.List;
import java.util.Arrays;

public class GamesManager {
    private final VKChatPlugin plugin;
    private int currentSafeCode;
    private int safeReward;
    private Random random = new Random();
    private List<String> jokes = Arrays.asList(
        "Почему крипер не ходит на вечеринки? Потому что он сразу взрывается!",
        "Что сказал Эндермен Стиву? Ничего, он просто украл твой блок и ушел.",
        "Как скелеты называют друг друга? Бро!",
        "Какой любимый жанр музыки у Гаста? Рок... Бедрок!",
        "Почему зомби не сражаются друг с другом? У них нет кишок!",
        "Почему Стив никогда не болеет? Потому что у него всегда полное здоровье после еды!",
        "Почему жители такие богатые? Потому что они продают грязь за изумруды!"
    );

    public GamesManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        generateNewSafe();
    }

    public void generateNewSafe() {
        currentSafeCode = random.nextInt(900) + 100; // 100 to 999
        safeReward = random.nextInt(50) + 50; // 50 to 100 репутации
    }

    public void trySafe(int vkId, int code, int peer) {
        if (code == currentSafeCode) {
            plugin.getReputationManager().addPoints(vkId, safeReward);
            plugin.getVkManager().sendMessage(peer, " СЕЙФ ВЗЛОМАН!\nИгрок @id" + vkId + " подобрал верный код [" + currentSafeCode + "] и забрал " + safeReward + " очков репутации!");
            generateNewSafe();
        } else if (code > currentSafeCode) {
            plugin.getVkManager().sendMessage(peer, " Неверно! Код меньше, чем " + code);
        } else {
            plugin.getVkManager().sendMessage(peer, " Неверно! Код больше, чем " + code);
        }
    }

    public int getLastSafeReward() {
        return safeReward;
    }

    public String getRandomJoke() {
        return jokes.get(random.nextInt(jokes.size()));
    }
}