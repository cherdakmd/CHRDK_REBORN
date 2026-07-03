package ru.example.vkchatstreams;

public class StreamEvent {
    private final String channel;
    private final String title;
    private final String url;
    private final boolean live;
    private final int viewerCount;
    private final String game;

    public StreamEvent(String channel, String title, String url, boolean live, int viewerCount, String game) {
        this.channel = channel;
        this.title = title;
        this.url = url;
        this.live = live;
        this.viewerCount = viewerCount;
        this.game = game;
    }

    public StreamEvent(String channel, String title, String url, boolean live) {
        this(channel, title, url, live, 0, "");
    }

    public String getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public boolean isLive() { return live; }
    public int getViewerCount() { return viewerCount; }
    public String getGame() { return game; }
}
