package ru.example.vkchatmarket.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerGuiState {
    private final Map<UUID, GuiState> states = new ConcurrentHashMap<>();

    public void set(UUID uuid, String categoryKey, int page, String searchQuery) {
        states.put(uuid, new GuiState(categoryKey, page, searchQuery));
    }

    public GuiState get(UUID uuid) {
        return states.getOrDefault(uuid, GuiState.DEFAULT);
    }

    public void clear(UUID uuid) {
        states.remove(uuid);
    }

    public record GuiState(String categoryKey, int page, String searchQuery) {
        public static final GuiState DEFAULT = new GuiState("all", 0, null);
        public boolean isSearch() { return searchQuery != null; }
    }
}
