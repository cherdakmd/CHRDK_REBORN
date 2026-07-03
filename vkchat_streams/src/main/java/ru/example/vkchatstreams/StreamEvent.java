package ru.example.vkchatstreams;

public class StreamEvent {
    private final String platform;
    private final String channel;
    private final String title;
    private final String url;
    private final boolean live;
    private final String vkUrl;
    private final String youtubeUrl;
    private final String twitchUrl;

    public StreamEvent(String platform, String channel, String title, String url, boolean live,
                       String vkUrl, String youtubeUrl, String twitchUrl) {
        this.platform = platform;
        this.channel = channel;
        this.title = title;
        this.url = url;
        this.live = live;
        this.vkUrl = vkUrl;
        this.youtubeUrl = youtubeUrl;
        this.twitchUrl = twitchUrl;
    }

    public StreamEvent(String platform, String channel, String title, String url, boolean live) {
        this(platform, channel, title, url, live, "", "", "");
    }

    public String getPlatform() { return platform; }
    public String getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public boolean isLive() { return live; }
    public String getVkUrl() { return vkUrl; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getTwitchUrl() { return twitchUrl; }
}
