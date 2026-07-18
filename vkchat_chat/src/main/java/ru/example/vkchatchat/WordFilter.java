package ru.example.vkchatchat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordFilter {
    private final VKChatChatPlugin plugin;
    private List<String> forbiddenWords = new ArrayList<>();
    private List<Pattern> compiledPatterns = new ArrayList<>();
    private String mode = "replace";
    private int muteDuration = 60;
    private boolean warnPlayer = true;
    private boolean enabled = false;
    private final Set<UUID> tempMutedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> muteExpiry = new ConcurrentHashMap<>();

    private static final Map<Character, String> CHAR_SUBSTITUTIONS = new HashMap<>();
    static {
        CHAR_SUBSTITUTIONS.put('а', "[аa@4]");
        CHAR_SUBSTITUTIONS.put('a', "[аa@4]");
        CHAR_SUBSTITUTIONS.put('о', "[оo0]");
        CHAR_SUBSTITUTIONS.put('o', "[оo0]");
        CHAR_SUBSTITUTIONS.put('и', "[иi1]");
        CHAR_SUBSTITUTIONS.put('i', "[иi1]");
        CHAR_SUBSTITUTIONS.put('е', "[еe3]");
        CHAR_SUBSTITUTIONS.put('e', "[еe3]");
        CHAR_SUBSTITUTIONS.put('с', "[сc$]");
        CHAR_SUBSTITUTIONS.put('c', "[сc$]");
        CHAR_SUBSTITUTIONS.put('с', "[сc]");
        CHAR_SUBSTITUTIONS.put('s', "[сs$]");
        CHAR_SUBSTITUTIONS.put('у', "[уu]");
        CHAR_SUBSTITUTIONS.put('u', "[уu]");
        CHAR_SUBSTITUTIONS.put('к', "[кk]");
        CHAR_SUBSTITUTIONS.put('k', "[кk]");
        CHAR_SUBSTITUTIONS.put('х', "[хx]");
        CHAR_SUBSTITUTIONS.put('x', "[хx]");
        CHAR_SUBSTITUTIONS.put('н', "[нh]");
        CHAR_SUBSTITUTIONS.put('h', "[нh]");
        CHAR_SUBSTITUTIONS.put('т', "[тt]");
        CHAR_SUBSTITUTIONS.put('t', "[тt]");
        CHAR_SUBSTITUTIONS.put('р', "[рp]");
        CHAR_SUBSTITUTIONS.put('p', "[рp]");
        CHAR_SUBSTITUTIONS.put('п', "[пn]");
        CHAR_SUBSTITUTIONS.put('n', "[пn]");
        CHAR_SUBSTITUTIONS.put('л', "[лl]");
        CHAR_SUBSTITUTIONS.put('l', "[лl]");
        CHAR_SUBSTITUTIONS.put('ь', "[ьb]");
        CHAR_SUBSTITUTIONS.put('b', "[ьb]");
        CHAR_SUBSTITUTIONS.put('ы', "[ыy]");
        CHAR_SUBSTITUTIONS.put('y', "[ыy]");
        CHAR_SUBSTITUTIONS.put('з', "[з3]");
        CHAR_SUBSTITUTIONS.put('3', "[з3]");
        CHAR_SUBSTITUTIONS.put('ё', "[ёq]");
        CHAR_SUBSTITUTIONS.put('q', "[ёq]");
        CHAR_SUBSTITUTIONS.put('ж', "[жj]");
        CHAR_SUBSTITUTIONS.put('j', "[жj]");
        CHAR_SUBSTITUTIONS.put('ш', "[шw]");
        CHAR_SUBSTITUTIONS.put('w', "[шw]");
        CHAR_SUBSTITUTIONS.put('ф', "[фf]");
        CHAR_SUBSTITUTIONS.put('f', "[фf]");
        CHAR_SUBSTITUTIONS.put('д', "[дd]");
        CHAR_SUBSTITUTIONS.put('d', "[дd]");
    }

    public WordFilter(VKChatChatPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        enabled = config.getBoolean("filter.enabled", false);
        mode = config.getString("filter.mode", "replace").toLowerCase();
        muteDuration = config.getInt("filter.mute-duration-seconds", 60);
        warnPlayer = config.getBoolean("filter.warn-player", true);

        forbiddenWords = config.getStringList("filter.words");
        if (forbiddenWords.isEmpty()) {
            forbiddenWords = new ArrayList<>();
        }

        List<String> patternStrings = config.getStringList("filter.patterns");
        compiledPatterns = new ArrayList<>();
        for (String pat : patternStrings) {
            try {
                compiledPatterns.add(Pattern.compile(pat, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid filter pattern: " + pat + " — " + e.getMessage());
            }
        }
    }

    public FilterResult check(String message) {
        if (!enabled) return null;

        String normalized = message.toLowerCase();

        for (String word : forbiddenWords) {
            if (word == null || word.isEmpty()) continue;
            Pattern pattern = buildWordPattern(word.toLowerCase());
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) {
                return new FilterResult(true, word, matcher.start(), matcher.end());
            }
        }

        for (Pattern pat : compiledPatterns) {
            Matcher matcher = pat.matcher(message);
            if (matcher.find()) {
                return new FilterResult(true, matcher.group(), matcher.start(), matcher.end());
            }
        }

        return null;
    }

    public String applyFilter(String message) {
        if (!enabled) return message;

        String result = message;

        for (String word : forbiddenWords) {
            if (word == null || word.isEmpty()) continue;
            Pattern pattern = buildWordPattern(word.toLowerCase());
            Matcher matcher = pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String matched = matcher.group();
                StringBuilder replacement = new StringBuilder();
                for (int i = 0; i < matched.length(); i++) {
                    replacement.append('*');
                }
                matcher.appendReplacement(sb, replacement.toString());
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        for (Pattern pat : compiledPatterns) {
            Matcher matcher = pat.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String matched = matcher.group();
                StringBuilder replacement = new StringBuilder();
                for (int i = 0; i < matched.length(); i++) {
                    replacement.append('*');
                }
                matcher.appendReplacement(sb, replacement.toString());
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    private Pattern buildWordPattern(String word) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?<![\\p{L}\\p{N}])");

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            String substitution = CHAR_SUBSTITUTIONS.get(c);
            if (substitution != null) {
                regex.append(substitution);
            } else {
                regex.append("[");
                regex.append(Pattern.quote(String.valueOf(c)));
                regex.append("]");
            }

            regex.append("[\\s\\-_.!,;:?*'\"+={}\\[\\]\\\\/]*");
        }

        regex.append("(?![\\p{L}\\p{N}])");

        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public String getMode() { return mode; }
    public int getMuteDuration() { return muteDuration; }
    public boolean isWarnPlayer() { return warnPlayer; }
    public boolean isEnabled() { return enabled; }
    public List<String> getForbiddenWords() { return Collections.unmodifiableList(forbiddenWords); }

    public void addWord(String word) {
        if (word == null || word.trim().isEmpty()) return;
        String trimmed = word.trim().toLowerCase();
        if (!forbiddenWords.contains(trimmed)) {
            forbiddenWords.add(trimmed);
            saveWordList();
        }
    }

    public boolean removeWord(String word) {
        if (word == null) return false;
        boolean removed = forbiddenWords.remove(word.trim().toLowerCase());
        if (removed) saveWordList();
        return removed;
    }

    private void saveWordList() {
        FileConfiguration config = plugin.getConfig();
        config.set("filter.words", forbiddenWords);
        plugin.saveConfig();
    }

    public void tempMute(UUID uuid) {
        tempMutedPlayers.add(uuid);
        muteExpiry.put(uuid, System.currentTimeMillis() + muteDuration * 1000L);
    }

    public boolean isTempMuted(UUID uuid) {
        if (!tempMutedPlayers.contains(uuid)) return false;
        Long expiry = muteExpiry.get(uuid);
        if (expiry != null && System.currentTimeMillis() > expiry) {
            tempMutedPlayers.remove(uuid);
            muteExpiry.remove(uuid);
            return false;
        }
        return true;
    }

    public void removeTempMute(UUID uuid) {
        tempMutedPlayers.remove(uuid);
        muteExpiry.remove(uuid);
    }

    public static class FilterResult {
        public final boolean filtered;
        public final String matchedWord;
        public final int start;
        public final int end;

        public FilterResult(boolean filtered, String matchedWord, int start, int end) {
            this.filtered = filtered;
            this.matchedWord = matchedWord;
            this.start = start;
            this.end = end;
        }
    }
}
