package ru.example.vkchatnations.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchatnations.VKChatNationsPlugin;

public class TaxTask extends BukkitRunnable {
    private final VKChatNationsPlugin plugin;

    public TaxTask(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getNationManager().processDailyTaxes();
    }
}
