package ru.example.vkchatstreams;

public class StreamEvent {
    private final String channel;
    private final String title;
    private final String url;
    private final boolean live;
    private final int viewerCount;
    private final String game;
    private final long startTime;

    public StreamEvent(String channel, String title, String url, boolean live, int viewerCount, String game, long startTime) {
        this.channel = channel;
        this.title = title;
        this.url = url;
        this.live = live;
        this.viewerCount = viewerCount;
        this.game = game;
        this.startTime = startTime;
    }

    public StreamEvent(String channel, String title, String url, boolean live, int viewerCount, String game) {
        this(channel, title, url, live, viewerCount, game, System.currentTimeMillis());
    }

    public StreamEvent(String channel, String title, String url, boolean live) {
        this(channel, title, url, live, 0, "", System.currentTimeMillis());
    }

    public String getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public boolean isLive() { return live; }
    public int getViewerCount() { return viewerCount; }
    public String getGame() { return game; }
    public long getStartTime() { return startTime; }

    public String getUptime() {
        return formatUptime((System.currentTimeMillis() - startTime) / 1000);
    }

    public static String formatUptime(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        if (totalSeconds < 60) return totalSeconds + "с";
        if (totalSeconds < 3600) return (totalSeconds / 60) + "м " + (totalSeconds % 60) + "с";
        return (totalSeconds / 3600) + "ч " + ((totalSeconds % 3600) / 60) + "м";
    }
}
