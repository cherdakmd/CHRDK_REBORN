package ru.example.vkchat.voting;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import ru.example.vkchat.VKChatPlugin;

import java.lang.reflect.Method;

public class VoteListener implements Listener {

    private final VKChatPlugin plugin;
    private Method getVoteMethod;
    private Method getUsernameMethod;
    private Method getServiceNameMethod;

    public VoteListener(VKChatPlugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> votifierEventClass = Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            getVoteMethod = votifierEventClass.getMethod("getVote");
            Class<?> voteClass = Class.forName("com.vexsoftware.votifier.model.Vote");
            getUsernameMethod = voteClass.getMethod("getUsername");
            getServiceNameMethod = voteClass.getMethod("getServiceName");
        } catch (Exception e) {
            plugin.getLogger().warning("[Vote] Не удалось загрузить Votifier API: " + e.getMessage());
        }
    }

    @EventHandler
    public void onVote(Event event) {
        if (getVoteMethod == null) return;
        try {
            Class<?> votifierEventClass = Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            if (!votifierEventClass.isInstance(event)) return;
            Object vote = getVoteMethod.invoke(event);
            if (vote == null) return;
            String username = (String) getUsernameMethod.invoke(vote);
            String serviceName = (String) getServiceNameMethod.invoke(vote);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(username);
                if (player != null && player.isOnline()) {
                    plugin.getVotingManager().onVote(player.getUniqueId(), serviceName);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[Vote] Ошибка обработки голоса: " + e.getMessage());
        }
    }
}
