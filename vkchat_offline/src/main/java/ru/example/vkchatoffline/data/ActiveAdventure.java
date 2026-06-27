package ru.example.vkchatoffline.data;

import java.util.UUID;

public class ActiveAdventure {
    public final int vkId;
    public final UUID uuid;
    public final String playerName;
    public final String route;
    public final long startTime;
    public long hardDeadline;

    public int stage;
    public int maxStages;
    public int hp;
    public int maxHp;
    public long nextEventTime;
    public boolean waitingChoice;
    public long choiceDeadline;
    public String pendingType;
    public String pendingTitle;
    public int supplies;
    public int morale;
    public int xpGained;
    public int inspiration;
    public String condition;
    public int deathSavesUsed;
    public int gold;
    public int relics;
    public String blessing;
    public int sanity;
    public String campaignChapter;

    public ActiveAdventure(int vkId, UUID uuid, String playerName, String route, long startTime) {
        this.vkId = vkId;
        this.uuid = uuid;
        this.playerName = playerName;
        this.route = route;
        this.startTime = startTime;
        this.stage = 0;
        this.maxStages = 3;
        this.maxHp = 100;
        this.hp = 100;
        this.supplies = 3;
        this.morale = 100;
        this.xpGained = 0;
        this.inspiration = 0;
        this.condition = "none";
        this.deathSavesUsed = 0;
        this.gold = 0;
        this.relics = 0;
        this.blessing = "none";
        this.sanity = 100;
        this.campaignChapter = "";
    }
}
