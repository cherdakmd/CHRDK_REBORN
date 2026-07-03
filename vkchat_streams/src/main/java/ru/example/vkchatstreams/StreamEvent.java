package ru.example.vkchatstreams;

public class StreamEvent {
    private final String platform;
    private final String channel;
    private final String title;
    private final String url;
    private final boolean live;

    public StreamEvent(String platform, String channel, String title, String url, boolean live) {
        this.platform = platform;
        this.channel = channel;
        this.title = title;
        this.url = url;
        this.live = live;
    }

    public String getPlatform() { return platform; }
    public String getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public boolean isLive() { return live; }
}
