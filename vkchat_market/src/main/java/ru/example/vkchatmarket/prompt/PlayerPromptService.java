package ru.example.vkchatmarket.prompt;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPromptService {
    private final Map<UUID, PromptState> prompts = new ConcurrentHashMap<>();

    public enum PromptType { NONE, SEARCH, CUSTOM_AMOUNT, ALERT_SET }

    public void startSearch(UUID uuid) {
        prompts.put(uuid, new PromptState(PromptType.SEARCH, "", ""));
    }

    public void startCustomAmount(UUID uuid, String itemId, String categoryKey) {
        prompts.put(uuid, new PromptState(PromptType.CUSTOM_AMOUNT, itemId, categoryKey));
    }

    public void startAlertSet(UUID uuid, String itemId) {
        prompts.put(uuid, new PromptState(PromptType.ALERT_SET, itemId, ""));
    }

    public PromptState get(UUID uuid) {
        return prompts.getOrDefault(uuid, PromptState.NONE);
    }

    public PromptType getType(UUID uuid) {
        return get(uuid).type();
    }

    public String getItemId(UUID uuid) {
        return get(uuid).data();
    }

    public String getCategory(UUID uuid) {
        return get(uuid).category();
    }

    public void clear(UUID uuid) {
        prompts.remove(uuid);
    }

    public record PromptState(PromptType type, String data, String category) {
        public static final PromptState NONE = new PromptState(PromptType.NONE, "", "");
    }
}
